package com.mwdle;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.mwdle.bitwarden.BitwardenAuthenticationException;
import com.mwdle.bitwarden.BitwardenCLI;
import com.mwdle.bitwarden.BitwardenSessionManager;
import com.mwdle.converters.BitwardenItemConverter;
import com.mwdle.model.BitwardenItem;
import hudson.Extension;
import hudson.model.ItemGroup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.security.core.Authentication;

/**
 * This provider is responsible for resolving Bitwarden credentials into real, usable Jenkins credentials.
 */
@Extension
public class BitwardenCredentialsProvider extends CredentialsProvider {

    private static final Logger LOGGER = Logger.getLogger(BitwardenCredentialsProvider.class.getName());

    /**
     * Called by Jenkins whenever a build needs to resolve credentials. This implementation fetches the
     * complete list of items from the Bitwarden vault and dynamically converts them into Jenkins
     * credentials on the fly.
     * <p>
     * For each item retrieved from Bitwarden, this method creates <strong>two</strong> in-memory Jenkins
     * credentials:
     * <ol>
     * <li>One where the credential ID is the Bitwarden item's <strong>name</strong>.</li>
     * <li>One where the credential ID is the Bitwarden item's <strong>UUID</strong>.</li>
     * </ol>
     * This allows pipeline authors to reference the same secret using either its human-readable name or its
     * unique, stable ID (e.g., {@code credentialsId: 'My Production API Key'}) or
     * {@code credentialsId: 'a1b2c3d4-e5f6-...'}).
     *
     * @param type The class of credentials being requested.
     * @param itemGroup The context in which the credentials are being requested.
     * @param authentication The authentication context of the user or process.
     * @param domainRequirements Any domain requirements for the credentials.
     * @return A list of dynamically-generated credentials matching the request.
     */
    @Override
    @Nonnull
    public <C extends Credentials> List<C> getCredentialsInItemGroup(
            @Nonnull Class<C> type,
            @Nullable ItemGroup itemGroup,
            @Nullable Authentication authentication,
            @Nonnull List<DomainRequirement> domainRequirements) {

        LOGGER.fine(() -> "getCredentialsInItemGroup: type=" + type.getSimpleName()
                + " itemGroup=" + (itemGroup != null ? itemGroup.getFullName() : "null")
                + " authentication=" + (authentication != null ? authentication.getName() : "null"));

        if (itemGroup == null || authentication == null) {
            LOGGER.fine("getCredentialsInItemGroup: itemGroup or authentication is null — returning empty list");
            return Collections.emptyList();
        }

        List<BitwardenItem> bitwardenItems;
        try {
            BitwardenCLI.sync(BitwardenSessionManager.getInstance().getSessionToken());
            bitwardenItems =
                    BitwardenCLI.listItems(BitwardenSessionManager.getInstance().getSessionToken());
        } catch (BitwardenAuthenticationException e) {
            LOGGER.severe("Bitwarden authentication failed: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException | InterruptedException e) {
            LOGGER.warning("Failed to fetch Bitwarden item(s): " + e.getMessage());
            return Collections.emptyList();
        }

        List<C> result = new ArrayList<>();
        bitwardenItems.forEach(item -> {
            LOGGER.fine(() -> "Processing item: id=" + item.getId() + " name='" + item.getName() + "'");
            BitwardenItemConverter converter = BitwardenItemConverter.findConverter(item);
            if (converter != null) {
                LOGGER.fine(() -> "Using converter: " + converter.getClass().getSimpleName());
                String description = String.format("Bitwarden: %s (ID: %s)", item.getName(), item.getId());
                // Create the credential twice, to allow fetching it both by id OR name
                Credentials credential = converter.convert(CredentialsScope.GLOBAL, item.getName(), description, item);
                if (type.isInstance(credential)) result.add(type.cast(credential));
                credential = converter.convert(CredentialsScope.GLOBAL, item.getId(), description, item);
                if (type.isInstance(credential)) result.add(type.cast(credential));
            } else
                LOGGER.fine(() -> "No converter found for item: id=" + item.getId() + " name='" + item.getName() + "'");
        });

        LOGGER.fine(() -> "Returning " + result.size() + " credentials");
        return result;
    }
}
