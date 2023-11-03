import java.util.*;
import java.io.IOException;
import java.net.*;

public class FS_Tracker {
    private String ip_address = "10.0.1.10";
    private int tcp_port = 42069;
    private Map<String, List<String>> nodes;  // ip do nodo e lista com o nome dos ficheiros do nodo

    // Constructors

    public FS_Tracker() {
        this.nodes = new HashMap<String, List<String>>();
    }

    // Getters

    public String get_tracker_ip_address() {
        return this.ip_address;
    }

    public int get_tracker_tcp_port() {
        return this.tcp_port;
    }

    public Map<String, List<String>> get_tracker_nodes() {
        return this.nodes;
    }

    // Setters

    public void set_tracker_ip_address(String ip_address) {
        this.ip_address = ip_address;
    }

    public void set_tracker_tcp_port(int tcp_port) {
        this.tcp_port = tcp_port;
    }

    // Methods

    public void register_node() {
        
    }
    
    public void delete_node() {

    }

    public String search_file(String file_name) {
        String file_node = null;

        for (Map.Entry<String, List<String>> entry : this.nodes.entrySet()) {
            String key = entry.getKey();
            List<String> node_files = entry.getValue();

            if (node_files.contains(file_name)) {
                file_node = key;
            }
        }
        return file_node;
    }

    private static void handle_node_connections(Socket client_socket) {
        InetAddress nodeAddress = client_socket.getInetAddress();
        String nodeIp = nodeAddress.getHostAddress();

        System.out.println("Concectado " + nodeIp);
    }

    public static void main(String[] args) {
        FS_Tracker tracker = new FS_Tracker();

        try (ServerSocket serverSocket = new ServerSocket(tracker.get_tracker_tcp_port())) {
            System.out.println("\u001B[32mTracker is waiting for connections...\u001B[0m\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handle_node_connections(clientSocket);
            }
        } catch (IOException e) {
            System.err.println("Tracker failed to start or accept connections: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
