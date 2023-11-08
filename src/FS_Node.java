import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Node {
    private String ip_adress;
    private int tcp_port;
    private int udp_port;
    
    // Dados tracker
    private Map<String,List<String>> dataTracker;

    // Dados locais
    private List<String> dataLocal;
    
    // Constructors

    public FS_Node(String path) {
        this.ip_adress = null;
        this.tcp_port = 0;
        this.udp_port = 0;
        this.dataTracker = new HashMap<>();
        this.dataLocal = new ArrayList<>();
    
        // Access the files in the specified directory
        File folder = new File(path);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        this.dataLocal.add(file.getName());
                    }
                }
            }
        }
    }
    

    // Getters

    public String get_node_ip_adress() {
        return this.ip_adress;
    }

    public int get_node_tcp_port() {
        return this.tcp_port;
    }

    public int get_node_udp_port() {
        return this.udp_port;
    }

    public List<String> get_shared_files() {
        return this.dataLocal;
    }

    // Setters

    public void set_node_ip_adress(String ip_adress) {
        this.ip_adress = ip_adress;
    }

    public void set_node_tcp_port(int tcp_port) {
        this.tcp_port = tcp_port;
    }

    public void set_node_udp_port(int udp_port) {
        this.udp_port = udp_port;
    }

    public void set_shared_files(Map<String, Set<File>> shared_files) {
        this.shared_files = shared_files;
    }

    // Methods

    public void connect_to_node() {

    }

    public void end_connection_with_tracker() {

    }

    public void add_file(File file, String folder_name) {
        this.shared_files.get(folder_name).add(file);
    }  
    
    public static void main(String[] args) {
        String tracker_ip_address = "10.0.1.10";
        int tracker_tcp_port = 42069;

        FS_Node node = new FS_Node(args[0]);

        try (Socket socket = new Socket(tracker_ip_address, tracker_tcp_port)) {
             
            System.out.println("\u001B[32mNode connected to tracker\u001B[0m\n");
            
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(node.get_shared_files());

            String node_ip_address = (String) in.readObject();
            node.set_node_ip_adress(node_ip_address);
            System.out.println("Received IP address from tracker: " + node_ip_address);

            while (true) {
                
            }

        } catch (Exception e) {
            System.out.println("\u001B[31mNode failed to connect to tracker\u001B[0m\n");
            System.err.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}
