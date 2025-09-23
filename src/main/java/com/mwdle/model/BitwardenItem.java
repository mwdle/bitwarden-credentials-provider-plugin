package com.mwdle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitwardenItem {
    private String id;
    private String name;
    private String notes;
    private BitwardenLogin login;
    private BitwardenSshKey sshKey;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public BitwardenLogin getLogin() {
        return login;
    }

    public BitwardenSshKey getSshKey() {
        return sshKey;
    }
}