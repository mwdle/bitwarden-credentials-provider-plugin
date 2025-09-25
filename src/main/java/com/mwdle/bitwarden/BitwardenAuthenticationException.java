package com.mwdle.bitwarden;

import java.io.IOException;

/**
 * A specialized IOException thrown when the Bitwarden CLI fails due to an authentication error.
 */
public class BitwardenAuthenticationException extends IOException {
    public BitwardenAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
