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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client for interacting with the Bitwarden CLI.
 * <p>
 * This client is designed to be short-lived and used within a try-with-resources block,
 * as it implements {@link AutoCloseable} to guarantee cleanup of temporary files and sessions.
 * <p>
 * It ensures thread safety for concurrent Jenkins builds by creating a unique, temporary
 * data directory for each instance, effectively isolating every CLI session.
 * <p>
 * This approach is preferable to using the <code>bw serve</code> command,
 * which does not support concurrent operations without leaving the vault in the unlocked state at all times.
 */
public class BitwardenClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(BitwardenClient.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final StandardUsernamePasswordCredentials apiCredentials;
    private final StringCredentials masterPassword;
    private final Path tempAppDataDir;
    private final String sessionToken;

    /**
     * Constructs and initializes a new Bitwarden client session.
     * The constructor performs the full login, unlock, and sync process,
     * preparing it to fetch credentials using the {@link #getSecret(String)} method.
     *
     * @param apiCredentials A Jenkins StandardUsernamePasswordCredentials containing the Bitwarden Client ID and Client Secret.
     * @param masterPassword A Jenkins StringCredentials containing the Bitwarden Master Password.
     * @param serverUrl      The URL for a self-hosted Bitwarden/Vaultwarden instance. Can be null or empty for the default Bitwarden cloud.
     * @throws IOException          If a CLI command fails or temporary directory creation fails.
     * @throws InterruptedException If a CLI command is interrupted.
     */
    public BitwardenClient(StandardUsernamePasswordCredentials apiCredentials, StringCredentials masterPassword, String serverUrl) throws IOException, InterruptedException {
        this.apiCredentials = apiCredentials;
        this.masterPassword = masterPassword;
        this.tempAppDataDir = Files.createTempDirectory("bitwarden-cli-");
        if (serverUrl != null && !serverUrl.isEmpty()) {
            executeCommand(new ProcessBuilder("bw", "config", "server", serverUrl));
        }
        login();
        this.sessionToken = unlock();
        sync();
    }

    /**
     * Logs into the Bitwarden CLI using the configured API key.
     *
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    private void login() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "login", "--apikey", "--quiet");
        Map<String, String> env = pb.environment();
        env.put("BW_CLIENTID", this.apiCredentials.getUsername());
        env.put("BW_CLIENTSECRET", this.apiCredentials.getPassword().getPlainText());
        executeCommand(pb);
    }

    /**
     * Unlocks the vault using the Master Password and returns the session token.
     *
     * @return The session token for subsequent commands.
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    private String unlock() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "unlock", "--raw", "--passwordenv", "BITWARDEN_MASTER_PASSWORD");
        Map<String, String> env = pb.environment();
        env.put("BITWARDEN_MASTER_PASSWORD", this.masterPassword.getSecret().getPlainText());
        return executeCommand(pb);
    }

    /**
     * Syncs the local vault cache with the server to ensure data is up-to-date.
     * This is called once in the constructor.
     *
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    private void sync() throws IOException, InterruptedException {
        executeCommand(new ProcessBuilder("bw", "sync", "--quiet"));
    }

    /**
     * Fetches a secret item from the vault by its name or ID and deserializes it.
     * @param nameOrId The name or ID of the item to fetch.
     * @return A type-safe {@link BitwardenItem} object.
     * @throws IOException If the CLI command fails or JSON parsing fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public BitwardenItem getSecret(String nameOrId) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "get", "item", nameOrId);
        Map<String, String> env = pb.environment();
        env.put("BW_SESSION", this.sessionToken);
        String itemJson = executeCommand(pb);
        return OBJECT_MAPPER.readValue(itemJson, BitwardenItem.class);
    }

    /**
     * Executes a given shell command and returns its standard output.
     * All commands are run within an isolated, temporary data directory.
     *
     * @param pb The configured ProcessBuilder for the command.
     * @return The standard output of the command as a trimmed String.
     * @throws IOException          If the command returns a non-zero exit code.
     * @throws InterruptedException If the command is interrupted.
     */
    private String executeCommand(ProcessBuilder pb) throws IOException, InterruptedException {
        Map<String, String> env = pb.environment();
        env.put("BITWARDENCLI_APPDATA_DIR", this.tempAppDataDir.toAbsolutePath().toString());

        pb.redirectErrorStream(true);
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
     * Cleans up the session and all temporary resources used by the client.
     * This method is called automatically when the client is used in a try-with-resources block.
     * It first attempts to log out of the CLI session, then recursively deletes the temporary data directory.
     *
     * @throws IOException if there is an error walking the file tree during cleanup.
     */
    @Override
    public void close() throws IOException {
        logout();
        try (java.util.stream.Stream<Path> walk = Files.walk(tempAppDataDir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(file -> {
                if (!file.delete()) {
                    LOGGER.warning("Failed to delete file during cleanup: " + file.getAbsolutePath());
                }
            });
        }
    }

    /**
     * Logs out of the Bitwarden CLI. Failures are caught and logged, as they are not
     * critical (the temporary directory will be deleted anyway).
     */
    public void logout() {
        try {
            executeCommand(new ProcessBuilder("bw", "logout", "--quiet"));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to log out of Bitwarden CLI session. This is generally not a critical error, as the temporary session directory will be deleted anyway. Error: {0}",
                    e.getMessage());
        }
    }
}