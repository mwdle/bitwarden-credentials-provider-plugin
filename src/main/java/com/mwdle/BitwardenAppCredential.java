package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.util.Secret;
import java.io.IOException;

public interface BitwardenAppCredential extends StandardCredentials {

    String getBitwardenItemId();

    String getBitwardenItemName();

    // Helper to get whichever lookup value is configured
    String getLookupValue();

    // The main secret getter, as required by the guide for lazy-loading providers
    Secret getSecret() throws IOException, InterruptedException;
}