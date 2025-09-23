package com.mwdle.converters;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.mwdle.BitwardenAppCredential;
import com.mwdle.model.BitwardenItem;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

public abstract class BitwardenItemConverter implements ExtensionPoint {

    public static BitwardenItemConverter findConverter(BitwardenItem item) {
        return Jenkins.get().getExtensionList(BitwardenItemConverter.class)
                .stream()
                .filter(converter -> converter.canConvert(item))
                .findFirst()
                .orElse(null);
    }

    public abstract boolean canConvert(BitwardenItem item);

    public abstract StandardCredentials convert(BitwardenAppCredential pointer, BitwardenItem item);
}