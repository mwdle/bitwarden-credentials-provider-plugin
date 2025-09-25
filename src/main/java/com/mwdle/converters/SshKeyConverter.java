package com.mwdle.converters;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenSshKey;
import hudson.Extension;
import java.util.logging.Logger;

/**
 * Converts a Bitwarden 'SSH Key' item into a Jenkins {@link BasicSSHUserPrivateKey} credential.
 * <p>
 * Extracts the private key and optionally derives the username from the public key comment.
 */
@Extension
public class SshKeyConverter extends BitwardenItemConverter {

    private static final Logger LOGGER = Logger.getLogger(SshKeyConverter.class.getName());

    /**
     * Derives the username from the public key comment if available.
     * <p>
     * If the public key comment contains a string in the form "user@host", the username will be the part before the '@'.
     *
     * @param sshKeyData the SSH key data from the Bitwarden item.
     * @return the derived username, or an empty string if not derivable.
     */
    private static String getUsername(BitwardenSshKey sshKeyData) {
        String username = "";
        String publicKey = sshKeyData.getPublicKey();
        if (publicKey != null) {
            String[] parts = publicKey.trim().split("\\s+");
            if (parts.length > 2) {
                // Get the last part, which is the comment (e.g., "user@host")
                String comment = parts[parts.length - 1];
                username = comment.split("@")[0];
            }
        }
        return username;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns true if the Bitwarden item contains a non-null {@code privateKey} field.
     */
    @Override
    public boolean canConvert(BitwardenItem item) {
        BitwardenSshKey sshKeyData = item.getSshKey();
        boolean canConvert = sshKeyData != null && sshKeyData.getPrivateKey() != null;
        LOGGER.fine(() ->
                "canConvert: item id=" + item.getId() + " name='" + item.getName() + "' canConvert=" + canConvert);
        return canConvert;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns a {@link BasicSSHUserPrivateKey}, deriving the username from the public key comment if available.
     */
    @Override
    public BasicSSHUserPrivateKey convert(CredentialsScope scope, String id, String description, BitwardenItem item) {
        LOGGER.fine(() -> "convert: id=" + id + " item id=" + item.getId() + " name='" + item.getName() + "'");
        BitwardenSshKey sshKeyData = item.getSshKey();
        String username = getUsername(sshKeyData);
        LOGGER.fine(() -> "convert: derived username='" + username + "'");
        BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource =
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(sshKeyData.getPrivateKey());

        return new BasicSSHUserPrivateKey(scope, id, username, privateKeySource, "", description);
    }
}
