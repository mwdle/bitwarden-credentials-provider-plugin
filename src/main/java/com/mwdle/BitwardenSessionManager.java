package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.mwdle.model.BitwardenStatus;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe singleton that manages and caches a single, global Bitwarden session token.
 * <p>
 * This class ensures that the slow, network-intensive login and unlock operations required for Bitwarden interactions
 * are performed infrequently. It validates the cached session using {@code bw status} before returning it,
 * and refreshes the session only when necessary by orchestrating calls to the stateless {@link BitwardenCLI} utility.
 */
@Extension
public class BitwardenSessionManager {

    /**
     * A lock to ensure that the session token refresh process is thread-safe. This prevents
     * multiple concurrent jobs from attempting to log in at the same time when the session
     * is found to be invalid.
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * The cached Bitwarden session token. This token is stored in memory and reused across
     * builds to prevent API rate-limiting and improve secret fetching performance. It is refreshed by
     * {@link #getNewSessionToken(StandardUsernamePasswordCredentials, StringCredentials, String)} when it becomes invalid.
     */
    private String sessionToken;


    /**
     * Provides global access to the single instance of this manager, as managed by Jenkins.
     *
     * @return The singleton instance of {@link BitwardenSessionManager}.
     */
    public static BitwardenSessionManager get() {
        return Jenkins.get().getExtensionList(BitwardenSessionManager.class).get(0);
    }

    /**
     * Provides thread-safe access to a valid Bitwarden session token.
     * <p>
     * This method first performs a check using {@code bw status} to validate the cached token.
     * It only performs the slow login/unlock sequence if the cached token is missing or has been invalidated.
     *
     * @return A valid session token.
     * @throws IOException          If the login/unlock process fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public String getSessionToken() throws IOException, InterruptedException {
        if (isTokenValid()) {
            return sessionToken;
        }

        lock.lock();
        try {
            // Double-check if another thread renewed the token while we were waiting for the lock.
            if (isTokenValid()) {
                return sessionToken;
            }

            // If we are the thread responsible for refreshing, perform the full login.
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

    /**
     * Performs a check to see if the cached session token is still valid.
     *
     * @return {@code true} if the token is present and the vault status is {@code unlocked}.
     */
    private boolean isTokenValid() {
        if (this.sessionToken == null) {
            return false;
        }
        try {
            BitwardenStatus response = BitwardenCLI.status(this.sessionToken);
            return response.getStatus().equals("unlocked");
        } catch (Exception e) {
            // If the status command fails for any reason the token is considered invalid
            return false;
        }
    }

    /**
     * Performs the full authentication sequence by orchestrating calls to the
     * {@link BitwardenCLI} utility, and returns a new session token.
     */
    private String getNewSessionToken(StandardUsernamePasswordCredentials apiKey, StringCredentials masterPassword, String serverUrl) throws IOException, InterruptedException {
        BitwardenCLI.logout();
        if (serverUrl != null && !serverUrl.isEmpty()) {
            BitwardenCLI.configServer(serverUrl);
        }
        BitwardenCLI.login(apiKey);
        return BitwardenCLI.unlock(masterPassword);
    }
}