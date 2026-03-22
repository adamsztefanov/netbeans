package mauz.terminal.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import mauz.terminal.BackendIoMetrics;
import mauz.terminal.TerminalBackend;

/**
 * Local process backend using raw stream forwarding. The factory keeps this
 * behind the same API as the SSH backend so the frontend remains transport
 * agnostic.
 */
public final class LocalConPtyBackend implements TerminalBackend {

    private final LocalConPtyConfig config;
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mauz-local-backend-io"); // NOI18N
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Consumer<byte[]>> outputListenerRef =
            new AtomicReference<>(bytes -> {
            });
    private final AtomicLong bytesFromRemote = new AtomicLong();
    private final AtomicLong bytesToRemote = new AtomicLong();
    private final AtomicReference<Instant> lastReadAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastWriteAt = new AtomicReference<>();
    private final Instant startedAt = Instant.now();
    private final Object lock = new Object();

    private volatile Process process;
    private volatile OutputStream processInput;
    private volatile boolean started;
    private volatile boolean closed;

    public LocalConPtyBackend(LocalConPtyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public void setOutputListener(Consumer<byte[]> listener) {
        outputListenerRef.set(listener == null ? bytes -> {
        } : listener);
    }

    @Override
    public void start() throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException("backend is closed");
            }
            if (started) {
                return;
            }
            ProcessBuilder builder = new ProcessBuilder(config.command());
            builder.directory(config.workingDirectory().toFile());
            process = builder.start();
            processInput = process.getOutputStream();
            started = true;
            pumpAsync(process.getInputStream());
            pumpAsync(process.getErrorStream());
        }
    }

    private void pumpAsync(InputStream source) {
        ioExecutor.submit(() -> pump(source));
    }

    private void pump(InputStream source) {
        byte[] buffer = new byte[8192];
        try (InputStream in = source) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                bytesFromRemote.addAndGet(read);
                lastReadAt.set(Instant.now());
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                outputListenerRef.get().accept(chunk);
            }
        } catch (IOException ignored) {
            // stream closes during normal shutdown
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }
        OutputStream out;
        synchronized (lock) {
            if (closed) {
                throw new IOException("backend is closed");
            }
            if (!started || processInput == null) {
                throw new IOException("backend is not started");
            }
            out = processInput;
        }
        out.write(data);
        out.flush();
        bytesToRemote.addAndGet(data.length);
        lastWriteAt.set(Instant.now());
    }

    @Override
    public void resize(int cols, int rows, int pixelWidth, int pixelHeight) {
        // Placeholder. Real ConPTY resize support can be added later without
        // changing TerminalBackend contract.
    }

    @Override
    public BackendIoMetrics ioMetrics() {
        return new BackendIoMetrics(
                bytesFromRemote.get(),
                bytesToRemote.get(),
                startedAt,
                lastReadAt.get(),
                lastWriteAt.get()
        );
    }

    @Override
    public void close() throws IOException {
        Process proc;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            started = false;
            proc = process;
            process = null;
            processInput = null;
        }

        if (proc != null) {
            proc.destroy();
            try {
                if (!proc.waitFor(1, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                proc.destroyForcibly();
            }
        }

        ioExecutor.shutdownNow();
    }
}
