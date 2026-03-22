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

import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * Immutable SFTP directory entry metadata.
 */
public final class SftpDirectoryEntry {

    private final String filename;
    private final String longFilename;
    private final boolean directory;
    private final boolean regularFile;
    private final boolean symbolicLink;
    private final long size;
    private final int permissions;
    private final FileTime modifiedTime;

    public SftpDirectoryEntry(
            String filename,
            String longFilename,
            boolean directory,
            boolean regularFile,
            boolean symbolicLink,
            long size,
            int permissions,
            FileTime modifiedTime) {
        this.filename = Objects.requireNonNull(filename, "filename"); // NOI18N
        this.longFilename = longFilename == null ? filename : longFilename;
        this.directory = directory;
        this.regularFile = regularFile;
        this.symbolicLink = symbolicLink;
        this.size = size;
        this.permissions = permissions;
        this.modifiedTime = modifiedTime;
    }

    public String filename() {
        return filename;
    }

    public String longFilename() {
        return longFilename;
    }

    public boolean directory() {
        return directory;
    }

    public boolean regularFile() {
        return regularFile;
    }

    public boolean symbolicLink() {
        return symbolicLink;
    }

    public long size() {
        return size;
    }

    public int permissions() {
        return permissions;
    }

    public FileTime modifiedTime() {
        return modifiedTime;
    }
}
