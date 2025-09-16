package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BitwardenClient {

    private final StandardUsernamePasswordCredentials apiCredentials;
    private final StringCredentials masterPassword;
    private final String serverUrl;

    public BitwardenClient(StandardUsernamePasswordCredentials apiCredentials, StringCredentials masterPassword, String serverUrl) {
        this.apiCredentials = apiCredentials;
        this.masterPassword = masterPassword;
        this.serverUrl = serverUrl;
    }

    public String getSessionToken() throws IOException, InterruptedException {
        logout();
        // Set server config if a URL is provided
        if (this.serverUrl != null && !this.serverUrl.isEmpty()) {
            executeCommand(new ProcessBuilder("bw", "config", "server", this.serverUrl));
        }
        login();
        return unlock();
    }

    private void login() throws IOException, InterruptedException {
        // We use ProcessBuilder to securely pass credentials via environment variables
        ProcessBuilder pb = new ProcessBuilder("bw", "login", "--apikey");
        Map<String, String> env = pb.environment();
        env.put("BW_CLIENTID", this.apiCredentials.getUsername());
        env.put("BW_CLIENTSECRET", this.apiCredentials.getPassword().getPlainText());

        // The login command doesn't produce useful stdout, but we check for errors
        String result = executeCommand(pb);
        // A real implementation would have more robust error checking
        System.out.println("Login command executed. Result: " + result);
    }

    private String unlock() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "unlock", "--raw", "--passwordenv", "BITWARDEN_MASTER_PASSWORD");
        Map<String, String> env = pb.environment();
        env.put("BITWARDEN_MASTER_PASSWORD", this.masterPassword.getSecret().getPlainText());

        return executeCommand(pb);
    }

    /**
     * A helper method to execute a shell command and return its standard output.
     * @param pb The configured ProcessBuilder.
     * @return The standard output of the command as a String.
     * @throws IOException If the command returns a non-zero exit code.
     */
    private String executeCommand(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true); // Combine stdout and stderr
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ". Output: " + output);
        }

        return output.toString().trim();
    }

    /**
     * Fetches a secret item from the vault by its name or ID.
     * @param nameOrId The name or ID of the item.
     * @param sessionToken The active session token.
     * @return The raw JSON of the item as a String.
     */
    public String getSecret(String nameOrId, String sessionToken) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "get", "item", nameOrId, "--session", sessionToken);
        return executeCommand(pb);
    }

    /**
     * Logs out of the Bitwarden CLI.
     */
    public void logout() {
        try {
            ProcessBuilder pb = new ProcessBuilder("bw", "logout");
            executeCommand(pb);
            System.out.println("Logout command executed.");
        } catch (Exception e) {
            // Don't throw an exception on logout failure, just log it.
            System.err.println("Failed to logout, but continuing anyway. " + e.getMessage());
        }
    }

}