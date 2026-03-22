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
package org.netbeans.modules.dlight.terminal.backend.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.sshd.sftp.client.SftpClient;
import org.netbeans.modules.dlight.terminal.backend.ssh.SshClientSessionHandle;

/**
 * MINA SSHD-backed SFTP service that reuses an authenticated SSH session.
 */
public final class SshSftpClientService implements SftpClientService {

    private final SshClientSessionHandle sessionHandle;

    public SshSftpClientService(SshClientSessionHandle sessionHandle) {
        this.sessionHandle = Objects.requireNonNull(sessionHandle, "sessionHandle").retain(); // NOI18N
    }

    @Override
    public String canonicalPath(String remotePath) throws IOException {
        try (SftpClient client = sessionHandle.createSftpClient()) {
            return client.canonicalPath(pathOrCurrentDir(remotePath));
        }
    }

    @Override
    public SftpDirectoryEntry stat(String remotePath) throws IOException {
        try (SftpClient client = sessionHandle.createSftpClient()) {
            return toEntry(remotePath, remotePath, client.stat(pathOrCurrentDir(remotePath)));
        }
    }

    @Override
    public List<SftpDirectoryEntry> listDirectory(String remotePath) throws IOException {
        try (SftpClient client = sessionHandle.createSftpClient()) {
            List<SftpDirectoryEntry> entries = new ArrayList<>();
            for (SftpClient.DirEntry entry : client.readDir(pathOrCurrentDir(remotePath))) {
                String filename = entry.getFilename();
                if (".".equals(filename) || "..".equals(filename)) { // NOI18N
                    continue;
                }
                entries.add(toEntry(filename, entry.getLongFilename(), entry.getAttributes()));
            }
            return entries;
        }
    }

    @Override
    public void download(String remotePath, OutputStream target) throws IOException {
        Objects.requireNonNull(target, "target"); // NOI18N
        try (SftpClient client = sessionHandle.createSftpClient();
                InputStream input = client.read(pathOrCurrentDir(remotePath))) {
            copy(input, target);
        }
    }

    @Override
    public void upload(InputStream source, String remotePath) throws IOException {
        Objects.requireNonNull(source, "source"); // NOI18N
        try (SftpClient client = sessionHandle.createSftpClient();
                OutputStream output = client.write(pathOrCurrentDir(remotePath))) {
            copy(source, output);
            output.flush();
        }
    }

    @Override
    public void createDirectory(String remotePath) throws IOException {
        try (SftpClient client = sessionHandle.createSftpClient()) {
            client.mkdir(pathOrCurrentDir(remotePath));
        }
    }

    @Override
    public void deleteFile(String remotePath) throws IOException {
        try (SftpClient client = sessionHandle.createSftpClient()) {
            client.remove(pathOrCurrentDir(remotePath));
        }
    }

    @Override
    public void rename(String sourcePath, String targetPath) throws IOException {
        try (SftpClient client = sessionHandle.createSftpClient()) {
            client.rename(pathOrCurrentDir(sourcePath), pathOrCurrentDir(targetPath));
        }
    }

    @Override
    public void close() {
        sessionHandle.close();
    }

    private static String pathOrCurrentDir(String remotePath) {
        String value = remotePath == null ? "" : remotePath.trim();
        return value.isEmpty() ? "." : value; // NOI18N
    }

    private static SftpDirectoryEntry toEntry(String filename, String longFilename, SftpClient.Attributes attributes) {
        return new SftpDirectoryEntry(
                filename,
                longFilename,
                attributes != null && attributes.isDirectory(),
                attributes != null && attributes.isRegularFile(),
                attributes != null && attributes.isSymbolicLink(),
                attributes == null ? 0L : attributes.getSize(),
                attributes == null ? 0 : attributes.getPermissions(),
                attributes == null ? null : attributes.getModifyTime()
        );
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
    }
}
