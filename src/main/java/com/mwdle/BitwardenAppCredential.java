package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.util.Secret;
import java.io.IOException;

public interface BitwardenAppCredential extends StandardCredentials {

    String getBitwardenItemId();

    String getBitwardenItemName();

    String getLookupValue();

    Secret getSecret() throws IOException, InterruptedException;
}