package com.mwdle.converters;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.mwdle.BitwardenAppCredential;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenLogin;
import hudson.Extension;
import hudson.model.Descriptor;

@Extension
public class LoginConverter extends BitwardenItemConverter {
    @Override
    public boolean canConvert(BitwardenItem item) {
        return item.getLogin() != null && (item.getLogin().getUsername() != null || item.getLogin().getPassword() != null);
    }

    @Override
    public StandardUsernamePasswordCredentials convert(BitwardenAppCredential pointer, BitwardenItem item) {
        BitwardenLogin loginData = item.getLogin();
        try {
            String username = (loginData.getUsername() != null) ? loginData.getUsername() : "";
            String password = (loginData.getPassword() != null) ? loginData.getPassword() : "";
            return new UsernamePasswordCredentialsImpl(pointer.getScope(), pointer.getId(), pointer.getDescription(), username, password);
        } catch (Descriptor.FormException e) {
            return null;
        }
    }
}