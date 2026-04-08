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

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.modules.dlight.terminal.backend.TerminalGlyphNormalizer;
import org.netbeans.modules.dlight.terminal.backend.TerminalBackend;

/**
 * Adapts the transport-neutral SSH backend to JediTerm's TtyConnector API.
 */
public final class BackendTtyConnector implements TtyConnector {

    private final TerminalBackend backend;
    private final String name;
    private final PipedInputStream input;
    private final PipedOutputStream output;
    private final InputStreamReader reader;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final CountDownLatch closed = new CountDownLatch(1);

    public BackendTtyConnector(TerminalBackend backend, Charset charset, String name) throws IOException {
        this.backend = backend;
        this.name = name;
        this.input = new PipedInputStream(128 * 1024);
        this.output = new PipedOutputStream(input);
        this.reader = new InputStreamReader(input, charset);
        this.backend.setOutputListener(this::onBytes);
        this.backend.setCloseListener(this::close);
        this.connected.set(true);
        try {
            this.backend.start();
        } catch (IOException | RuntimeException ex) {
            close();
            throw ex;
        }
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        int read = reader.read(buf, offset, length);
        TerminalGlyphNormalizer.normalize(buf, offset, read);
        return read;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        backend.write(bytes);
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(reader.getEncoding() == null ? Charset.defaultCharset() : Charset.forName(reader.getEncoding())));
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void resize(TermSize termSize) {
        if (!connected.get()) {
            return;
        }
        try {
            backend.resize(termSize.getColumns(), termSize.getRows(), 0, 0);
        } catch (IOException ex) {
            close();
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        closed.await();
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        try {
            backend.close();
        } catch (IOException ignored) {
        }
        try {
            output.close();
        } catch (IOException ignored) {
        }
        try {
            input.close();
        } catch (IOException ignored) {
        }
        closed.countDown();
    }

    private void onBytes(byte[] bytes) {
        if (!connected.get() || bytes == null || bytes.length == 0) {
            return;
        }
        try {
            output.write(bytes);
            output.flush();
        } catch (IOException ex) {
            close();
        }
    }
}
