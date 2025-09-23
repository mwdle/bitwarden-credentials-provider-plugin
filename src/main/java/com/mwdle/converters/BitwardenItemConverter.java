package com.mwdle.converters;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.mwdle.BitwardenBackedCredential;
import com.mwdle.model.BitwardenItem;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * Defines the contract for converting a {@link BitwardenItem} into a standard Jenkins {@link StandardCredentials} object.
 * <p>
 * This abstract class is an {@link ExtensionPoint}, allowing different implementations to be
 * discovered by Jenkins at runtime. This creates an extensible system for supporting
 * various Bitwarden item types.
 */
public abstract class BitwardenItemConverter implements ExtensionPoint {

    /**
     * Finds the first available and registered converter that can handle the given Bitwarden item.
     *
     * @param item The Bitwarden item to find a converter for.
     * @return A suitable {@link BitwardenItemConverter} instance, or {@code null} if none are found.
     */
    public static BitwardenItemConverter findConverter(BitwardenItem item) {
        return Jenkins.get().getExtensionList(BitwardenItemConverter.class)
                .stream()
                .filter(converter -> converter.canConvert(item))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if this converter can handle the given Bitwarden item.
     * <p>
     * Each implementation should check for the presence of the specific data it needs
     * (e.g., a 'login' object or a 'notes' field).
     * <p>
     * If one implementation overlaps with another implementation, whichever one Jenkins resolves first will be selected.
     *
     * @param item The parsed JSON of the Bitwarden item.
     * @return {@code true} if this converter can handle the item.
     */
    public abstract boolean canConvert(BitwardenItem item);

    /**
     * Converts the Bitwarden item into a Jenkins credential.
     *
     * @param pointer The original "pointer" credential from the Jenkins store.
     * @param item    The parsed JSON of the Bitwarden item.
     * @return The resulting Jenkins credential.
     */
    public abstract StandardCredentials convert(BitwardenBackedCredential pointer, BitwardenItem item);
}