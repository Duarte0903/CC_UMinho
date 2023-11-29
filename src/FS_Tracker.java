import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;

public class FS_Tracker {
<<<<<<< HEAD
    private static String ip = "10.0.1.10";
    private static int port = 42069;
    private int numConnectedNodes;
    private Map<String, List<String>> nodesFiles;   // Key: Node Ip
                                                    // Value : Node files 
    
    private Reentrantlock lock;
    
    // Constructors

    public FS_Tracker() {
        this.numConnectedNodes = 0;
        this.nodesFiles = new HashMap <String,List<String>> ();
        this.lock = new Reentrantlock();
    }

    // Getters

    public String getIp() {
        return FS_Tracker.ip;
    }

    public int getPort() {
        return FS_Tracker.port;
    }

    public int getNumNodesConnected() {
        lock.lock();
        try{
            return this.numConnectedNodes;
        } finally{
            lock.unlock();
        }
    }

    public Map<String, List<String>> getNodesFiles() {
      lock.lock();
        try{
            return this.nodesFiles;
        } finally{
            lock.unlock();
        }
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
        lock.lock();
        try{
            this.nodesFiles.remove(nodeIp);
            this.numConnectedNodes--;
        } finally{
            lock.unlock();
        }
    }

    public void insertNewNode(String nodeIp, List<String> files) {
        lock.lock();
        try{
            this.nodesFiles.put(nodeIp, files);
            this.numConnectedNodes++;
        } finally{
            lock.unlock();
        }
    }

    public void insertNodeData(String nodeIp, String file) {
        lock.lock();
        try{
            this.nodesFiles.get(nodeIp).add(file);
        } finally{
            lock.unlock();
        }
    }

    public List<String> getFileLocations(String fileName) {
        List<String> nodeIps = new ArrayList<>();

        lock.lock();
        try{
            for (Map.Entry<String, List<String>> entry : this.nodesFiles.entrySet()) {
                String nodeIp = entry.getKey();
                List<String> files = entry.getValue();
        
                if (files.contains(fileName)) {
                    nodeIps.add(nodeIp);
                    break; // Break after finding the first node with the file (assuming uniqueness)
                }
            }
        } finally{
            lock.unlock();
        }
        return nodeIps;
    }    

=======
     
>>>>>>> 547cfbe64870ac18ea1a94d8d16770e36f79af10
    public static void main(String[] args) {
        TrackerData serverData = new TrackerData();

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