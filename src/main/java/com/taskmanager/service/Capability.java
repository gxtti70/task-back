package com.taskmanager.service;

public enum Capability {
    ATTACHMENTS("attachments", "Los adjuntos requieren un plan Premium.");

    private final String key;
    private final String upgradeMessage;

    Capability(String key, String upgradeMessage) {
        this.key = key;
        this.upgradeMessage = upgradeMessage;
    }

    public String key() { return key; }
    public String upgradeMessage() { return upgradeMessage; }
}
