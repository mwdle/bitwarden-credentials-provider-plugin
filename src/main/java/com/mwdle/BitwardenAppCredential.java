package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.util.Secret;
import java.io.IOException;

/**
 * Defines the contract for a "pointer" credential that references an item in a Bitwarden vault.
 * <p>
 * This interface represents the configuration for a credential that will be lazily fetched by the
 * {@link com.mwdle.provider.BitwardenCredentialsProvider}. It extends {@link StandardCredentials}, making it a
 * recognizable type within the Jenkins ecosystem.
 */
public interface BitwardenAppCredential extends StandardCredentials {

    /**
     * @return The unique UUID of the Bitwarden item, if configured for lookup by ID.
     */
    String getBitwardenItemId();

    /**
     * @return The (potentially non-unique) name of the Bitwarden item, if configured for lookup by name.
     */
    String getBitwardenItemName();

    /**
     * A helper method to get the configured lookup value, which is either the item's ID or its name.
     *
     * @return The configured value used to find the item in the Bitwarden vault.
     */
    String getLookupValue();

    /**
     * The standard method for retrieving the secret content of a credential.
     * <p>
     * For this credential type, the implementation is handled at runtime by a lazy-loading proxy.
     * This interface declares the required exceptions as a best practice for providers that
     * fetch secrets from a remote store.
     *
     * @return The resolved secret.
     * @throws IOException if the secret cannot be retrieved due to an I/O error.
     * @throws InterruptedException if the operation is interrupted.
     */
    Secret getSecret() throws IOException, InterruptedException;
}