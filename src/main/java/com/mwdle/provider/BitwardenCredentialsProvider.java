package com.mwdle.provider;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.mwdle.BitwardenBackedCredential;
import hudson.Extension;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;
import org.springframework.security.core.Authentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The main engine of the plugin.
 * <p>
 * This provider is responsible for finding "pointer" credentials ({@link BitwardenBackedCredential})
 * and resolving them into real, usable Jenkins credentials. It uses a lazy-loading "proxy"
 * pattern to ensure that the slow process of fetching secrets from the Bitwarden CLI does not
 * block or slow down the Jenkins UI.
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

        // Find all of our "pointer" credentials that are stored in other credential stores (e.g., the default Jenkins store).
        List<BitwardenBackedCredential> pointers = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                // This filter is crucial to prevent an infinite recursion where the provider would try to call itself.
                .filter(p -> p != this)
                .flatMap(p -> p.getCredentialsInItemGroup(BitwardenBackedCredential.class, itemGroup, authentication, domainRequirements).stream()).toList();

        // For each pointer found, create a lazy-loading proxy object.
        // This is a fast, in-memory operation that keeps the UI responsive.
        @SuppressWarnings("unchecked") List<C> result = pointers.stream().map(ptr -> (C) Proxy.newProxyInstance(BitwardenCredentialsProvider.class.getClassLoader(), new Class<?>[]{type}, // The proxy will implement the interface the pipeline is asking for.
                new BitwardenItemProxy(ptr, itemGroup, authentication))).collect(Collectors.toList());

        return result;
    }
}