package com.mwdle.converters;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenLogin;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.Secret;
import java.util.logging.Logger;

/**
 * Converts a {@link BitwardenLogin} item into a Jenkins {@link StandardUsernamePasswordCredentials}.
 */
@Extension
public class LoginConverter extends BitwardenItemConverter {

    private static final Logger LOGGER = Logger.getLogger(LoginConverter.class.getName());

    /**
     * {@inheritDoc}
     * <p>
     * Returns true if the Bitwarden item contains a non-null {@code login} object.
     */
    @Override
    public boolean canConvert(BitwardenItem item) {
        boolean canConvert = item.getLogin() != null
                && (item.getLogin().getUsername() != null || item.getLogin().getPassword() != null);
        LOGGER.fine(() ->
                "canConvert: item id=" + item.getId() + " name='" + item.getName() + "' canConvert=" + canConvert);
        return canConvert;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Constructs a {@link UsernamePasswordCredentialsImpl} using the username and password
     * from the Bitwarden item. Safely handles nulls by returning empty strings.
     * This means that either the username or password field must always be present if the fetch succeeds.
     */
    @Override
    public StandardUsernamePasswordCredentials convert(
            CredentialsScope scope, String id, String description, BitwardenItem item) {
        LOGGER.fine(() -> "convert: id=" + id + " item id=" + item.getId() + " name='" + item.getName() + "'");
        BitwardenLogin loginData = item.getLogin();
        try {
            Secret username = (loginData.getUsername() != null) ? loginData.getUsername() : Secret.fromString("");
            Secret password = (loginData.getPassword() != null) ? loginData.getPassword() : Secret.fromString("");
            return new UsernamePasswordCredentialsImpl(
                    scope, id, description, username.getPlainText(), password.getPlainText());
        } catch (Descriptor.FormException e) {
            // Should not happen when creating programmatically, but returning null is safe.
            LOGGER.warning(() -> "LoginConverter.convert: failed to create credentials for id=" + id + " name='"
                    + item.getName() + "': " + e.getMessage());
            return null;
        }
    }
}
