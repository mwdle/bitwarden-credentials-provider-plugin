package com.mwdle.converters;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.mwdle.model.BitwardenItem;
import hudson.Extension;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

/**
 * Converts a Bitwarden 'Secure Note' item into a Jenkins {@link StringCredentials}.
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
     * Constructs a {@link StringCredentialsImpl} using the content of the {@code notes} field.
     */
    @Override
    public StringCredentials convert(CredentialsScope scope, String id, String description, BitwardenItem item) {
        return new StringCredentialsImpl(scope, id, description, Secret.fromString(item.getNotes()));
    }
}