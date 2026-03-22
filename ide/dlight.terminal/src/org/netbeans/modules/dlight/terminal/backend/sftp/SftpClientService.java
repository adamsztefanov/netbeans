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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Transport-neutral SFTP operations for an authenticated SSH session.
 */
public interface SftpClientService extends Closeable {

    String canonicalPath(String remotePath) throws IOException;

    SftpDirectoryEntry stat(String remotePath) throws IOException;

    List<SftpDirectoryEntry> listDirectory(String remotePath) throws IOException;

    void download(String remotePath, OutputStream target) throws IOException;

    void upload(InputStream source, String remotePath) throws IOException;

    void createDirectory(String remotePath) throws IOException;

    void deleteFile(String remotePath) throws IOException;

    void rename(String sourcePath, String targetPath) throws IOException;
}
