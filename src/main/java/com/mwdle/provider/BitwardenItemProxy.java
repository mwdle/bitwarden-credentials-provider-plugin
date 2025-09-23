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

/**
 * The lazy-loading mechanism for Bitwarden-backed credentials.
 * <p>
 * This class implements {@link InvocationHandler}, which allows it to intercept method calls
 * made to the dynamic proxy credential object created by {@link BitwardenCredentialsProvider}.
 * It serves non-secret data instantly from the "pointer" credential and only
 * triggers the slow Bitwarden CLI process when a method that requires a real secret
 * (like {@code getSecret()} or {@code getPassword()}) is called.
 */
public class BitwardenItemProxy implements InvocationHandler, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The original "pointer" credential containing the lookup information.
     */
    private final BitwardenBackedCredential pointer;
    /**
     * The Jenkins context (e.g., a Folder or the root Jenkins instance) in which the lookup is being performed.
     */
    private final ItemGroup<?> context;
    /** The authentication context of the user or system performing the lookup. */
    private final Authentication authentication;
    /** A transient, volatile field to cache the fully resolved credential after the first fetch. */
    private transient volatile StandardCredentials resolved;

    /**
     * Constructs a new proxy handler.
     *
     * @param pointer The "pointer" credential that this proxy represents.
     * @param context The Jenkins context for the credential lookup.
     * @param authentication The authentication context for the lookup.
     */
    public BitwardenItemProxy(BitwardenBackedCredential pointer, ItemGroup<?> context, Authentication authentication) {
        this.pointer = pointer;
        this.context = context;
        this.authentication = authentication;
    }

    /**
     * Intercepts all method calls made to the proxy credential.
     * <p>
     * This is the core of the lazy-loading logic. It routes calls to either the "fast path"
     * for simple getters or the "slow path" which triggers the full secret resolution.
     *
     * @param proxy  The proxy instance that the method was invoked on.
     * @param method The {@code Method} instance corresponding to the interface method invoked on the proxy instance.
     * @param args   An array of objects containing the values of the arguments passed in the method invocation on the proxy instance.
     * @return The value to return from the method invocation on the proxy instance.
     * @throws Throwable The exception to throw from the method invocation on the proxy instance.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Handle simple, non-secret methods instantly by delegating to the pointer.
        if (isSimpleGetter(method)) {
            return method.invoke(pointer, args);
        }

        // If a secret-related method is called, ensure the secret has been fetched from Bitwarden.
        // This is synchronized to be thread-safe during the first resolution.
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

    /**
     * Checks if a method is a simple, non-secret getter that can be answered instantly from the pointer.
     *
     * @param method The method being called.
     * @return true if the method does not require fetching the real secret.
     */
    private boolean isSimpleGetter(Method method) {
        String name = method.getName();
        return name.equals("getId") || name.equals("getDescription") || name.equals("getScope");
    }

    /**
     * The main "slow path" method. This is only called once per proxy instance when a secret is first requested.
     * <p>
     * It performs the full, multi-second process of calling the Bitwarden CLI, fetching the item,
     * and using the "Converter" system to produce the final, real Jenkins credential object.
     *
     * @return The fully resolved, real Jenkins credential.
     * @throws IOException If any part of the process fails.
     * @throws InterruptedException If the CLI process is interrupted.
     */
    private StandardCredentials resolveSecret() throws IOException, InterruptedException {
        BitwardenGlobalConfig config = BitwardenGlobalConfig.get();
        String apiKeyCredId = config.getApiCredentialId();
        String masterPassCredId = config.getMasterPasswordCredentialId();

        // Look up the credentials required to authenticate the Bitwarden CLI.
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

            // Find the correct converter for the fetched Bitwarden item.
            BitwardenItemConverter converter = BitwardenItemConverter.findConverter(item);
            if (converter != null) {
                // Delegate the conversion to the specialist converter.
                return converter.convert(pointer, item);
            }
        }
        return null;
    }
}