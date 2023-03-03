package fr.openent.zimbra.core.enums;

public enum SoapRequestFields {
    BODY("Body"),
    START("s"),
    END("e");

    private final String soapRequestField;

    SoapRequestFields(String soapRequestField) {
        this.soapRequestField = soapRequestField;
    }

    public String method() {
        return this.soapRequestField;
    }
}
