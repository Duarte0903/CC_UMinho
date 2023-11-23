import java.util.*;
import java.io.*;
import java.net.*;

public class ConnectionsHandlerTracker implements Runnable {
    
    private static Socket nodeSocket;
    private TrackerData localData;
    

    ConnectionsHandlerTracker (Socket nodeSocket, TrackerData localData){
        ConnectionsHandlerTracker.nodeSocket = nodeSocket;
        this.localData = localData;
    }
    
    public Socket getNodeSocket() {
        return ConnectionsHandlerTracker.nodeSocket;
    }

    public TrackerData getLocalData() {
        return localData;
    }

    @Override
    public void run() {

        InetAddress nodeAddress = getNodeSocket().getInetAddress();
        String nodeIp = nodeAddress.getHostAddress();

        this.localData.insertNewNode(nodeIp,null);

        System.out.println("\u001B[32m Node connected with server \u001B[0m\n" + "Node IP address: " + nodeIp + "\n");

        try {
            
            // Abertura dos estremos de escrita e leitura
            ObjectOutputStream out = new ObjectOutputStream(getNodeSocket().getOutputStream());
            ObjectInputStream in = new ObjectInputStream(getNodeSocket().getInputStream());

            // Notifica o node do seu ip
            out.writeObject(nodeIp);

            // Recebe informacao e insere
            List<String> filesNode = new ArrayList<String>((List<String>) in.readObject());
            this.localData.insertNewNode(nodeIp,filesNode);
            
            while (!getNodeSocket().isClosed()) {
                try {
                    Object obj = in.readObject();

                    if (obj instanceof String) {
                        String inp = (String) obj;
                        System.out.println(inp);

                        String[] commandParts = inp.split(" ");
                        System.out.println("Command Parts: " + Arrays.toString(commandParts));
                        String commandName = commandParts[0];
                        List<String> commandArguments = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length)));

                        switch (commandName) {
                            // Sends nodes that contain the file
                            case "get":
                                // Gets the locations of the file
                                ArrayList<String> fileLocations = (ArrayList<String>) localData.getFileLocations(commandArguments.get(0));

                                // Sends the node the locations of the file
                                out.writeObject(fileLocations);

                                break;

                            case "insert":
                                // Insert file in node data
                                break;

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
            // Elemina os dados do node no servidor
            this.localData.removeNodeData(nodeIp);
  
        } catch (Exception e) {
                System.err.println("Details: " + e.getMessage() + "\n");
                e.printStackTrace();
        }
    }

}

