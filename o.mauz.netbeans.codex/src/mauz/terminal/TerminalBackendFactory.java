package mauz.terminal;

import mauz.terminal.local.LocalConPtyBackend;
import mauz.terminal.local.LocalConPtyConfig;
import mauz.terminal.ssh.SshConnectionConfig;
import mauz.terminal.ssh.SshTerminalBackend;

/**
 * Central factory used by frontend code to choose a transport backend.
 */
public final class TerminalBackendFactory {

    public TerminalBackend createLocalConPtyBackend(LocalConPtyConfig config) {
        return new LocalConPtyBackend(config);
    }

    public TerminalBackend createSshBackend(SshConnectionConfig config) {
        return new SshTerminalBackend(config);
    }
}
