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
        // Parse the username from the end of the public key string
        String publicKey = sshKeyData.getPublicKey();
        String[] parts = publicKey.trim().split("\\s+");
        String username = parts.length > 2 ? parts[2] : "";

        BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(sshKeyData.getPrivateKey());

        return new BasicSSHUserPrivateKey(pointer.getScope(), pointer.getId(), username, privateKeySource, "", pointer.getDescription());
    }
}