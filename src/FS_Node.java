import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Node {
    private static String ip;
    private static int port;
    
    // Dados tracker
    // private Map<String,List<String>> serverData;

    // Dados locais
    private List<String> localData;
    
    // Constructors

    public FS_Node(String path) {
        FS_Node.ip = null;
        FS_Node.port = 0;
        
        //this.serverData = new HashMap<>();
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

    public String getIp() {
        return FS_Node.ip;
    }

    public int getPort() {
        return FS_Node.port;
    }

    public List<String> getLocalData() {
        return this.localData;
    }

    //public Map<String, List<String>> getServerData() {
    //    return serverData;
    //}

    // Setters

    public static void setIp(String ip) {
        FS_Node.ip = ip;
    }

    public static void setPort(int port) {
        FS_Node.port = port;
    }

    public void setLocalData(List<String> localData) {
        this.localData = localData;
    }

    //public void setServerData(Map<String, List<String>> serverData) {
     //   this.serverData = serverData;
    //}

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
        System.out.println(string);
        output.writeObject(string);

        // Gets the locations of the files from the server
        List<String> fileLocations = (List<String>) input.readObject();
    
        return fileLocations;
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

            while(true){

            }
            // Aceita conexoes UDP de outros Nodos
            // ...In construction...

        } catch (Exception e) {
            System.out.println("\u001B[31m Node failed to connect to tracker. \u001B[0m\n");
            System.err.println("Details: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

}

