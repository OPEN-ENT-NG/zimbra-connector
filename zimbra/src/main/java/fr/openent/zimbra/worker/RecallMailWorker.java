package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.RecallMailService;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserUtils;

public class RecallMailWorker extends QueueWorker<RecallTask> {
    public final RecallMailService recallMailService = serviceManager.getRecallMailService();
    public static final String RECALL_MAIL_HANDLER_ADDRESS = "zimbra.recall.handler";

    public RecallMailWorker() {
        super.log = LoggerFactory.getLogger(RecallMailWorker.class);
        this.queueService = serviceManager.getRecallQueueService();
    }

    @Override
    public void start() throws Exception {
        super.start();
        this.setMaxQueueSize(configManager.getZimbraRecallWorkerMaxQueue());
        this.eb.localConsumer(RECALL_MAIL_HANDLER_ADDRESS, this);
    }

    public void execute(RecallTask task) {
        UserUtils.getUserInfos(eb, task.getReceiverId().toString(), user -> {

            recallMailService.deleteMessage(task.getRecallMessage(), user, task.getReceiverEmail(), task.getRecallMessage().getSenderEmail())
                    .compose(isDeleted -> queueService.editTaskStatus(task, TaskStatus.FINISHED))
                    .onFailure(err -> {
                        queueService.logFailureOnTask(task, ErrorEnum.ERROR_UPDATING_TASK.method())
                                .onFailure(error -> {
                                    String errMessage = String.format("[Zimbra@%s::execute]:  " +
                                                    "an error has occurred while updating task status: %s",
                                            this.getClass().getSimpleName(), error.getMessage());
                                    log.error(errMessage);
                                });
                        String errMessage = String.format("[Zimbra@%s::execute]:  " +
                                        "an error has occurred while executing recall task: %s",
                                this.getClass().getSimpleName(), err.getMessage());
                        log.error(errMessage);
                    });
        });
    }
}
