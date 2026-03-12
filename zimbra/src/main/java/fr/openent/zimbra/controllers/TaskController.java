package fr.openent.zimbra.controllers;

import fr.openent.zimbra.service.synchro.SynchroTask;
import fr.openent.zimbra.tasks.cron.ICalRequestCron;
import fr.openent.zimbra.tasks.cron.RecallMailCron;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class TaskController extends BaseController {
	protected static final Logger log = LoggerFactory.getLogger(TaskController.class);

	final SynchroTask syncLauncherTask;
	final RecallMailCron recallMailCron;
	final ICalRequestCron iCalRequestCron;
	final SynchroTask syncMailerTask;

	public TaskController(SynchroTask syncLauncherTask, RecallMailCron recallMailCron, ICalRequestCron iCalRequestCron, SynchroTask syncMailerTask) {
		this.syncLauncherTask = syncLauncherTask;
		this.recallMailCron = recallMailCron;
		this.iCalRequestCron = iCalRequestCron;
		this.syncMailerTask = syncMailerTask;
	}

	@Post("api/internal/sync-launcher")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void syncLauncher(HttpServerRequest request) {
		log.info("Trigger sync launcher task");
		syncLauncherTask.handle(0L);
		render(request, null, 202);
	}

	@Post("api/internal/recall-mail")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void recallMail(HttpServerRequest request) {
		log.info("Trigger recall mail task");
		recallMailCron.handle(0L);
		render(request, null, 202);
	}

	@Post("api/internal/ical-request")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void iCalRequest(HttpServerRequest request) {
		log.info("Triggered ical request task");
		iCalRequestCron.handle(0L);
		render(request, null, 202);
	}

	@Post("api/internal/sync-mailer")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void syncMailer(HttpServerRequest request) {
		log.info("Triggered sync mailer task");
		syncMailerTask.handle(0L);
		render(request, null, 202);
	}

}
