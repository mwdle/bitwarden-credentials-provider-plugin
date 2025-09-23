package com.mwdle.converters;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.mwdle.BitwardenAppCredential;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenSshKey;
import hudson.Extension;

@Extension
public class SshKeyConverter extends BitwardenItemConverter {
    @Override
    public boolean canConvert(BitwardenItem item) {
        BitwardenSshKey sshKeyData = item.getSshKey();
        return sshKeyData != null && sshKeyData.getPrivateKey() != null && sshKeyData.getPublicKey() != null;
    }

    @Override
    public BasicSSHUserPrivateKey convert(BitwardenAppCredential pointer, BitwardenItem item) {
        BitwardenSshKey sshKeyData = item.getSshKey();
        String username = getUsername(sshKeyData);
        BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(sshKeyData.getPrivateKey());

        return new BasicSSHUserPrivateKey(pointer.getScope(), pointer.getId(), username, privateKeySource, "", pointer.getDescription());
    }

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
}