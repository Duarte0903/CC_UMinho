import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectionsHandlerTracker implements Runnable {
    
    private Socket nodeSocket;
    private FS_Tracker localData;
    private ReentrantLock lock;
    private Condition condition;

    ConnectionsHandlerTracker (Socket nodeSocket, FS_Tracker localData){
        this.nodeSocket = nodeSocket;
        this.localData = localData;
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
    }
    
    @Override
    public void run() {

        InetAddress nodeAddress = this.nodeSocket.getInetAddress();
        String nodeIp = nodeAddress.getHostAddress();

        this.localData.insertNewNode(nodeIp,null);

        System.out.println("\u001B[32m Node connected with server\u001B[0m" + "Node IP address: " + nodeIp + "\n");

        try {
            
            // Abertura dos estremos de escrita e leitura
            ObjectOutputStream out = new ObjectOutputStream(this.nodeSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(this.nodeSocket.getInputStream());

            // Notifica o node do seu ip
            out.writeObject(nodeIp);

            // Recebe informacao e insere
            List<String> filesNode = new ArrayList<String>((List<String>) in.readObject());
            this.localData.insertNewNode(nodeIp,filesNode);
            System.out.println("Informacao dos ficheiros do node com ip:" + nodeIp + "adicionada. \n");
            
            // Test
            System.out.println ("files do Node" + filesNode);

            while (!this.nodeSocket.isClosed()) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(this.nodeSocket.getInputStream()));
                    String inp = reader.readLine();
                
                    //while (inp != null) {
                        String[] commandParts = inp.split(" ");
                        String commandName = commandParts[0];
                        List<String> commandArguments = Arrays.asList(Arrays.copyOfRange(commandParts, 1, commandParts.length));
            
                        switch (commandName) {
                            // Sends nodes that contain the file
                            case "get":
                                // Gets the locations of the file
                                ArrayList<String> fileLocations = (ArrayList<String>) localData.getFileLocations(commandArguments.get(0));

                                // Sends the node the locations of the file
                                out.writeObject(fileLocations);

                                break;
            
                            case "insert":
                                // Insert file in node data
                                break;
            
                            default:
                                System.out.println("Unknown command: " + inp);
                                break;
                        }
                        inp = reader.readLine();
                    //}
                } catch(Exception e){
                    e.printStackTrace();
                }
            }

            // Elemina os dados do node no servidor
            this.localData.removeNodeData(nodeIp);
  
        } catch (Exception e) {
                System.err.println("Details: " + e.getMessage() + "\n");
                e.printStackTrace();
        }
    }

}

