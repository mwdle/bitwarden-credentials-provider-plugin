package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mwdle.model.BitwardenItem;
import com.mwdle.model.BitwardenStatus;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A utility class for executing Bitwarden CLI commands.
 * <p>
 * This class contains only static methods and holds no state. It is responsible for
 * the low-level logic of constructing and running {@link ProcessBuilder} commands.
 */
public final class BitwardenCLI {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void login(StandardUsernamePasswordCredentials apiKey) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "login", "--apikey", "--quiet");
        Map<String, String> env = pb.environment();
        env.put("BW_CLIENTID", apiKey.getUsername());
        env.put("BW_CLIENTSECRET", apiKey.getPassword().getPlainText());
        executeCommand(pb);
    }

    public static void logout() throws InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "logout");
        try { executeCommand(pb); } catch (IOException ignored) {} // If logging out fails, we are already logged out
    }

    public static String unlock(StringCredentials masterPassword) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "unlock", "--raw", "--passwordenv", "BITWARDEN_MASTER_PASSWORD");
        Map<String, String> env = pb.environment();
        env.put("BITWARDEN_MASTER_PASSWORD", masterPassword.getSecret().getPlainText());
        return executeCommand(pb);
    }

    public static void sync() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "sync", "--quiet");
        executeCommand(pb);
    }

    public static BitwardenStatus status(String sessionToken) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "status");
        pb.environment().put("BW_SESSION", sessionToken);
        String json = executeCommand(pb);
        return OBJECT_MAPPER.readValue(json, BitwardenStatus.class);
    }

    public static BitwardenItem getSecret(String nameOrId, String sessionToken) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bw", "get", "item", nameOrId);
        pb.environment().put("BW_SESSION", sessionToken);
        String itemJson = executeCommand(pb);
        return OBJECT_MAPPER.readValue(itemJson, BitwardenItem.class);
    }

    public static void configServer(String serverUrl) throws IOException, InterruptedException {
        executeCommand(new ProcessBuilder("bw", "config", "server", serverUrl));
    }

    private static String executeCommand(ProcessBuilder pb) throws IOException, InterruptedException {
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
}