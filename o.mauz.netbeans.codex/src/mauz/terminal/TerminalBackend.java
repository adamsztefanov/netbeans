package mauz.terminal;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Transport-neutral backend contract for interactive terminal sessions.
 */
public interface TerminalBackend extends Closeable {

    void start() throws IOException;

    void write(byte[] data) throws IOException;

    void resize(int cols, int rows, int pixelWidth, int pixelHeight) throws IOException;

    void setOutputListener(Consumer<byte[]> listener);

    default BackendIoMetrics ioMetrics() {
        return new BackendIoMetrics(0, 0, null, null, null);
    }
}
