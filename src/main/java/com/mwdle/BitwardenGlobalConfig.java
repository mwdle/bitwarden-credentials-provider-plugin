package com.mwdle;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

@Extension
public class BitwardenGlobalConfig extends GlobalConfiguration {

    private String serverUrl;
    private String apiCredentialId;
    private String masterPasswordCredentialId;

    public BitwardenGlobalConfig() {
        load();
    }

    public static BitwardenGlobalConfig get() {
        return GlobalConfiguration.all().get(BitwardenGlobalConfig.class);
    }

    public String getServerUrl() { return serverUrl; }
    public String getApiCredentialId() { return apiCredentialId; }
    public String getMasterPasswordCredentialId() { return masterPasswordCredentialId; }

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

    @POST
    public ListBoxModel doFillApiCredentialIdItems(@AncestorInPath Jenkins context, @QueryParameter String apiCredentialId) {
        context.checkPermission(Jenkins.ADMINISTER);

        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(SecurityContextHolder.getContext().getAuthentication(),
                        context,
                        StandardUsernamePasswordCredentials.class,
                        Collections.emptyList(),
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.withScope(CredentialsScope.SYSTEM),
                                CredentialsMatchers.withScope(CredentialsScope.GLOBAL)
                        )
                )
                .includeCurrentValue(apiCredentialId);
    }

    @POST
    public ListBoxModel doFillMasterPasswordCredentialIdItems(@AncestorInPath Jenkins context, @QueryParameter String masterPasswordCredentialId) {
        context.checkPermission(Jenkins.ADMINISTER);

        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(SecurityContextHolder.getContext().getAuthentication(),
                        context,
                        StringCredentials.class,
                        Collections.emptyList(),
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.withScope(CredentialsScope.SYSTEM),
                                CredentialsMatchers.withScope(CredentialsScope.GLOBAL)
                        )
                )
                .includeCurrentValue(masterPasswordCredentialId);
    }
}