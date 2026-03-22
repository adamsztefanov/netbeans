package mauz.terminal.local;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Local backend configuration. This can be swapped for a true ConPTY/JNI
 * implementation without changing the frontend contract.
 */
public final class LocalConPtyConfig {

    private final List<String> command;
    private final Path workingDirectory;

    public LocalConPtyConfig(List<String> command, Path workingDirectory) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        this.command = List.copyOf(new ArrayList<>(command));
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
    }

    public List<String> command() {
        return command;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }
}
