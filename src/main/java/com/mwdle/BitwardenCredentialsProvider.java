package com.mwdle;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.fasterxml.jackson.core.type.TypeReference;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Nonnull
    public <C extends Credentials> List<C> getCredentialsInItemGroup(
            @Nonnull Class<C> requestedCredType,
            @Nullable ItemGroup itemGroup,
            @Nullable Authentication authentication,
            @Nonnull List<DomainRequirement> domainRequirements) {

        if (itemGroup == null || authentication == null) {
            return Collections.emptyList();
        }

        BitwardenGlobalConfig config = BitwardenGlobalConfig.get();
        String apiCredentialId = config.getApiCredentialId();
        String masterPassCredentialId = config.getMasterPasswordCredentialId();
        if (apiCredentialId == null || masterPassCredentialId == null) {
            return Collections.emptyList();
        }

        StandardUsernamePasswordCredentials apiCredentials = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this)
                .flatMap(p -> p.getCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, itemGroup, authentication, domainRequirements).stream())
                .filter(c -> c.getId().equals(apiCredentialId)).findFirst().orElse(null);

        StringCredentials masterPassword = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this).flatMap(p -> p.getCredentialsInItemGroup(StringCredentials.class, itemGroup, authentication, domainRequirements).stream())
                .filter(c -> c.getId().equals(masterPassCredentialId)).findFirst().orElse(null);

        if (apiCredentials == null || masterPassword == null) {
            return Collections.emptyList();
        }

        List<BitwardenAppCredential> pointers = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this)
                .flatMap(p -> p.getCredentialsInItemGroup(BitwardenAppCredential.class, itemGroup, authentication, domainRequirements).stream())
                .toList();

        if (pointers.isEmpty()) {
            return Collections.emptyList();
        }

        try (BitwardenClient client = new BitwardenClient(apiCredentials, masterPassword, config.getServerUrl())) {
            String sessionToken = client.getSessionToken();

            @SuppressWarnings("unchecked")
            List<C> result = pointers.stream()
                    .map(ptr -> {
                        try {
                            String itemJson = client.getSecret(ptr.getLookupValue(), sessionToken);
                            Map<String, Object> item = objectMapper.readValue(itemJson, new TypeReference<>() {});
                            if (requestedCredType.isAssignableFrom(StringCredentials.class)) {
                                if (item.get("login") instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> loginData = (Map<String, Object>) item.get("login");
                                    if (loginData.get("password") != null) { return (C) new StringCredentialsImpl(ptr.getScope(), ptr.getId(), ptr.getDescription(), Secret.fromString((String) loginData.get("password"))); }
                                }
                                if (item.get("notes") instanceof String) { return (C) new StringCredentialsImpl(ptr.getScope(), ptr.getId(), ptr.getDescription(), Secret.fromString((String) item.get("notes"))); }
                            } else if (requestedCredType.isAssignableFrom(StandardUsernamePasswordCredentials.class)) {
                                if (item.get("login") instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> loginData = (Map<String, Object>) item.get("login");
                                    if (loginData.get("username") != null && loginData.get("password") != null) { return (C) new UsernamePasswordCredentialsImpl(ptr.getScope(), ptr.getId(), ptr.getDescription(), (String) loginData.get("username"), (String) loginData.get("password")); }
                                }
                            }
                            return null;
                        } catch (Exception e) {
                            System.err.println("ERROR inside map for item '" + ptr.getLookupValue() + "': " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return result;
        } catch (IOException | InterruptedException e) {
            System.err.println("ERROR communicating with Bitwarden CLI: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}