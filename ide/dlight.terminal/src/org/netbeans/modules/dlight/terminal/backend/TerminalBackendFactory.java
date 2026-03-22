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
package org.netbeans.modules.dlight.terminal.backend;

import java.io.IOException;
import org.netbeans.modules.dlight.terminal.backend.local.LocalConPtyBackend;
import org.netbeans.modules.dlight.terminal.backend.local.LocalConPtyConfig;
import org.netbeans.modules.dlight.terminal.backend.sftp.SftpClientService;
import org.netbeans.modules.dlight.terminal.backend.sftp.SshSftpClientService;
import org.netbeans.modules.dlight.terminal.backend.ssh.SshClientSessionHandle;
import org.netbeans.modules.dlight.terminal.backend.ssh.SshConnectionConfig;
import org.netbeans.modules.dlight.terminal.backend.ssh.SshTerminalBackend;

/**
 * Creates transport backends for terminal sessions.
 */
public final class TerminalBackendFactory {

    public TerminalBackend createLocalConPtyBackend(LocalConPtyConfig config) {
        return new LocalConPtyBackend(config);
    }

    public TerminalBackend createSshBackend(SshConnectionConfig config) {
        return new SshTerminalBackend(config);
    }

    public TerminalBackend createSshBackend(SshClientSessionHandle sessionHandle) {
        return new SshTerminalBackend(sessionHandle);
    }

    public SshClientSessionHandle createSshSessionHandle(SshConnectionConfig config) throws IOException {
        return SshClientSessionHandle.open(config);
    }

    public SftpClientService createSftpClientService(SshClientSessionHandle sessionHandle) {
        return new SshSftpClientService(sessionHandle);
    }
}
