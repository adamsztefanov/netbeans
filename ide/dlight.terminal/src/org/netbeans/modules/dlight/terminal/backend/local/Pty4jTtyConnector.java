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

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import org.netbeans.modules.dlight.terminal.backend.TerminalGlyphNormalizer;

/**
 * Small Pty4J bridge for JediTerm.
 */
public final class Pty4jTtyConnector extends ProcessTtyConnector {

    private final PtyProcess process;

    public Pty4jTtyConnector(PtyProcess process, Charset charset, List<String> commandLine) {
        super(process, charset, commandLine);
        this.process = process;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        int read = super.read(buf, offset, length);
        TerminalGlyphNormalizer.normalize(buf, offset, read);
        return read;
    }

    @Override
    public void resize(TermSize termSize) {
        if (isConnected()) {
            process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
        }
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public String getName() {
        return "Local"; // NOI18N
    }
}
