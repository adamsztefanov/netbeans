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

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyChannelConfiguration;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

/**
 * Reference-counted authenticated SSH client session that can be shared by
 * terminal and SFTP services.
 */
public final class SshClientSessionHandle implements Closeable {

    private final Object lock = new Object();
    private final SshConnectionConfig config;
    private final SshClient client;
    private final ClientSession session;

    private int referenceCount = 1;
    private boolean closed;

    private SshClientSessionHandle(SshConnectionConfig config, SshClient client, ClientSession session) {
        this.config = Objects.requireNonNull(config, "config"); // NOI18N
        this.client = Objects.requireNonNull(client, "client"); // NOI18N
        this.session = Objects.requireNonNull(session, "session"); // NOI18N
    }

    public static SshClientSessionHandle open(SshConnectionConfig config) throws IOException {
        Objects.requireNonNull(config, "config"); // NOI18N

        SshClient newClient = null;
        ClientSession newSession = null;
        try {
            // NetBeans module classloading does not expose MINA's optional
            // BouncyCastle path reliably here. Password auth works fine on JCE.
            System.setProperty(SecurityUtils.REGISTER_BOUNCY_CASTLE_PROP, Boolean.FALSE.toString());
            SecurityUtils.setAPrioriDisabledProvider(SecurityUtils.BOUNCY_CASTLE, true);
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
            return new SshClientSessionHandle(config, newClient, newSession);
        } catch (IOException | RuntimeException ex) {
            safeClose(newSession);
            safeClose(newClient);
            throw ex instanceof IOException ? (IOException) ex
                    : new IOException("Failed to establish SSH session", ex); // NOI18N
        }
    }

    public SshConnectionConfig config() {
        return config;
    }

    public SshClientSessionHandle retain() {
        synchronized (lock) {
            ensureOpen();
            referenceCount++;
            return this;
        }
    }

    public ClientSession session() {
        synchronized (lock) {
            ensureOpen();
            return session;
        }
    }

    public ChannelShell createShellChannel(PtyChannelConfiguration pty, Map<String, String> env) throws IOException {
        synchronized (lock) {
            ensureOpen();
            return session.createShellChannel(pty, env);
        }
    }

    public SftpClient createSftpClient() throws IOException {
        synchronized (lock) {
            ensureOpen();
            return SftpClientFactory.instance().createSftpClient(session);
        }
    }

    @Override
    public void close() {
        ClientSession currentSession = null;
        SshClient currentClient = null;

        synchronized (lock) {
            if (closed) {
                return;
            }
            referenceCount--;
            if (referenceCount > 0) {
                return;
            }
            closed = true;
            currentSession = session;
            currentClient = client;
        }

        safeClose(currentSession);
        safeClose(currentClient);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SSH session handle is closed"); // NOI18N
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
