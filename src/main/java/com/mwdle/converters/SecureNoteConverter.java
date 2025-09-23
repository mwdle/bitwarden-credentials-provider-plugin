package com.mwdle.converters;

import com.mwdle.BitwardenBackedCredential;
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
    public StringCredentials convert(BitwardenBackedCredential pointer, BitwardenItem item) {
        return new StringCredentialsImpl(pointer.getScope(), pointer.getId(), pointer.getDescription(), Secret.fromString(item.getNotes()));
    }
}