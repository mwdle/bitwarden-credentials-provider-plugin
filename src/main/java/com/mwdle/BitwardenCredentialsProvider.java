package com.mwdle;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.springframework.security.core.Authentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable; // <-- Required import
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Extension
public class BitwardenCredentialsProvider extends CredentialsProvider {

    @Override
    @Nonnull
    public <C extends Credentials> List<C> getCredentialsInItemGroup(
            @Nonnull Class<C> type,
            @Nullable ItemGroup itemGroup, // <-- Correctly marked as Nullable
            @Nullable Authentication authentication, // <-- Correctly marked as Nullable
            @Nonnull List<DomainRequirement> domainRequirements) {

        // If we don't have a context to search in, we can't do anything.
        if (itemGroup == null || authentication == null) {
            return Collections.emptyList();
        }

        // --- Step 1: Get the global configuration ---
        BitwardenGlobalConfig config = BitwardenGlobalConfig.get();
        String apiKeyCredId = config.getApiKeyCredentialId();
        String masterPassCredId = config.getMasterPasswordCredentialId();

        if (apiKeyCredId == null || masterPassCredId == null) {
            return Collections.emptyList();
        }

        // --- Step 2: Look up auth credentials using an inlined, recursion-safe stream ---
        StandardUsernamePasswordCredentials apiKey = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(provider -> provider != this)
                .flatMap(provider -> provider.getCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, itemGroup, authentication, domainRequirements).stream())
                .filter(c -> c.getId().equals(apiKeyCredId))
                .findFirst().orElse(null);

        StringCredentials masterPassword = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(provider -> provider != this)
                .flatMap(provider -> provider.getCredentialsInItemGroup(StringCredentials.class, itemGroup, authentication, domainRequirements).stream())
                .filter(c -> c.getId().equals(masterPassCredId))
                .findFirst().orElse(null);

        if (apiKey == null || masterPassword == null) {
            return Collections.emptyList();
        }

        // --- Look up pointer credentials using the same inlined, recursion-safe stream ---
        List<BitwardenAppCredential> pointers = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(provider -> provider != this)
                .flatMap(provider -> provider.getCredentialsInItemGroup(BitwardenAppCredential.class, itemGroup, authentication, domainRequirements).stream())
                .collect(Collectors.toList());


        // --- Dummy data logic for testing ---
        if (type.isAssignableFrom(StringCredentials.class)) {
            return pointers.stream()
                    .map(ptr -> (C) new StringCredentialsImpl(
                            ptr.getScope(),
                            ptr.getId(),
                            "Dummy secret for item: '" + ptr.getItemName() + "'",
                            Secret.fromString("it-works-" + ptr.getItemName())
                    ))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}