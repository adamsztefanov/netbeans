/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.dlight.terminal.backend.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.common.channel.PtyChannelConfiguration;
import org.apache.sshd.common.channel.StreamingChannel;
import org.netbeans.modules.dlight.terminal.backend.BackendIoMetrics;
import org.netbeans.modules.dlight.terminal.backend.TerminalBackend;

/**
 * Apache MINA SSHD-backed interactive shell backend.
 */
public final class SshTerminalBackend implements TerminalBackend {

    private static final Logger LOG = Logger.getLogger(SshTerminalBackend.class.getName());

    private final Object lock = new Object();
    private final SshConnectionConfig config;
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ssh-terminal-backend-io"); // NOI18N
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Consumer<byte[]>> outputListenerRef =
            new AtomicReference<>(bytes -> {
            });
    private final AtomicReference<Runnable> closeListenerRef =
            new AtomicReference<>(() -> {
            });
    private final AtomicLong bytesFromRemote = new AtomicLong();
    private final AtomicLong bytesToRemote = new AtomicLong();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastReadAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastWriteAt = new AtomicReference<>();
    private final SshClientSessionHandle initialSessionHandle;

    private volatile SshClientSessionHandle sessionHandle;
    private volatile ChannelShell channel;
    private volatile OutputStream channelInput;
    private volatile Future<?> stdoutPump;
    private volatile Future<?> stderrPump;
    private volatile boolean started;
    private volatile boolean closed;

    public SshTerminalBackend(SshConnectionConfig config) {
        this.config = Objects.requireNonNull(config, "config"); // NOI18N
        this.initialSessionHandle = null;
    }

    public SshTerminalBackend(SshClientSessionHandle sessionHandle) {
        SshClientSessionHandle retainedHandle = Objects.requireNonNull(sessionHandle, "sessionHandle").retain(); // NOI18N
        this.config = retainedHandle.config();
        this.initialSessionHandle = retainedHandle;
    }

    @Override
    public void setOutputListener(Consumer<byte[]> listener) {
        outputListenerRef.set(listener == null ? bytes -> {
        } : listener);
    }

    @Override
    public void setCloseListener(Runnable listener) {
        closeListenerRef.set(listener == null ? () -> {
        } : listener);
    }

    @Override
    public void start() throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException("backend is closed"); // NOI18N
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
        SshClientSessionHandle activeSessionHandle = null;
        ChannelShell newChannel = null;

        try {
            activeSessionHandle = initialSessionHandle != null
                    ? initialSessionHandle
                    : SshClientSessionHandle.open(config);

            PtyChannelConfiguration pty = new PtyChannelConfiguration();
            pty.setPtyType(config.termType());
            pty.setPtyColumns(config.initialColumns());
            pty.setPtyLines(config.initialRows());
            pty.setPtyWidth(config.initialPixelWidth());
            pty.setPtyHeight(config.initialPixelHeight());

            Map<String, String> shellEnv = new LinkedHashMap<>();
            shellEnv.put("TERM", config.termType()); // NOI18N
            shellEnv.put("COLORTERM", "truecolor"); // NOI18N
            shellEnv.put("CLICOLOR", "1"); // NOI18N
            shellEnv.put("TERM_PROGRAM", "ApacheNetBeans"); // NOI18N
            shellEnv.put("TERM_PROGRAM_VERSION", "ssh"); // NOI18N
            shellEnv.put("NETBEANS_TERMINAL", "ssh"); // NOI18N
            shellEnv.put("NVIM_TUI_ENABLE_TRUE_COLOR", "1"); // NOI18N

            newChannel = activeSessionHandle.createShellChannel(pty, shellEnv);
            newChannel.setStreaming(StreamingChannel.Streaming.Async);
            newChannel.open().verify(Duration.ofMillis(config.channelOpenTimeoutMillis()));

            channelInput = newChannel.getInvertedIn();
            sessionHandle = activeSessionHandle;
            channel = newChannel;

            ChannelShell openedChannel = newChannel;
            stdoutPump = ioExecutor.submit(() -> pump(openedChannel.getInvertedOut()));
            stderrPump = ioExecutor.submit(() -> pump(openedChannel.getInvertedErr()));
        } catch (IOException | RuntimeException ex) {
            safeClose(newChannel);
            if (activeSessionHandle != null && sessionHandle == null) {
                activeSessionHandle.close();
            }
            throw ex instanceof IOException ? (IOException) ex
                    : new IOException("Failed to start SSH terminal backend", ex); // NOI18N
        }
    }

    private void pump(InputStream source) {
        if (source == null) {
            return;
        }
        byte[] buffer = new byte[8192];
        try (InputStream input = source) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                bytesFromRemote.addAndGet(read);
                lastReadAt.set(Instant.now());
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                try {
                    outputListenerRef.get().accept(chunk);
                } catch (RuntimeException listenerException) {
                    LOG.log(Level.FINE, "Output listener threw an exception", listenerException); // NOI18N
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
        OutputStream output;
        synchronized (lock) {
            if (closed) {
                throw new IOException("backend is closed"); // NOI18N
            }
            if (!started || channelInput == null) {
                throw new IOException("backend is not started"); // NOI18N
            }
            output = channelInput;
        }
        output.write(data);
        output.flush();
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
    public void close() {
        SshClientSessionHandle currentSessionHandle;
        ChannelShell currentChannel;
        Future<?> outputPump;
        Future<?> errorPump;
        OutputStream input;

        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            started = false;
            currentSessionHandle = sessionHandle;
            currentChannel = channel;
            outputPump = stdoutPump;
            errorPump = stderrPump;
            input = channelInput;
            sessionHandle = null;
            channel = null;
            stdoutPump = null;
            stderrPump = null;
            channelInput = null;
        }

        if (outputPump != null) {
            outputPump.cancel(true);
        }
        if (errorPump != null) {
            errorPump.cancel(true);
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
        if (currentSessionHandle != null) {
            currentSessionHandle.close();
        } else if (initialSessionHandle != null) {
            initialSessionHandle.close();
        }
        try {
            closeListenerRef.get().run();
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "SSH terminal close listener failed", ex); // NOI18N
        }
    }

    private static void safeClose(ChannelShell value) {
        if (value != null) {
            value.close(false);
        }
    }

}
