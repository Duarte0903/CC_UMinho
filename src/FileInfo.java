import java.util.ArrayList;

public class FileInfo {
    
    private String hostIp;
    private String fileName;
    private Long size;
    private Integer lastChunk;
    private ArrayList<Boolean> chunks;

    
    public FileInfo(String hostIp, String fileName,Long size,Boolean isOriginal){
        this.hostIp = hostIp;
        this.fileName = fileName;
        this.size = size;

        this.lastChunk = (int) (this.size/1024);
       
        this.chunks = new ArrayList<Boolean>();
        
        if(isOriginal){
            for(int i = 1; i<=this.lastChunk; i++){
                this.chunks.set(i,true);
            }
        }
    }

    public void setChunks(int chunk) {
        if(chunk <= this.lastChunk){
            this.chunks.set(chunk,true);
        }
    }    

    public String getHostIp() {
        return hostIp;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getSize() {
        return size;
    }

    public ArrayList<Boolean> getChunks() {
        return chunks;
    }

}
