import java.util.List;
import java.util.Map;
import java.io.File;

public class FS_Node {
    private String ip_adress;
    private int tcp_port;
    private int udp_port;
    private Map<String, List<File>> shared_files;

    // Constructors

    

    // Getters

    public String get_node_ip_adress() {
        return this.ip_adress;
    }

    public int get_node_tcp_port() {
        return this.tcp_port;
    }

    public int get_node_udp_port() {
        return this.udp_port;
    }

    public Map<String, List<File>> get_shared_files() {
        return this.shared_files;
    }

    // Setters

    public void set_node_ip_adress(String ip_adress) {
        this.ip_adress = ip_adress;
    }

    public void set_node_tcp_port(int tcp_port) {
        this.tcp_port = tcp_port;
    }

    public void set_node_udp_port(int udp_port) {
        this.udp_port = udp_port;
    }

    public void set_shared_files(Map<String, List<File>> shared_files) {
        this.shared_files = shared_files;
    }

    // Methods


}
