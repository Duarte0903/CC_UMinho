import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

import org.w3c.dom.Node;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class FS_Node {

    // Connection info
    private String ip;
    private static int tcpPort = 42069;
    private static int udpPort = 65534;
    private ObjectOutputStream outputToTracker;

    // Files info
    private Map<String,FileInfo> localData;  // Node files
    private static String filesDirectory;

    // UDP ports info
    private Map<Integer,Boolean> portInfo;      
    
    // Constructors

    public FS_Node(String path) {
        this.setIp(null);
        this.localData = new HashMap<>();
        FS_Node.setFilesDirectory(path);
    
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

    public Map<String, FileInfo> getLocalData() {
        return localData;
    }
    
    public String getFilesDirectory() {
        return FS_Node.filesDirectory;
    }
    
    // Setters
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public void setLocalData(Map<String,FileInfo> localData) {
        this.localData = localData;
    }

    public void setOutputToTracker(ObjectOutputStream outputToTracker) {
        this.outputToTracker = outputToTracker;
    }

    public static void setFilesDirectory(String filesDirectory) {
        FS_Node.filesDirectory = filesDirectory;
    }
    
    // Methods

    public void sendDataBytes(ObjectOutputStream out) throws IOException {
        
        int numFiles = this.localData.keySet().size();
        out.writeInt(numFiles);
        out.flush();
        
        // Storing data in the byte array
        for (Map.Entry<String, FileInfo> entry : this.localData.entrySet()) {
            
            FileInfo aux = entry.getValue();
            
            // File name prep
            String fileName = entry.getKey();
            byte[] fileNameBytes = fileName.getBytes();
            int nameLength = fileNameBytes.length;
            //System.out.println(fileName);
            
            // File size
            long size = aux.getSize();
            //System.out.println(size);
            //System.out.println();
            
            byte[] info = new byte[5+nameLength];
            
            // Storing file name
            info[0] = (byte) nameLength;
            System.arraycopy(fileNameBytes, 0, info, 1, nameLength);

            // Storing the file size
            info[1+nameLength] = (byte) (size >> 24);
            info[2+nameLength] = (byte) (size >> 16);
            info[3+nameLength] = (byte) (size >> 8);
            info[4+nameLength] = (byte) size;

            out.writeInt(info.length);
            out.write(info);
            out.flush();
            
        }

    }

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

    public static byte[] readFileToByteArray(File file) {
        
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

    public int getUniquePort() {

        for (Map.Entry<Integer, Boolean> entry : this.portInfo.entrySet()) {
            if (!entry.getValue()) {
                int port = entry.getKey();
                this.portInfo.put(port, true);
                return port;
            }
        }
        return -1;
    }

    public static void sendAck(int foundLast, DatagramSocket socket, InetAddress address, int port, boolean isClosing) throws IOException {
     
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

    /* 
    public String chooseNode(){
        String selectedNode = null;

                // Check for an unchosen node in the available nodes
                for (String entry : nodes) {
                    if (!alreadyChoosen.contains(node)) {
                        selectedNode = entry;
                        alreadyChoosen.add(entry);
                    }
                }

                boolean foundNode = false;
                // If all nodes are already chosen, rotate the chosen nodes
                if (!alreadyChoosen.isEmpty() && !foundNode) {
                    // Remove the first chosen node and add it to the end
                    String rotatedNode = alreadyChoosen.remove(0);
                    alreadyChoosen.add(rotatedNode);
                    selectedNode = rotatedNode;
                }

        return null;        
    }
    */
    
    public int sendRequests(FS_Node node, String fileName, List<FileInfo> fileLocations) throws FileNotFoundException {
        
        // Return value
        int value = 1;

        // File info
        int lastChunk = fileLocations.get(0).getLastChunk();
        long size = fileLocations.get(0).getSize();

        // Creating the file in which we will write
        File file = new File(getFilesDirectory() + "/" + fileName);
        FileOutputStream outToFile = new FileOutputStream(file);

        // Map to hold which nodes have the key packet
        Map<Integer,List<String>> info = new HashMap<>();

        // Inserting info into the map
        for(int i = 0; i<fileLocations.size(); i++){
            
            FileInfo nodeInfo = fileLocations.get(i);
            List<Boolean> aux = nodeInfo.getChunks();

            for(int j = 0; j <= lastChunk; j++){
                if(aux.get(j)==true){
                    info.get(j).add(nodeInfo.getHostIp());
                }
            }    
        }
        
        // Creating thread pool of workers
        int poolSize = 10; 
        ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
        
        List<String> alreadyChoosen = new ArrayList<>();
        // Algorithm to choose which node to request given chunk
        for(int chunk = 0; chunk <= lastChunk; chunk++){
            
            List<String> nodes = info.get(chunk);
              
            if (nodes != null && !nodes.isEmpty()) {
                
                String selectedNode = nodes.get(0);
        
                if (selectedNode != null) {

                    // Calculating where we start to read
                    long startPosition = chunk * 5000;
                    long endPosition = startPosition + 5000;

                    if(endPosition>=size) endPosition = size;

                    // Perform the task with the selected node
                    Runnable task = new Request(node,fileName, selectedNode, startPosition, endPosition, outToFile);
                    threadPool.submit(task);
                } else {
                    // Handle the case where no node can be chosen
                    System.out.println("No available node in chunk!" + chunk);
                }
            } else {
                System.out.println("Chunk does no exist in any node!" + chunk);
            }
        }

        // Sutting down thread pool
        threadPool.shutdown();
        
        return value;
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

    private List<FileInfo> getFileLocations(byte[] file, int numNodes) throws IOException {
        
        List<FileInfo> filesArray = new ArrayList<>();

        int infoSize = file.length;

        int pos = 0;
        while(numNodes > 0 && pos < infoSize){        
            System.out.println();
            System.out.println("ciclo a comecar, pos: "+ pos);

            // HostIp 
            int hostLength = file[pos];
            byte[] filehost = new byte[hostLength];
            System.arraycopy(file, pos+1, filehost, 0, hostLength);
            String hostIp = new String(filehost);
            System.out.println(hostIp);

            // File name 
            int nameLength = file[pos+1+hostLength];
            byte[] fileNameBytes = new byte[nameLength];
            System.arraycopy(file, pos+2+hostLength, fileNameBytes, 0, nameLength);
            String fileName = new String(fileNameBytes);
            System.out.println(fileName);
            
            // File size
            long size = ((file[pos+2+hostLength+nameLength] & 0xFFL) << 24) |
                        ((file[pos+3+hostLength+nameLength] & 0xFFL) << 16) |
                        ((file[pos+4+hostLength+nameLength] & 0xFFL) << 8) |
                        ( file[pos+5+hostLength+nameLength] & 0xFFL);
            System.out.println(size);
            
            // Chunk list
            
            int listsize = ((file[pos+6+hostLength+nameLength] & 0xFF) << 24) |
                            ((file[pos+7+hostLength+nameLength] & 0xFF) << 16) |
                            ((file[pos+8+hostLength+nameLength] & 0xFF) << 8) |
                            (file[pos+9+hostLength+nameLength] & 0xFF);
            System.out.println("Given size: " + listsize);
            System.out.println("Calculated size: " + ((size/5000)+1));

            List<Boolean> list = new ArrayList<>();
            
            int counter = pos+10+hostLength+nameLength;
            while (counter<listsize+pos+10+hostLength+nameLength) {
            
                int bool = (int) file[counter];
                //System.out.println(bool);
                
                if(bool==1){
                  list.add(true);
                }
                else{
                  list.add(false);
                }

                counter++;
            }
            //System.out.println("CHEGUEI AQUI"+hostIp);

            FileInfo fileInfo = new FileInfo(hostIp, fileName, size, false);

            fileInfo.setChunks(list);

            filesArray.add(fileInfo);

            numNodes--;
            pos = pos + listsize+10+hostLength+nameLength;
            System.out.println(pos);
        }
            
        return filesArray;
    }

    public static void main(String[] args) {
        String serverIp = "10.0.1.10";
        int serverPort = 42069;

        // Creates node
        FS_Node node = new FS_Node(args[0]);
    
        // Establishes connection with server
        try {
            Socket serverSocket = new Socket(serverIp, serverPort);
            System.out.println("\u001B[32m Node connected to tracker.\u001B[0m\n");
            
            // Creates pipes
            ObjectOutputStream output = new ObjectOutputStream(serverSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serverSocket.getInputStream());

            // Setting node output to Tracker
            node.setOutputToTracker(output);

            // Receives his ip adress
            String nodeIp = (String) input.readObject();
            node.setIp(nodeIp);

            // Notifies server of the files that the node has
            node.sendDataBytes(output);
            
            // Opens main UDP socket
            DatagramSocket udpSocket = new DatagramSocket(getNodeUdpPort());

            // Thread that handles new incoming request from other nodes
            Thread udpRequestsThread = new Thread( () -> {
                byte[] message = new byte[1024];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(message, message.length);

                    // Receiving packet
                    try {
                        udpSocket.receive(packet);
                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
        
                        System.out.println("Received request from other node!");

                        // Process the received packet
                        node.processRequests(packet.getData(), address, port);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            udpRequestsThread.start();

            // Reading the inputs in the terminal
            while (true) {           
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String inp = reader.readLine();
                    
                    String[] commandParts = inp.split(" ");
                    String commandName = commandParts[0];
                    List<String> commandArguments = Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length));
                        
                    switch (commandName) {
                        // User want to donwload a file
                        case "get": 
                            String fileName = commandArguments.get(0);
                            output.writeObject("get " + fileName);

                            int numNodes = input.readInt();
                            
                            if(numNodes>0){

                                List<FileInfo> files = new ArrayList<>();

                                int dataSize = input.readInt();
                                System.out.println("Data size: " + dataSize);
                                byte[] file = new byte[dataSize]; 
                                input.readFully(file);

                                // Reading byte array with the information about the file locations
                                List<FileInfo> fileLocation = node.getFileLocations(file, numNodes); 
                    
                                System.out.println("Sending requests.");
                                //node.sendRequests(node,fileName,fileLocations); 
                            
                            } else{
                                System.out.println("\u001B[31m The requested file is not available in any nodes. \u001B[0m\n");
                            } 

                            break;

                        // User wants to know with files they have
                        case "printFiles":
                            node.printFileNames();
                            break;
                        
                        // User want to exit connections
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