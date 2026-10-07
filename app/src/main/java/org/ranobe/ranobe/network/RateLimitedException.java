package org.ranobe.ranobe.network;

import java.io.IOException;

// The server answered 429 (too many requests), optionally saying how long to wait.
public class RateLimitedException extends IOException {
    private static final long MAX_WAIT_SECONDS = 300;

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    // Seconds from a Retry-After header, or 0 when it is missing or is an HTTP date.
    public static long parseRetryAfterSeconds(String header) {
        if (header == null) return 0;
        try {
            long seconds = Long.parseLong(header.trim());
            return Math.max(0, Math.min(seconds, MAX_WAIT_SECONDS));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // How long to wait before retrying, using {@code fallbackMillis} when the server gave no hint.
    public long retryAfterMillis(long fallbackMillis) {
        return retryAfterSeconds > 0 ? retryAfterSeconds * 1000L : fallbackMillis;
    }
}
