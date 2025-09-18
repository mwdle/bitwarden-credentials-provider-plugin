package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BitwardenAppCredentialImpl extends BaseStandardCredentials implements BitwardenAppCredential {

    private final String lookupMethod;
    private final String bitwardenItemId;
    private final String bitwardenItemName;

    @DataBoundConstructor
    public BitwardenAppCredentialImpl(@Nullable CredentialsScope scope, @Nullable String id, @Nullable String description,
                                      @Nullable String lookupMethod, @Nullable String bitwardenItemId, @Nullable String bitwardenItemName) {
        super(scope, id, description);
        this.lookupMethod = lookupMethod;
        this.bitwardenItemId = (lookupMethod != null && lookupMethod.equals("id")) ? bitwardenItemId : null;
        this.bitwardenItemName = (lookupMethod != null && lookupMethod.equals("name")) ? bitwardenItemName : null;
    }

    @Override
    public String getBitwardenItemId() { return this.bitwardenItemId; }

    @Override
    public String getBitwardenItemName() { return this.bitwardenItemName; }

    @Override
    public String getLookupValue() {
        return "name".equals(lookupMethod) ? bitwardenItemName : bitwardenItemId;
    }

    public String getLookupMethod() { return this.lookupMethod; }

    @Override
    public Secret getSecret() { return null; }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() { return "Bitwarden-Backed Credential"; }

        public ListBoxModel doFillLookupMethodItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("By Name", "name");
            items.add("By ID", "id");
            return items;
        }
    }
}