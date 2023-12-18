import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private long fileSize;
    private long startPosition;
    private int chunk;
    private long endPosition;
    private RandomAccessFile outputToFile;

    public Request(FS_Node node,String fileName,long fileSize, int chunk, String receiverIp, long startPosition, long endPosition, RandomAccessFile out) {
        this.node = node;
        this.receiverIp = receiverIp;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunk = chunk;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.outputToFile = out;
    }
            
    @Override
    public void run() {
            try {

            // Create a new socket with a dynamically allocated port
            int localPort = node.getUniquePort();
            //System.out.println(localPort);
            DatagramSocket newSocket = new DatagramSocket(localPort);
              
            try {

                DatagramPacket request = node.createRequestDatagram(this.fileName,this.receiverIp,FS_Node.getNodeUdpPort(),this.startPosition,this.endPosition);
                newSocket.send(request);
                
                int seq = 0;
                int senderPort = 0;
                
                InetAddress address = null;
            
                // Esperar pelo ack do request
                while (true) {
                    byte[] ack = new byte[3]; // Create another packet for datagram ackknowledgement
                    DatagramPacket ackpack = new DatagramPacket(ack, ack.length);
                         
                    int ackSequence = 0;
                    boolean ackRec = false; 

                    try {
                        newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                        newSocket.receive(ackpack);
                        address = ackpack.getAddress();
                        senderPort = ackpack.getPort();
                        ackSequence = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff); // Figuring the sequence number
                        ackRec = true; // We received the ack
                    } catch (SocketTimeoutException e) {
                            //System.out.println("Socket timed out waiting for ack");
                            ackRec = false; // We did not receive an ack
                    }

                    // If the package was received correctly 
                    if ((ackSequence == seq+1) && (ackRec)) {
                        seq = ackSequence;
                        //System.out.println("Ack received: Sequence Number = " + ackSequence);
                        break;
                    } 
                    // Package was not received, so we resend it
                    else {
                        //System.out.println("A REENVIAR PEDIDO");
                        newSocket.send(request);
                        //System.out.println("Resending: Sequence Number = " + seq);
                    }
                }

                //System.out.println();
                //System.out.println("PEDIDO ENVIADO COM SUCESSO  " + "START: " + startPosition + " END: " + endPosition);
                //System.out.println();

                // Receber dados
                boolean lastChunk = false;
                while (!lastChunk) {

                    // Cria array para guardar mensagem
                    byte[] message = new byte[1024];
                    DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
                    
                    // Espera pela rececao de dados
                    boolean flag = false;
                    while(!flag){
                        try {
                            newSocket.setSoTimeout(50); // Waiting for the server to send the ack
                            newSocket.receive(receivedPacket);
                            flag = true;
                        } catch (SocketTimeoutException e) {
                            //System.out.println("Socket timed out waiting for file chunk");
                        }
                    }
                    if(receivedPacket.getPort() == senderPort){
                    
                        message = receivedPacket.getData();
                        //System.out.println("RECEIVED SOME DATA");
                            
                        // Gets other node info
                        address = receivedPacket.getAddress();
                        int port = receivedPacket.getPort();
                            
                        // Retrieves received sequence
                        int sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

                        // Verifies if is end of receiving data
                        if(message[2] == (byte) (1)) lastChunk = true;   

                        // Checks the file name lenght and finds the fileName
                        int nameLength = (int) message[3];
                        byte[] fileNameBytes = new byte[nameLength];
                        System.arraycopy(message, 4, fileNameBytes, 0, nameLength); 
                        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

                        // Retrieves received checksum
                        long receivedChecksum = ((message[1020] & 0xFFL) << 24) |
                                                ((message[1021] & 0xFFL) << 16) |
                                                ((message[1022] & 0xFFL) << 8) |
                                                (message[1023] & 0xFFL);

                        // Calculate checksum
                        CRC32 crc = new CRC32();
                        crc.update(message, 0, 1019); 
                        long checksum = crc.getValue();

                        // Checks sequence number
                        if (sequenceNumber == (seq+1) ) {
                            // Checks if data is ok
                            if(receivedChecksum == checksum){
                                    
                                    // Set the last sequence number to be the one we just received
                                    //System.out.println("Received: Sequence number:" + seq);
                                    seq = sequenceNumber+1;
                    
                                    // Creates the array that is going to be writen in the file
                                    byte[] fileByteArray = new byte[1016-nameLength];

                                    // Retrieve data from message
                                    if((startPosition + 1016-nameLength) >= endPosition){
                                        System.arraycopy(message, 4+nameLength, fileByteArray, 0, (int)(endPosition-startPosition));
                                        //String temp = new String(fileByteArray);
                                        //System.out.println(temp);
                                        //System.out.println("ESCREVI NA POSICAO " +startPosition+ " A QUANTIDADE: " + (int)(endPosition-startPosition));
                                        //System.out.println();
                                    } else{
                                        System.arraycopy(message, 4+nameLength, fileByteArray, 0, 1016-nameLength);
                                        ///System.out.println();
                                        //System.out.println("ESCREVI DA POSICAO " +startPosition+ " A POSICAO " + (startPosition+1016-nameLength));
                                        //System.out.println();
                                    }
                    
                                    this.node.getLock().lock();
                                    try{
                                        // Finds the position to write
                                        this.outputToFile.seek(startPosition); 
                                        // Writes the data in the file
                                        this.outputToFile.write(fileByteArray);
                                        
                                        this.startPosition += 1016-nameLength;
                                    } finally{
                                        this.node.getLock().unlock();
                                    } 

                                    if(lastChunk){
                                        
                                        node.sendAck(seq, newSocket, address, senderPort, false);                  
                                        
                                        //System.out.println("ULTIMO CHUNK");
                                        
                                        int isClosing = 0;
                                        int tillBreak = 20;
                                        boolean breakFlag = false;
                                        while (true && tillBreak>=0) {
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
                                                //System.out.println("Socket timed out waiting for ack");
                                                ackRec = false; // We did not receive an ack
                                            }

                                            // If the package was received correctly 
                                            if (((ackSequence == seq+1) && (ackRec) && (isClosing==1)) || breakFlag == true) {
                                                if(!breakFlag){
                                                    seq = ackSequence+1;
                                                    //System.out.println("Ack received: Sequence Number = " + ackSequence);
                                                    node.sendAck(seq, newSocket, address, senderPort, true);
                                                } else{
                                                    //System.out.println("Enviar closing ack pela "+ (10-tillBreak) +" vez. Num:"  + ackSequence);
                                                    node.sendAck(seq, newSocket, address, senderPort, true);
                                                    tillBreak--;
                                                }
                                                breakFlag = true;
                                            }
                                            else{
                                                node.sendAck(seq, newSocket, address, senderPort, false);
                                            } 
                                        }
                                    } else{
                                        // Send acknowledgement
                                        node.sendAck(seq, newSocket, address, port, false);
                                    }
                                
                            } else {
                                //System.out.println("Expected checksum number: " + checksum + " but received " + receivedChecksum + ". DISCARDING");
                                // Re send the acknowledgement
                                node.sendAck(seq, newSocket, address, port, false);
                            }
                        } else {
                                //System.out.println("Expected sequence number: " + (seq + 1) + " but received " + sequenceNumber + ". DISCARDING");
                                // Re send the acknowledgement
                                node.sendAck(seq, newSocket, address, port, false);
                        }
                    }

                }

                node.getLock().lock();
                try{
                    String data = "insert " + this.fileName + " " + this.chunk + " " + this.fileSize;
                    node.getOutputToTracker().writeObject(data);
                    //System.out.println(data);
                } finally {
                    node.getLock().unlock();
                }

                int aux = 0;
                if(this.fileSize%5000==0){
                    aux = ((int) (this.fileSize/10000)) - 1;
                } else{
                    aux = (int) (this.fileSize/10000);
                }
                System.out.println("GOT CHUNK: " + this.chunk + " LAST: "+ aux);
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

