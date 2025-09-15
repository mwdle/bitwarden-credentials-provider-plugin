package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

@Extension
public class BitwardenGlobalConfig extends GlobalConfiguration {

    private String serverUrl;
    private String apiKeyCredentialId;
    private String masterPasswordCredentialId;

    public BitwardenGlobalConfig() {
        load();
    }

    public static BitwardenGlobalConfig get() {
        return GlobalConfiguration.all().get(BitwardenGlobalConfig.class);
    }

    // --- GETTERS ---
    public String getServerUrl() { return serverUrl; }
    // These two methods were missing
    public String getApiKeyCredentialId() { return apiKeyCredentialId; }
    public String getMasterPasswordCredentialId() { return masterPasswordCredentialId; }

    // --- SETTERS ---
    @DataBoundSetter
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        save();
    }
    @DataBoundSetter
    public void setApiKeyCredentialId(String apiKeyCredentialId) {
        this.apiKeyCredentialId = apiKeyCredentialId;
        save();
    }
    @DataBoundSetter
    public void setMasterPasswordCredentialId(String masterPasswordCredentialId) {
        this.masterPasswordCredentialId = masterPasswordCredentialId;
        save();
    }

    // --- UI HELPERS ---
    @POST
    public ListBoxModel doFillApiKeyCredentialIdItems(@AncestorInPath Item item) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
                // Use the modern includeMatching method that implicitly uses the current context
                .includeMatching(
                        item,
                        UsernamePasswordCredentialsImpl.class,
                        // Provide an explicit empty list of DomainRequirements
                        Collections.emptyList(),
                        // Match all credentials of this type
                        CredentialsMatchers.always()
                );
    }

    @POST
    public ListBoxModel doFillMasterPasswordCredentialIdItems(@AncestorInPath Item item) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
                // Use the modern includeMatching method that implicitly uses the current context
                .includeMatching(
                        item,
                        StringCredentialsImpl.class,
                        // Provide an explicit empty list of DomainRequirements
                        Collections.emptyList(),
                        // Match all credentials of this type
                        CredentialsMatchers.always()
                );
    }
}