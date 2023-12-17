import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TrackerData {
    private static String ip = "10.0.1.10";
    private static int port = 42069;
    private int numConnectedNodes;
    
    
    private Map<String, Map<String,FileInfo>> nodesFiles;   // Key: Node Ip
                                                            // Value : Key: File Name 
                                                            //         Value: Node file info                                             

    // Data safety variable
    public ReentrantReadWriteLock dataLock;                                                
    
    // Constructors

    public TrackerData() {
        this.numConnectedNodes = 0;
        this.nodesFiles = new HashMap <String,Map<String,FileInfo>> ();
        this.dataLock = new ReentrantReadWriteLock();
    }

    // Getters

    public String getIp() {
        return TrackerData.ip;
    }

    public int getPort() {
        return TrackerData.port;
    }

    public int getNumNodesConnected() {
        try {
            this.dataLock.readLock().lock();
            return this.numConnectedNodes;
        } finally {
            this.dataLock.readLock().unlock();
        }
    }

    public Map<String, Map<String,FileInfo>> getNodesFilesMap() {
        try {
            this.dataLock.readLock().lock();
            return this.nodesFiles;
        } finally {
            this.dataLock.readLock().unlock();
        }
    }

    public FileInfo getNodeFileInfo(String nodeIp, String file){
        try {
            this.dataLock.readLock().lock();
            return this.nodesFiles.get(nodeIp).get(file);
        } finally {
            this.dataLock.readLock().unlock();
        }
    }

    // Methods

    public void printConnectedNodes() {
        System.out.println("Connected nodes:");

        if (getNumNodesConnected()==0) {
            System.out.println("Zero connected nodes\n");
        
        } else {
            System.out.println("Os nodos conectados sao: \n");
            for (Map.Entry<String, Map<String,FileInfo>> entry : getNodesFilesMap().entrySet()) {
                System.out.println("  ->" + entry.getKey() + "\n");
            }
            System.out.println("\n");
        }
    }

    public void removeNodeData(String nodeIp) {
        try {
            this.dataLock.writeLock().lock();
            this.nodesFiles.remove(nodeIp);
            this.numConnectedNodes--;
        } finally {
            this.dataLock.writeLock().unlock();
        }
    }

    public void insertNodeData(String nodeIp, List<FileInfo> files) {
        try {
            this.dataLock.writeLock().lock();
            
            if(!getNodesFilesMap().containsKey(nodeIp)){
                this.numConnectedNodes++;
            }

            if(files!=null){
                for(FileInfo file : files){
                    getNodesFilesMap().get(nodeIp).put(file.getFileName(),file);
                }
            } else {
                getNodesFilesMap().put(nodeIp, new HashMap<>());
            }

        } finally {
            this.dataLock.writeLock().unlock();
        }
    }

    public void insertNodeDataSingle(String nodeIp, FileInfo fileInfo) {
        try {
            this.dataLock.writeLock().lock();
            
            this.nodesFiles.get(nodeIp).put(fileInfo.getFileName(),fileInfo);
        } finally {
            this.dataLock.writeLock().unlock();
        }
    }

    public List<FileInfo> getFileLocations(String fileName) {
        List<FileInfo> fileNodes = new ArrayList<>();
    
        try {
            this.dataLock.readLock().lock();
            for (Map.Entry<String, Map<String,FileInfo>> entry : this.nodesFiles.entrySet()) {
                
                Map<String,FileInfo> files = entry.getValue();
    
                if (files.containsKey(fileName)) {
                    fileNodes.add(files.get(fileName));
                }
            }
            return fileNodes;
        } finally {
            this.dataLock.readLock().unlock();
        }
    }   
}