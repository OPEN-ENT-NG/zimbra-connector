package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.service.DbMailService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.*;

import static fr.wseduc.webutils.http.Renders.renderJson;


public class ReturnedMailService {

    private final DbMailService dbMailService;
    private final MessageService messageService;
    private UserService userService;
    private NotificationService notificationService;

    private EventBus eb;

    private static final Logger log = LoggerFactory.getLogger(ReturnedMailService.class);


    public ReturnedMailService(DbMailService dbMailService, MessageService messageService, UserService userService,
                               NotificationService notificationService, EventBus eb) {
        this.dbMailService = dbMailService;
        this.messageService = messageService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.eb = eb;
    }

    public void returnMails(UserInfos user, JsonObject body, HttpServerRequest request, Handler<Either<String, JsonObject>> result) {
        String messagesId = body.getString("id");
        String comment = body.getString("comment");
        // Etape 1 : récupérer le message à supprimer
        messageService.getMessage(messagesId, user, getMail -> {
            if (getMail.isRight()) {
                JsonObject mail = getMail.right().getValue();
                this.getReturnedMailsInfos(mail, comment, user, returnedMailsEvent -> {
                    if (returnedMailsEvent.isRight()) {
                        JsonObject returnedMail = returnedMailsEvent.right().getValue();
                        // Etape 3 : Insérer les infos du message renvoyé en base de données
                        dbMailService.insertReturnedMail(returnedMail, insertMailLog -> {
                            if (insertMailLog.isRight()) {
                                // Etape 4 : Récuperer les ADML pour leur envoyer la notification de validation
                                final List<String> recipients = new ArrayList<>();
                                userService.getLocalAdministrators(user.getStructures().get(0), admins -> {
                                    if (admins != null) {
                                        for (Object adminObj : admins) {
                                            JsonObject userAdml = (JsonObject) adminObj;
                                            recipients.add(userAdml.getString("id"));
                                        }
                                        // Etape 5 : Envoie de la notification aux ADML
                                        notificationService.sendReturnMailNotification(user, mail.getString("subject"), recipients, request, notifEvent -> {
                                            if (notifEvent.isRight()) {
                                                result.handle(new Either.Right<>(new JsonObject()));
                                            } else {
                                                result.handle(new Either.Left<>("[Zimbra] returnMails : Error while sending notifications to ADML"));
                                                log.error("[Zimbra] returnMails : Error while sending notifications to ADML");
                                            }
                                        });
                                    } else {
                                        result.handle(new Either.Left<>("[Zimbra] returnMails : Error while getting list of ADML"));
                                        log.error("[Zimbra] returnMails : Error while getting list of ADML");
                                    }
                                });
                            } else {
                                result.handle(new Either.Left<>("[Zimbra] returnMails : Error while inserting returned mails : " + insertMailLog.left().getValue()));
                                log.error("[Zimbra] returnMails : Error while inserting returned mails");
                            }
                        });
                    } else {
                        result.handle(new Either.Left<>("[Zimbra] returnMails : Error while getting returned mail infos : " + returnedMailsEvent.left().getValue()));
                        log.error("[Zimbra] returnMails : Error while getting returned mail infos");
                    }
                });
            } else {
                result.handle(new Either.Left<>("[Zimbra] returnMails : Error while getting returned mail infos : " + getMail.left().getValue()));
                log.error("[Zimbra] returnMails : Error while getting message info from Zimbra API");
            }
        });
    }

    public void getMailReturned(String structureId, Handler<Either<String, JsonArray>> result) {
        dbMailService.getMailReturned(structureId, event -> {
            if (event.isRight()) {
                result.handle(new Either.Right<>(event.right().getValue()));
            } else {
                result.handle(new Either.Left<>("[Zimbra] getMailReturned : Error while getting returned mail by id structure"));
                log.error("[Zimbra] getMailReturned : Error while getting returned mail by id structure");
            }
        });
    }

    public void getMailReturnedByStatut(String statut, Handler<Either<String, JsonArray>> result) {
        dbMailService.getMailReturnedByStatut(statut, event -> {
            if (event.isRight()) {
                result.handle(new Either.Right<>(event.right().getValue()));
            } else {
                result.handle(new Either.Left<>("[Zimbra] getMailReturned : Error while getting returned mail by id structure"));
                log.error("[Zimbra] getMailReturned : Error while getting returned mail by id structure");
            }
        });
    }

    public void getMailReturnedByMailsIdsAndUser(List<String> mailIds, JsonArray mails, String user_id, Handler<Either<String, JsonArray>> result) {
        dbMailService.getMailReturnedByMailsIdsAndUser(mailIds, user_id, returnedMailsEvent -> {
            if (returnedMailsEvent.isRight()) {
                JsonArray returnedMails = returnedMailsEvent.right().getValue();
                for (int i = 0; i < mails.size(); i++) {
                    for (int j = 0; j < returnedMails.size(); j++) {
                        if (mails.getJsonObject(i).getString("id").equals(returnedMails.getJsonObject(j).getString("mail_id"))) {
                            mails.getJsonObject(i).put("returned", returnedMails.getJsonObject(j).getString("statut"));
                        }
                    }
                }
                result.handle(new Either.Right<>(mails));
            } else {
                result.handle(new Either.Left<>("[Zimbra] getMailReturned : Error while getting returned mail by id structure"));
                log.error("[Zimbra] getMailReturned : Error while getting returned mail by id structure");
            }
        });
    }

    public void deleteMessages(List<String> returnedMailsIds, Handler<Either<String, JsonArray>> result) {
        // Etape 1 : Récupérer toute les infos sur les mails à delete dans la base
        dbMailService.getMailReturnedByIds(returnedMailsIds, returnedMailEvent -> {
            if (returnedMailEvent.isRight()) {
                JsonArray returnedMails = returnedMailEvent.right().getValue();
                JsonArray returnedMailsStatut = new JsonArray();
                JsonArray returnedMailsInProgress = new JsonArray();
                for (int i = 0; i < returnedMails.size(); i++) {
                    JsonObject returnedMail = new JsonObject()
                            .put("id", returnedMails.getJsonObject(i).getLong("id"))
                            .put("statut", "PROGRESS");
                    returnedMailsInProgress.add(returnedMail);
                }
                this.updateStatus(returnedMailsInProgress, updateStatutInProgressEvent -> {
                    if (updateStatutInProgressEvent.isRight()) {
                        this.recursiveDeleteMailLoop(returnedMails, returnedMailsStatut, 0, event -> {
                            if (event.isRight()) {
                                result.handle(new Either.Right<>(returnedMailsStatut));
                            } else {
                                result.handle(new Either.Left<>("All mails not deleted"));
                            }
                        });
                    } else {
                        result.handle(new Either.Left<>("[Zimbra] deleteMessages: Error while updating returned mail statut to in progress : " +
                                updateStatutInProgressEvent.left().getValue()));
                        log.error("[Zimbra] deleteMessages: Error while updating returned mail statut to in progress : " +
                                updateStatutInProgressEvent.left().getValue());
                    }
                });
            } else {
                result.handle(new Either.Left<>("[Zimbra] deleteMessages: Error while getting returned mails by ids : " +
                        returnedMailEvent.left().getValue()));
            }
        });
    }


    private void recursiveDeleteMailLoop(JsonArray returnedMails, JsonArray returnedMailsStatut, int index, Handler<Either<String, JsonObject>> result) {
        this.addDeleteFutures(returnedMails.getJsonObject(index), event -> {
            Long returned_mail_id = returnedMails.getJsonObject(index).getLong("id");
            // TODO : mettre cron au cas ou prod crash ou relance donc Zimbra.java
            if (event.isRight()) {
                returnedMailsStatut
                        .add(new JsonObject()
                                .put("id", returned_mail_id)
                                .put("statut", "REMOVED"));
                // Etape 3 : Une fois que nous avons parcouru tout les mails, on supprime les mails de la boîte de reception et on met à jour leur statut
            } else {
                returnedMailsStatut
                        .add(new JsonObject()
                                .put("id", returned_mail_id)
                                .put("statut", "ERROR"));
            }
            if (index == returnedMails.size() - 1) {
                this.updateStatus(returnedMailsStatut, result);
            } else {
                this.recursiveDeleteMailLoop(returnedMails, returnedMailsStatut, index + 1, result);
            }
            ;
        });
    }

    private void addDeleteFutures(JsonObject returnedMail, Handler<Either<String, JsonObject>> result) {
        JsonArray userIds = new JsonArray(returnedMail.getString("recipient"));
        int j = 0;
        this.recursiveDeleteMail(userIds, j, returnedMail, deleteEvent -> {
            if (deleteEvent.isRight()) {
                result.handle(new Either.Right<>(deleteEvent.right().getValue()));
            } else {
                result.handle(new Either.Left<>("[Zimbra] addDeleteFutures: Error while deleting mails : " +
                        deleteEvent.left().getValue()));
            }
        });
    }

    private void recursiveDeleteMail(JsonArray userIds, int indexUser, JsonObject returnedMail, Handler<Either<String, JsonObject>> result) {
        this.deleteMail(userIds.getJsonObject(indexUser), returnedMail, deleteEvent -> {
            if (deleteEvent.isRight()) {
                if (indexUser == userIds.size() - 1) {
                    result.handle(new Either.Right<>(deleteEvent.right().getValue()));
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    this.recursiveDeleteMail(userIds, indexUser + 1, returnedMail, result);
                }
            } else {
                result.handle(new Either.Left<>("[Zimbra] recursiveDeleteMail: Error while deleting mail : " +
                        deleteEvent.left().getValue()));
            }
        });
    }


    private void updateStatus(JsonArray returnedMailsStatuts, Handler<Either<String, JsonObject>> result) {
        dbMailService.updateStatut(returnedMailsStatuts, updateStatutEvent -> {
            if (updateStatutEvent.isRight()) {
                for(int i = 0; i < returnedMailsStatuts.size(); i++) {
                    returnedMailsStatuts.getJsonObject(i).put("date", updateStatutEvent.right().getValue().getJsonObject(0).getString("date"));
                }
                result.handle(new Either.Right<>(new JsonObject()));
            } else {
                result.handle(new Either.Left<>("[Zimbra] updateStatus: Error while updating status of returned mails : " +
                        updateStatutEvent.left().getValue()));
                log.error("[Zimbra] updateStatus: Error while updating status of returned mails : ",
                        updateStatutEvent.left().getValue());
            }
        });
    }

    private void deleteMail(JsonObject userInfos, JsonObject returnedMail, Handler<Either<String, JsonObject>> handler) {
        // Etape 1 : on récupère les infos de l'utilisateur
        String userId = userInfos.getString("id");
        UserUtils.getUserInfos(eb, userId, user -> {
            messageService.retrieveMailFromZimbra(returnedMail, userInfos, mailIds -> {
                if (mailIds.isRight()) {
                    if (mailIds.right().getValue().size() > 0) {
                        List<String> ids = mailIds.right().getValue();
                        this.deleteMailFromZimbra(ids, user, handler);
                    } else {
                        handler.handle(new Either.Right<>(new JsonObject()));
                    }
                }
            });
        });
    }

    private void deleteMailFromZimbra(List<String> ids, UserInfos user, Handler<Either<String, JsonObject>> handler) {
        // Etape 4 : On déplace le mail à supprimer vers la corbeille
        messageService.moveMessagesToFolder(ids, FrontConstants.FOLDER_TRASH, user,
                moveToTrash -> {
                    if (moveToTrash.isRight()) {
                        // Etape 5 : On supprime définitivement le message de la boite de réception
                        messageService.deleteMessages(ids, user, event -> {
                            if (event.isRight()) {
                                handler.handle(new Either.Right<>(event.right().getValue()));
                            } else {
                                handler.handle(new Either.Left<>("[Zimbra] getReturnedMailsInfos : Error while deleting mails : " + event.left().getValue()));
                            }
                        });
                    } else {
                        log.error("Erreur lors du déplacement vers la corbeille du mail " + ids.get(0));
                    }
                });
    }

    private void getReturnedMailsInfos(JsonObject mail, String comment, UserInfos
            user, Handler<Either<String, JsonObject>> result) {
        String mail_date = new java.text.SimpleDateFormat("MM/dd/yyyy")
                .format(new java.util.Date(mail.getLong("date")));
        JsonObject returnedMail = new JsonObject();
        // Etape 2 : récupérer la liste des utilisateurs des groupes
        JsonArray recipients = mail.getJsonArray("to").addAll(mail.getJsonArray("cc")).addAll(mail.getJsonArray("bcc"));
        userService.getUsers(recipients, recipients, usersFromGroup -> {
            if (usersFromGroup.isRight()) {
                Set<JsonObject> setUser = new HashSet<>();
                JsonArray usersGroup = usersFromGroup.right().getValue();
                for (int i = 0; i < usersGroup.size(); i++) {
                    JsonObject userInfo = new JsonObject()
                            .put("id", usersGroup.getJsonObject(i).getString("id"))
                            .put("mail", usersGroup.getJsonObject(i).getString("login") + "@" + Zimbra.domain);
                    setUser.add(userInfo);
                }
                JsonArray to = new JsonArray(Arrays.asList(setUser.toArray()));
                returnedMail
                        .put("subject", mail.getString("subject"))
                        .put("userId", user.getUserId())
                        .put("userName", user.getLastName() + " " + user.getFirstName())
                        .put("userMail", user.getLogin() + "@" + Zimbra.domain)
                        .put("mailId", mail.getString("id"))
                        .put("structureId", user.getStructures().get(0))
                        .put("nb_messages", to.size())
                        .put("to", to)
                        .put("mail_date", mail_date)
                        .put("comment", comment);
                result.handle(new Either.Right<>(returnedMail));
            } else {
                result.handle(new Either.Left<>("[Zimbra] getReturnedMailsInfos : Error while getting users id from group id"));
                log.error("[Zimbra] getReturnedMailsInfos : Error while getting users id from group id");
            }

        });
    }

    public void renderReturnedMail(HttpServerRequest request, UserInfos user, Either<String, JsonArray> event) {
        List<String> mailIds = new ArrayList<>();
        JsonArray mails = event.right().getValue();
        for (int i = 0; i < mails.size(); i++) {
            mailIds.add(mails.getJsonObject(i).getString("id"));
        }
        if (user != null && mailIds.size() > 0) {
            this.getMailReturnedByMailsIdsAndUser(mailIds, mails, user.getUserId(), returnedMailsEvent -> {
                if (returnedMailsEvent.isRight()) {
                    renderJson(request, returnedMailsEvent.right().getValue());
                } else {
                    renderJson(request, mails);
                }
            });
        } else {
            renderJson(request, mails);
        }
    }
}