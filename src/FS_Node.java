import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Node {
    private static String ip;
    private static int TCPport;
    private static int UDPport;

    // Ficheiros locais
    private List<String> localData;
    
    // Constructors

    public FS_Node(String path) {
        FS_Node.ip = null;
        FS_Node.TCPport = 42069;
        FS_Node.UDPport = 69696;
        this.localData = new ArrayList<>();
    
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

    public static String getIp() {
        return ip;
    }

    public static int getTCPport() {
        return TCPport;
    }

    public static int getUDPport() {
        return UDPport;
    }

    public List<String> getLocalData() {
        return this.localData;
    }

    // Setters

    public static void setIp(String ip) {
        FS_Node.ip = ip;
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

    public static List<String> getFileLocations(String command,String file, ObjectOutputStream output, ObjectInputStream input) throws IOException, ClassNotFoundException {
        // Notifies the server
        String string = command + " " + file;

        output.writeObject(string);

        // Gets the locations of the files from the server
        List<String> fileLocations = (List<String>) input.readObject();
    
        return fileLocations;
    }

    private static void receiveUdpPackets(int UDPport) throws IOException {
        DatagramSocket socket = new DatagramSocket(UDPport);

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

        socket.receive(receivePacket);
        String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

        System.out.println("Received UDP message: " + receivedMessage);

        socket.close();
    }

    // Main function
    public static void main(String[] args) {
        String serverIp = "10.0.1.10";
        int serverPort = 42069;

        // Cria a instancia Node
        FS_Node nodeData = new FS_Node(args[0]);
    
        // Estabelece a conecxao com o Servidor
        try (Socket serverSocket = new Socket(serverIp, serverPort)) {
             
            System.out.println("\u001B[32m Node connected to tracker. \u001B[0m\n");
            
            // Cria os pipes de escrita e leitura
            ObjectOutputStream output = new ObjectOutputStream(serverSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serverSocket.getInputStream());

            // Recebe o seu endereco de ip
            String nodeIp = (String) input.readObject();
            FS_Node.setIp(nodeIp);
            System.out.println("Received IP address from tracker: " + nodeIp);

            // Notifica o servidor dos ficheiros que tem armazenados
            output.writeObject(nodeData.getLocalData());

            Thread terminalNode = new Thread ( () -> {

                while (true) { 
                       
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String inp = reader.readLine();
                    
                        String[] commandParts = inp.split(" ");
                        String commandName = commandParts[0];
                        List<String> commandArguments = Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length));
                        
                        switch (commandName) {
                            case "get":
                                // Gets the locations of the file
                                List<String> fileLocations = getFileLocations(commandName,commandArguments.get(0), output, input);

                                System.out.println(fileLocations);

                                //Calls a method that creates threads to download the file from various nodes
                                break;
                        
                            case "exit":
                                output.writeObject(commandName);
                                serverSocket.close();
                                System.out.println("Node is exiting.");
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
            terminalNode.start();

            try { 
                while(true){
                    receiveUdpPackets(getUDPport());
                }
            } catch (Exception e){
               System.out.println("Could not receive UDP packets!");
            }
            
        } catch (Exception e) {
            System.out.println("\u001B[31m Node failed to connect to tracker. \u001B[0m\n");
            System.err.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

}

