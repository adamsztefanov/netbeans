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

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Wincon.COORD;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.dlight.terminal.backend.BackendIoMetrics;
import org.netbeans.modules.dlight.terminal.backend.TerminalBackend;

/**
 * Local Windows pseudoconsole backend with raw stream forwarding.
 */
public final class LocalConPtyBackend implements TerminalBackend {

    private static final Logger LOG = Logger.getLogger(LocalConPtyBackend.class.getName());
    private static final Kernel32 KERNEL32 = Kernel32.INSTANCE;
    private static final ConPtyKernel32 CONPTY = ConPtyKernel32.INSTANCE;
    private static final int PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE = 0x00020016;
    private static final boolean DEBUG_LOCAL_PTY =
            Boolean.getBoolean("org.netbeans.modules.dlight.terminal.local.debug"); // NOI18N

    private final LocalConPtyConfig config;
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "local-terminal-backend-io"); // NOI18N
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
    private final AtomicReference<Instant> lastReadAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastWriteAt = new AtomicReference<>();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final Object lock = new Object();

    private volatile WinNT.HANDLE pseudoConsole;
    private volatile WinNT.HANDLE processHandle;
    private volatile WinNT.HANDLE threadHandle;
    private volatile WinNT.HANDLE inputWriteHandle;
    private volatile WinNT.HANDLE outputReadHandle;
    private volatile WinNT.HANDLE pseudoConsoleInput;
    private volatile WinNT.HANDLE pseudoConsoleOutput;
    private volatile Pointer attributeList;
    private volatile Future<?> outputPump;
    private volatile boolean started;
    private volatile boolean closed;
    private volatile boolean firstOutputLogged;

    public LocalConPtyBackend(LocalConPtyConfig config) {
        this.config = Objects.requireNonNull(config, "config"); // NOI18N
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
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) { // NOI18N
            throw new IOException("ConPTY backend is only supported on Windows"); // NOI18N
        }
        if (DEBUG_LOCAL_PTY) {
            LOG.log(Level.INFO, "Starting ConPTY command={0} cwd={1}",
                    new Object[]{config.command(), config.workingDirectory()}); // NOI18N
        }

        WinBase.SECURITY_ATTRIBUTES securityAttributes = new WinBase.SECURITY_ATTRIBUTES();
        securityAttributes.dwLength = new DWORD(securityAttributes.size());
        securityAttributes.bInheritHandle = true;

        WinNT.HANDLEByReference inputReadRef = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference inputWriteRef = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference outputReadRef = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference outputWriteRef = new WinNT.HANDLEByReference();
        WinNT.HANDLEByReference pseudoConsoleRef = new WinNT.HANDLEByReference();
        WinBase.PROCESS_INFORMATION processInformation = new WinBase.PROCESS_INFORMATION();
        Pointer localAttributeList = null;

        try {
            checkWin32(KERNEL32.CreatePipe(inputReadRef, inputWriteRef, securityAttributes, 0), "CreatePipe(stdin)"); // NOI18N
            checkWin32(KERNEL32.CreatePipe(outputReadRef, outputWriteRef, securityAttributes, 0), "CreatePipe(stdout)"); // NOI18N

            WinNT.HANDLE localInputWrite = inputWriteRef.getValue();
            WinNT.HANDLE localOutputRead = outputReadRef.getValue();
            checkWin32(KERNEL32.SetHandleInformation(localInputWrite, WinBase.HANDLE_FLAG_INHERIT, 0), "SetHandleInformation(stdin write)"); // NOI18N
            checkWin32(KERNEL32.SetHandleInformation(localOutputRead, WinBase.HANDLE_FLAG_INHERIT, 0), "SetHandleInformation(stdout read)"); // NOI18N

            WinNT.HRESULT createResult = CONPTY.CreatePseudoConsole(
                    coord(config.initialColumns(), config.initialRows()),
                    inputReadRef.getValue(),
                    outputWriteRef.getValue(),
                    0,
                    pseudoConsoleRef
            );
            checkHResult(createResult, "CreatePseudoConsole"); // NOI18N
            if (DEBUG_LOCAL_PTY) {
                LOG.log(Level.INFO, "CreatePseudoConsole succeeded size={0}x{1}",
                        new Object[]{config.initialColumns(), config.initialRows()}); // NOI18N
            }

            LongByReference attributeListSize = new LongByReference();
            CONPTY.InitializeProcThreadAttributeList(Pointer.NULL, 1, 0, attributeListSize);
            localAttributeList = new Memory(attributeListSize.getValue());
            checkWin32(CONPTY.InitializeProcThreadAttributeList(localAttributeList, 1, 0, attributeListSize),
                    "InitializeProcThreadAttributeList"); // NOI18N

            Pointer pseudoConsolePointer = new Memory(Native.POINTER_SIZE);
            pseudoConsolePointer.setPointer(0, pseudoConsoleRef.getValue().getPointer());
            checkWin32(CONPTY.UpdateProcThreadAttribute(
                    localAttributeList,
                    0,
                    new Pointer(PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE),
                    pseudoConsolePointer,
                    new SIZE_T(Native.POINTER_SIZE),
                    Pointer.NULL,
                    null
            ), "UpdateProcThreadAttribute"); // NOI18N

            StartupInfoEx startupInfo = new StartupInfoEx();
            startupInfo.cb = new DWORD(startupInfo.size());
            startupInfo.lpAttributeList = localAttributeList;
            startupInfo.write();

            List<String> command = new ArrayList<>(config.command());
            String executable = command.get(0);
            String commandLine = buildCommandLine(command);
            if (DEBUG_LOCAL_PTY) {
                LOG.log(Level.INFO, "CreateProcessW executable={0} commandLine={1}",
                        new Object[]{executable, commandLine}); // NOI18N
            }
            char[] commandLineChars = Native.toCharArray(commandLine);
            int creationFlags = WinBase.EXTENDED_STARTUPINFO_PRESENT
                    | WinBase.CREATE_UNICODE_ENVIRONMENT;

            checkWin32(
                    KERNEL32.CreateProcessW(
                            executable,
                            commandLineChars,
                            null,
                            null,
                            false,
                            new DWORD(creationFlags),
                            Pointer.NULL,
                            config.workingDirectory().toAbsolutePath().toString(),
                            startupInfo,
                            processInformation
                    ),
                    "CreateProcessW" // NOI18N
            );

            pseudoConsole = pseudoConsoleRef.getValue();
            processHandle = processInformation.hProcess;
            threadHandle = processInformation.hThread;
            inputWriteHandle = localInputWrite;
            outputReadHandle = localOutputRead;
            pseudoConsoleInput = inputReadRef.getValue();
            pseudoConsoleOutput = outputWriteRef.getValue();
            attributeList = localAttributeList;
            localAttributeList = null;

            outputPump = ioExecutor.submit(this::pumpOutput);
            ioExecutor.submit(this::waitForProcessExit);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.INFO, "ConPTY startup failed", ex); // NOI18N
            if (localAttributeList != null) {
                CONPTY.DeleteProcThreadAttributeList(localAttributeList);
            }
            safeCloseHandle(processInformation.hThread);
            safeCloseHandle(processInformation.hProcess);
            safeClosePseudoConsole(pseudoConsoleRef.getValue());
            safeCloseHandle(inputWriteRef.getValue());
            safeCloseHandle(inputReadRef.getValue());
            safeCloseHandle(outputReadRef.getValue());
            safeCloseHandle(outputWriteRef.getValue());
            throw ex instanceof IOException ? (IOException) ex
                    : new IOException("Failed to start ConPTY backend", ex); // NOI18N
        }
    }

    private void pumpOutput() {
        byte[] buffer = new byte[8192];
        IntByReference readRef = new IntByReference();
        while (!closed) {
            WinNT.HANDLE currentReadHandle = outputReadHandle;
            if (currentReadHandle == null) {
                return;
            }
            boolean success = KERNEL32.ReadFile(currentReadHandle, buffer, buffer.length, readRef, null);
            int read = readRef.getValue();
            if (!success) {
                int lastError = KERNEL32.GetLastError();
                if (lastError == WinError.ERROR_BROKEN_PIPE || lastError == WinError.ERROR_PIPE_NOT_CONNECTED) {
                    if (!closed) {
                        close();
                    }
                    return;
                }
                if (!closed) {
                    LOG.log(Level.FINE, "ConPTY output pump stopped, error={0}", lastError); // NOI18N
                    close();
                }
                return;
            }
            if (read <= 0) {
                continue;
            }
            bytesFromRemote.addAndGet(read);
            lastReadAt.set(Instant.now());
            byte[] chunk = new byte[read];
            System.arraycopy(buffer, 0, chunk, 0, read);
            if (DEBUG_LOCAL_PTY && !firstOutputLogged) {
                firstOutputLogged = true;
                LOG.log(Level.INFO, "ConPTY first output bytes: {0}", summarizeBytes(chunk)); // NOI18N
            }
            outputListenerRef.get().accept(chunk);
        }
    }

    private void waitForProcessExit() {
        while (!closed) {
            WinNT.HANDLE currentProcess = processHandle;
            if (currentProcess == null) {
                return;
            }
            int waitResult = KERNEL32.WaitForSingleObject(currentProcess, 250);
            if (waitResult == WinError.WAIT_TIMEOUT) {
                continue;
            }
            if (waitResult == WinBase.WAIT_OBJECT_0) {
                close();
                return;
            }
            if (!closed) {
                LOG.log(Level.FINE, "ConPTY process waiter stopped, result={0}", waitResult); // NOI18N
                close();
            }
            return;
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }
        WinNT.HANDLE output;
        synchronized (lock) {
            if (closed) {
                throw new IOException("backend is closed"); // NOI18N
            }
            if (!started || inputWriteHandle == null) {
                throw new IOException("backend is not started"); // NOI18N
            }
            output = inputWriteHandle;
        }
        IntByReference writtenRef = new IntByReference();
        checkWin32(KERNEL32.WriteFile(output, data, data.length, writtenRef, null), "WriteFile"); // NOI18N
        checkWin32(KERNEL32.FlushFileBuffers(output), "FlushFileBuffers"); // NOI18N
        bytesToRemote.addAndGet(data.length);
        lastWriteAt.set(Instant.now());
        if (DEBUG_LOCAL_PTY && bytesToRemote.get() == data.length) {
            LOG.log(Level.INFO, "ConPTY first input bytes: {0}", summarizeBytes(data)); // NOI18N
        }
    }

    @Override
    public void resize(int cols, int rows, int pixelWidth, int pixelHeight) throws IOException {
        if (cols <= 0 || rows <= 0) {
            return;
        }
        WinNT.HANDLE currentPseudoConsole;
        synchronized (lock) {
            if (closed || !started) {
                return;
            }
            currentPseudoConsole = pseudoConsole;
        }
        if (currentPseudoConsole != null) {
            if (DEBUG_LOCAL_PTY) {
                LOG.log(Level.INFO, "ResizePseudoConsole {0}x{1}", new Object[]{cols, rows}); // NOI18N
            }
            checkHResult(CONPTY.ResizePseudoConsole(currentPseudoConsole, coord(cols, rows)),
                    "ResizePseudoConsole"); // NOI18N
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
        WinNT.HANDLE currentPseudoConsole;
        WinNT.HANDLE currentProcess;
        WinNT.HANDLE currentThread;
        WinNT.HANDLE currentInputWrite;
        WinNT.HANDLE currentOutputRead;
        WinNT.HANDLE currentPseudoConsoleInput;
        WinNT.HANDLE currentPseudoConsoleOutput;
        Pointer currentAttributeList;
        Future<?> currentOutputPump;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            started = false;
            currentPseudoConsole = pseudoConsole;
            currentProcess = processHandle;
            currentThread = threadHandle;
            currentInputWrite = inputWriteHandle;
            currentOutputRead = outputReadHandle;
            currentPseudoConsoleInput = pseudoConsoleInput;
            currentPseudoConsoleOutput = pseudoConsoleOutput;
            currentAttributeList = attributeList;
            currentOutputPump = outputPump;
            pseudoConsole = null;
            processHandle = null;
            threadHandle = null;
            inputWriteHandle = null;
            outputReadHandle = null;
            pseudoConsoleInput = null;
            pseudoConsoleOutput = null;
            attributeList = null;
            outputPump = null;
        }

        if (currentOutputPump != null) {
            currentOutputPump.cancel(true);
        }
        safeCloseHandle(currentInputWrite);
        safeCloseHandle(currentOutputRead);

        if (currentProcess != null) {
            int waitResult = KERNEL32.WaitForSingleObject(currentProcess, (int) TimeUnit.SECONDS.toMillis(1));
            if (waitResult != WinBase.WAIT_OBJECT_0) {
                KERNEL32.TerminateProcess(currentProcess, 0);
                KERNEL32.WaitForSingleObject(currentProcess, (int) TimeUnit.SECONDS.toMillis(1));
            }
        }

        safeCloseHandle(currentThread);
        safeCloseHandle(currentProcess);
        safeClosePseudoConsole(currentPseudoConsole);
        safeCloseHandle(currentPseudoConsoleInput);
        safeCloseHandle(currentPseudoConsoleOutput);
        if (currentAttributeList != null) {
            CONPTY.DeleteProcThreadAttributeList(currentAttributeList);
        }
        if (DEBUG_LOCAL_PTY) {
            LOG.log(Level.INFO, "Closed ConPTY backend in={0} out={1}",
                    new Object[]{bytesFromRemote.get(), bytesToRemote.get()}); // NOI18N
        }
        try {
            closeListenerRef.get().run();
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Local terminal close listener failed", ex); // NOI18N
        }
        ioExecutor.shutdownNow();
    }

    private static String summarizeBytes(byte[] bytes) {
        int limit = Math.min(bytes.length, 32);
        StringBuilder builder = new StringBuilder(limit * 4);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02x", bytes[i] & 0xff)); // NOI18N
        }
        if (bytes.length > limit) {
            builder.append(" ..."); // NOI18N
        }
        return builder.toString();
    }

    private static String buildCommandLine(List<String> command) {
        StringBuilder builder = new StringBuilder();
        for (String arg : command) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(quoteWindowsArgument(arg));
        }
        return builder.toString();
    }

    private static String quoteWindowsArgument(String value) {
        if (value == null || value.isEmpty()) {
            return "\"\""; // NOI18N
        }
        boolean needsQuotes = value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0 || value.indexOf('"') >= 0;
        if (!needsQuotes) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        int backslashes = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') {
                backslashes++;
            } else if (ch == '"') {
                builder.append(repeat('\\', backslashes * 2 + 1)).append('"');
                backslashes = 0;
            } else {
                if (backslashes > 0) {
                    builder.append(repeat('\\', backslashes));
                    backslashes = 0;
                }
                builder.append(ch);
            }
        }
        if (backslashes > 0) {
            builder.append(repeat('\\', backslashes * 2));
        }
        builder.append('"');
        return builder.toString();
    }

    private static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }

    private static COORD coord(int cols, int rows) {
        COORD coord = new COORD();
        coord.X = (short) cols;
        coord.Y = (short) rows;
        return coord;
    }

    private static void checkWin32(boolean success, String operation) throws IOException {
        if (!success) {
            throw new IOException(operation + " failed with error " + KERNEL32.GetLastError()); // NOI18N
        }
    }

    private static void checkHResult(WinNT.HRESULT result, String operation) throws IOException {
        if (result == null || result.intValue() != 0) {
            int value = result == null ? -1 : result.intValue();
            throw new IOException(operation + " failed with HRESULT 0x" + Integer.toHexString(value)); // NOI18N
        }
    }

    private static void safeCloseHandle(WinNT.HANDLE handle) {
        if (handle != null && handle.getPointer() != null && !Pointer.NULL.equals(handle.getPointer())) {
            KERNEL32.CloseHandle(handle);
        }
    }

    private static void safeClosePseudoConsole(WinNT.HANDLE handle) {
        if (handle != null && handle.getPointer() != null && !Pointer.NULL.equals(handle.getPointer())) {
            CONPTY.ClosePseudoConsole(handle);
        }
    }

    private interface ConPtyKernel32 extends StdCallLibrary {

        ConPtyKernel32 INSTANCE = Native.load("kernel32", ConPtyKernel32.class); // NOI18N

        WinNT.HRESULT CreatePseudoConsole(COORD size, WinNT.HANDLE input, WinNT.HANDLE output, int flags,
                WinNT.HANDLEByReference pseudoConsole);

        void ClosePseudoConsole(WinNT.HANDLE pseudoConsole);

        WinNT.HRESULT ResizePseudoConsole(WinNT.HANDLE pseudoConsole, COORD size);

        boolean InitializeProcThreadAttributeList(Pointer attributeList, int attributeCount, int flags,
                LongByReference size);

        boolean UpdateProcThreadAttribute(Pointer attributeList, int flags, Pointer attribute, Pointer value,
                SIZE_T size, Pointer previousValue, PointerByReference returnSize);

        void DeleteProcThreadAttributeList(Pointer attributeList);
    }

    public static class StartupInfoEx extends WinBase.STARTUPINFO {

        public Pointer lpAttributeList;

        @Override
        protected List<String> getFieldOrder() {
            List<String> fields = new ArrayList<>(super.getFieldOrder());
            fields.add("lpAttributeList"); // NOI18N
            return fields;
        }
    }

    private interface WinError {

        int ERROR_BROKEN_PIPE = 109;
        int ERROR_PIPE_NOT_CONNECTED = 233;
        int WAIT_TIMEOUT = 258;
    }
}
