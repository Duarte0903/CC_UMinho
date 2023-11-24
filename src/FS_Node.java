import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Node {
    private String ip;
    private int tcp_port = 42069;
    private int udp_port = 65535;
    private List<String> localData;  // ficheiros do node
    private static String files_directory;
    
    // Constructors

    public FS_Node(String path) {
        this.setIp(null);
        this.localData = new ArrayList<>();

        FS_Node.files_directory = path;
    
        // Access the files in the specified directory
        File folder = new File(path);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        this.localData.add(file.getName());
                    }
                }
            }
        }
    }
    
    // Getters

    public String getIp() {
        return this.ip;
    }

    public int get_node_tcp_port() {
        return this.tcp_port;
    }

    public int get_node_udp_port() {
        return this.udp_port;
    }

    public List<String> getLocalData() {
        return this.localData;
    }

    public String get_files_directory() {
        return this.files_directory;
    }

    // Setters

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setLocalData(List<String> localData) {
        this.localData = localData;
    }

    // Methods

    public void printFileNames() {
        if (this.localData.isEmpty()) {
            System.out.println("\nNo files in node database.\n");
        } else {
            System.out.println("\nFile names in node database: \n\n");
            for (String fileName : localData) {
                System.out.println("    " + fileName + "\n");
            }
        }
    }

    public void request_file_from_node(String node_ip, String file_name) {
        try {
            DatagramSocket udp_socket = new DatagramSocket();
            String request_message = "FileTransferRequest:" + file_name;
            byte[] request_data = request_message.getBytes();
            InetAddress receiverAddress = InetAddress.getByName(node_ip);
            DatagramPacket requestPacket = new DatagramPacket(request_data, request_data.length, receiverAddress, this.get_node_udp_port());
            udp_socket.send(requestPacket);
            udp_socket.close();
        }  catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handle_udp_requests() {
        try {
            DatagramSocket udpSocket = new DatagramSocket(get_node_udp_port());
            byte[] receiveData = new byte[1024]; // ajustar o tamanho do buffer
            
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                udpSocket.receive(receivePacket);
                String received_message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("\u001B[32mReceived UDP request: " + "\u001B[0m\n");
                InetAddress address = receivePacket.getAddress();

                if (received_message.startsWith("FileTransferRequest:")) {
                    String[] parts = received_message.split(":");
                    String requestedFileName = parts[1];
                
                    if (this.getLocalData().contains(requestedFileName)) {
                        // File exists in the node's directory
                        Thread file_request_thread = new Thread(() -> {
                            try (DatagramSocket socket = new DatagramSocket()) {
                                File fileToSend = new File(this.get_files_directory() + requestedFileName);
                
                                // Prepare to send file size as the first packet
                                long fileSize = fileToSend.length();
                                byte[] fileSizeBytes = String.valueOf(fileSize).getBytes();
                                DatagramPacket fileSizePacket = new DatagramPacket(
                                        fileSizeBytes,
                                        fileSizeBytes.length,
                                        address,
                                        this.get_node_udp_port()
                                );
                                socket.send(fileSizePacket);
                
                                // Send the file content in subsequent packets
                                try (FileInputStream fileInputStream = new FileInputStream(fileToSend)) {
                                    byte[] buffer = new byte[1024];
                                    int bytesRead;
                
                                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                        DatagramPacket packet = new DatagramPacket(
                                                buffer,
                                                bytesRead,
                                                address,
                                                this.get_node_udp_port()
                                        );
                                        socket.send(packet);
                                        // Clear the buffer after sending
                                        buffer = new byte[1024];
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        file_request_thread.start();
                    } else {
                        System.out.println("\u001B[31mRequested file does not exist in the directory.\u001B[0m\\n");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String serverIp = "10.0.1.10";
        int serverPort = 42069;

        // Cria a instancia Node
        FS_Node node = new FS_Node(args[0]);
    
        // Estabelece a conecxao com o Servidor
        try {
            Socket serverSocket = new Socket(serverIp, serverPort);
            System.out.println("\u001B[32mNode connected to tracker.\u001B[0m\n");
            
            // Cria os pipes de escrita e leitura
            ObjectOutputStream output = new ObjectOutputStream(serverSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serverSocket.getInputStream());

            // Recebe o seu endereco de ip
            String nodeIp = (String) input.readObject();
            node.setIp(nodeIp);
            System.out.println("Received IP address from tracker: " + nodeIp);

            // Notifica o servidor dos ficheiros que tem armazenados
            output.writeObject(node.getLocalData());

            Thread udp_requests_thread = new Thread( () -> {
                node.handle_udp_requests();
            });
            udp_requests_thread.start();

            while (true) {           
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String inp = reader.readLine();
                    
                    String[] command_parts = inp.split(" ");
                    String command_name = command_parts[0];
                    List<String> command_arguments = Arrays.asList(Arrays.copyOfRange(command_parts, 1, command_parts.length));
                        
                    switch (command_name) {
                        case "get": 
                            String file_name = command_arguments.get(0);
                            output.writeObject("get " + file_name);
                            List<String> file_locations = (List<String>)input.readObject();  // Endereços IP dos nodos onde está o ficheiro

                            if (file_locations.isEmpty()) {
                                System.out.println("\u001B[31mThe requested file is not available in any nodes. \u001B[0m\n");
                            }

                            else {
                                System.out.println("File location: " + file_locations.get(0) + "\n");
                                node.request_file_from_node(file_locations.get(0), file_name);
                            }

                            break;

                        case "printFiles":
                            node.printFileNames();
                            break;
                        
                        case "exit":
                            output.writeObject(command_name);
                            serverSocket.close();
                            System.out.println("Node is exiting.");
                            System.exit(0);
                            break;
                        
                        default:
                            System.out.println("Unknown command: " + input);
                            break;
                    }
                    
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.out.println("\u001B[31mNode failed to connect to tracker. \u001B[0m\n");
            System.err.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}