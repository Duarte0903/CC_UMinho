import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Tracker {
    
    // Server network data
    private static String ip = "10.0.1.10";
    private static int port = 42069;

    // Node derived data
    private int numConnectedNodes;
    private Map<String, List<String>> nodesFiles;   // Key: Node Ip
                                                    // Value : Node files 
    
    // Constructors

    public FS_Tracker() {
        this.numConnectedNodes = 0;
        this.nodesFiles = new HashMap <String,List<String>> ();
    }

    // Getters

    public String getIp() {
        return FS_Tracker.ip;
    }

    public int getPort() {
        return FS_Tracker.port;
    }

    public int getNumNodesConnected() {
        return this.numConnectedNodes;
    }

    public Map<String, List<String>> getNodesFiles() {
        return this.nodesFiles;
    }

    // Methods

    public void printConnectedNodes() {
        System.out.println("Connected nodes:");

        if (getNumNodesConnected()==0) {
            System.out.println("Zero connected nodes\n");
        
        } else {
            System.out.println("Os nodos conectados sao: \n");
            for (Map.Entry<String, List<String>> entry : getNodesFiles().entrySet()) {
                System.out.println("  ->" + entry.getKey() + "\n");
            }
            System.out.println("\n");
        }
    }

    public void removeNodeData(String nodeIp) {
        this.nodesFiles.remove(nodeIp);
        this.numConnectedNodes--;
    }

    public void insertNewNode(String nodeIp, List<String> files) {
        this.nodesFiles.put(nodeIp, files);
        this.numConnectedNodes++;
    }

    public void insertNodeData(String nodeIp, String file) {
        this.nodesFiles.get(nodeIp).add(file);
    }

    public List<String> getFileLocations(String fileName) {
        List<String> nodeIps = new ArrayList<String> ();

        for (Map.Entry<String, List<String>> entry : this.nodesFiles.entrySet()) {
            String key = entry.getKey();
            List<String> nodeFiles = entry.getValue();

            if (nodeFiles.contains(fileName)) {
                nodeIps.add(key);
            }
        }
        return nodeIps;

    }

    public static void main(String[] args) {
        FS_Tracker serverData = new FS_Tracker();

        try {
            
            ServerSocket serverSocket = new ServerSocket(serverData.getPort());
            System.out.println("\u001B[32m Servidor ativo com ip " +  serverData.getIp() + " e com porta " +  serverData.getPort() +" \u001B[0m \n");

            Thread terminalTracker = new Thread ( () -> {

                while (true) { 
                       
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String input = reader.readLine();
                    
                        String[] commandParts = input.split(" ");
                        String commandName = commandParts[0];
                        List<String> commandArguments = Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length));
        
                        switch (commandName) {
                            case "connections":
                                serverData.printConnectedNodes();;
                                break;
                        
                            case "exit":
                                serverSocket.close();
                                System.out.println("Tracker is exiting...");
                                System.exit(0);
                                break;
                        
                            default:
                                System.out.println("Unknown command: " + input);
                                break;
                        }
                    
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                } 
              }
            );
            terminalTracker.start();
                

            while (true) {
                Socket nodeSocket = serverSocket.accept();
                Thread t = new Thread(new ConnectionsHandlerTracker(nodeSocket,serverData));
                t.start();
            }
            

        } catch (IOException e) {
            System.err.println("\u001B[31mTracker failed to start connections:\u001B[0m\n");
            System.out.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}