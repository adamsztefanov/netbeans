package mauz.terminal.ssh;

import java.util.Objects;

/**
 * SSH connection and PTY settings for an interactive shell session.
 */
public final class SshConnectionConfig {

    private final String host;
    private final int port;
    private final String username;
    private final char[] password;
    private final int connectTimeoutMillis;
    private final int authTimeoutMillis;
    private final int channelOpenTimeoutMillis;
    private final String termType;
    private final int initialColumns;
    private final int initialRows;
    private final int initialPixelWidth;
    private final int initialPixelHeight;
    private final boolean acceptUnknownHostKeys;

    private SshConnectionConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password.clone();
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.authTimeoutMillis = builder.authTimeoutMillis;
        this.channelOpenTimeoutMillis = builder.channelOpenTimeoutMillis;
        this.termType = builder.termType;
        this.initialColumns = builder.initialColumns;
        this.initialRows = builder.initialRows;
        this.initialPixelWidth = builder.initialPixelWidth;
        this.initialPixelHeight = builder.initialPixelHeight;
        this.acceptUnknownHostKeys = builder.acceptUnknownHostKeys;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public char[] password() {
        return password.clone();
    }

    String passwordAsString() {
        return new String(password);
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int authTimeoutMillis() {
        return authTimeoutMillis;
    }

    public int channelOpenTimeoutMillis() {
        return channelOpenTimeoutMillis;
    }

    public String termType() {
        return termType;
    }

    public int initialColumns() {
        return initialColumns;
    }

    public int initialRows() {
        return initialRows;
    }

    public int initialPixelWidth() {
        return initialPixelWidth;
    }

    public int initialPixelHeight() {
        return initialPixelHeight;
    }

    public boolean acceptUnknownHostKeys() {
        return acceptUnknownHostKeys;
    }

    public static Builder builder(String host, int port, String username, char[] password) {
        return new Builder(host, port, username, password);
    }

    public static final class Builder {

        private final String host;
        private final int port;
        private final String username;
        private final char[] password;

        private int connectTimeoutMillis = 15000;
        private int authTimeoutMillis = 15000;
        private int channelOpenTimeoutMillis = 15000;
        private String termType = "xterm-256color"; // NOI18N
        private int initialColumns = 120;
        private int initialRows = 35;
        private int initialPixelWidth = 0;
        private int initialPixelHeight = 0;
        private boolean acceptUnknownHostKeys = true;

        private Builder(String host, int port, String username, char[] password) {
            this.host = requireNotBlank(host, "host");
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port must be in range 1..65535");
            }
            this.port = port;
            this.username = requireNotBlank(username, "username");
            this.password = Objects.requireNonNull(password, "password").clone();
        }

        public Builder connectTimeoutMillis(int value) {
            this.connectTimeoutMillis = positive(value, "connectTimeoutMillis");
            return this;
        }

        public Builder authTimeoutMillis(int value) {
            this.authTimeoutMillis = positive(value, "authTimeoutMillis");
            return this;
        }

        public Builder channelOpenTimeoutMillis(int value) {
            this.channelOpenTimeoutMillis = positive(value, "channelOpenTimeoutMillis");
            return this;
        }

        public Builder termType(String value) {
            this.termType = requireNotBlank(value, "termType");
            return this;
        }

        public Builder initialSize(int columns, int rows, int pixelWidth, int pixelHeight) {
            if (columns <= 0 || rows <= 0) {
                throw new IllegalArgumentException("columns and rows must be > 0");
            }
            this.initialColumns = columns;
            this.initialRows = rows;
            this.initialPixelWidth = Math.max(pixelWidth, 0);
            this.initialPixelHeight = Math.max(pixelHeight, 0);
            return this;
        }

        public Builder acceptUnknownHostKeys(boolean value) {
            this.acceptUnknownHostKeys = value;
            return this;
        }

        public SshConnectionConfig build() {
            return new SshConnectionConfig(this);
        }

        private static int positive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be > 0");
            }
            return value;
        }

        private static String requireNotBlank(String value, String name) {
            String trimmed = Objects.requireNonNull(value, name).trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return trimmed;
        }
    }
}
