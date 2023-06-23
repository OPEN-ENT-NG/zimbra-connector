package fr.openent.zimbra.core.enums;

import java.util.Objects;

public enum RecipientType {
    GROUP("group"),
    USER("user"),
    UNKNOWN("unknown");

    private final String recipientType;

    RecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public String method() {
        return this.recipientType;
    }

    public static RecipientType fromString (String type) {
        for (RecipientType recipientType : RecipientType.values()) {
            if (Objects.equals(recipientType.method(), type))
                return recipientType;
        }
        return RecipientType.UNKNOWN;
    }
}