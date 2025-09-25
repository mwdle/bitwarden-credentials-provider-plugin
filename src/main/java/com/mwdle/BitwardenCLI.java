package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenStatus;
import hudson.util.Secret;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * A utility class for executing Bitwarden CLI commands.
 * <p>
 * This class contains only static methods and holds no state. It is responsible for
 * the low-level logic of constructing and running {@link ProcessBuilder} commands,
 * acting as a thin wrapper around the {@code bw} executable.
 */
public final class BitwardenCLI {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Logs into the Bitwarden CLI using the API key.
     *
     * @param apiKey The Jenkins credential containing the Bitwarden Client ID and Client Secret.
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void login(StandardUsernamePasswordCredentials apiKey) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "login", "--apikey", "--quiet");
        Map<String, String> env = pb.environment();
        env.put("BW_CLIENTID", apiKey.getUsername());
        env.put("BW_CLIENTSECRET", apiKey.getPassword().getPlainText());
        executeCommand(pb);
    }

    /**
     * Logs out of the Bitwarden CLI. This is a best-effort operation.
     * Failures are ignored, as a failure typically means the session was already invalid.
     *
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void logout() throws InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "logout");
        try {
            executeCommand(pb);
        } catch (IOException ignored) {
            // If logging out fails, we are likely already logged out. Safe to ignore.
        }
    }

    /**
     * Unlocks the vault using the Master Password and returns the session token.
     *
     * @param masterPassword The Jenkins credential containing the Bitwarden Master Password.
     * @return The session token for subsequent commands.
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static Secret unlock(StringCredentials masterPassword) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "unlock", "--raw", "--passwordenv", "BITWARDEN_MASTER_PASSWORD");
        Map<String, String> env = pb.environment();
        env.put("BITWARDEN_MASTER_PASSWORD", masterPassword.getSecret().getPlainText());
        return Secret.fromString(executeCommand(pb));
    }

    /**
     * Syncs the local vault cache with the server to ensure data is up-to-date.
     *
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void sync(Secret sessionToken) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "sync", "--quiet");
        pb.environment().put("BW_SESSION", Secret.toString(sessionToken));
        executeCommand(pb);
    }

    /**
     * Checks the status of the Bitwarden CLI session.
     *
     * @param sessionToken The session token to validate.
     * @return A {@link BitwardenStatus} object representing the current state.
     * @throws IOException          If the CLI command fails or JSON parsing fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static BitwardenStatus status(Secret sessionToken) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "status");
        pb.environment().put("BW_SESSION", Secret.toString(sessionToken));
        String json = executeCommand(pb);
        return OBJECT_MAPPER.readValue(json, BitwardenStatus.class);
    }

    /**
     * Fetches a list of all items from the vault.
     *
     * @param sessionToken The active session token to use for authentication.
     * @return A List of {@link BitwardenItem} objects.
     * @throws IOException          If the CLI command fails or JSON parsing fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static List<BitwardenItem> listItems(Secret sessionToken) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "list", "items");
        pb.environment().put("BW_SESSION", Secret.toString(sessionToken));
        String json = executeCommand(pb);
        return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    }

    /**
     * Configures the Bitwarden CLI to point to a specific server URL.
     *
     * @param serverUrl The URL of the self-hosted Bitwarden or Vaultwarden instance.
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void configServer(String serverUrl) throws IOException, InterruptedException {
        executeCommand(new ProcessBuilder("bw", "config", "server", serverUrl));
    }

    /**
     * The low-level command executor. All other methods in this class delegate to this.
     *
     * @param pb The configured ProcessBuilder for the command to run.
     * @return The standard output of the command as a trimmed String.
     * @throws IOException          If the command returns a non-zero exit code.
     * @throws InterruptedException If the command is interrupted.
     */
    private static String executeCommand(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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
}
