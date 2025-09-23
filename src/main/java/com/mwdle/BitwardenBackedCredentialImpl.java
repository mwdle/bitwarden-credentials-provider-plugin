package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The concrete, user-configurable implementation of a {@link BitwardenBackedCredential}.
 * <p>
 * Instances of this class are what get created and saved in the Jenkins credential store
 * when a user adds a "Bitwarden-Backed Credential" from the UI.
 */
public class BitwardenBackedCredentialImpl extends BaseStandardCredentials implements BitwardenBackedCredential {

    /** The method to use for looking up the Bitwarden item ("name" or "id"). */
    private final String lookupMethod;
    /** The ID of the Bitwarden item. Null if lookupMethod is "name". */
    private final String bitwardenItemId;
    /** The name of the Bitwarden item. Null if lookupMethod is "id". */
    private final String bitwardenItemName;

    /**
     * Constructor called by Jenkins's data-binding framework when a user creates or saves this credential.
     *
     * @param scope The scope of the credential (e.g., GLOBAL).
     * @param id The unique ID for this credential within Jenkins.
     * @param description A user-provided description for this credential.
     * @param lookupMethod The selected lookup method information, including lookup type, and lookup criteria, such as item name or item id.
     */
    @DataBoundConstructor
    public BitwardenBackedCredentialImpl(@Nullable CredentialsScope scope, @Nullable String id, @Nullable String description, LookupMethodData lookupMethod) {
        super(scope, id, description);
        this.lookupMethod = lookupMethod.value;
        this.bitwardenItemId = this.lookupMethod.equals("id") ? lookupMethod.bitwardenItemId : null;
        this.bitwardenItemName = this.lookupMethod.equals("name") ? lookupMethod.bitwardenItemName : null;
    }

    /**
     * A static helper class that exactly matches the nested JSON structure
     * sent by the <f:radioBlock name="lookupMethod"> tag in the Jelly UI.
     * This allows Jenkins's data-binding framework to correctly deserialize the form data.
     */
    public static final class LookupMethodData {
        private final String value;
        private final String bitwardenItemId;
        private final String bitwardenItemName;

        @DataBoundConstructor
        public LookupMethodData(String value, String bitwardenItemId, String bitwardenItemName) {
            this.value = value;
            this.bitwardenItemId = bitwardenItemId;
            this.bitwardenItemName = bitwardenItemName;
        }
    }

    @Override
    public String getBitwardenItemId() { return this.bitwardenItemId; }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBitwardenItemName() { return this.bitwardenItemName; }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLookupValue() {
        return "name".equals(lookupMethod) ? bitwardenItemName : bitwardenItemId;
    }

    /**
     * @return The configured lookup method ("name" or "id").
     */
    public String getLookupMethod() { return this.lookupMethod; }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns null because the actual secret is resolved lazily at runtime
     * by the {@link com.mwdle.provider.BitwardenItemProxy}. This object is only a configuration pointer.
     */
    @Override
    public Secret getSecret() { return null; }

    /**
     * The standard Jenkins descriptor for this credential type. It provides metadata
     * like the display name used in the UI's "Kind" dropdown.
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() { return "Bitwarden-Backed Credential"; }
    }
}