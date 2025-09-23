package com.mwdle;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
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

@Extension
public class BitwardenCredentialsProvider extends CredentialsProvider {

    @Override
    @Nonnull
    public <C extends Credentials> List<C> getCredentialsInItemGroup(@Nonnull Class<C> type,
            @Nullable ItemGroup itemGroup,
            @Nullable Authentication authentication,
            @Nonnull List<DomainRequirement> domainRequirements) {

        if (itemGroup == null || authentication == null) {
            return Collections.emptyList();
        }

        List<BitwardenAppCredential> pointers = Jenkins.get().getExtensionList(CredentialsProvider.class).stream()
                .filter(p -> p != this)
                .flatMap(p -> p.getCredentialsInItemGroup(BitwardenAppCredential.class, itemGroup, authentication, domainRequirements).stream())
                .toList();

        @SuppressWarnings("unchecked") List<C> result = pointers.stream().map(ptr -> (C) Proxy.newProxyInstance(BitwardenCredentialsProvider.class.getClassLoader(), new Class<?>[]{type}, new BitwardenItemProxy(ptr, itemGroup, authentication))).collect(Collectors.toList());

        return result;
    }
}