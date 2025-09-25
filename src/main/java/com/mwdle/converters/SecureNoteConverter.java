package com.mwdle.converters;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.mwdle.model.BitwardenItem;
import hudson.Extension;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

/**
 * Converts a Bitwarden 'Secure Note' item into a Jenkins credential.
 * If the name ends with ".env", treat it as a {@link FileCredentialsImpl}.
 * Otherwise, default to {@link StringCredentialsImpl}.
 */
@Extension
public class SecureNoteConverter extends BitwardenItemConverter {
    /**
     * {@inheritDoc}
     * <p>
     * Returns true if the Bitwarden item contains a non-null {@code notes} field.
     */
    @Override
    public boolean canConvert(BitwardenItem item) {
        return item.getNotes() != null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a {@link StringCredentialsImpl} using the content of the {@code notes} field.
     * Returns a {@link FileCredentialsImpl} instead if the item name ends with .env (intended to support Docker Compose)
     */
    @Override
    public StandardCredentials convert(CredentialsScope scope, String id, String description, BitwardenItem item) {
        if (item.getName().toLowerCase().endsWith(".env")) {
            return new FileCredentialsImpl(
                    scope, id, description, item.getName(), SecretBytes.fromString(item.getNotes()));
        } else {
            return new StringCredentialsImpl(scope, id, description, Secret.fromString(item.getNotes()));
        }
    }
}
