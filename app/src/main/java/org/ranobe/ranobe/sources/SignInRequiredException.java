package org.ranobe.ranobe.sources;

import java.io.IOException;

/** A source needs the user to sign in before it will serve this chapter. */
public class SignInRequiredException extends IOException {
    public SignInRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
