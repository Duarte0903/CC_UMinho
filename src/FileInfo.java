import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileInfo {
    
    private String hostIp;
    private String fileName;
    private Long size;
    private Integer lastChunk; // Last position on chunks array
    private List<Boolean> chunks;

    public FileInfo(String hostIp, String fileName,Long size,Boolean isOriginal){
        this.hostIp = hostIp;
        this.fileName = fileName;
        this.size = size;

        if(size%5000==0){
            this.lastChunk = ((int) (this.size/10000)) - 1;
        } else{
            this.lastChunk = (int) (this.size/10000);
        }

        if(isOriginal){
            this.chunks = new ArrayList<Boolean>(Collections.nCopies(this.lastChunk+1, true));
        } else{
            this.chunks = new ArrayList<Boolean>(Collections.nCopies(this.lastChunk+1, false));
        }
    }

    public void setChunk(int chunk) {
        if(chunk <= this.lastChunk){
            this.chunks.set(chunk,true);
        }
    }    

    public void setChunks(List<Boolean> chunks) {
        this.chunks = chunks;
    }

    public Integer getLastChunk() {
        return lastChunk;
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

    public List<Boolean> getChunks() {
        return chunks;
    }

}
