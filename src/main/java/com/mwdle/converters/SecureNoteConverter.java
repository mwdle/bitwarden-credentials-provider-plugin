package com.mwdle.converters;

import com.mwdle.BitwardenBackedCredential;
import com.mwdle.model.BitwardenItem;
import hudson.Extension;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

@Extension
public class SecureNoteConverter extends BitwardenItemConverter {
    @Override
    public boolean canConvert(BitwardenItem item) {
        return item.getNotes() != null;
    }

    @Override
    public StringCredentials convert(BitwardenBackedCredential pointer, BitwardenItem item) {
        return new StringCredentialsImpl(pointer.getScope(), pointer.getId(), pointer.getDescription(), Secret.fromString(item.getNotes()));
    }
}