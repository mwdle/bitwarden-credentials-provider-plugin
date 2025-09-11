package com.mwdle;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

/**
 * Manages the system-wide configuration for our plugin.
 * It extends GlobalConfiguration and is marked with @Extension so Jenkins
 * automatically discovers it and adds it to the "Configure System" page.
 */
@Extension
public class BitwardenGlobalConfig extends GlobalConfiguration {

    // These fields will store the IDs of the selected Jenkins credentials.
    private String apiKeyCredentialId;
    private String masterPasswordCredentialId;

    // When Jenkins starts, it creates an instance of this class and calls load().
    public BitwardenGlobalConfig() {
        load();
    }

    // A helper method to easily get the configuration from anywhere in the plugin.
    public static BitwardenGlobalConfig get() {
        return GlobalConfiguration.all().get(BitwardenGlobalConfig.class);
    }

    // Getters for our fields
    public String getApiKeyCredentialId() { return apiKeyCredentialId; }
    public String getMasterPasswordCredentialId() { return masterPasswordCredentialId; }

    // @DataBoundSetter tells Jenkins to call these methods when the admin saves the form.
    @DataBoundSetter
    public void setApiKeyCredentialId(String apiKeyCredentialId) {
        this.apiKeyCredentialId = apiKeyCredentialId;
        save(); // Persist the configuration to disk.
    }
    @DataBoundSetter
    public void setMasterPasswordCredentialId(String masterPasswordCredentialId) {
        this.masterPasswordCredentialId = masterPasswordCredentialId;
        save();
    }

    /**
     * This method is called by the config.jelly UI to populate the dropdown list
     * for the API Key credential. The name is very important: doFill<fieldName>Items.
     */
    @POST
    public ListBoxModel doFillApiKeyCredentialIdItems() {
        // This permission check is required for security.
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        // This is the standard, secure way to build a dropdown of credentials
        // that are of the "Username with password" type.
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, Jenkins.get(), UsernamePasswordCredentialsImpl.class, Collections.<DomainRequirement>emptyList());
    }

    @POST
    public ListBoxModel doFillMasterPasswordCredentialIdItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        // This builds a dropdown of credentials that are of the "Secret text" type.
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, Jenkins.get(), StringCredentialsImpl.class, Collections.<DomainRequirement>emptyList());
    }
}