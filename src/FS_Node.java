import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Node {
    private String node_name;
    private String ip_adress;
    private int tcp_port;
    private int udp_port;
    private Map<String, Set<File>> shared_files;   // A String corresponde ao nome do arquivo e o Set a pastas de ficheiros  

    // Constructors

    public FS_Node(String node_name, String ip_adress, int tcp_port, int udp_port) {
        this.node_name = node_name;
        this.ip_adress = ip_adress;
        this.tcp_port = tcp_port;
        this.udp_port = udp_port;
        this.shared_files = new HashMap<String, Set<File>>();
    }

    // Getters

    public String get_node_name() {
        return this.node_name;
    }

    public String get_node_ip_adress() {
        return this.ip_adress;
    }

    public int get_node_tcp_port() {
        return this.tcp_port;
    }

    public int get_node_udp_port() {
        return this.udp_port;
    }

    public Map<String, Set<File>> get_shared_files() {
        return this.shared_files;
    }

    // Setters

    public void set_node_name(String node_name) {
        this.node_name = node_name;
    }

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

    public void connect_to_tracker(String tracker_ip_address, int tracker_tcp_port) {
    }

    public void connect_to_node() {

    }

    public void end_connection_with_tracker() {

    }

    public void add_file(File file, String folder_name) {
        this.shared_files.get(folder_name).add(file);
    }

    @Override
    public String toString() {
        return "Node Name:" + this.node_name +
               "IP Address: " + this.ip_adress + 
               "TCP Port:" + this.tcp_port + 
               "UDP Port:" + this.udp_port;
    }    
    
    public static void main(String[] args) {
        String tracker_ip_address = "10.0.1.10";
        int tracker_tcp_port = 42069;

        try (Socket socket = new Socket(tracker_ip_address, tracker_tcp_port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            System.out.println("\u001B[32mNode connected to tracker\u001B[0m\n");

        } catch (Exception e) {
            System.out.println("\u001B[31mNode failed to connect to tracker\u001B[0m\n");
            System.err.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}