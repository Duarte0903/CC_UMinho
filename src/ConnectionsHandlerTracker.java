import java.io.BufferedReader;
import java.io.InputStreamReader;
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

        this.trackerData.register_node(node_ip_address);

        System.out.println("\u001B[32mNode connected\u001B[32m" + "Node IP address: " + node_ip_address + "\n");

        try {
  
            ObjectOutputStream out = new ObjectOutputStream(this.socket.getOutputStream());
            out.writeObject(node_ip_address);

            while (!this.socket.isClosed()) {}
  
        } catch (Exception e) {
                System.err.println("Details: " + e.getMessage() + "\n");
                e.printStackTrace();
        }
    }

}
