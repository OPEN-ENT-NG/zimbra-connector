package fr.openent.zimbra.core.enums;

public enum AddressType {
    F("f");

    private final String addressType;

    AddressType(String addressType) {
        this.addressType = addressType;
    }

    public String method() {
        return this.addressType;
    }
}