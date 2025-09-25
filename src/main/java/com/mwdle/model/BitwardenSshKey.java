package com.mwdle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represents the nested 'sshKey' object within a Bitwarden item JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
// Suppress SpotBugs warning for fields populated by the Jackson JSON parser
@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
public class BitwardenSshKey {
    /**
     * The private key text.
     */
    private String privateKey;
    /**
     * The public key text, which may include a comment.
     */
    private String publicKey;

    /**
     * @return The private key text.
     */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * @return The public key text.
     */
    public String getPublicKey() {
        return publicKey;
    }
}
