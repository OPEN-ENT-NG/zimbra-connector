package fr.openent.zimbra.worker;

import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.RecallMailService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.LoggerFactory;

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

    private void handleDeleteMailError(RecallTask task, String err) {
        if (!err.equals(ErrorEnum.MAIL_NOT_FOUND.method()) || task.getRetry() < 4) {
            queueService.logFailureOnTask(task, ErrorEnum.ERROR_UPDATING_TASK.method())
                    .onFailure(error -> {
                        String errMessage = String.format("[Zimbra@%s::handleDeleteMailError]:  " +
                                "an error has occurred while updating task status: %s",
                                this.getClass().getSimpleName(), error.getMessage());
                        log.error(errMessage);
                    });
        } else {
            queueService.editTaskStatus(task, TaskStatus.FINISHED)
                    .onFailure(error -> {
                        String errMessage = String.format("[Zimbra@%s::handleDeleteMailError]:  " +
                                "an error has occurred while updating task status: %s",
                                this.getClass().getSimpleName(), error.getMessage());
                        log.error(errMessage);
                    });
        }
    }

    public Future<Void> execute(RecallTask task) {
        Promise<Void> promise = Promise.promise();
        recallMailService
                .deleteMessage(task.getRecallMessage(), task.getReceiverId(), task.getReceiverEmail(),
                        task.getRecallMessage().getSenderEmail())
                .compose(isDeleted -> queueService.editTaskStatus(task, TaskStatus.FINISHED))
                .onSuccess(res -> promise.complete())
                .onFailure(err -> {
                    handleDeleteMailError(task, err.getMessage());
                    promise.fail(ErrorEnum.ERROR_EXECUTING_TASK.method());
                    String errMessage = String.format("[Zimbra@%s::execute]:  " +
                            "an error has occurred while executing recall task: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                });
        return promise.future();
    }
}
