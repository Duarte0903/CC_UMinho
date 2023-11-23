import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class TrackerData {

    // Server network data
    private static String ip = "10.0.1.10";
    private static int port = 42069;

    // Node derived data
    private int numConnectedNodes;
    private Map<String, List<String>> nodesFiles;   // Key: Node Ip
                                                    // Value : Node files 

    // Safety tools
    private ReadWriteLock lock;                                                
    
    // Constructors

    public void ServerData() {
        this.numConnectedNodes = 0;
        this.nodesFiles = new HashMap<>();
    }

    // Getters

    public String getIp() {
        return TrackerData.ip;
    }

    public int getPort() {
        return TrackerData.port;
    }

    public int getNumNodesConnected() {
        lock.readLock().lock();
        try {
            return this.numConnectedNodes;
        } finally {
            lock.readLock().unlock();
        }
        
    }

    public Map<String, List<String>> getNodesFiles() {
        lock.readLock().lock();
        try {
            return this.nodesFiles;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Methods

    public void printConnectedNodes() {

        if (getNumNodesConnected()==0) {
            System.out.println("\nZero connected nodes\n");
        
        } else {
            System.out.println("\nConnected nodes: \n");
            for (Map.Entry<String, List<String>> entry : getNodesFiles().entrySet()) {
                System.out.println("  ->" + entry.getKey() + "\n");
            }
            System.out.println("\n");
        }
    }

    public void removeNodeData(String nodeIp) {
        lock.writeLock().lock();
        try {
           this.nodesFiles.remove(nodeIp);
           this.numConnectedNodes--; 
        } finally {
            lock.writeLock().unlock();
        }
    
    }

    public void insertNewNode(String nodeIp, List<String> files) {
        lock.writeLock().lock();
        try {
           this.nodesFiles.put(nodeIp, files);
           this.numConnectedNodes++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void insertNodeData(String nodeIp, String file) {
        lock.writeLock().lock();
        try {
           this.nodesFiles.get(nodeIp).add(file);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<String> getFileLocations(String fileName) {
        List<String> nodeIps = new ArrayList<String> ();

        lock.writeLock().lock();
        try {
            for (Map.Entry<String, List<String>> entry : this.nodesFiles.entrySet()) {
                String key = entry.getKey();
                List<String> nodeFiles = entry.getValue();

                if (nodeFiles.contains(fileName)) {
                    nodeIps.add(key);
                }
            }
            return nodeIps;
        
        } finally {
            lock.writeLock().unlock();
        }

    }

}
