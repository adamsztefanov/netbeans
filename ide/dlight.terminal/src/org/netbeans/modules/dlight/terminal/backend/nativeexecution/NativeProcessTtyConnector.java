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
package org.netbeans.modules.dlight.terminal.backend.nativeexecution;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.netbeans.modules.nativeexecution.api.NativeProcess;

public final class NativeProcessTtyConnector extends ProcessTtyConnector {

    private static final Logger LOG = Logger.getLogger(NativeProcessTtyConnector.class.getName());
    private final NativeProcess process;
    private final ExecutionEnvironment env;
    private final String tty;
    private final String name;

    public NativeProcessTtyConnector(
            NativeProcess process,
            ExecutionEnvironment env,
            String tty,
            Charset charset,
            String name,
            List<String> commandLine) {
        super(process, charset, commandLine);
        this.process = process;
        this.env = env;
        this.tty = tty;
        this.name = name;
    }

    @Override
    public void resize(TermSize termSize) {
        if (!isConnected() || tty == null || tty.isBlank()) {
            return;
        }
        try {
            Class<?> sttySupport = Class.forName("org.netbeans.modules.nativeexecution.pty.SttySupport"); // NOI18N
            sttySupport.getMethod("apply", ExecutionEnvironment.class, String.class, String.class).invoke(
                    null,
                    env,
                    tty,
                    "cols " + termSize.getColumns() + " rows " + termSize.getRows()); // NOI18N
        } catch (ReflectiveOperationException ex) {
            LOG.log(Level.FINEST, "Could not resize native execution terminal", ex); // NOI18N
        }
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public String getName() {
        return name;
    }
}
