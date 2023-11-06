import java.io.Serializable;
import java.util.List;

public class Command implements Serializable {
    private String name;
    private List<String> arguments;

    public Command(String name, List<String> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public List<String> getArguments() {
        return arguments;
    }
} 