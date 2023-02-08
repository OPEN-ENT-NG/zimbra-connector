package fr.openent.zimbra.worker;

public enum QueueWorkerStatus {
    NOT_STARTED("notStarted"),
    RUNNING("running"),
    PAUSED("paused");

    private final String value;

    QueueWorkerStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
