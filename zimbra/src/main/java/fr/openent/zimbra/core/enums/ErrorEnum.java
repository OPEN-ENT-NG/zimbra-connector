package fr.openent.zimbra.core.enums;

public enum ErrorEnum {

    TASKS_NOT_RETRIEVED("zimbra.error.retrieving.task");

    private final String errorEnum;

    ErrorEnum(String errorEnum) {
        this.errorEnum = errorEnum;
    }

    public String method() {
        return this.errorEnum;
    }
}
