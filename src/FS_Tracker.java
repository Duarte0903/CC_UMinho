import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Tracker {
    private static String ip_address = "10.0.1.10";
    private static int tcp_port = 42069;
    private int connected_nodes;
    private Map<String, List<String>> nodes;  // ip do nodo e lista com o nome dos ficheiros do nodo

    // Constructors

    public FS_Tracker() {
        this.connected_nodes = 0;
        this.nodes = new HashMap<String, List<String>>();
    }

    // Getters

    public String get_tracker_ip_address() {
        return this.ip_address;
    }

    public int get_tracker_tcp_port() {
        return this.tcp_port;
    }

    public int get_connected_nodes() {
        return this.connected_nodes;
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

    public void print_connected_nodes() {
        System.out.println("Connected nodes:");

        if (get_tracker_nodes().entrySet().isEmpty()) {
            System.out.println("Zero connected nodes\n");
        }

        else {
            for (Map.Entry<String, List<String>> entry : get_tracker_nodes().entrySet()) {
                System.out.println(entry.getKey());
            }
            System.out.println("\n");
        }
    }

    public void delete_node(String node_ip_address) {
        this.nodes.remove(node_ip_address);
        this.connected_nodes--;
    }

    public void registerNodeData(String node_ip_address, List<String> files) {
        List<String> node_files = new ArrayList<String>();
        node_files = files;
        this.nodes.put(node_ip_address, node_files);
    }


    public static void main(String[] args) {
        FS_Tracker tracker = new FS_Tracker();

        try {
            
            ServerSocket server = new ServerSocket(tracker.get_tracker_tcp_port());
            System.out.println("\u001B[32mServidor ativo em " +  ip_address + " porta " +  tcp_port +" \u001B[0m \n");

            
            Thread commandsTracker = new Thread ( () -> {

                while (true) { 
                       
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String input = reader.readLine();
                    
                        String[] command_parts = input.split(" ");
                        String command_name = command_parts[0];
                        List<String> commandArguments = Arrays.asList(Arrays.copyOfRange(command_parts, 1, command_parts.length));
        
                        switch (command_name) {
                            case "connections":
                                tracker.print_connected_nodes();
                                break;
                        
                            case "exit":
                                server.close();
                                System.out.println("Tracker is exiting.");
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

            commandsTracker.start();
                

            while (true) {
                try {
                    Socket node_socket = server.accept();
                    Thread t = new Thread(new ConnectionsHandlerTracker(node_socket,tracker));
                    t.start();
                
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            

        } catch (IOException e) {
            System.err.println("\u001B[31mTracker failed to start connections:\u001B[0m\n");
            System.out.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}