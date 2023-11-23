import java.util.*;
import java.io.*;
import java.net.*;

public class ConnectionsHandlerTracker implements Runnable {
    
    private static Socket nodeSocket;
    private FS_Tracker localData;
    

    ConnectionsHandlerTracker(Socket nodeSocket, FS_Tracker localData) {
        ConnectionsHandlerTracker.nodeSocket = nodeSocket;
        this.localData = localData;
    }
    
    public Socket getNodeSocket() {
        return ConnectionsHandlerTracker.nodeSocket;
    }

    public FS_Tracker getLocalData() {
        return localData;
    }

    @Override
    public void run() {

        InetAddress nodeAddress = getNodeSocket().getInetAddress();
        String nodeIp = nodeAddress.getHostAddress();

        this.localData.insertNewNode(nodeIp,null);

        System.out.println("\u001B[32mNode connected with server\u001B[0m\n" + "Node IP address: " + nodeIp + "\n");

        try {
            // Abertura dos estremos de escrita e leitura
            ObjectOutputStream out = new ObjectOutputStream(getNodeSocket().getOutputStream());
            ObjectInputStream in = new ObjectInputStream(getNodeSocket().getInputStream());

            // Notifica o node do seu ip
            out.writeObject(nodeIp);

            // Recebe informacao e insere
            List<String> filesNode = new ArrayList<String>((List<String>) in.readObject());
            this.localData.insertNewNode(nodeIp,filesNode);
            System.out.println("Informacao dos ficheiros do node com ip:" + nodeIp + " adicionada. \n");
            
            System.out.println ("files do Node" + filesNode + "\n"); // Test
            
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
                            case "get":
                                String requested_file = commandArguments.get(0);
                                List<String> file_locations = localData.getFileLocations(requested_file);
                                out.writeObject(file_locations);
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
