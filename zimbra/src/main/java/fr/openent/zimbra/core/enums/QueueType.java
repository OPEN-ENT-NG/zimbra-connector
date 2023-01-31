package fr.openent.zimbra.core.enums;

public enum QueueType {
    ICAL("ical"),
    RECALL("recall");

    private final String queueType;

    QueueType(String queueType) {
        this.queueType = queueType;
    }

    public String method() {
        return this.queueType;
    }
}
