import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class ConnectionsHandlerTracker implements Runnable {
    
    private Socket socket;
    private FS_Tracker trackerData;

    ConnectionsHandlerTracker (Socket socket, FS_Tracker trackerData){
        this.socket = socket;
        this.trackerData = trackerData;
    }
    

    @Override
    public void run() {

        InetAddress node_address = this.socket.getInetAddress();
        String node_ip_address = node_address.getHostAddress();

        this.trackerData.registerNodeData(node_ip_address,null);

        System.out.println("\u001B[32mNode connected\u001B[32m" + "Node IP address: " + node_ip_address + "\n");

        try {
            
            // Abertura dos estremos de escrita e leitura
            ObjectOutputStream out = new ObjectOutputStream(this.socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(this.socket.getInputStream());

            // Notifica o node do seu ip
            out.writeObject(node_ip_address);

            // Recebe informacao e insere
            List<String> dataNode = (List<String>) in.readObject();
            this.trackerData.registerNodeData(node_ip_address,dataNode);
            System.out.println("Informacao dos ficheiros do node com ip:" +node_ip_address+ "adicionada. \n");
            System.out.println ("pastas do Node" + dataNode);

            while (!this.socket.isClosed()) {
    
            }

            // Elemina os dados do node no servidor
            this.trackerData.delete_node(node_ip_address);
  
        } catch (Exception e) {
                System.err.println("Details: " + e.getMessage() + "\n");
                e.printStackTrace();
        }
    }

}
