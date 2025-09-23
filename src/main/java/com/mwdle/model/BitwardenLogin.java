package com.mwdle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the nested 'login' object within a Bitwarden item JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitwardenLogin {
    /**
     * The username associated with the login.
     */
    private String username;
    /**
     * The password associated with the login.
     */
    private String password;

    /**
     * @return The username for this login.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The password for this login.
     */
    public String getPassword() {
        return password;
    }
}