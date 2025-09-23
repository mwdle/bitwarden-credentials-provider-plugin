package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mwdle.model.BitwardenItem;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public class BitwardenClient implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final StandardUsernamePasswordCredentials apiCredentials;
    private final StringCredentials masterPassword;
    private final Path tempAppDataDir;
    private final String sessionToken;

    public BitwardenClient(StandardUsernamePasswordCredentials apiCredentials, StringCredentials masterPassword, String serverUrl) throws IOException, InterruptedException {
        this.apiCredentials = apiCredentials;
        this.masterPassword = masterPassword;
        // Create a unique temporary directory for this client instance.
        // This isolates the session from all other concurrent operations.
        this.tempAppDataDir = Files.createTempDirectory("bitwarden-cli-");
        // Set server config if a URL is provided
        if (serverUrl != null && !serverUrl.isEmpty()) {
            executeCommand(new ProcessBuilder("bw", "config", "server", serverUrl));
        }
        login();
        sessionToken = unlock();
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
        Map<String, String> env = pb.environment();
        env.put("BITWARDENCLI_APPDATA_DIR", this.tempAppDataDir.toAbsolutePath().toString());

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
     * @return The raw JSON of the item as a String.
     */
    public BitwardenItem getSecret(String nameOrId) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "get", "item", nameOrId, "--session", sessionToken);
        String itemJson = executeCommand(pb);
        return OBJECT_MAPPER.readValue(itemJson, BitwardenItem.class);
    }

    /**
     * Cleans up the session and all temporary resources used by the client.
     * This method first attempts to log out of the Bitwarden CLI to invalidate the
     * server-side session, then recursively deletes the temporary data directory
     * created for this client instance. This is called automatically when the
     * client is used in a try-with-resources block.
     *
     * @throws IOException if there is an error walking the file tree during cleanup.
     */
    @Override
    public void close() throws IOException {
        try {
            executeCommand(new ProcessBuilder("bw", "logout"));
            System.out.println("Logout command executed.");
        } catch (Exception e) {
            System.err.println("Failed to logout. Error: " + e.getMessage());
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(tempAppDataDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            System.err.println("Failed to delete file during cleanup: " + file.getAbsolutePath());
                        }
                    });
        }
    }
}