package mauz.terminal;

import java.time.Instant;

/**
 * Runtime counters for backend traffic monitoring.
 */
public record BackendIoMetrics(
        long bytesFromRemote,
        long bytesToRemote,
        Instant startedAt,
        Instant lastReadAt,
        Instant lastWriteAt
) {
}
