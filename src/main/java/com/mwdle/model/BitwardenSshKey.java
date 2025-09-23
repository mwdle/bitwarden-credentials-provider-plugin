package com.mwdle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the nested 'sshKey' object within a Bitwarden item JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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