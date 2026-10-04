package org.ranobe.ranobe.sources;

import java.io.IOException;

/**
 * A source wants the user to pass a human check (such as a Cloudflare Turnstile) in a browser before it
 * will serve more chapters. Requests made by the app cannot pass it, but a WebView can.
 */
public class ChallengeRequiredException extends IOException {
    public static final String PREFIX = "Human check required";

    public ChallengeRequiredException(String detail) {
        super(detail == null || detail.isEmpty() ? PREFIX : PREFIX + ": " + detail);
    }

    /** Lets code that only has an error message (such as the reader) recognise this error. */
    public static boolean isChallengeRequired(String message) {
        return message != null && message.startsWith(PREFIX);
    }
}
