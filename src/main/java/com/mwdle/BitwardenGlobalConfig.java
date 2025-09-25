package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.util.ListBoxModel;
import java.util.Collections;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Manages the system-wide configuration for the Bitwarden Credentials Provider plugin.
 * <p>
 * This class is a singleton discovered by Jenkins via its {@link Extension} annotation.
 * It makes the plugin's settings available on the "Configure System" page (/manage/configure).
 */
@Extension
public class BitwardenGlobalConfig extends GlobalConfiguration {

    /** The URL of the self-hosted Bitwarden/Vaultwarden server. */
    private String serverUrl;
    /** The Jenkins credential ID for the Bitwarden API Key (Client ID & Secret). */
    private String apiCredentialId;
    /** The Jenkins credential ID for the Bitwarden Master Password. */
    private String masterPasswordCredentialId;

    /**
     * Called by Jenkins at startup to create an instance of this class.
     * The {@link #load()} method populates the fields from the saved XML configuration on disk.
     */
    public BitwardenGlobalConfig() {
        load();
    }

    /**
     * Provides global access to the single instance of this configuration.
     *
     * @return The singleton instance of {@link BitwardenGlobalConfig}.
     */
    public static BitwardenGlobalConfig get() {
        return GlobalConfiguration.all().get(BitwardenGlobalConfig.class);
    }

    // --- GETTERS ---
    public String getServerUrl() {
        return serverUrl;
    }

    public String getApiCredentialId() {
        return apiCredentialId;
    }

    public String getMasterPasswordCredentialId() {
        return masterPasswordCredentialId;
    }

    // --- SETTERS ---
    @DataBoundSetter
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        save();
    }

    @DataBoundSetter
    public void setApiCredentialId(String apiCredentialId) {
        this.apiCredentialId = apiCredentialId;
        save();
    }

    @DataBoundSetter
    public void setMasterPasswordCredentialId(String masterPasswordCredentialId) {
        this.masterPasswordCredentialId = masterPasswordCredentialId;
        save();
    }

    /**
     * Populates the dropdown list for the 'Bitwarden API Key Credential' field in the UI.
     * <p>
     * This method is called automatically by Jenkins's UI framework (Stapler)
     * because its name follows the convention {@code doFill<FieldName>Items}.
     *
     * @param context The current Jenkins context, injected by Stapler.
     * @param apiCredentialId The currently saved value of the field, for ensuring it's in the list.
     * @return A {@link ListBoxModel} containing the credential options.
     */
    @POST
    public ListBoxModel doFillApiCredentialIdItems(
            @AncestorInPath Jenkins context, @QueryParameter String apiCredentialId) {
        context.checkPermission(CredentialsProvider.VIEW);
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        SecurityContextHolder.getContext().getAuthentication(),
                        context,
                        StandardUsernamePasswordCredentials.class,
                        Collections.emptyList(),
                        // Filter to show only credentials stored in the global and system scope.
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.withScope(CredentialsScope.SYSTEM),
                                CredentialsMatchers.withScope(CredentialsScope.GLOBAL)))
                .includeCurrentValue(apiCredentialId);
    }

    /**
     * Populates the dropdown list for the 'Bitwarden Master Password Credential' field in the UI.
     *
     * @param context The current Jenkins context, injected by Stapler.
     * @param masterPasswordCredentialId The currently saved value of the field.
     * @return A {@link ListBoxModel} containing the credential options.
     */
    @POST
    public ListBoxModel doFillMasterPasswordCredentialIdItems(
            @AncestorInPath Jenkins context, @QueryParameter String masterPasswordCredentialId) {
        context.checkPermission(CredentialsProvider.VIEW);
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        SecurityContextHolder.getContext().getAuthentication(),
                        context,
                        StringCredentials.class,
                        Collections.emptyList(),
                        // Filter to show only credentials stored in the global and system scope.
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.withScope(CredentialsScope.SYSTEM),
                                CredentialsMatchers.withScope(CredentialsScope.GLOBAL)))
                .includeCurrentValue(masterPasswordCredentialId);
    }
}
