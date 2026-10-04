package org.ranobe.ranobe.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RateLimitedExceptionTest {
    @Test
    public void retryAfterSecondsAreParsedAndCapped() {
        assertEquals(30, RateLimitedException.parseRetryAfterSeconds("30"));
        assertEquals(30, RateLimitedException.parseRetryAfterSeconds(" 30 "));
        assertEquals(300, RateLimitedException.parseRetryAfterSeconds("99999"));
    }

    @Test
    public void missingOrUnusableRetryAfterMeansNoHint() {
        assertEquals(0, RateLimitedException.parseRetryAfterSeconds(null));
        assertEquals(0, RateLimitedException.parseRetryAfterSeconds(""));
        assertEquals(0, RateLimitedException.parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertEquals(0, RateLimitedException.parseRetryAfterSeconds("-5"));
    }

    @Test
    public void waitUsesTheServerHintOtherwiseTheFallback() {
        assertEquals(30_000L, new RateLimitedException("x", 30).retryAfterMillis(5_000L));
        assertEquals(5_000L, new RateLimitedException("x", 0).retryAfterMillis(5_000L));
    }
}
