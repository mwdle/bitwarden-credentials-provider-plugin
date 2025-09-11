package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * This class represents our "pointer" to a secret stored in Bitwarden.
 * It extends BaseStandardCredentials to get the standard ID, scope, and
 * description fields for free.
 */
public class BitwardenAppCredential extends BaseStandardCredentials {

    // This will store the name of the secret in Bitwarden, e.g., "Docker Hub Credentials"
    private final String itemName;

    /**
     * The @DataBoundConstructor tells Jenkins how to create an instance of this
     * class when a user saves the form in the UI. The parameter names
     * MUST match the 'field' attributes in the config.jelly file.
     */
    @DataBoundConstructor
    public BitwardenAppCredential(CredentialsScope scope, String id, String description, String itemName) {
        super(scope, id, description);
        this.itemName = itemName;
    }

    // A simple getter for our custom field.
    public String getItemName() {
        return itemName;
    }

    /**
     * The Descriptor is an inner class that tells Jenkins about this credential type.
     * The @Extension annotation is crucial; it's how Jenkins discovers this
     * component when the plugin starts.
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        /**
         * This is the name that will appear in the "Kind" dropdown in the UI.
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Bitwarden-Backed Credential";
        }
    }
}