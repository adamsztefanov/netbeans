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
package org.netbeans.modules.dlight.terminal.backend.local;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Local backend configuration. This is intentionally simple so native ConPTY
 * integration can be dropped in later without changing frontend code.
 */
public final class LocalConPtyConfig {

    private final List<String> command;
    private final Path workingDirectory;
    private final int initialColumns;
    private final int initialRows;

    public LocalConPtyConfig(List<String> command, Path workingDirectory) {
        this(command, workingDirectory, 120, 35);
    }

    public LocalConPtyConfig(List<String> command, Path workingDirectory, int initialColumns, int initialRows) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty"); // NOI18N
        }
        if (initialColumns <= 0 || initialRows <= 0) {
            throw new IllegalArgumentException("initialColumns and initialRows must be > 0"); // NOI18N
        }
        this.command = List.copyOf(new ArrayList<>(command));
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory"); // NOI18N
        this.initialColumns = initialColumns;
        this.initialRows = initialRows;
    }

    public List<String> command() {
        return command;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    public int initialColumns() {
        return initialColumns;
    }

    public int initialRows() {
        return initialRows;
    }
}
