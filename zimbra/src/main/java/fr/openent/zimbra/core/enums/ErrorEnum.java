package fr.openent.zimbra.core.enums;

public enum ErrorEnum {

    TASKS_NOT_RETRIEVED("zimbra.error.retrieving.task"),
    ERROR_RETRIEVING_MAIL("error.retrieving.mail"),
    ERROR_CREATING_TASKS("error.creating.tasks"),
    ERROR_CREATING_TASKS_TRANSACTION("error.creating.tasks.transaction"),
    FAIL_NOTIFY_ADML("fail.notify.admls"),
    ERROR_CREATING_RECALL_MAIL("error.creating.recall.mail"),
    ERROR_FETCHING_IDS("error.fetching.ids"),
    WRONG_MAIL_OWNER("wrong.mail.owner"),
    ZIMBRA_ERROR_QUEUE("zimbra.error.queue"),
    ERROR_TASK_MODEL("zimbra.error.task.model"),
    ERROR_QUEUE_TASK("zimbra.error.queue.task"),
    ERROR_TASK_RETRIEVE("zimbra.error.task.retrieve"),
    ERROR_RECALL_RETRIEVE("zimbra.error.retrieve.recalls"),
    ERROR_ACTION_UPDATE("zimbra.error.update.action"),
    ERROR_FETCHING_TASK("zimbra.error.fetching.task"),
    ERROR_NO_SUCH_MESSAGE("zimbra.recall.no.such.message"),
    ERROR_FETCHING_ACTION("zimbra.error.fetching.action"),
    ERROR_FETCHING_STRUCTURE("zimbra.error.fetching.structures"),
    FAIL_ACCEPT_RECALL("fail.acept.recall"),
    ADML_NO_RIGHT_STRUCTURES("adml.no.rights.on.structs"),
    FAIL_DELETE_RECALL("fail.delete.recall"),
    FAIL_LIST_STRUCTURES("fail.list.structures"),
    ERROR_FETCHING_ADML("zimbra.error.fetching.adml"),
    ERROR_RETRIEVING_ACTION("zimbra.error.retrieving.action"),
    ERROR_DELETING_MAIL("zimbra.error.deleting.mail"),
    ERROR_EDITING_TASK("zimbra.error.editing.task"),
    ERROR_FETCHING_MODEL("zimbra.fetching.model"),
    ERROR_UPDATING_TASK("zimbra.error.updating.task"),
    ACTION_DOES_NOT_EXIST("action.does.not.exit"),
    ERROR_NOTIFY_CALENDAR("fail.to.notify.calendar.module"),
    USER_NOT_DEFINED("user.not.defined"),
    ERROR_RETRIEVING_ICAL("error.retrieving.ical"),
    ERROR_CREATING_LOGS("error.creating.logs"),
    NO_MAIL_TO_RECALL("no.mail.to.recall");

    private final String errorEnum;

    ErrorEnum(String errorEnum) {
        this.errorEnum = errorEnum;
    }

    public String method() {
        return this.errorEnum;
    }
}
