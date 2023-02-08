package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.enums.TaskStatus;

import java.util.List;
import java.util.UUID;

public class RecallTaskInfo extends TaskInfo {
    private final int recallMailId;
    private final List<UUID> receiversId;
    private final int retry;

    public RecallTaskInfo(int actionId, TaskStatus status, int recallMailId, List<UUID> receiversId, int retry) {
        super(actionId, status);
        this.recallMailId = recallMailId;
        this.receiversId = receiversId;
        this.retry = retry;
    }

    public int getRecallMailId() {
        return recallMailId;
    }

    public List<UUID> getReceiversId() {
        return receiversId;
    }

    public int getRetry() {
        return retry;
    }
}
