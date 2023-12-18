import java.util.*;
import java.io.*;
import java.net.*;

public class FS_Tracker {
     
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
                

            while (true & !serverSocket.isClosed()) {
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