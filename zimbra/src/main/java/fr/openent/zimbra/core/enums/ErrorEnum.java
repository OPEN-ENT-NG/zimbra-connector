package fr.openent.zimbra.core.enums;

public enum ErrorEnum {

    TASKS_NOT_RETRIEVED("zimbra.error.retrieving.task"),
    ERROR_RETRIEVING_MAIL("error.retrieving.mail"),
    ERROR_CREATING_TASKS("error.creating.tasks"),
    FAIL_NOTIFY_ADML("fail.notify.admls"),
    ERROR_CREATING_RECALL_MAIL("error.creating.recall.mail"),
    ERROR_FETCHING_IDS("error.fetching.ids"),
    WRONG_MAIL_OWNER("wrong.mail.owner"),
    ZIMBRA_ERROR_QUEUE("zimbra.error.queue"),
    ERROR_TASK_MODEL("zimbra.error.task.model"),
    ERROR_QUEUE_TASK("zimbra.error.queue.task"),
    ERROR_TASK_RETRIEVE("zimbra.error.task.retrieve"),
    ERROR_FETCHING_TASK("zimbra.error.fetching.task"),
    ERROR_FETCHING_ACTION("zimbra.error.fetching.action"),
    ERROR_FETCHING_STRUCTURE("zimbra.error.fetching.structures"),
    ERROR_FETCHING_ADML("zimbra.error.fetching.adml"),
    ERROR_RETRIEVING_ACTION("zimbra.error.retrieving.action"),
    ERROR_EDITING_TASK("zimbra.error.editing.task"),
    ERROR_UPDATING_TASK("zimbra.error.updating.task"),
    NO_MAIL_TO_RECALL("no.mail.to.recall");

    private final String errorEnum;

    ErrorEnum(String errorEnum) {
        this.errorEnum = errorEnum;
    }

    public String method() {
        return this.errorEnum;
    }
}
