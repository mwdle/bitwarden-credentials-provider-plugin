package com.mwdle;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.mwdle.converters.BitwardenItemConverter;
import com.mwdle.model.BitwardenItem;
import hudson.Extension;
import hudson.model.ItemGroup;
import org.springframework.security.core.Authentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This provider is responsible for resolving Bitwarden credentials into real, usable Jenkins credentials.
 */
@Extension
public class BitwardenCredentialsProvider extends CredentialsProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public <C extends Credentials> List<C> getCredentialsInItemGroup(@Nonnull Class<C> type, @Nullable ItemGroup itemGroup, @Nullable Authentication authentication, @Nonnull List<DomainRequirement> domainRequirements) {

        if (itemGroup == null || authentication == null) {
            return Collections.emptyList();
        }

        List<BitwardenItem> bitwardenItems;
        try {
            BitwardenCLI.sync(BitwardenSessionManager.get().getSessionToken());
            bitwardenItems = BitwardenCLI.listItems(BitwardenSessionManager.get().getSessionToken());
        } catch (IOException | InterruptedException e) {
            return Collections.emptyList();
        }

        List<C> result = new ArrayList<>();
        bitwardenItems.forEach(item -> {
            BitwardenItemConverter converter = BitwardenItemConverter.findConverter(item);
            if (converter != null) {
                String description = String.format("Bitwarden: %s (ID: %s)", item.getName(), item.getId());
                // Create the credential twice, to allow fetching it both by id OR name.
                @SuppressWarnings("unchecked") C credByName = (C) converter.convert(CredentialsScope.GLOBAL, item.getName(), description, item);
                result.add(credByName);
                @SuppressWarnings("unchecked") C credById = (C) converter.convert(CredentialsScope.GLOBAL, item.getId(), description, item);
                result.add(credById);
            }
        });

        return result;
    }
}