package mauz.terminal.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import mauz.terminal.BackendIoMetrics;
import mauz.terminal.TerminalBackend;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyChannelConfiguration;

/**
 * MINA SSHD-backed interactive shell channel with PTY.
 */
public final class SshTerminalBackend implements TerminalBackend {

    private static final Logger LOG = Logger.getLogger(SshTerminalBackend.class.getName());

    private final Object lock = new Object();
    private final SshConnectionConfig config;
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mauz-ssh-backend-io"); // NOI18N
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Consumer<byte[]>> outputListenerRef =
            new AtomicReference<>(bytes -> {
            });
    private final AtomicLong bytesFromRemote = new AtomicLong();
    private final AtomicLong bytesToRemote = new AtomicLong();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastReadAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastWriteAt = new AtomicReference<>();

    private volatile SshClient client;
    private volatile ClientSession session;
    private volatile ChannelShell channel;
    private volatile OutputStream channelInput;
    private volatile Future<?> stdoutPump;
    private volatile Future<?> stderrPump;
    private volatile boolean started;
    private volatile boolean closed;

    public SshTerminalBackend(SshConnectionConfig config) {
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
            doStartLocked();
            started = true;
            startedAt.set(Instant.now());
        }
    }

    private void doStartLocked() throws IOException {
        SshClient newClient = null;
        ClientSession newSession = null;
        ChannelShell newChannel = null;

        try {
            newClient = SshClient.setUpDefaultClient();
            if (config.acceptUnknownHostKeys()) {
                newClient.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            }
            newClient.start();

            Duration connectTimeout = Duration.ofMillis(config.connectTimeoutMillis());
            newSession = newClient.connect(config.username(), config.host(), config.port())
                    .verify(connectTimeout)
                    .getSession();
            newSession.addPasswordIdentity(config.passwordAsString());
            newSession.auth().verify(Duration.ofMillis(config.authTimeoutMillis()));

            PtyChannelConfiguration pty = new PtyChannelConfiguration();
            pty.setPtyType(config.termType());
            pty.setPtyColumns(config.initialColumns());
            pty.setPtyLines(config.initialRows());
            pty.setPtyWidth(config.initialPixelWidth());
            pty.setPtyHeight(config.initialPixelHeight());

            newChannel = newSession.createShellChannel(pty, Map.of("TERM", config.termType())); // NOI18N
            newChannel.open().verify(Duration.ofMillis(config.channelOpenTimeoutMillis()));

            channelInput = newChannel.getInvertedIn();
            client = newClient;
            session = newSession;
            channel = newChannel;

            ChannelShell openedChannel = newChannel;
            stdoutPump = ioExecutor.submit(() -> pump(openedChannel.getInvertedOut()));
            stderrPump = ioExecutor.submit(() -> pump(openedChannel.getInvertedErr()));
        } catch (IOException | RuntimeException ex) {
            safeClose(newChannel);
            safeClose(newSession);
            safeClose(newClient);
            throw ex instanceof IOException ? (IOException) ex
                    : new IOException("Failed to start SSH terminal backend", ex);
        }
    }

    private void pump(InputStream source) {
        if (source == null) {
            return;
        }
        byte[] buffer = new byte[8192];
        try (InputStream in = source) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                bytesFromRemote.addAndGet(read);
                lastReadAt.set(Instant.now());
                byte[] copy = new byte[read];
                System.arraycopy(buffer, 0, copy, 0, read);
                try {
                    outputListenerRef.get().accept(copy);
                } catch (RuntimeException listenerEx) {
                    LOG.log(Level.FINE, "Output listener threw an exception", listenerEx); // NOI18N
                }
            }
        } catch (IOException ex) {
            if (!closed) {
                LOG.log(Level.FINE, "SSH stream pump stopped", ex); // NOI18N
            }
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }

        OutputStream input;
        synchronized (lock) {
            if (closed) {
                throw new IOException("backend is closed");
            }
            if (!started || channelInput == null) {
                throw new IOException("backend is not started");
            }
            input = channelInput;
        }

        input.write(data);
        input.flush();
        bytesToRemote.addAndGet(data.length);
        lastWriteAt.set(Instant.now());
    }

    @Override
    public void resize(int cols, int rows, int pixelWidth, int pixelHeight) throws IOException {
        if (cols <= 0 || rows <= 0) {
            return;
        }

        ChannelShell shell;
        synchronized (lock) {
            if (closed || !started) {
                return;
            }
            shell = channel;
        }

        if (shell != null) {
            shell.sendWindowChange(cols, rows, Math.max(pixelWidth, 0), Math.max(pixelHeight, 0));
        }
    }

    @Override
    public BackendIoMetrics ioMetrics() {
        return new BackendIoMetrics(
                bytesFromRemote.get(),
                bytesToRemote.get(),
                startedAt.get(),
                lastReadAt.get(),
                lastWriteAt.get()
        );
    }

    @Override
    public void close() throws IOException {
        SshClient currentClient;
        ClientSession currentSession;
        ChannelShell currentChannel;
        Future<?> outPump;
        Future<?> errPump;
        OutputStream input;

        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            started = false;

            currentClient = client;
            currentSession = session;
            currentChannel = channel;
            outPump = stdoutPump;
            errPump = stderrPump;
            input = channelInput;

            client = null;
            session = null;
            channel = null;
            stdoutPump = null;
            stderrPump = null;
            channelInput = null;
        }

        if (outPump != null) {
            outPump.cancel(true);
        }
        if (errPump != null) {
            errPump.cancel(true);
        }
        ioExecutor.shutdownNow();

        if (input != null) {
            try {
                input.close();
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Error while closing SSH input stream", ex); // NOI18N
            }
        }

        safeClose(currentChannel);
        safeClose(currentSession);
        safeClose(currentClient);
    }

    private static void safeClose(ChannelShell value) {
        if (value != null) {
            value.close(false);
        }
    }

    private static void safeClose(ClientSession value) {
        if (value != null) {
            value.close(false);
        }
    }

    private static void safeClose(SshClient value) {
        if (value != null) {
            try {
                value.stop();
            } catch (RuntimeException ignore) {
                // no-op
            }
        }
    }
}
