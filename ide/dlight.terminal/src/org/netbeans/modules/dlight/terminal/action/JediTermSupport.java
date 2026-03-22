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
package org.netbeans.modules.dlight.terminal.action;

import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.netbeans.lib.terminalemulator.support.TermOptions;
import org.netbeans.modules.dlight.terminal.backend.TerminalBackendFactory;
import org.netbeans.modules.dlight.terminal.backend.local.Pty4jTtyConnector;
import org.netbeans.modules.dlight.terminal.backend.nativeexecution.NativeProcessTtyConnector;
import org.netbeans.modules.dlight.terminal.backend.ssh.BackendTtyConnector;
import org.netbeans.modules.dlight.terminal.backend.ssh.SshConnectionConfig;
import org.netbeans.modules.dlight.terminal.ui.JediTermTab;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.netbeans.modules.nativeexecution.api.HostInfo;
import org.netbeans.modules.nativeexecution.api.NativeProcess;
import org.netbeans.modules.nativeexecution.api.NativeProcessBuilder;
import org.netbeans.modules.nativeexecution.api.pty.PtySupport;
import org.netbeans.modules.nativeexecution.api.util.ConnectionManager;
import org.netbeans.modules.nativeexecution.api.util.ConnectionManager.CancellationException;
import org.netbeans.modules.nativeexecution.api.util.HostInfoUtils;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.windows.IOContainer;

public final class JediTermSupport {

    private static final Logger LOG = Logger.getLogger(JediTermSupport.class.getName());
    private static final RequestProcessor RP = new RequestProcessor(JediTermSupport.class);
    private static final TerminalBackendFactory FACTORY = new TerminalBackendFactory();
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final int INITIAL_COLUMNS = 120;
    private static final int INITIAL_ROWS = 35;
    private static final String LOCAL_ICON = "org/netbeans/modules/dlight/terminal/action/local_term.svg"; // NOI18N
    private static final String REMOTE_ICON = "org/netbeans/modules/dlight/terminal/action/remote_term.svg"; // NOI18N
    private static final String WINDOWS_PWSH = "C:\\Program Files\\PowerShell\\7\\pwsh.exe"; // NOI18N
    private static final String WINDOWS_POWERSHELL = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"; // NOI18N
    private static final String WINDOWS_GIT_BASH = "C:\\Program Files\\Git\\bin\\bash.exe"; // NOI18N
    private static final String WINDOWS_CMD = "C:\\Windows\\System32\\cmd.exe"; // NOI18N
    private static final Path WINDOWS_COLORS_SCRIPT = Paths.get("C:\\Scripts\\CustomPowerShellColors.ps1"); // NOI18N

    private JediTermSupport() {
    }

    public static void openTerminal(
            IOContainer ioContainer,
            String tabTitle,
            ExecutionEnvironment env,
            String dir,
            boolean silentMode) {
        if (ioContainer == null || env == null) {
            return;
        }
        RP.post(() -> {
            try {
                TtyConnector connector = createConnector(env, dir);
                Icon icon = ImageUtilities.loadImageIcon(env.isLocal() ? LOCAL_ICON : REMOTE_ICON, false);
                SwingUtilities.invokeLater(() -> addTab(ioContainer, tabTitle, icon, connector));
            } catch (IOException | CancellationException ex) {
                handleOpenFailure(ex, silentMode);
            }
        });
    }

    public static void openSshTerminal(IOContainer ioContainer, SshConnectionConfig config) {
        if (ioContainer == null || config == null) {
            return;
        }
        String tabTitle = NbBundle.getMessage(
                SshTerminalBackendAction.class,
                "SshTerminalTabTitle",
                config.username(),
                config.host(),
                config.port());
        RP.post(() -> {
            try {
                TtyConnector connector = new BackendTtyConnector(
                        FACTORY.createSshBackend(config),
                        UTF_8,
                        "SSH"); // NOI18N
                Icon icon = ImageUtilities.loadImageIcon(REMOTE_ICON, false);
                SwingUtilities.invokeLater(() -> addTab(ioContainer, tabTitle, icon, connector));
            } catch (IOException ex) {
                handleOpenFailure(ex, false);
            }
        });
    }

    private static void addTab(IOContainer ioContainer, String tabTitle, Icon icon, TtyConnector connector) {
        JediTermTab tab = new JediTermTab(ioContainer, tabTitle, connector);
        ioContainer.add(tab, tab);
        ioContainer.setTitle(tab, tabTitle);
        ioContainer.setToolTipText(tab, tabTitle);
        if (icon != null) {
            ioContainer.setIcon(tab, icon);
        }
        SwingUtilities.invokeLater(() -> {
            tab.start();
            ioContainer.select(tab);
            tab.requestFocusInWindow();
        });
    }

    private static TtyConnector createConnector(ExecutionEnvironment env, String dir) throws IOException, CancellationException {
        if (env.isLocal()) {
            return createLocalConnector(dir);
        }
        return createRemoteConnector(env, dir);
    }

    private static TtyConnector createLocalConnector(String dir) throws IOException {
        List<String> command = buildLocalCommand();
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.putIfAbsent("TERM", "xterm-256color"); // NOI18N
        if (isBash(command.get(0))) {
            environment.put("CHERE_INVOKING", "1"); // NOI18N
        }
        PtyProcess process = new PtyProcessBuilder()
                .setCommand(command.toArray(new String[0]))
                .setEnvironment(environment)
                .setDirectory(resolveLocalWorkingDirectory(dir).toString())
                .setInitialColumns(INITIAL_COLUMNS)
                .setInitialRows(INITIAL_ROWS)
                .setRedirectErrorStream(true)
                .setWindowsAnsiColorEnabled(true)
                .start();
        return new Pty4jTtyConnector(process, UTF_8, command);
    }

    private static TtyConnector createRemoteConnector(ExecutionEnvironment env, String dir) throws IOException, CancellationException {
        ConnectionManager.getInstance().connectTo(env);
        ConnectionManager.getInstance().addConnectionToRecentConnections(env);
        HostInfo hostInfo = HostInfoUtils.getHostInfo(env);
        List<String> command = buildRemoteCommand(hostInfo);

        NativeProcessBuilder builder = NativeProcessBuilder.newProcessBuilder(env)
                .setExecutable(command.get(0))
                .setUsePty(true)
                .setCharset(UTF_8)
                .redirectError();
        if (command.size() > 1) {
            builder.setArguments(command.subList(1, command.size()).toArray(new String[0]));
        }
        if (!isBlank(dir)) {
            builder.setWorkingDirectory(dir);
        }

        NativeProcess process = builder.call();
        return new NativeProcessTtyConnector(
                process,
                env,
                PtySupport.getTTY(process),
                UTF_8,
                env.getDisplayName(),
                command);
    }

    private static List<String> buildLocalCommand() {
        List<String> preferredWindowsCommand = buildPreferredWindowsCommand();
        if (preferredWindowsCommand != null) {
            return preferredWindowsCommand;
        }
        String shell = findDefaultShell();
        if (isBlank(shell) || !Files.isRegularFile(Paths.get(shell))) {
            shell = configuredLocalShell();
            if (isBlank(shell) || !Files.isRegularFile(Paths.get(shell))) {
                shell = Utilities.isWindows() ? "cmd.exe" : "/bin/sh"; // NOI18N
            }
        }
        List<String> command = new ArrayList<>();
        command.add(shell);
        String shellName = baseName(shell).toLowerCase();
        if (Utilities.isWindows()) {
            if ("bash.exe".equals(shellName)) { // NOI18N
                command.add("--login"); // NOI18N
                command.add("-i"); // NOI18N
            } else if ("pwsh.exe".equals(shellName) || "powershell.exe".equals(shellName)) { // NOI18N
                command.add("-NoLogo"); // NOI18N
            }
        } else if ("bash".equals(shellName) || "zsh".equals(shellName)) { // NOI18N
            command.add("-l"); // NOI18N
        }
        return command;
    }

    private static List<String> buildPreferredWindowsCommand() {
        if (!Utilities.isWindows()) {
            return null;
        }
        String pwsh = WINDOWS_PWSH;
        if (!Files.isRegularFile(Paths.get(pwsh))) {
            return null;
        }
        List<String> command = new ArrayList<>();
        command.add(pwsh);
        command.add("-NoExit"); // NOI18N
        command.add("-ExecutionPolicy"); // NOI18N
        command.add("Bypass"); // NOI18N
        if (Files.isRegularFile(WINDOWS_COLORS_SCRIPT)) {
            command.add("-File"); // NOI18N
            command.add(WINDOWS_COLORS_SCRIPT.toString());
        } else {
            command.add("-NoLogo"); // NOI18N
        }
        return command;
    }

    private static List<String> buildRemoteCommand(HostInfo hostInfo) {
        String shell = hostInfo.getLoginShell();
        if (isBlank(shell)) {
            shell = hostInfo.getShell();
        }
        if (isBlank(shell)) {
            shell = "/bin/sh"; // NOI18N
        }
        List<String> command = new ArrayList<>();
        command.add(shell);
        String shellName = baseName(shell).toLowerCase();
        if ("bash".equals(shellName) || "zsh".equals(shellName) || "ksh".equals(shellName)) { // NOI18N
            command.add("-l"); // NOI18N
        }
        return command;
    }

    private static String configuredLocalShell() {
        return TermOptions.getDefault(NbPreferences.forModule(TermOptions.class)).getShellPath();
    }

    private static String findDefaultShell() {
        return Utilities.isWindows() ? firstExistingFile(
                WINDOWS_PWSH,
                WINDOWS_POWERSHELL,
                WINDOWS_GIT_BASH,
                WINDOWS_CMD
        ) : firstExistingFile(
                "/bin/bash", // NOI18N
                "/bin/zsh", // NOI18N
                "/bin/sh" // NOI18N
        );
    }

    private static Path resolveLocalWorkingDirectory(String dir) {
        if (!isBlank(dir)) {
            Path requested = Paths.get(dir);
            if (Files.isDirectory(requested)) {
                return requested.toAbsolutePath().normalize();
            }
        }
        Path home = Paths.get(System.getProperty("user.home", ".")); // NOI18N
        if (Files.isDirectory(home)) {
            return home.toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize(); // NOI18N
    }

    private static void handleOpenFailure(Exception ex, boolean silentMode) {
        LOG.log(Level.FINE, "Failed to open JediTerm terminal", ex); // NOI18N
        if (silentMode) {
            return;
        }
        String message = NbBundle.getMessage(
                JediTermSupport.class,
                "TerminalAction.FailedToStart.text",
                rootCauseMessage(ex));
        SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private static String firstExistingFile(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate) && Files.isRegularFile(Paths.get(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static String baseName(String path) {
        if (isBlank(path)) {
            return ""; // NOI18N
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static boolean isBash(String shell) {
        String shellName = baseName(shell).toLowerCase();
        return "bash".equals(shellName) || "bash.exe".equals(shellName); // NOI18N
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
