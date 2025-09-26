package com.mwdle.bitwarden;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * A utility class for executing Bitwarden CLI commands.
 * <p>
 * This class contains only static methods and holds no state. It is responsible for
 * the low-level logic of constructing and running {@link ProcessBuilder} commands,
 * acting as a thin wrapper around the {@code bw} executable.
 * It uses {@link BitwardenCLIManager} to locate and manage the CLI binary.
 */
public final class BitwardenCLI {

    private static final Logger LOGGER = Logger.getLogger(BitwardenCLI.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates a ProcessBuilder for a Bitwarden CLI command, using the managed executable.
     * This centralizes the logic for finding the 'bw' executable path.
     *
     * @param command The arguments to pass to the 'bw' command (e.g., "login", "--apikey").
     * @return A configured ProcessBuilder instance.
     */
    private static ProcessBuilder bitwardenCommand(String... command) {
        String executablePath = BitwardenCLIManager.getInstance().getExecutablePath();
        List<String> commandParts = new ArrayList<>();
        commandParts.add(executablePath);
        commandParts.addAll(Arrays.asList(command));
        LOGGER.fine(() -> "Building Bitwarden command: " + String.join(" ", commandParts));
        return new ProcessBuilder(commandParts);
    }

    /**
     * Logs into the Bitwarden CLI using the API key.
     *
     * @param apiKey The Jenkins credential containing the Bitwarden Client ID and Client Secret.
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void login(StandardUsernamePasswordCredentials apiKey) throws IOException, InterruptedException {
        LOGGER.info("Logging in with API key credentials.");
        ProcessBuilder pb = bitwardenCommand("login", "--apikey", "--quiet");
        Map<String, String> env = pb.environment();
        env.put("BW_CLIENTID", apiKey.getUsername());
        env.put("BW_CLIENTSECRET", apiKey.getPassword().getPlainText());
        executeCommand(pb);
        LOGGER.info("Login successful.");
    }

    /**
     * Logs out of the Bitwarden CLI. This is a best-effort operation.
     * Failures are ignored, as a failure typically means the session was already invalid.
     *
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void logout() throws InterruptedException {
        LOGGER.info("Logging out...");
        ProcessBuilder pb = bitwardenCommand("logout");
        try {
            executeCommand(pb);
            LOGGER.info("Logout successful.");
        } catch (IOException e) {
            LOGGER.warning("Logout failed (likely already logged out). " + e.getMessage());
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
        LOGGER.info("Unlocking vault.");
        ProcessBuilder pb = bitwardenCommand("unlock", "--raw", "--passwordenv", "BITWARDEN_MASTER_PASSWORD");
        Map<String, String> env = pb.environment();
        env.put("BITWARDEN_MASTER_PASSWORD", masterPassword.getSecret().getPlainText());
        LOGGER.info("Vault unlocked successfully.");
        return Secret.fromString(executeCommand(pb));
    }

    /**
     * Syncs the local vault cache with the server to ensure data is up-to-date.
     *
     * @throws IOException          If the CLI command fails.
     * @throws InterruptedException If the CLI command is interrupted.
     */
    public static void sync(Secret sessionToken) throws IOException, InterruptedException {
        LOGGER.info("Syncing vault.");
        ProcessBuilder pb = bitwardenCommand("sync", "--quiet");
        pb.environment().put("BW_SESSION", Secret.toString(sessionToken));
        executeCommand(pb);
        LOGGER.info("Vault sync complete.");
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
        LOGGER.info("Fetching CLI status.");
        ProcessBuilder pb = bitwardenCommand("status");
        pb.environment().put("BW_SESSION", Secret.toString(sessionToken));
        String json = executeCommand(pb);
        LOGGER.info("CLI status fetched successfully.");
        LOGGER.fine(() -> "Status JSON: " + json);
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
        LOGGER.info("Fetching vault items.");
        ProcessBuilder pb = bitwardenCommand("list", "items");
        pb.environment().put("BW_SESSION", Secret.toString(sessionToken));
        String json = executeCommand(pb);
        LOGGER.info("Vault items fetched successfully.");
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
        LOGGER.info(() -> "Configuring server URL: " + serverUrl);
        executeCommand(bitwardenCommand("config", "server", serverUrl));
        LOGGER.info("Server URL configured successfully.");
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
        LOGGER.fine(() -> "Executing command: " + String.join(" ", pb.command()));
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
            String errorMsg = "Command failed with exit code " + exitCode + ". Output: " + output;
            LOGGER.severe(errorMsg);
            throw new IOException(errorMsg);
        }
        return output.toString().trim();
    }
}
