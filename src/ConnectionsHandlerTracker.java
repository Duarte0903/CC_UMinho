import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ConnectionsHandlerTracker implements Runnable {
    
    private static Socket nodeSocket;
    private TrackerData localData;
    
    ConnectionsHandlerTracker(Socket nodeSocket, TrackerData localData) {
        ConnectionsHandlerTracker.nodeSocket = nodeSocket;
        this.localData = localData;
    }
    
    public Socket getNodeSocket() {
        return ConnectionsHandlerTracker.nodeSocket;
    }

    public TrackerData getLocalData() {
        return localData;
    }

    public void receiveDataBytes(String nodeIp, byte[] data) {

       // Ler o length do nome
        int fileNameLength = data[0]; 
        //System.out.println(fileNameLength);

        // Ler o nome do ficheiro
        byte[] fileNameBytes = new byte[fileNameLength];
        System.arraycopy(data, 1, fileNameBytes, 0, fileNameLength);
        String fileName = new String(fileNameBytes);
        //System.out.println(fileName);

        // ler o size do ficheiro
        long fileSize = ((data[1+fileNameLength] & 0xFFL) << 24) |
                        ((data[2+fileNameLength] & 0xFFL) << 16) |
                        ((data[3+fileNameLength] & 0xFFL) << 8) |
                        (data[4+fileNameLength] & 0xFFL);
        //System.out.println(fileSize); 

        FileInfo fileInfo = new FileInfo(nodeIp,fileName,fileSize,true); 
        
        this.localData.insertNodeDataSingle(nodeIp, fileInfo);
    }


    private void sendFileLocationBytes(ObjectOutputStream out, FileInfo fileInfo) throws IOException {
        
            
            // HostIp prep
            String hostIp = fileInfo.getHostIp();
            byte[] filehost = hostIp.getBytes();
            int hostLength = filehost.length;
            //System.out.println(hostIp);

            // File name prep
            String fileName = fileInfo.getFileName();
            byte[] fileNameBytes = fileName.getBytes();
            int nameLength = fileNameBytes.length;
            //System.out.println(fileName);
            
            // File size
            long size = fileInfo.getSize();
            //System.out.println(size);
            //System.out.println();
            
            List<Boolean> list = fileInfo.getChunks();
            int listsize = list.size();
            System.out.println(listsize);

            byte[] info = new byte[10+nameLength+hostLength+listsize];
            
            // Storing resident node ip
            info[0] = (byte) hostLength;
            System.arraycopy(filehost, 0, info, 1, hostLength);

            // Storing file name
            info[1+hostLength] = (byte) nameLength;
            System.arraycopy(fileNameBytes, 0, info, 2+hostLength, nameLength);

            // Storing the file size
            info[2+hostLength+nameLength] = (byte) (size >> 24);
            info[3+hostLength+nameLength] = (byte) (size >> 16);
            info[4+hostLength+nameLength] = (byte) (size >> 8);
            info[5+hostLength+nameLength] = (byte) size;

            // Storing chunk list
            info[6+hostLength+nameLength] = (byte) (listsize >> 24);
            info[7+hostLength+nameLength] = (byte) (listsize >> 16);
            info[8+hostLength+nameLength] = (byte) (listsize >> 8);
            info[9+hostLength+nameLength] = (byte) listsize;

            int i = 0;
            int counter = 10+hostLength+nameLength;
            for(Boolean value : list) {
                if(value==true){
                    info[counter] = (byte) 1;
                } else {
                    info[counter] = (byte) 0;
                }
                
                counter++;
            }

            // Sending the data
            out.writeInt(info.length);
            out.write(info);
            out.flush();
            
    }


    @Override
    public void run() {

        InetAddress nodeAddress = getNodeSocket().getInetAddress();
        String nodeIp = nodeAddress.getHostAddress();

        this.localData.insertNodeData(nodeIp,null);

        System.out.println("\u001B[32mNode connected with server\u001B[0m\n" + "Node IP address: " + nodeIp + "\n");

        try {
            // Opening pipes
            ObjectOutputStream out = new ObjectOutputStream(getNodeSocket().getOutputStream());
            ObjectInputStream in = new ObjectInputStream(getNodeSocket().getInputStream());

            // Notifies node about his ip
            out.writeObject(nodeIp);

            int numFiles = in.readInt();
            //System.out.println(numFiles);

            while(numFiles>0){
                int size = in.readInt();
                byte[] file = new byte[size]; 
                in.read(file);

                // Insert information about the file in the database
                receiveDataBytes(nodeIp,file);

                numFiles--;
            }

            // Debugging/UI
            //for(Map.Entry<String, FileInfo> entry : getLocalData().getNodesFilesMap().get(nodeIp).entrySet()){
            //    System.out.println("Nome: " + entry.getValue().getFileName());
            //    System.out.println("Size" + entry.getValue().getSize());
            //}

            System.out.println("Informacao dos ficheiros do node com ip:" + nodeIp + " adicionada. \n");            
            //System.out.println ("Files do Node" + filesNode + "\n"); // Test
            
            // Awaits for new packet from the node
            while (!getNodeSocket().isClosed()) {
                try {
                    // Reads an object
                    Object obj = in.readObject();

                    if (obj instanceof String) {
                        String inp = (String) obj;
                        System.out.println(inp);

                        String[] commandParts = inp.split(" ");
                        //System.out.println("Command Parts: " + Arrays.toString(commandParts));
                        String commandName = commandParts[0];
                        List<String> commandArguments = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length)));

                        switch (commandName) {
                            // Node wants to know file locations about a certein file
                            case "get":
                                String requested_file = commandArguments.get(0);
                                List<FileInfo> file_locations = localData.getFileLocations(requested_file);
                                
                                // Number of locations of the file chunks
                                int numberOnodes = file_locations.size();                                                                                             
                                
                                // Notifies the node about how many nodes have file chunks
                                out.writeInt(numberOnodes);
                                
                                int num = 0;
                                while(num<numberOnodes){

                                    sendFileLocationBytes(out,file_locations.get(num));
                                    num++;
                                }
                                
                                break;

                            // Node has new packet donwloaded
                            case "insert":
                                // Insert file in node data

                                break;

                            // Node wants to end communication    
                            case "exit":
                                // Closes socket
                                getNodeSocket().close();
                                break;

                            default:
                                System.out.println("Unknown command: " + inp);
                                break;
                        }
                    } else {
                        System.out.println("Invalid object received from the node.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Deletes all data in the data base referent to the node
            this.localData.removeNodeData(nodeIp);
  
        } catch (Exception e) {
                System.err.println("Details: " + e.getMessage() + "\n");
                e.printStackTrace();
        }
    }

}
