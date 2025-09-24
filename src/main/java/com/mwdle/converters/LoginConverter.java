package com.mwdle.converters;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenLogin;
import hudson.Extension;
import hudson.model.Descriptor;

/**
 * Converts a {@link BitwardenLogin} item into a Jenkins {@link StandardUsernamePasswordCredentials}.
 */
@Extension
public class LoginConverter extends BitwardenItemConverter {
    /**
     * {@inheritDoc}
     * <p>
     * Returns true if the Bitwarden item contains a non-null {@code login} object.
     */
    @Override
    public boolean canConvert(BitwardenItem item) {
        return item.getLogin() != null && (item.getLogin().getUsername() != null || item.getLogin().getPassword() != null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Constructs a {@link UsernamePasswordCredentialsImpl} using the username and password
     * from the Bitwarden item. Safely handles nulls by returning empty strings.
     * This means that either the username or password field must always be present if the fetch succeeds.
     */
    @Override
    public StandardUsernamePasswordCredentials convert(CredentialsScope scope, String id, String description, BitwardenItem item) {
        BitwardenLogin loginData = item.getLogin();
        try {
            String username = (loginData.getUsername() != null) ? loginData.getUsername() : "";
            String password = (loginData.getPassword() != null) ? loginData.getPassword() : "";
            return new UsernamePasswordCredentialsImpl(scope, id, description, username, password);
        } catch (Descriptor.FormException e) {
            // Should not happen when creating programmatically, but returning null is safe.
            return null;
        }
    }
}