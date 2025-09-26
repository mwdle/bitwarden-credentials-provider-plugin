package com.mwdle.bitwarden;

import hudson.Extension;
import hudson.init.Terminator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;

/**
 * Manages the Bitwarden CLI executable lifecycle.
 *
 * <p>Handles OS detection, downloading, extraction, permission setting,
 * and cleanup of the executable on Jenkins shutdown.</p>
 */
@Extension
public final class BitwardenExecutableManager {

    private static final Logger LOGGER = Logger.getLogger(BitwardenExecutableManager.class.getName());
    private final String executablePath;

    /**
     * Constructs the singleton BitwardenExecutableManager.
     * Detects the OS, determines the target path, and downloads the executable if it doesn't exist.
     */
    public BitwardenExecutableManager() {
        LOGGER.fine("Starting executable initialization.");
        String downloadUrl;
        String executableName;

        OS os = OS.detect();
        if (os == OS.WINDOWS) {
            downloadUrl = "https://bitwarden.com/download/?app=cli&platform=windows";
            executableName = "bw.exe";
        } else if (os == OS.MAC) {
            downloadUrl = "https://bitwarden.com/download/?app=cli&platform=macos";
            executableName = "bw";
        } else {
            downloadUrl = "https://bitwarden.com/download/?app=cli&platform=linux";
            executableName = "bw";
        }

        File pluginBinDir = getPluginBinDirectory();
        File executableFile = new File(pluginBinDir, executableName);

        if (!executableFile.exists()) {
            LOGGER.info("Bitwarden CLI not found. Downloading...");
            try {
                downloadAndExtract(new URI(downloadUrl).toURL(), executableFile);
            } catch (IOException | URISyntaxException e) {
                LOGGER.log(Level.SEVERE, "Failed to initialize Bitwarden executable", e);
                throw new RuntimeException("Failed to initialize Bitwarden executable", e);
            }
        }

        this.executablePath = executableFile.getAbsolutePath();
    }

    /**
     * Provides global access to the single instance of this manager, as managed by Jenkins.
     *
     * @return The singleton instance of {@link BitwardenSessionManager}.
     */
    public static BitwardenExecutableManager getInstance() {
        return Jenkins.get().getExtensionList(BitwardenExecutableManager.class).get(0);
    }

    /**
     * Downloads the zip archive, extracts the executable, and sets the necessary execute permissions.
     * @param downloadUrl the URL of the zip archive to download
     * @param targetFile the destination file for the extracted executable
     * @throws IOException if the download or extraction fails
     */
    private void downloadAndExtract(URL downloadUrl, File targetFile) throws IOException {
        LOGGER.fine(() -> "Downloading Bitwarden CLI from URL: " + downloadUrl);
        File bwCliZip = File.createTempFile("bw-cli", ".zip");
        try {
            try (InputStream in = downloadUrl.openStream()) {
                Files.copy(in, bwCliZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.fine("Downloaded zip to: " + bwCliZip.getAbsolutePath());
            }

            try (ZipFile zipFile = new ZipFile(bwCliZip)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                boolean foundExecutable = false;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().equalsIgnoreCase("bw")
                            || entry.getName().equalsIgnoreCase("bw.exe")) {
                        try (InputStream zipInputStream = zipFile.getInputStream(entry)) {
                            Files.copy(zipInputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.fine("Extracted executable: " + targetFile.getAbsolutePath());
                            foundExecutable = true;
                            break;
                        }
                    }
                }
                if (!foundExecutable) {
                    throw new IOException("Could not find 'bw' or 'bw.exe' executable in the downloaded zip file.");
                }
            }
        } finally {
            Files.deleteIfExists(bwCliZip.toPath());
        }

        if (targetFile.setExecutable(true, true)) {
            LOGGER.info("Downloaded Bitwarden CLI executable: " + targetFile.getAbsolutePath());
        } else {
            LOGGER.warning("Could not set executable permission on Bitwarden CLI.");
        }
    }

    /**
     * Gets the absolute path to the managed Bitwarden CLI executable.
     *
     * @return The full path to the 'bw' executable.
     */
    public String getExecutablePath() {
        if (executablePath == null) {
            throw new IllegalStateException(
                    "Bitwarden executable could not be initialized. Please check the Jenkins logs for errors.");
        }
        return executablePath;
    }

    /**
     * Deletes the Bitwarden CLI executable on Jenkins shutdown.
     * The latest version will be automatically redownloaded at next start.
     *
     * <p>Invoked at shutdown via {@link Terminator}.
     * Skips cleanup if the executable was never initialized.</p>
     */
    @Terminator
    public void cleanup() {
        try {
            File executable = new File(this.getExecutablePath());
            if (executable.exists() && executable.delete()) {
                LOGGER.info("Deleted Bitwarden CLI executable on shutdown.");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to delete Bitwarden CLI executable on shutdown: " + e.getMessage());
        }
    }

    /**
     * Gets a dedicated 'bin' directory within this plugin's home folder.
     *
     * @return A file handle to the 'bin' directory.
     */
    private File getPluginBinDirectory() {
        LOGGER.fine("Getting plugin bin directory.");
        File pluginsDir = new File(Jenkins.get().getRootDir(), "plugins");
        File pluginDir = new File(pluginsDir, "bitwarden-credentials-provider-plugin");
        File binDir = new File(pluginDir, "bin");
        if (!binDir.exists()) {
            if (!binDir.mkdirs()) {
                String errorMessage = "Could not create plugin bin directory: " + binDir.getAbsolutePath()
                        + "\nDoes Jenkins have proper file permissions?";
                LOGGER.severe(errorMessage);
                throw new RuntimeException(errorMessage);
            } else {
                LOGGER.fine("Created plugin bin directory: " + binDir.getAbsolutePath());
            }
        } else {
            LOGGER.fine("Plugin bin directory already exists: " + binDir.getAbsolutePath());
        }
        return binDir;
    }

    /**
     * Represents supported operating systems for the Bitwarden CLI.
     */
    private enum OS {
        WINDOWS,
        MAC,
        LINUX;

        /**
         * Detects the current operating system.
         *
         * @return the detected OS enum
         * @throws UnsupportedOperationException if OS is not supported
         */
        static OS detect() {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) return WINDOWS;
            if (osName.contains("mac")) return MAC;
            if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return LINUX;
            LOGGER.fine(() -> "Detected OS: " + osName);
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }
    }
}
