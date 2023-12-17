import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class Request implements Runnable{
            
    // Node info
    private FS_Node node;

    // Receiver node info
    private String receiverIp;

    // File related info
    private String fileName;
    private long startPosition;
    private long endPosition;
    private FileOutputStream outputToFile;

    public Request(FS_Node node,String fileName, String receiverIp, long startPosition, long endPosition, FileOutputStream out) {
        this.node = node;
        this.receiverIp = receiverIp;
        this.fileName = fileName;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.outputToFile = out;
    }
            
        @Override
        public void run() {
            try {

            // Create a new socket with a dynamically allocated port
            int localPort = node.getUniquePort();
            DatagramSocket newSocket = new DatagramSocket(localPort);
              
            try {

                DatagramPacket request = node.createRequestDatagram(this.fileName,receiverIp,FS_Node.getNodeUdpPort());
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

                    InetAddress address;

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
                        address = receivedPacket.getAddress();
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
                                this.outputToFile.write(fileByteArray);
                                System.out.println("Received: Sequence number:" + seq);
                
                                // Send acknowledgement
                                node.sendAck(seq+1, newSocket, address, port, false);
                            
                            } else {
                                System.out.println("Expected checksum number: " + checksum + " but received " + receivedChecksum + ". DISCARDING");
                                // Re send the acknowledgement
                                node.sendAck(seq, newSocket, address, port, false);
                            }
                        } else {
                            System.out.println("Expected sequence number: " + (seq + 1) + " but received " + sequenceNumber + ". DISCARDING");
                            // Re send the acknowledgement
                            node.sendAck(seq, newSocket, address, port, false);
                        }
                        // Check for last datagram
                        if (lastChunk) {
                            this.outputToFile.close();
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
                            //node.sendAck(seq+1, newSocket, address, senderPort, true);
                            break;
                        } 
                    }
                    
                    newSocket.setSoTimeout(50);
                    newSocket.close();
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
              
              } catch (SocketException e) {
                e.printStackTrace();
              }
    }
}