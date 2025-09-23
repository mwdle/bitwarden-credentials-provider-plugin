package com.mwdle.provider;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.mwdle.BitwardenBackedCredential;
import com.mwdle.BitwardenClient;
import com.mwdle.BitwardenGlobalConfig;
import com.mwdle.converters.BitwardenItemConverter;
import com.mwdle.model.BitwardenItem;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;

public class BitwardenItemProxy implements InvocationHandler, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final BitwardenBackedCredential pointer;
    private final ItemGroup<?> context;
    private final Authentication authentication;
    private transient volatile StandardCredentials resolved;

    public BitwardenItemProxy(BitwardenBackedCredential pointer, ItemGroup<?> context, Authentication authentication) {
        this.pointer = pointer;
        this.context = context;
        this.authentication = authentication;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (isSimpleGetter(method)) {
            return method.invoke(pointer, args);
        }

        if (resolved == null) {
            synchronized (this) {
                if (resolved == null) {
                    resolved = resolveSecret();
                }
            }
        }
        if (resolved == null) {
            throw new IOException("Failed to resolve Bitwarden credential for item: " + pointer.getLookupValue());
        }
        return method.invoke(resolved, args);
    }

    private boolean isSimpleGetter(Method method) {
        String name = method.getName();
        return name.equals("getId") || name.equals("getDescription") || name.equals("getScope");
    }

    private StandardCredentials resolveSecret() throws IOException, InterruptedException {
        BitwardenGlobalConfig config = BitwardenGlobalConfig.get();
        String apiKeyCredId = config.getApiCredentialId();
        String masterPassCredId = config.getMasterPasswordCredentialId();

        StandardUsernamePasswordCredentials apiKey = Jenkins.get().getExtensionList(CredentialsProvider.class).stream().filter(p -> !(p instanceof BitwardenCredentialsProvider)).flatMap(p -> p.getCredentialsInItemGroup(StandardUsernamePasswordCredentials.class, context, authentication, Collections.emptyList()).stream()).filter(c -> c.getId().equals(apiKeyCredId)).findFirst().orElse(null);
        StringCredentials masterPassword = Jenkins.get().getExtensionList(CredentialsProvider.class).stream().filter(p -> !(p instanceof BitwardenCredentialsProvider)).flatMap(p -> p.getCredentialsInItemGroup(StringCredentials.class, context, authentication, Collections.emptyList()).stream()).filter(c -> c.getId().equals(masterPassCredId)).findFirst().orElse(null);
        if (apiKey == null || masterPassword == null) {
            throw new IOException("Could not find API Key or Master Password credentials configured for the Bitwarden plugin.");
        }

        try (BitwardenClient client = new BitwardenClient(apiKey, masterPassword, config.getServerUrl())) {
            BitwardenItem item = client.getSecret(pointer.getLookupValue());
            if (item == null) {
                return null;
            }
            BitwardenItemConverter converter = BitwardenItemConverter.findConverter(item);
            if (converter != null) {
                return converter.convert(pointer, item);
            }
        }
        return null;
    }
}