import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

import org.w3c.dom.Node;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class FS_Node {

    // Connection info
    private String ip;
    private static int tcpPort = 42069;
    private static int udpPort = 65534;
    
    // Files info
    private Map<String,FileInfo> localData;  // Node files
    private static String filesDirectory;

    private Map<Integer,Boolean> portInfo;      
    
    // Constructors

    public FS_Node(String path) {
        this.setIp(null);
        this.localData = new HashMap<>();

        FS_Node.filesDirectory = path;
    
        // Access the files in the specified directory
        File folder = new File(path);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    FileInfo info = new FileInfo(getIp(),file.getName(),file.length(),true);
                    if (file.isFile()) {
                        this.localData.put(file.getName(),info);
                    }
                }
            }
        }

        this.portInfo = new HashMap<>();

        for (int i = 1; i < 65534 ; i++) {
            portInfo.put(i, false);
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

    public Map<String,FileInfo> getLocalData() {
        return this.localData;
    }

    public String getFilesDirectory() {
        return FS_Node.filesDirectory;
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
            for (Entry<String, FileInfo> entry : getLocalData().entrySet()) {
                System.out.println("    " + entry.getKey() + "\n");
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

    private int getUniquePort() {

        for (Map.Entry<Integer, Boolean> entry : portInfo.entrySet()) {
            if (!entry.getValue()) {
                int port = entry.getKey();
                portInfo.put(port, true);
                return port;
            }
        }
        return -1;
    }

    private static void sendAck(int foundLast, DatagramSocket socket, InetAddress address, int port, boolean isClosing) throws IOException {
     
        // Send acknowledgement
        byte[] ackPacket = new byte[3];
        ackPacket[0] = (byte) (foundLast >> 8);
        ackPacket[1] = (byte) (foundLast);

        // Tells if it is closing the socket
        if(isClosing){
            ackPacket[2] = (byte) (1);
        } else {
            ackPacket[2] = (byte) (0);
        }

        // The ack datagram packet to be sent
        DatagramPacket acknowledgement = new DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(acknowledgement);
        
        // For debugging reasons
        System.out.println("Sent ack: Sequence Number = " + foundLast);
    }

    private void sendRequests(String fileName, List<FileInfo> fileLocations, FS_Node node) throws FileNotFoundException {
                          
        class Request implements Runnable {
            
            private String fileName;
            private String nodeAdress;
            private FS_Node node;
            private FileOutputStream out;

    
            public Request(String fileName, String nodeAdress, FS_Node node, FileOutputStream out) {
                this.fileName = fileName;
                this.nodeAdress = nodeAdress;
                this.node = node;
                this.out = out;
            }
    
            @Override
            public void run() {
              try {

                // Create a new socket with a dynamically allocated port
                int localPort = node.getUniquePort();
                DatagramSocket newSocket = new DatagramSocket(localPort);
              
                try {

                    DatagramPacket request = createRequestDatagram(this.fileName,this.nodeAdress,getNodeUdpPort());
                    newSocket.send(request);

                    int seq = 0;
                    int senderPort = 0;
                    
                    // Esperar pelo ack do request
                    while (true) {
                        byte[] ack = new byte[3]; // Create another packet for datagram ackknowledgement
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);
                         
                        int ackSequence = 0;
                        boolean ackRec = false; 

                        try {
                            newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                            newSocket.receive(ackpack);
                            senderPort = ackpack.getPort();
                            ackSequence = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff); // Figuring the sequence number
                            ackRec = true; // We received the ack
                        } catch (SocketTimeoutException e) {
                            System.out.println("Socket timed out waiting for ack");
                            ackRec = false; // We did not receive an ack
                        }

                        // If the package was received correctly 
                        if ((ackSequence == seq+1) && (ackRec)) {
                            seq = ackSequence;
                            System.out.println("Ack received: Sequence Number = " + ackSequence);
                            break;
                        } 
                        // Package was not received, so we resend it
                        else {
                            newSocket.send(request);
                            System.out.println("Resending: Sequence Number = " + seq);
                        }
                    }


                    // Receber dados
                    boolean lastChunk = false;
                    while (!lastChunk) {

                        byte[] message = new byte[1024];

                        DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                        
                        boolean flag = false;
                        while(!flag){
                            try {
                                newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                                newSocket.receive(receivedPacket);
                                flag = true;
                            } catch (SocketTimeoutException e) {
                                System.out.println("Socket timed out waiting for file chunk");
                            }
                        }
                        message = receivedPacket.getData();
                        
                        // Other node info
                        InetAddress address = receivedPacket.getAddress();
                        int port = receivedPacket.getPort();
                        
                        // Retrieves received sequence
                        int sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

                        // Verifies if is eof
                        if(message[2] == (byte) (1)) lastChunk = true;   

                        // Checks the file name lenght and finds the fileName
                        int nameLength = (int) message[3];

                        byte[] fileNameBytes = new byte[nameLength];
                        System.arraycopy(message, 4, fileNameBytes, 0, nameLength); 

                        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

                        // Retrieves received checksum
                        long receivedChecksum =
                                    ((message[1020] & 0xFFL) << 24) |
                                    ((message[1021] & 0xFFL) << 16) |
                                    ((message[1022] & 0xFFL) << 8) |
                                    (message[1023] & 0xFFL);

                        // Calculate checksum
                        CRC32 crc = new CRC32();
                        crc.update(message, 0, 1019); 
                        long checksum = crc.getValue();

                        byte[] fileByteArray = new byte[1016-nameLength];

                        if (sequenceNumber == (seq+1) ) {
                            if(receivedChecksum == checksum){
                                // set the last sequence number to be the one we just received
                                seq = sequenceNumber;
                
                                // Retrieve data from message
                                System.arraycopy(message, 4+nameLength, fileByteArray, 0, 1016-nameLength);
                
                                // Write the retrieved data to the file and print received data sequence number
                                this.out.write(fileByteArray);
                                System.out.println("Received: Sequence number:" + seq);
                
                                // Send acknowledgement
                                sendAck(seq+1, newSocket, address, port, false);
                            
                            } else {
                                System.out.println("Expected checksum number: " + checksum + " but received " + receivedChecksum + ". DISCARDING");
                                // Re send the acknowledgement
                                sendAck(seq, newSocket, address, port, false);
                            }
                        } else {
                            System.out.println("Expected sequence number: " + (seq + 1) + " but received " + sequenceNumber + ". DISCARDING");
                            // Re send the acknowledgement
                            sendAck(seq, newSocket, address, port, false);
                        }
                        // Check for last datagram
                        if (lastChunk) {
                            this.out.close();
                        }

                    } 
                    
                    int isClosing = 0;
                    // Receber ack de fecho e enviar ack correspondente e fechar socket
                    while (true) {
                        byte[] ack = new byte[3]; // Create another packet for datagram ackknowledgement
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);
                         
                        int ackSequence = 0;
                        boolean ackRec = false; 

                        try {
                            newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                            newSocket.receive(ackpack);
                            ackSequence = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff); // Figuring the sequence number
                            isClosing = (int) ack[2];
                            ackRec = true; // We received the ack
                        } catch (SocketTimeoutException e) {
                            System.out.println("Socket timed out waiting for ack");
                            ackRec = false; // We did not receive an ack
                        }

                        // If the package was received correctly 
                        if ((ackSequence == seq+2) && (ackRec) && (isClosing==1)) {
                            seq = ackSequence;
                            System.out.println("Ack received: Sequence Number = " + ackSequence);
                            sendAck(seq+1, newSocket, InetAddress.getByName(this.nodeAdress), senderPort, true);
                            break;
                        } 
                    }
                    
                    newSocket.setSoTimeout(50);
                    newSocket.close();
                    
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
              
              } catch (SocketException e) {
                e.printStackTrace();
              }
            }
        }

        File file = new File(getFilesDirectory() + "/" + fileName);
        FileOutputStream outToFile = new FileOutputStream(file);

        // Para quando formos fazer fragmentacao vamos executar este codigo
        // onde cada thread vai buscar um certo chunck a um certo nodo
        int poolSize = 1; 
        ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);

        Runnable task = new Request(fileName,fileLocations.get(0), node, outToFile);

        threadPool.submit(task);

        threadPool.shutdown();
    }
    
    public DatagramPacket createRequestDatagram (String fileName, String nodeIp, int port) throws UnknownHostException {

        InetAddress receiverAddress = InetAddress.getByName(nodeIp);

        int sequenceNumber = 0;
        
        byte[] message = new byte[1024]; 
        message[0] = (byte) (sequenceNumber >> 8);
        message[1] = (byte) (sequenceNumber);
        // Signaling that this is not eof
        message[2] = (byte) (0);
            
        // File name prep
        byte[] fileNameBytes = fileName.getBytes();
        int nameLength = fileNameBytes.length;
        message[3] = (byte) nameLength;
        System.arraycopy(fileNameBytes, 0, message, 4, nameLength);

        // Calculate checksum of the packet
        CRC32 crc = new CRC32();
        crc.update(message, 0, 1019); 
        long checksum = crc.getValue();

        // Append the checksum to the message
        message[1020] = (byte) (checksum >> 24);
        message[1021] = (byte) (checksum >> 16);
        message[1022] = (byte) (checksum >> 8);
        message[1023] = (byte) checksum;

        return new DatagramPacket(message, message.length, receiverAddress, port);
    }

    public int sendFile(DatagramSocket newSocket, InetAddress senderAddress, int port, int sequence, String fileName, byte[] fileBytes, long startPosition, long endPosition) throws IOException {
        
        int ackSequence = 0; 
        int sequenceNumber = sequence; 

        // File name prep
        byte[] fileNameBytes = fileName.getBytes();
        int nameLength = fileNameBytes.length;
        
        if(fileBytes.length < endPosition) return -1;

        for (int i = (int) startPosition; i < (int) endPosition; i = i + 1016-nameLength) {
            sequenceNumber += 1;

            // Create message
            byte[] message = new byte[1024]; // First two bytes of the data are for control (datagram integrity and order)
            
            // Write the sequence
            message[0] = (byte) (sequenceNumber >> 8);
            message[1] = (byte) (sequenceNumber);

            // File Name injection
            message[3] = (byte) nameLength;
            System.arraycopy(fileNameBytes, 0, message, 4, nameLength);

            // Check if it is eof
            if((i + 1016-nameLength) >= endPosition){
                message[2] = (byte) (1); // last datagram to be send
                System.arraycopy(fileBytes, i, message, 4+nameLength, (int) (endPosition - i));
            } else {
                message[2] = (byte) (0); // still sending datagrams
                System.arraycopy(fileBytes, i, message, 4+nameLength, 1016-nameLength);
            }

            // Calculate checksum
            CRC32 crc = new CRC32();
            crc.update(message, 0, 1019); 
            long checksum = crc.getValue();

            // Append the checksum to the message
            message[1020] = (byte) (checksum >> 24);
            message[1021] = (byte) (checksum >> 16);
            message[1022] = (byte) (checksum >> 8);
            message[1023] = (byte) checksum;

            // Sending the data
            
            System.out.println("Sending file chunk number" + i);
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, senderAddress, port);
            newSocket.send(sendPacket); 
            
            boolean ackRec;

            System.out.println("Waiting for ack from chunk number" + i);
            while (true) {
                byte[] ack = new byte[3]; // Create another packet for datagram ackknowledgement
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                    newSocket.receive(ackpack);
                    ackSequence = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff); // Figuring the sequence number
                    ackRec = true; // We received the ack
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out waiting for ack");
                    ackRec = false; // We did not receive an ack
                }

                // If the package was received correctly next packet can be sent
                if ((ackSequence == sequenceNumber+1) && (ackRec)) {
                    System.out.println("Ack received: Sequence Number = " + ackSequence);
                    break;
                } 
                // Package was not received, so we resend it
                else {
                    newSocket.send(sendPacket);
                    System.out.println("Resending: Sequence Number = " + sequenceNumber);
                }
            }
        }

        return ackSequence+1;
    }

    //  Recebe os primeiros packets vindos de um qualquer nodo referentes a pedidos e aloca uma
    // thread que cria um socket novo com um porta diferente onde vai acontecer
    // a conexao entre os dois nodos.
    public void processRequests(byte[] firstMessage, InetAddress senderAddress, int port) {
        
        try {
            
            // Create a new socket with a dynamically allocated port
            int localPort = this.getUniquePort();
            DatagramSocket newSocket = new DatagramSocket(localPort);

            // Create a thread to handle communication on the new socket
            Thread communicationThread = new Thread(() -> {
                
                try {
                    
                    System.out.println("Processing request from node.");

                    Boolean passedFirstPacket = false;
                    Boolean newPacketFlag = false;

                    int lastSequence = 0;

                    while(!passedFirstPacket) {

                        byte[] message = new byte[1024];

                        if(newPacketFlag){
                             
                            // Receive packet and retrieve the data
                            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                            newSocket.receive(receivedPacket);
                            message = receivedPacket.getData(); 

                        } else {
                            System.arraycopy(firstMessage,0,message,0,1024);
                        }

                        // Retrieves received sequence
                        int sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

                        // Checks the file name lenght and finds the fileName
                        int nameLength = (int) message[3];

                        byte[] fileNameBytes = new byte[nameLength];
                        System.arraycopy(message, 4, fileNameBytes, 0, nameLength); 

                        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
 
                        int nextPosition = 4 + nameLength;

                        // Retrieves received checksum
                        long startPosition =
                                    ((message[nextPosition+1] & 0xFFL) << 24) |
                                    ((message[nextPosition+2] & 0xFFL) << 16) |
                                    ((message[nextPosition+3] & 0xFFL) << 8) |
                                    (message[nextPosition+4] & 0xFFL);

                        // Retrieves received checksum
                        long bytesToCopy =
                                    ((message[nextPosition+5] & 0xFFL) << 24) |
                                    ((message[nextPosition+6] & 0xFFL) << 16) |
                                    ((message[nextPosition+7] & 0xFFL) << 8) |
                                    (message[nextPosition+8] & 0xFFL);            

                        // Retrieves received checksum
                        long receivedChecksum =
                                    ((message[1020] & 0xFFL) << 24) |
                                    ((message[1021] & 0xFFL) << 16) |
                                    ((message[1022] & 0xFFL) << 8) |
                                    (message[1023] & 0xFFL);

                        // Calculate checksum
                        CRC32 crc = new CRC32();
                        crc.update(message, 0, 1019); 
                        long checksum = crc.getValue();

                        // Check if sequence is correct
                        if ((receivedChecksum == checksum)) {
                            
                            System.out.println("Sending ack for node request.");
                            
                            sendAck(sequenceNumber+1, newSocket, senderAddress, port, false);

                            passedFirstPacket = true;

                            File requestedFile = new File(this.getFilesDirectory() + "/" + fileName);
                            byte[] fileByteArray = readFileToByteArray(requestedFile);

                            System.out.println("Now sending file.");

                            lastSequence = sendFile(newSocket, senderAddress, port, sequenceNumber+1, fileName, fileByteArray, startPosition, bytesToCopy);

                            System.out.println("File sent.");
                            
                        } else {
                                
                            System.out.println("Expected chekcsum number: " + receivedChecksum + " but calculated " + checksum + ". DISCARDING");
                            
                            System.out.println("Asking node to resend the request.");

                            sendAck(sequenceNumber, newSocket, senderAddress, port, false);

                            newPacketFlag = true;    
                        }        
                    }                

                    System.out.println("Socket closing, sendind closing ack.");
                    // Tem de enviar ack a dizer que vai fechar
                    sendAck(lastSequence, newSocket, senderAddress, port, true);

                    System.out.println("Waiting for last ack.");
                    // Tem de receber ack a dizer que a mensagem foi recebida e que o outro nodo tambem vai fechar?
                    while (true) {
                        
                        byte[] ack = new byte[3]; // Create another packet for datagram ackknowledgement
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                        int ackSequence = 0;
                        boolean ackRec = false;
                        int conn = 0;

                        try {
                            newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                            newSocket.receive(ackpack);
                            ackSequence = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff); // Figuring the sequence number
                            conn = (int) ack[2];
                            ackRec = true; // We received the ack
                        } catch (SocketTimeoutException e) {
                            System.out.println("Socket timed out waiting for ack");
                            ackRec = false; // We did not receive an ack
                        }

                        // If the package was received correctly 
                        if ((ackSequence == lastSequence+1) && (ackRec) && (conn == 1)) {
                            System.out.println("Ack received: Sequence Number = " + ackSequence);
                            break;
                        }    
                        // Package was not received, so we resend it
                        else {
                            sendAck(lastSequence, newSocket, senderAddress, port, true);
                            System.out.println("Resending: Sequence Number = " + lastSequence);
                        }
                    }


                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } finally {
                    // Close the socket when done
                    System.out.println("Closing the secondary socket.");
                    newSocket.close();
                    this.portInfo.put(localPort, false);
                }
            });
            communicationThread.start();
                       
        } catch (Exception e) {
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
            System.out.println("\u001B[32m Node connected to tracker.\u001B[0m\n");
            
            // Cria os pipes de escrita e leitura
            ObjectOutputStream output = new ObjectOutputStream(serverSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serverSocket.getInputStream());

            // Recebe o seu endereco de ip
            String nodeIp = (String) input.readObject();
            node.setIp(nodeIp);
            //System.out.println("Received IP address from tracker: " + nodeIp);

            // Notifica o servidor dos ficheiros que tem armazenados
            output.writeObject(node.getLocalData());

            DatagramSocket udpSocket = new DatagramSocket(getNodeUdpPort());

            Thread udpRequestsThread = new Thread( () -> {
                byte[] message = new byte[1024];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(message, message.length);
                    
                    try {
                        udpSocket.receive(packet);
                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
        
                        System.out.println("Received request from other node!");

                        // Process the received datagram
                        node.processRequests(packet.getData(), address, port);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
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
                            List<FileInfo> fileLocations = (List<FileInfo>)input.readObject();  // Endereços IP dos nodos onde está o ficheiro

                            if (fileLocations.isEmpty()) {
                            
                                System.out.println("\u001B[31m The requested file is not available in any nodes. \u001B[0m\n");
                            
                            } else {
                            
                                System.out.println("File location: " + fileLocations.get(0) + "\n");
                                // Temos de desenvolver algoritmo para verificar onde vamos buscar cada chunk do ficheiro
                            
                                System.out.println("Sending requests.");
                                node.sendRequests(fileName,fileLocations,node);

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