package fr.openent.zimbra.tasks.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.helper.MessageHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.Message;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.model.soap.SoapMessageHelper;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.service.impl.MessageService;
import fr.openent.zimbra.service.impl.NotificationService;
import fr.openent.zimbra.service.impl.RecipientService;
import fr.openent.zimbra.tasks.service.DbRecallMail;
import fr.openent.zimbra.tasks.service.RecallMailService;
import fr.openent.zimbra.service.StructureService;
import fr.openent.zimbra.utils.DateUtils;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.user.UserInfos;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static fr.openent.zimbra.model.constant.ZimbraConstants.ADDR_TYPE_FROM;

public class RecallMailServiceImpl implements RecallMailService {
    private final RecallQueueServiceImpl recallQueueService;
    private final NotificationService notificationService;
    private final StructureService structureService;
    private final DbRecallMail dbMailService;
    private final RecipientService recipientService;
    private final MessageService messageService;

    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(RecallMailServiceImpl.class);

    public RecallMailServiceImpl(DbRecallMail dbMailService, MessageService messageService, RecallQueueServiceImpl recallQueueService,
                                 NotificationService notificationService, StructureService structureService, RecipientService recipientService) {
        this.dbMailService = dbMailService;
        this.recallQueueService = recallQueueService;
        this.notificationService = notificationService;
        this.structureService = structureService;
        this.recipientService = recipientService;
        this.messageService = messageService;
    }

    public Future<List<RecallMail>> getRecallMails () {
        throw new NotImplementedException();
    }

    @Override
    public Future<Void> acceptRecall(int recallId) {
        Promise<Void> promise = Promise.promise();

        dbMailService.acceptRecall(recallId)
                .onSuccess(v -> promise.complete())
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::acceptRecall]:  " +
                                    "error while accepting recall: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.FAIL_ACCEPT_RECALL.method());
                });

        return promise.future();
    }

    public Future<List<RecallMail>> getRecallMailsForOneStructure (String structureId) {
        return dbMailService.getRecallMailByStruct(structureId);
   }

    private Future<Message> fetchUserIdForRecalledMessage(Message message) {
        Promise<Message> promise = Promise.promise();

        recipientService.getUserIdsFromEmails(message.getAllAddresses())
                .onSuccess(userMap -> {
                    message.setUserMapping(userMap);
                    promise.complete(message);
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::fetchUserIdForRecalledMessage]:  " +
                                    "Error fetching recipients id",
                            this.getClass().getSimpleName());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_FETCHING_IDS.method());
                });

        return promise.future();
    }

    private Future<Message> getMessageToRecall(UserInfos user, String messageId) {
        Promise<Message> promise = Promise.promise();

        SoapMessageHelper.getMessageById(user.getUserId(), messageId)
                .compose(this::fetchUserIdForRecalledMessage)
                .onSuccess(message -> {
                    if (MessageHelper.isUserMailSender(message, user)) {
                        promise.complete(message);
                    } else {
                        String errMessage = String.format("[Zimbra@%s::getMessageToRecall]:  " +
                                        "User is not the mail's sender.",
                                this.getClass().getSimpleName());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.WRONG_MAIL_OWNER.method());
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::getMessageToRecall]:  " +
                                    "error while retrieving mail: %s",
                            this.getClass().getSimpleName(), err);
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_RETRIEVING_MAIL.method());
                });

        return promise.future();
    }

    private Future<RecallMail> createRecallDataForQueue(UserInfos user, Message message, String comment) {
        Promise<RecallMail> promise = Promise.promise();

        recallQueueService.createAction(UUID.fromString(user.getUserId()), ActionType.RECALL, false)
                .onSuccess(action -> {
                    promise.complete(new RecallMail(-1, message, action, comment));
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createRecallDataForQueue]:  " +
                                    "error while creating recall action and tasks: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                });

        return promise.future();
    }

    private List<RecallTask> createTasksFromRecallMail(RecallMail mail) {
        return mail.getMessage().getEmailAddresses()
                .stream()
                .filter(addr -> !addr.getAddrType().equals(ADDR_TYPE_FROM) && mail.getMessage().getUserMapping().containsKey(addr.getAddress()))
                .map(addr -> new RecallTask(
                        -1,
                        TaskStatus.PENDING,
                        mail.getAction(),
                        mail, UUID.fromString(mail.getMessage().getUserMapping().get(addr.getAddress()).getUserId()),
                        addr.getAddress(),
                        0)
                )
                .collect(Collectors.toList()
        );
    }

    public Future<RecallMail> createRecallMailTasks(RecallMail recallMail) {
        Promise<RecallMail> promise = Promise.promise();

        recallQueueService.createTasksByBatch(recallMail.getAction(), createTasksFromRecallMail(recallMail), BATCH_SIZE)
                .onSuccess(tasks -> promise.complete(recallMail))
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createRecallMailTasks]:  " +
                                    "error while creating recall tasks: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_CREATING_TASKS.method());
                });

        return promise.future();

    }

    /**
     * Send notification for recall mail to all the structures adml of the user
     * @param mail  The mail to return
     * @param user  The user asking for the recall
     * @return      Failed future if any problem during adml notification, the mail to recall otherwise.
     */
    private Future<RecallMail> notifyADMLForRecall(RecallMail mail, UserInfos user) {
        Promise<RecallMail> promise = Promise.promise();

        structureService.getStructuresAndAdmls(user.getStructures())
                .onSuccess(structs -> {
                    structs.forEach(structure ->
                                notificationService.sendReturnMailNotification(user, mail.getMessage().getSubject(), structure.getId(), structure.getADMLS(), null)
                            );
                    promise.complete(mail);
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::notifyADMLForRecall]:  " +
                                    "notification to adml failed : %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.FAIL_NOTIFY_ADML.method());
                });

        return promise.future();
    }

    public Future<RecallMail> createRecallMail (UserInfos user, String messageId, String comment) {
        Promise<RecallMail> promise = Promise.promise();

        getMessageToRecall(user, messageId)
                .compose(zimbraMessage -> createRecallDataForQueue(user, zimbraMessage, comment))
                .compose(recallMail -> dbMailService.createRecallMail(recallMail, user))
                .compose(this::createRecallMailTasks)
                .compose(recallMail -> notifyADMLForRecall(recallMail, user))
                .onSuccess(promise::complete)
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createRecallMail]:  " +
                                    "error while creating recall mail data: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_CREATING_RECALL_MAIL.method());
                });

        return promise.future();
    }

    public Future<RecallMail> updateRecallMail (String recallMailId, String comment, String status) {
        throw new NotImplementedException();
    }

    public Future<List<UUID>> getUsers (String recallMailId) {
        // todo: get recall_mail from db
        // todo: call zimbra to retrieve user ids
        // todo: return user ids
        throw new NotImplementedException();
    }

    public Future<Void> deleteMessage (RecallMail recallMail, UserInfos user, String receiverEmail, String senderEmail) {
        Promise<Void> promise = Promise.promise();

        messageService.retrieveMailFromZimbra(
                new JsonObject()
                        .put(Field.USER_MAIL, senderEmail)
                        .put(Field.MID, recallMail.getMessage().getMailId().substring(1, recallMail.getMessage().getMailId().length() - 1)),
                new JsonObject().put(Field.ID, user.getUserId()).put(Field.MAIL, receiverEmail),
                mailId -> {
                    if (mailId.isRight()) {
                        messageService.deleteMessages(mailId.right().getValue(), user, false)
                                .onSuccess(promise::complete)
                                .onFailure(err -> {
                                    String errMessage = String.format("[Zimbra@%s::deleteMessage]:  " +
                                                    "error while deleting mail: %s",
                                            this.getClass().getSimpleName(), err.getMessage());
                                    log.error(errMessage);
                                    promise.fail(ErrorEnum.ERROR_DELETING_MAIL.method());
                                });
                    } else {
                        String errMessage = String.format("[Zimbra@%s::deleteMessage]:  " +
                                        "error while retrieving mail id : %s",
                                this.getClass().getSimpleName(), mailId.left().getValue());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.ERROR_DELETING_MAIL.method());
                    }
                }

        );

        return promise.future();
    }

    private String determineStatus(JsonObject mailData) {
        try {
            if (!mailData.getBoolean(Field.APPROVED)) {
                return Field.CAPITAL_WAITING;
            } else if (mailData.getInteger(TaskStatus.PENDING.method()) > 0) {
                return Field.CAPITAL_PROGRESS;
            } else {
                return Field.CAPITAL_REMOVED;
            }
        } catch (Exception e) {
            String errMessage = String.format("[Zimbra@%s::determineStatus]:  " +
                            "error while retrieving status from recall data : %s",
                    this.getClass().getSimpleName(), e.getMessage());
            log.error(errMessage);
            return "";
        }
    }

    @Override
    public Future<JsonArray> renderRecallMails(UserInfos user, JsonArray messageList) {
        Promise<JsonArray> promise = Promise.promise();
        dbMailService.checkRecalledInMailList(user.getUserId(), messageList)
                .onSuccess(correspondingRecall -> {
                    correspondingRecall.stream()
                            .filter(JsonObject.class::isInstance)
                            .map(mail -> (JsonObject) mail)
                            .forEach(recallMessage -> {
                                String recallId = recallMessage.getString(Field.LOCAL_MAIL_ID);
                                String status = determineStatus(recallMessage);
                                messageList
                                        .stream()
                                        .filter(message -> message instanceof JsonObject && Objects.equals(((JsonObject) message).getString(Field.ID), recallId))
                                        .map(JsonObject.class::cast)
                                        .forEach(message -> message.put(Field.RETURNED, status));
                            });
                    promise.complete(messageList);
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::determineStatus]:  " +
                                    "error while retrieving status from recall data : %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_RECALL_RETRIEVE.method());
                });
        return promise.future();
       }
}
