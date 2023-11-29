import java.util.*;
import java.util.zip.CRC32;
import java.io.*;
import java.net.*;

public class FS_Node {

    // Connection info
    private String ip;
    private static int tcpPort = 42069;
    private static int udpPort = 65535;
    
    // Files info
    private List<String> localData;  // Node files
    private static String filesDirectory;
    
    // Constructors

    public FS_Node(String path) {
        this.setIp(null);
        this.localData = new ArrayList<>();

        FS_Node.filesDirectory = path;
    
        // Access the files in the specified directory
        File folder = new File(path);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        this.localData.add(file.getName());
                    }
                }
            }
        }
    }
    
    // Getters

    public String getIp() {
        return this.ip;
    }

    public static int getNodeTcpPort() {
        return tcpPort;
    }

    public static int getNodeUdpPort() {
        return udpPort;
    }

    public List<String> getLocalData() {
        return this.localData;
    }

    public String getFilesDirectory() {
        return this.filesDirectory;
    }

    // Setters

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setLocalData(List<String> localData) {
        this.localData = localData;
    }

    // Methods

    public void printFileNames() {
        if (this.localData.isEmpty()) {
            System.out.println("\nNo files in node database.\n");
        } else {
            System.out.println("\nFile names in node database: \n\n");
            for (String fileName : localData) {
                System.out.println("    " + fileName + "\n");
            }
        }
    }

    private static byte[] readFileToByteArray(File file) {
        
        FileInputStream fis = null;
        // Creating a byte array using the length of the file
        byte[] bArray = new byte[(int) file.length()];
        
        try {
            fis = new FileInputStream(file);
            fis.read(bArray);
            fis.close();

        } catch (IOException ioExp) {
            ioExp.printStackTrace();
        }
        return bArray;
    }

    private static void sendAck(int foundLast, DatagramSocket socket, InetAddress address, int port) throws IOException {
     
        // Send acknowledgement
        byte[] ackPacket = new byte[2];
        ackPacket[0] = (byte) (foundLast >> 8);
        ackPacket[1] = (byte) (foundLast);
        
        // The ack datagram packet to be sent
        DatagramPacket acknowledgement = new DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(acknowledgement);
        
        // For debugging reasons
        System.out.println("Sent ack: Sequence Number = " + foundLast);
    }

    public void sendRequest(DatagramSocket udpSocket, String nodeName, String fileName) {
        // Needs to check sequence, acks amd checksum
        try {
            InetAddress receiverAddress = InetAddress.getByName(nodeName);
            
            int sequenceNumber = 0;
            

            byte[] message = new byte[1024]; 
            message[0] = (byte) (sequenceNumber >> 8);
            message[1] = (byte) (sequenceNumber);
            message[2] = (byte) (1);
            // Signaling that this packet is a request
            message[3] = (byte) (1);
            
            // Preparing the request
            String requestMessage = "FileTransferRequest:" + fileName;
            byte[] requestData = requestMessage.getBytes();
            System.arraycopy(requestData, 0, message, 4, requestData.length);

            // Calculate checksum of the packet
            CRC32 crc = new CRC32();
            crc.update(message, 0, 1020); 
            long checksum = crc.getValue();

            // Append the checksum to the message
            message[1021] = (byte) (checksum >> 24);
            message[1022] = (byte) (checksum >> 16);
            message[1023] = (byte) (checksum >> 8);
            message[1024] = (byte) checksum;

            // Sending the data
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, receiverAddress, this.getNodeUdpPort());
            udpSocket.send(sendPacket); 
            // For debuging reasons
            System.out.println("Sent: Sequence number = " + sequenceNumber);
            
            // Verifieing if the other node received the packet
            int ackSequence = 0;
            boolean ack; 

            while (true) {
                // Create another packet for ack
                byte[] ackInfo = new byte[2]; 
                DatagramPacket ackpack = new DatagramPacket(ackInfo, ackInfo.length);

                try {
                    // Waiting for the other node to send the ack
                    udpSocket.setSoTimeout(50); 
                    udpSocket.receive(ackpack);

                    ackSequence = ((ackInfo[0] & 0xff) << 8) + (ackInfo[1] & 0xff); // Figuring the sequence number
                    ack = true; 
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out waiting for ack");
                    ack = false; 
                }

                // If the package was received correctly next packet can be sent
                if ((ackSequence == sequenceNumber) && (ack)) {
                    // For debugging reasons
                    System.out.println("Ack received: Sequence Number = " + ackSequence);
                    break;
                } 
                // Package was not received, so we resend it
                else {
                    udpSocket.send(sendPacket);
                    // For debugging reasons
                    System.out.println("Resending: Sequence Number = " + sequenceNumber);
                }
            }
        }  catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendFile(DatagramSocket udpSocket, byte[] fileBytes, InetAddress senderAddresss, int port) throws IOException {
        
        int sequenceNumber = 0; // For order
        boolean flag; // To see if we got to the end of the file
        int ackSequence = 0; // To see if the datagram was received correctly

        for (int i = 0; i < fileBytes.length; i = i + 1017) {
            sequenceNumber += 1;

            // Create message
            byte[] message = new byte[1024]; // First two bytes of the data are for control (datagram integrity and order)
            message[0] = (byte) (sequenceNumber >> 8);
            message[1] = (byte) (sequenceNumber);

            if ((i + 1021) >= fileBytes.length) { 
                flag = true;
                message[2] = (byte) (1); // We reached the end of the file (last datagram to be send)
            } else {
                flag = false;
                message[2] = (byte) (0); // We haven't reached the end of the file, still sending datagrams
            }

            // Signaling that this packet is not a request
            message[3] = (byte) (0);

            // Checks if it is the last datagram
            if (!flag) {
                System.arraycopy(fileBytes, i, message, 4, 1017);
            } else { 
                System.arraycopy(fileBytes, i, message, 4, fileBytes.length - i);
            }

            // Calculate checksum
            CRC32 crc = new CRC32();
            crc.update(message, 0, 1020); 
            long checksum = crc.getValue();

            // Append the checksum to the message
            message[1021] = (byte) (checksum >> 24);
            message[1022] = (byte) (checksum >> 16);
            message[1023] = (byte) (checksum >> 8);
            message[1024] = (byte) checksum;

            // Sending the data
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, senderAddresss, port);
            udpSocket.send(sendPacket); 
            System.out.println("Sent: Sequence number = " + sequenceNumber);

            boolean ack; 

            while (true) {
                // Create another packet for ack
                byte[] ackInfo = new byte[2]; 
                DatagramPacket ackpack = new DatagramPacket(ackInfo, ackInfo.length);

                try {
                    // Waiting for the other node to send the ack
                    udpSocket.setSoTimeout(50); 
                    udpSocket.receive(ackpack);

                    ackSequence = ((ackInfo[0] & 0xff) << 8) + (ackInfo[1] & 0xff); // Figuring the sequence number
                    ack = true; 
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out waiting for ack");
                    ack = false; 
                }

                // If the package was received correctly next packet can be sent
                if ((ackSequence == sequenceNumber) && (ack)) {
                    // For debugging reasons
                    System.out.println("Ack received: Sequence Number = " + ackSequence);
                    break;
                } 
                // Package was not received, so we resend it
                else {
                    udpSocket.send(sendPacket);
                    // For debugging reasons
                    System.out.println("Resending: Sequence Number = " + sequenceNumber);
                }
            }
        }
    }

    // Por completar
    public void handleUdpRequests(boolean isRequest, String nodeName, String fileName, DatagramSocket udpSocket) {
        
        try {
    
            Map<String,Integer> sequenceInfo = new HashMap<>(); // Key: Node name
                                                                // Value: Last node sequence value

            boolean eofFlag; 
            int sequenceNumber = 0; 
            int foundLast = 0; 
            
            if (isRequest) {
                sendRequest(udpSocket, nodeName, fileName);
            }

            while (true && !isRequest) {
                byte[] message = new byte[1024]; 
                byte[] fileByteArray = new byte[1017]; 

                
                // Receive packet and retrieve the data
                DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                udpSocket.receive(receivedPacket);
                message = receivedPacket.getData(); 

                // Get port and address for sending acknowledgment
                InetAddress address = receivedPacket.getAddress();
                int port = receivedPacket.getPort();

                // Retrieves received sequence
                sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);
                
                // Retrieves received checksum
                long receivedChecksum =
                        ((message[1019] & 0xFFL) << 24) |
                        ((message[1020] & 0xFFL) << 16) |
                        ((message[1021] & 0xFFL) << 8) |
                        (message[1022] & 0xFFL);

                // Check if we reached EOF
                eofFlag = (message[2] & 0xff) == 1;
                
                // Calculate checksum
                CRC32 crc = new CRC32();
                crc.update(message, 0, 1020); 
                long checksum = crc.getValue();

                // Check if sequence is correct
                if (sequenceNumber == (foundLast + 1) && (receivedChecksum == checksum)) {
                                    
                    // Store the sequence number
                    foundLast = sequenceNumber;

                    // Retrieve data from message
                    System.arraycopy(message, 4, fileByteArray, 0, 1017);
                    
                    if(message[3] != (byte) (1)){ // Se o packet for referente a um ficheiro que pedimos
                                                  // Apenas podemos receber um ficheiro de cada vez
                        
                        // Write the retrieved data to the file and print received data sequence number
                        //outToFile.write(fileByteArray);
                        //System.out.println("Received: Sequence number:" + foundLast);

                        // Check for last datagram
                        //if (eofFlag) {
                        //    outToFile.close();
                        //    break;
                        //}
                    }
                    // Se for referente a um pedido por parte de um node qualquer
                    else { // Podemos receber varios request ao mesmo tempos e temos de poder enviar varios ficheiro ao mesmo tempo   
                        
                        //sendAck(foundLast, udpSocket, address, port);
                        String receivedMessage = new String(fileByteArray);

                        if (receivedMessage.startsWith("FileTransferRequest:")) {
                            String[] parts = receivedMessage.split(":");
                            String requestedFileName = parts[1];
                        
                            if (this.getLocalData().contains(requestedFileName)) {
                
                                File requestedFile = new File(this.getFilesDirectory() + requestedFileName);
                                byte[] fileByte = readFileToByteArray(requestedFile);
                                
                                Thread sendFileToNode = new Thread ( () -> {
                                    try {
                                        sendFile(udpSocket,fileByte,address,port);
                                    } catch (IOException e) {
                                        System.out.println("Could not send file due to IOException in thread.");
                                        e.printStackTrace();
                                    }    
                                });
                                sendFileToNode.start();
                            }
                        }
                    }
                    // Send ack
                    //sendAck(foundLast, udpSocket, address, port);
                        
                } else {
                    if(sequenceNumber != (foundLast + 1)){
                       System.out.println("Expected sequence number: " + (foundLast + 1) + " but received " + sequenceNumber + ". DISCARDING");
                       // Resend the ack
                       sendAck(foundLast, udpSocket, address, port);
                    }
                    else {
                       System.out.println("Expected chekcsum number: " + receivedChecksum + " but calculated " + checksum + ". DISCARDING");
                       // Resend the ack
                       sendAck(foundLast, udpSocket, address, port);
                    }
                
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String serverIp = "10.0.1.10";
        int serverPort = 42069;

        // Cria a instancia Node
        FS_Node node = new FS_Node(args[0]);
    
        // Estabelece a conecxao com o Servidor
        try {
            Socket serverSocket = new Socket(serverIp, serverPort);
            System.out.println("\u001B[32mNode connected to tracker.\u001B[0m\n");
            
            // Cria os pipes de escrita e leitura
            ObjectOutputStream output = new ObjectOutputStream(serverSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serverSocket.getInputStream());

            // Recebe o seu endereco de ip
            String nodeIp = (String) input.readObject();
            node.setIp(nodeIp);
            System.out.println("Received IP address from tracker: " + nodeIp);

            // Notifica o servidor dos ficheiros que tem armazenados
            output.writeObject(node.getLocalData());

            DatagramSocket udpSocket = new DatagramSocket(getNodeUdpPort());

            Thread udpRequestsThread = new Thread( () -> {
                node.handleUdpRequests(false, null, null, udpSocket);
            });
            udpRequestsThread.start();

            while (true) {           
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String inp = reader.readLine();
                    
                    String[] commandParts = inp.split(" ");
                    String commandName = commandParts[0];
                    List<String> commandArguments = Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length));
                        
                    switch (commandName) {
                        case "get": 
                            String fileName = commandArguments.get(0);
                            output.writeObject("get " + fileName);
                            List<String> fileLocations = (List<String>)input.readObject();  // Endereços IP dos nodos onde está o ficheiro

                            if (fileLocations.isEmpty()) {
                                System.out.println("\u001B[31m The requested file is not available in any nodes. \u001B[0m\n");
                            }

                            else {
                                System.out.println("File location: " + fileLocations.get(0) + "\n");
                                // Temos de desenvolver algoritmo para verificar onde vamos buscar cada chunk do ficheiro
                                node.handleUdpRequests(true,fileLocations.get(0), fileName, udpSocket);
                            }

                            break;

                        case "printFiles":
                            node.printFileNames();
                            break;
                        
                        case "exit":
                            output.writeObject(commandName);
                            serverSocket.close();
                            System.out.println("Node is exiting.");
                            System.exit(0);
                            break;
                        
                        default:
                            System.out.println("Unknown command: " + input);
                            break;
                    }
                    
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.out.println("\u001B[31mNode failed to connect to tracker. \u001B[0m\n");
            System.err.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}