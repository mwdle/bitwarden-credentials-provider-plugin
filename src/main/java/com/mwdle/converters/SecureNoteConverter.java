package com.mwdle.converters;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.mwdle.model.BitwardenItem;
import hudson.Extension;
import hudson.util.Secret;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

/**
 * Converts a Bitwarden 'Secure Note' item into a Jenkins credential.
 * If the name ends with ".env", treat it as a {@link FileCredentialsImpl}.
 * Otherwise, default to {@link StringCredentialsImpl}.
 */
@Extension
public class SecureNoteConverter extends BitwardenItemConverter {

    private static final Logger LOGGER = Logger.getLogger(SecureNoteConverter.class.getName());

    /**
     * {@inheritDoc}
     * <p>
     * Returns true if the Bitwarden item contains a non-null {@code notes} field.
     */
    @Override
    public boolean canConvert(BitwardenItem item) {
        boolean canConvert = item.getNotes() != null;
        LOGGER.fine(() ->
                "canConvert: item id=" + item.getId() + " name='" + item.getName() + "' canConvert=" + canConvert);
        return canConvert;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a {@link StringCredentialsImpl} using the content of the {@code notes} field.
     * Returns a {@link FileCredentialsImpl} instead if the item name ends with .env (intended to support Docker Compose)
     */
    @Override
    public StandardCredentials convert(CredentialsScope scope, String id, String description, BitwardenItem item) {
        LOGGER.fine(() -> "convert: id=" + id + " item id=" + item.getId() + " name='" + item.getName() + "'");
        if (item.getName().trim().toLowerCase().endsWith(".env")) {
            LOGGER.fine(() -> "convert: treating as FileCredentialsImpl due to .env suffix");
            return new FileCredentialsImpl(
                    scope, id, description, item.getName(), SecretBytes.fromRawBytes(item.getNotes().getBytes(StandardCharsets.UTF_8)));
        } else {
            LOGGER.fine(() -> "convert: treating as StringCredentialsImpl");
            return new StringCredentialsImpl(scope, id, description, Secret.fromString(item.getNotes()));
        }
    }
}
