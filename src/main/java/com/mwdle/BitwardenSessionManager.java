package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.mwdle.model.BitwardenStatus;
import com.mwdle.provider.BitwardenCredentialsProvider;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

@Extension
public class BitwardenSessionManager {

    private final ReentrantLock lock = new ReentrantLock();
    private String sessionToken;

    public static BitwardenSessionManager get() {
        return Jenkins.get().getExtensionList(BitwardenSessionManager.class).get(0);
    }

    public String getSessionToken() throws IOException, InterruptedException {
        if (isTokenValid()) {
            return sessionToken;
        }

        lock.lock();
        try {
            if (isTokenValid()) {
                return sessionToken;
            }

            BitwardenGlobalConfig config = BitwardenGlobalConfig.get();
            StandardUsernamePasswordCredentials apiKey = Jenkins.get().getExtensionList(CredentialsProvider.class).stream().filter(p -> !(p instanceof BitwardenCredentialsProvider)).flatMap(p -> p.getCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, Jenkins.get(), Jenkins.getAuthentication2(), Collections.emptyList()).stream()).filter(c -> c.getId().equals(config.getApiCredentialId())).findFirst().orElse(null);
            StringCredentials masterPassword = Jenkins.get().getExtensionList(CredentialsProvider.class).stream().filter(p -> !(p instanceof BitwardenCredentialsProvider)).flatMap(p -> p.getCredentialsInItemGroup(StringCredentials.class, Jenkins.get(), Jenkins.getAuthentication2(), Collections.emptyList()).stream()).filter(c -> c.getId().equals(config.getMasterPasswordCredentialId())).findFirst().orElse(null);

            if (apiKey == null || masterPassword == null) {
                throw new IOException("Could not find API Key or Master Password credentials configured for the Bitwarden plugin.");
            }

            this.sessionToken = getNewSessionToken(apiKey, masterPassword, config.getServerUrl());
            return this.sessionToken;
        } finally {
            lock.unlock();
        }
    }

    private boolean isTokenValid() {
        if (this.sessionToken == null) {
            return false;
        }
        try {
            BitwardenStatus response = BitwardenCLI.status(this.sessionToken);
            return "unlocked".equals(response.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    private String getNewSessionToken(StandardUsernamePasswordCredentials apiKey, StringCredentials masterPassword, String serverUrl) throws IOException, InterruptedException {
        BitwardenCLI.logout();
        if (serverUrl != null && !serverUrl.isEmpty()) {
            BitwardenCLI.configServer(serverUrl);
        }
        BitwardenCLI.login(apiKey);
        String token = BitwardenCLI.unlock(masterPassword);
        BitwardenCLI.sync();
        return token;
    }
}