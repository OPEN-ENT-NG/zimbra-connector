package fr.openent.zimbra.worker;

import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.RecallMailService;
import io.vertx.core.logging.LoggerFactory;

public class RecallMailWorker extends QueueWorker<RecallTask> {
    public final RecallMailService recallMailService = serviceManager.getRecallMailService();
    public static final String RECALL_MAIL_HANDLER_ADDRESS = "zimbra.handler";

    public RecallMailWorker() {
        super.log = LoggerFactory.getLogger(RecallMailWorker.class);
    }

    @Override
    public void start() throws Exception {
        super.start();
        this.setMaxQueueSize(configManager.getZimbraRecallWorkerMaxQueue());
        this.eb.localConsumer(RECALL_MAIL_HANDLER_ADDRESS, this);
    }

    public void execute(RecallTask task) {
        recallMailService.deleteMessage(task.getRecallMessage().getMessage().getMailId());
    }
}
