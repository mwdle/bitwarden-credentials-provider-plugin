package com.mwdle;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.springframework.security.core.Authentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Extension
public class BitwardenCredentialsProvider extends CredentialsProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @Nonnull
    public <C extends Credentials> List<C> getCredentialsInItemGroup(
            @Nonnull Class<C> type,
            @Nullable ItemGroup itemGroup,
            @Nullable Authentication authentication,
            @Nonnull List<DomainRequirement> domainRequirements) {

        System.out.println("\n--- PROVIDER DEBUG: getCredentialsInItemGroup called for type: " + type.getSimpleName() + " ---");

        if (itemGroup == null || authentication == null) {
            System.out.println("PROVIDER DEBUG: Exiting because itemGroup or authentication is null.");
            return Collections.emptyList();
        }

        BitwardenGlobalConfig config = BitwardenGlobalConfig.get();
        String apiCredentialId = config.getApiCredentialId();
        String masterPassCredId = config.getMasterPasswordCredentialId();
        if (apiCredentialId == null || masterPassCredId == null) {
            System.out.println("PROVIDER DEBUG: Exiting because plugin is not configured in Manage Jenkins.");
            return Collections.emptyList();
        }
        System.out.println("PROVIDER DEBUG: Found config. API Key ID: " + apiCredentialId);

        StandardUsernamePasswordCredentials apiCredentials = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this).flatMap(p -> p.getCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, itemGroup, authentication, domainRequirements).stream())
                .filter(c -> c.getId().equals(apiCredentialId)).findFirst().orElse(null);
        StringCredentials masterPassword = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this).flatMap(p -> p.getCredentialsInItemGroup(StringCredentials.class, itemGroup, authentication, domainRequirements).stream())
                .filter(c -> c.getId().equals(masterPassCredId)).findFirst().orElse(null);
        if (apiCredentials == null || masterPassword == null) {
            System.out.println("PROVIDER DEBUG: Exiting because API Key or Master Password credentials were not found.");
            return Collections.emptyList();
        }
        System.out.println("PROVIDER DEBUG: Successfully looked up auth credentials.");

        List<BitwardenAppCredential> pointers = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this).flatMap(p -> p.getCredentialsInItemGroup(BitwardenAppCredential.class, itemGroup, authentication, domainRequirements).stream())
                .toList();
        System.out.println("PROVIDER DEBUG: Found " + pointers.size() + " pointer credential(s).");

        if (pointers.isEmpty()) {
            return Collections.emptyList();
        }

        BitwardenClient client = new BitwardenClient(apiCredentials, masterPassword, config.getServerUrl());
        try {
            System.out.println("PROVIDER DEBUG: Getting session token...");
            String sessionToken = client.getSessionToken();
            System.out.println("PROVIDER DEBUG: Got session token successfully.");

            return pointers.stream()
                    .map(ptr -> {
                        System.out.println("PROVIDER DEBUG: Processing pointer with ID: " + ptr.getId() + " | LookupValue: " + ptr.getLookupValue());
                        try {
                            String itemJson = client.getSecret(ptr.getLookupValue(), sessionToken);
                            System.out.println("PROVIDER DEBUG: RAW JSON from Bitwarden for '" + ptr.getLookupValue() + "': " + itemJson);
                            Map<String, Object> item = OBJECT_MAPPER.readValue(itemJson, Map.class);
                            if (type.isAssignableFrom(StringCredentials.class)) {
                                if (item.get("login") instanceof Map) { Map<String, Object> loginData = (Map<String, Object>) item.get("login"); if (loginData.get("password") != null) { return (C) new StringCredentialsImpl(ptr.getScope(), ptr.getId(), ptr.getDescription(), Secret.fromString((String) loginData.get("password"))); } }
                                if (item.get("notes") instanceof String) { return (C) new StringCredentialsImpl(ptr.getScope(), ptr.getId(), ptr.getDescription(), Secret.fromString((String) item.get("notes"))); }
                            } else if (type.isAssignableFrom(StandardUsernamePasswordCredentials.class)) {
                                if (item.get("login") instanceof Map) { Map<String, Object> loginData = (Map<String, Object>) item.get("login"); if (loginData.get("username") != null && loginData.get("password") != null) { return (C) new UsernamePasswordCredentialsImpl(ptr.getScope(), ptr.getId(), ptr.getDescription(), (String) loginData.get("username"), (String) loginData.get("password")); } }
                            }
                            System.out.println("PROVIDER DEBUG: Could not map item '" + ptr.getLookupValue() + "' to the requested type.");
                            return null;
                        } catch (Exception e) {
                            System.err.println("PROVIDER DEBUG: ERROR inside map for item '" + ptr.getLookupValue() + "': " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (IOException | InterruptedException e) {
            System.err.println("PROVIDER DEBUG: ERROR communicating with Bitwarden CLI: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            client.logout();
        }
    }
}