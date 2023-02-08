package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.enums.TaskStatus;

public class IcalTaskInfo extends TaskInfo {
    private final String jsns;
    private final String body;

    public IcalTaskInfo(int actionId, TaskStatus status, String jsns, String body) {
        super(actionId, status);
        this.jsns = jsns;
        this.body = body;
    }

    public String getJsns() {
        return jsns;
    }

    public String getBody() {
        return body;
    }
}
