package com.mwdle.bitwarden;

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
 * Manages the Bitwarden CLI executable, ensuring it is downloaded and available.
 * This class handles OS detection, downloads the appropriate binary from its zip archive, and makes it executable.
 */
public final class BitwardenExecutableManager {

    private static final Logger LOGGER = Logger.getLogger(BitwardenExecutableManager.class.getName());
    public static final BitwardenExecutableManager INSTANCE = new BitwardenExecutableManager();
    private final String executablePath;

    /**
     * Constructs the singleton BitwardenExecutableManager.
     * Detects the OS, determines the target path, and downloads the executable if it doesn't exist.
     */
    private BitwardenExecutableManager() {
        LOGGER.fine("Starting executable initialization.");
        String os = System.getProperty("os.name").toLowerCase();
        LOGGER.fine(() -> "Detected OS: " + os);
        String downloadUrl;
        String executableName;

        if (os.contains("win")) {
            downloadUrl = "https://bitwarden.com/download/?app=cli&platform=windows";
            executableName = "bw.exe";
        } else if (os.contains("mac")) {
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
            if (!bwCliZip.delete()) {
                LOGGER.warning("Could not delete temporary zip file: " + bwCliZip.getAbsolutePath());
            }
        }

        if (targetFile.setExecutable(true, true)) {
            LOGGER.info("Downloaded Bitwarden CLI executable: " + targetFile.getAbsolutePath());
        } else {
            LOGGER.warning("Could not set executable permission on Bitwarden CLI.");
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
                LOGGER.warning("Could not create plugin bin directory: " + binDir.getAbsolutePath());
            } else {
                LOGGER.fine("Created plugin bin directory: " + binDir.getAbsolutePath());
            }
        } else {
            LOGGER.fine("Plugin bin directory already exists: " + binDir.getAbsolutePath());
        }
        return binDir;
    }
}
