/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.controllers;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.filters.AccessibleDocFilter;
import fr.openent.zimbra.filters.DevLevelFilter;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.RequestHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.ModuleConstants;
import fr.openent.zimbra.security.ExpertAccess;
import fr.openent.zimbra.service.data.SearchService;
import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.service.synchro.AddressBookService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.cache.Cache;
import org.entcore.common.cache.CacheOperation;
import org.entcore.common.cache.CacheScope;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.Config;
import org.vertx.java.core.http.RouteMatcher;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static fr.openent.zimbra.model.constant.FrontConstants.MESSAGE_ID;
import static fr.openent.zimbra.model.constant.ZimbraConstants.ZIMBRA_ID_STRUCTURE;
import static fr.openent.zimbra.model.constant.ZimbraConstants.ZIMBRA_MAIL;
import static fr.wseduc.webutils.request.RequestUtils.bodyToJson;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

public class ZimbraController extends BaseController {

    private ConfigManager appConfig;
    private UserService userService;
    private FolderService folderService;
    private AttachmentService attachmentService;
    private MessageService messageService;
    private ReturnedMailService returnedMailService;
    private SignatureService signatureService;
    private SearchService searchService;
    private ExpertModeService expertModeService;
    private RedirectionService redirectionService;
    private FrontPageService frontPageService;
    private AddressBookService addressBookService;
    private Storage storage;
    private EventStore eventStore;
    private WorkspaceHelper workspaceHelper;


    private enum ZimbraEvent {ACCESS, CREATE}

    private static final String WORKSPACE_BUS_ADDRESS = "org.entcore.workspace";
    private static final Logger log = LoggerFactory.getLogger(ZimbraController.class);

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);

        eventStore = EventStoreFactory.getFactory().getEventStore(Zimbra.class.getSimpleName());
        storage = new StorageFactory(vertx, config).getStorage();


        ServiceManager serviceManager = ServiceManager.init(vertx, eb, pathPrefix);

        this.appConfig = Zimbra.appConfig;
        this.searchService = serviceManager.getSearchService();
        this.userService = serviceManager.getUserService();
        this.folderService = serviceManager.getFolderService();
        this.signatureService = serviceManager.getSignatureService();
        this.messageService = serviceManager.getMessageService();
        this.attachmentService = serviceManager.getAttachmentService();
        this.expertModeService = serviceManager.getExpertModeService();
        this.redirectionService = serviceManager.getRedirectionService();
        this.frontPageService = serviceManager.getFrontPageService();
        this.addressBookService = serviceManager.getAddressBookService();
        this.returnedMailService = serviceManager.getReturnedMailService();
        this.workspaceHelper = new WorkspaceHelper(eb,storage);

    }

    @Get("zimbra")
    @SecuredAction("zimbra.view")
    public void view(HttpServerRequest request) {
        if (appConfig.isForceExpertMode()) {
            redirect(request, appConfig.getHost(), "/zimbra/preauth");
        } else {
            UserUtils.getUserInfos(eb, request, user -> {
                if (user != null) {
                    frontPageService.getFrontPageInfos(user, result -> {
                        if (result.failed()) {
                            renderView(request, null, "error.html", null);
                        } else {
                            renderView(request, result.result());
                        }
                    });
                } else {
                    unauthorized(request);
                }
            });
        }
        eventStore.createAndStoreEvent(ZimbraEvent.ACCESS.name(), request);
    }

    @Get("zimbra/json")
    @SecuredAction(value = "zimbra.view", type = ActionType.AUTHENTICATED)
    public void jsonInfos(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                frontPageService.getFrontPageInfos(user, result -> {
                    if (result.failed()) {
                        badRequest(request);
                    } else {
                        result.result().put("folders", new JsonArray(result.result().getString("folders")));
                        renderJson(request, result.result());
                    }
                });
            } else {
                unauthorized(request);
            }
        });
        eventStore.createAndStoreEvent(ZimbraEvent.ACCESS.name(), request);
    }

    /**
     * Redirect the connected user to an authenticated session of Zimbra
     *
     * @param request http request containing user info
     */
    @Get(ModuleConstants.URL_PREAUTH)
    @SecuredAction("zimbra.expert")
    public void preauth(HttpServerRequest request) {
        final String parameters = request.params().get("params");
        getUserInfos(eb, request, user -> {
            if (user != null) {
                try {
                    if (appConfig.getPurgeEmailedContacts()) {
                        addressBookService.purgeEmailedContacts(user);
                    }
                    String location = expertModeService.getPreauthUrl(user);
                    if (parameters != null && !parameters.isEmpty()) {
                        location += parameters;
                    }
                    redirect(request, appConfig.getZimbraUri(), location);
                    if (appConfig.isEnableAddressBookSynchro()) {
                        userService.syncAddressBookAsync(user);
                    }
                    eventStore.createAndStoreEvent(ZimbraEvent.ACCESS.name(), request);
                } catch (IOException e) {
                    renderError(request);
                }
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Trigger sync of user adressbook.
     * if all conditions are OK, sync is really launched
     *
     * @param request http request containing user info
     */
    @Get("user/syncaddressbook")
    @SecuredAction(value = "zimbra.view", type = ActionType.AUTHENTICATED)
    public void oauth(HttpServerRequest request) {
        getUserInfos(eb, request, user -> {
            if (user != null) {
                if (appConfig.isEnableAddressBookSynchro()) {
                    userService.syncAddressBookAsync(user);
                }
                eventStore.createAndStoreEvent(ZimbraEvent.ACCESS.name(), request);
                renderJson(request, new JsonObject());
            } else {
                unauthorized(request);
            }
        });
    }

    @Get("/user/mail")
    @SecuredAction("zimbra.user.info.mail")
    public void getMailAddressById(final HttpServerRequest request) {
        final String userId = request.params().get("userId");
        userService.getUserAddressMail(userId, userInfos -> {
            if (userInfos.failed()) {
                renderJson(request, new JsonObject().put(ZIMBRA_MAIL, "No user found"));
                log.error("[Zimbra] getMailAddressById : User id not found in database");
            } else {
                String mail = userInfos.result();
                renderJson(request, new JsonObject().put(ZIMBRA_MAIL, mail));
            }
        });
    }


    @Get("writeto")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void writeTo(final HttpServerRequest request) {
        final String id = request.params().get(MESSAGE_ID);
        final String name = request.params().get(Field.NAME);
        final String type = request.params().get("type");
        getUserInfos(eb, request, user -> {
            if (user != null) {
                redirectionService.getRedirectionUrl(user.getUserId(), id, name, type, redirectObject -> {
                    renderJson(request, redirectObject);
                });
            } else {
                unauthorized(request);
            }

        });
    }

    /**
     * Create a Draft email
     * In case of success, return Json Object :
     * {
     * MESSAGE_ID : "new-zimbra-email-id"
     * }
     *
     * @param request http request containing info
     *                Users infos
     *                In-Reply-To : Id of the message being replied to
     *                reply : type of reply (R for reply, F for Forward)
     *                body : message body
     *                subject : message subject
     *                to : id of each recipient
     *                cc : id of each cc recipient
     */
    @Post("draft")
    @SecuredAction("zimbra.create.draft")
    @ResourceFilter(DevLevelFilter.class)
    public void createDraft(final HttpServerRequest request) {
        if (Zimbra.appConfig.isActionBlocked(ConfigManager.UPDATE_ACTION)) {
            badRequest(request);
            return;
        }
        final String parentMessageId = request.params().get("In-Reply-To");
        String replyType = request.params().get("reply");

        if (replyType != null && !FrontConstants.UNDEFINED.equalsIgnoreCase(replyType)
                && !FrontConstants.REPLYTYPE_REPLY.equalsIgnoreCase(replyType)
                && !FrontConstants.REPLYTYPE_FORWARD.equalsIgnoreCase(replyType)) {
            log.warn("CreateDraft - Unknown reply type : " + replyType);
            replyType = null;
        }
        final String reply = replyType;
        getUserInfos(eb, request, user -> {
            if (user != null) {
                bodyToJson(request, message ->
                        messageService.saveDraft(message, user, null, parentMessageId, reply,
                                defaultResponseHandler(request))
                );
            } else {
                unauthorized(request);
            }
        });
    }


    /**
     * Update a Draft email
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                :id : draft Id
     *                Users infos
     *                body : message body
     *                subject : message subject
     *                to : id of each recipient
     *                cc : id of each cc recipient
     */
    @Put("draft/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void updateDraft(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);
        if (messageId == null || messageId.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        getUserInfos(eb, request, user -> {
            if (user != null) {
                bodyToJson(request, message ->
                        messageService.saveDraft(message, user, messageId, null, null,
                                defaultResponseHandler(request))
                );
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Send an email
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                body : message body
     *                subject : message subject
     *                to : id of each recipient
     *                cc : id of each cc recipient
     */
    @Post("send")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void send(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);
        final String parentMessageId = request.params().get("In-Reply-To");
        getUserInfos(eb, request, user -> {
            if (user != null) {
                bodyToJson(request, message -> messageService.sendMessage(messageId, message, user, parentMessageId, event -> {
                    if (event.isRight()) {
                        eventStore.createAndStoreEvent(ZimbraEvent.CREATE.name(), request);
                        Renders.renderJson(request, event.right().getValue(), 200);
                    } else {
                        JsonObject error = (new JsonObject()).put(Field.ERROR, event.left().getValue());
                        Renders.renderJson(request, error, 400);
                    }
                }));
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Return an email
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                id : message id to remove
     *                comment : comment of the removal
     */
    @Put("/return")
    @SecuredAction("zimbra.return.mail")
    public void returnMails(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            getUserInfos(eb, request, user -> {
                if (user != null) {
                    returnedMailService.returnMails(user, body, request, returnMailEvent -> {
                        if (returnMailEvent.isRight()) {
                            renderJson(request, returnMailEvent.right().getValue());
                        } else {
                            badRequest(request);
                            log.error("[Zimbra] returnMails : Failed returning mails");
                        }
                    });
                } else {
                    unauthorized(request);
                }
            });
        });
    }

    @Get("/return/list")
    @SecuredAction("zimbra.return.list")
    public void getReturnedMails(final HttpServerRequest request) {
        String structureId = request.params().get(ZIMBRA_ID_STRUCTURE);
        returnedMailService.getMailReturned(structureId, returnedMails -> {
            if (returnedMails.isRight()) {
                renderJson(request, returnedMails.right().getValue());
            } else {
                badRequest(request);
                log.error("[Zimbra]getReturnedMails: Collecting returned mail failed : " + returnedMails.left().getValue());
            }
        });
    }

    @Delete("/return/delete/:id")
    @SecuredAction("zimbra.return.delete")
    public void deleteReturnedMails(final HttpServerRequest request) {
        final String returnedMailId = request.params().get(MESSAGE_ID);
        returnedMailService.deleteMailReturned(returnedMailId, deleteEvent -> {
            if (deleteEvent.isRight()) {
                renderJson(request, deleteEvent.right().getValue().getJsonObject(0));
            } else {
                badRequest(request);
                log.error("[Zimbra]deleteReturnedMails: Deleting returned mail failed : " + deleteEvent.left().getValue());
            }
        });
    }

    /**
     * Delete definitively messages by Subject, Date, and User
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                Body :
     *                [
     *                {MESSAGE_ID : "idReturnedMail"},
     *                {MESSAGE_ID : "idReturnedMail"},
     *                {MESSAGE_ID : "idReturnedMail"}
     *                ]
     */
    @Delete("delete/sent")
    @SecuredAction("zimbra.return.mail.delete")
    public void deleteSentEmail(final HttpServerRequest request) {
        final List<String> returnedMailsIds = request.params().getAll(MESSAGE_ID);
        deleteMailByIds(request, returnedMailsIds);
    }

    public void deleteMailByIds(HttpServerRequest request, List<String> returnedMailsIds) {
        returnedMailService.deleteMessages(returnedMailsIds, deleteMailEvent -> {
            if (deleteMailEvent.isRight()) {
                renderJson(request, deleteMailEvent.right().getValue());
            } else {
                badRequest(request);
                log.error("[Zimbra] returnMails : Failed deleting mails");
            }
        });
    }

    @Get("root-folder")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getRootFolder(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> folderService.getRootFolder(user, res -> {
            if (res.failed()) renderError(request);
            else renderJson(request, res.result().toJson().getJsonArray("folders"));
        }));
    }

    /**
     * List messages in folders
     * If unread is true, filter only unread messages.
     * If search is set, must be at least 3 characters. Then filter by search.
     *
     * @param request http request containing info
     *                Users infos
     *                folder name or id
     *                unread ? filter only unread messages
     *                search ? filter only searched messages
     */
    @Get("list")
    @SecuredAction(value = "zimbra.list", type = ActionType.AUTHENTICATED)
    public void list(final HttpServerRequest request) {
        final String folder = request.params().get("folder");
        final String unread = request.params().get("unread");
        final String search = request.params().get("search");
        if (search != null && search.trim().length() < 3) {
            badRequest(request);
            return;
        }
        final String pageStr = Utils.getOrElse(request.params().get("page"), "0", false);
        if (folder == null || folder.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        getUserInfos(eb, request, user -> {
            if (user != null) {
                int page;
                try {
                    page = Integer.parseInt(pageStr);
                } catch (NumberFormatException e) {
                    page = 0;
                }
                boolean b = false;
                if (unread != null && !unread.isEmpty()) {
                    b = Boolean.parseBoolean(unread);
                }
                messageService.listMessages(folder, b, user, page, search, event -> {
                    if (event.isRight()) {
                        if (!folder.equals("/Sent")) {
                            renderJson(request, event.right().getValue());
                        } else {
                            returnedMailService.renderReturnedMail(request, user, event);
                        }
                    } else {
                        log.error(String.format("[Zimbra@ZimbraController::listMessages] failed to listMessages: %s", event.left().getValue()));
                        badRequest(request);
                    }

                });
            } else {
                unauthorized(request);
            }
        });
    }

    @Post("message/:id/upload/:idAttachment")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AccessibleDocFilter.class)
    public void uploadAttachment(final HttpServerRequest request) {
        final String id = request.params().get(Field.ID);
        final String idAttachment = request.params().get("idAttachment");
        if (id == null || idAttachment == null) {
            log.error("[Zimbra] uploadAttachment : Missing parameters");
            badRequest(request);
            return;
        }
        attachmentService.getDocument(eb, idAttachment, event -> {
            if (event.isRight()) {
                JsonObject document = event.right().getValue();
                String file = document.getString("file");
                storage.readStreamFile(file, buffer -> {
                    if (buffer == null) {
                        notFound(request);
                    } else {
                        UserUtils.getUserInfos(eb, request, user -> {
                            if (user == null) {
                                unauthorized(request);
                                return;
                            }
                            attachmentService.addAttachment(id, user, buffer, document, defaultResponseHandler(request));
                        });
                    }
                });
            } else {
                badRequest(request);
                log.error("[Zimbra] uploadAttachment : Failed getDocument - " + event.left().getValue());
            }
        });
    }


    /**
     * Count number of messages in a folder.
     * If unread is true, filter only unread messages.
     * In case of success, return Json :
     * {
     * data: count // number of (unread) messages
     * }
     *
     * @param request http request containing info
     *                Users infos
     *                folder name or id
     *                unread ? filter only unread messages
     */
    @Get("count/:folder")
    @Cache(value = "/zimbra/count/INBOX", scope = CacheScope.USER, ttlAsMinutes = 15)
    @SecuredAction(value = "zimbra.count", type = ActionType.AUTHENTICATED)
    public void count(final HttpServerRequest request) {
        final String folder = request.params().get("folder");
        final String unread = request.params().get("unread");
        if (folder == null || folder.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        getUserInfos(eb, request, user -> {
            if (user != null) {
                Boolean b = null;
                if (unread != null && !unread.isEmpty()) {
                    b = Boolean.valueOf(unread);
                }
                folderService.countMessages(folder, b, user, defaultResponseHandler(request));
            } else {
                unauthorized(request);
            }
        });
    }

    @Get("visible")
    @SecuredAction(value = "zimbra.visible", type = ActionType.AUTHENTICATED)
    public void visible(final HttpServerRequest request) {
        getUserInfos(eb, request, user -> {
            if (user != null) {
                searchService.findVisibleRecipients(user, I18n.acceptLanguage(request), request.params().get("search"),
                        defaultResponseHandler(request));
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Get a message content
     * Form of Front message returned :
     * {
     * MESSAGE_ID : "message_id",
     * "subject" : "message_subject",
     * "from" : "user_id_from",
     * "to" : [
     * "user_id_to",
     * ],
     * "cc" : [
     * "user_id_cc",
     * ],
     * "display_names" : [
     * "user_id",
     * "user_display_name",
     * ],
     * "date" : datesent,
     * "unread" : boolean_unread,
     * "attachments" : [{
     * MESSAGE_ID : "attachment_id",
     * "filename" : "attachment_filename",
     * "contentType" : "attachment_type",
     * "size" : "attachment_size"
     * },
     * ]
     * <p>
     * }
     *
     * @param request http request containing info
     *                id : message id
     *                Users infos
     */
    @Get("message/:id")
    @Cache(value = "/zimbra/count/INBOX", scope = CacheScope.USER, operation = CacheOperation.INVALIDATE)
    @SecuredAction(value = "zimbra.message", type = ActionType.AUTHENTICATED)
    public void getMessage(final HttpServerRequest request) {
        final String id = request.params().get(MESSAGE_ID);
        if (id == null || id.trim().isEmpty()) {
            badRequest(request);
            return;
        }
        getUserInfos(eb, request, user -> {
            if (user != null) {
                messageService.getMessage(id, user, defaultResponseHandler(request));
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Trash messages = move messages to trash
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                folder id
     *                Body :
     *                [
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                ]
     */
    @Put("trash")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void trash(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }

            RequestHelper.bodyToJson(request, body -> {
                final List<String> messageIds = body.getJsonArray(Field.ID, new JsonArray()).getList().isEmpty() ?
                        request.params().getAll(MESSAGE_ID) :
                        body.getJsonArray(Field.ID, new JsonArray()).getList();

                if (messageIds == null || messageIds.isEmpty()) {
                    badRequest(request);
                    return;
                }

                messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_TRASH, user)
                        .onSuccess(res -> Renders.renderJson(request, new JsonObject(), 200))
                        .onFailure(err -> {
                            JsonObject error = (new JsonObject()).put(Field.ERROR, err.getMessage());
                            log.error(String.format("[Zimbra@ZimbraController::moveMessagesToFolder] failed to moveMessagesToFolder: %s", err.getMessage()));
                            Renders.renderJson(request, error, 400);
                        });
            });
        });
    }

    /**
     * Restore = Move messages from trash to inbox.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                Body :
     *                [
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                ]
     */
    @Put("restore")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void restore(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }

            RequestHelper.bodyToJson(request, body -> {
                final List<String> messageIds = body.getJsonArray(Field.ID, new JsonArray()).getList().isEmpty() ?
                        request.params().getAll(MESSAGE_ID) :
                        body.getJsonArray(Field.ID, new JsonArray()).getList();

                if (messageIds == null || messageIds.isEmpty()) {
                    badRequest(request);
                    return;
                }

                messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_INBOX, user)
                        .onSuccess(res -> Renders.renderJson(request, new JsonObject(), 200))
                        .onFailure(err -> {
                            JsonObject error = (new JsonObject()).put(Field.ERROR, err.getMessage());
                            log.error(String.format("[Zimbra@ZimbraController::moveMessagesToFolder] failed to moveMessagesToFolder: %s", err.getMessage()));
                            Renders.renderJson(request, error, 400);
                        });
            });
        });
    }

    /**
     * Delete definitively messages from trash
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                Body :
     *                [
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                ]
     */
    @Delete("delete")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void delete(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }

            RequestHelper.bodyToJson(request, body -> {
                final List<String> messageIds = body.getJsonArray(Field.ID, new JsonArray()).getList().isEmpty() ?
                        request.params().getAll(MESSAGE_ID) :
                        body.getJsonArray(Field.ID, new JsonArray()).getList();

                if (messageIds == null || messageIds.isEmpty()) {
                    badRequest(request);
                    return;
                }

                messageService.deleteMessages(messageIds, user, true)
                        .onSuccess(res -> Renders.renderJson(request, new JsonObject(),200))
                        .onFailure(err -> {
                            JsonObject error = (new JsonObject()).put(Field.ERROR, err.getMessage());
                            log.error(String.format("[Zimbra@ZimbraController::deleteMessages] failed to deleteMessages: %s", err.getMessage()));
                            Renders.renderJson(request, error, 400);
                        });
            });
        });
    }

    /**
     * Empty trash folder
     *
     * @param request http request containing info
     *                Users infos
     */
    @Delete("emptyTrash")
    @SecuredAction(value = "zimbra.empty.trash", type = ActionType.AUTHENTICATED)
    @ResourceFilter(DevLevelFilter.class)
    public void emptyTrash(final HttpServerRequest request) {
        getUserInfos(eb, request, user -> {
            if (user != null) {
                messageService.emptyTrash(user, defaultResponseHandler(request));
            } else {
                unauthorized(request);
            }
        });
    }

    /**
     * Mark messages as unread / read
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                Body :
     *                id :
     *                [
     *                {"idmessage"},
     *                {"idmessage"},
     *                {"idmessage"},
     *                ]
     *                unread : boolean
     */
    @Post("toggleUnread")
    @Cache(value = "/zimbra/count/INBOX", scope = CacheScope.USER, operation = CacheOperation.INVALIDATE)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void toggleUnread(final HttpServerRequest request) {
        final List<String> ids = request.params().getAll(MESSAGE_ID);
        final String unread = request.params().get("unread");

        if (ids == null || ids.isEmpty() || unread == null || (!unread.equals("true") && !unread.equals(Field.FALSE))) {
            badRequest(request);
            return;
        }
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                messageService.toggleUnreadMessages(ids, Boolean.parseBoolean(unread), user, defaultResponseHandler(request));
            } else {
                unauthorized(request);
            }
        });

    }

    //Get max folder depth
    @Get("max-depth")
    @SecuredAction(value = "zimbra.max.depth", type = ActionType.AUTHENTICATED)
    public void getMaxDepth(final HttpServerRequest request) {
        renderJson(request, new JsonObject().put("max-depth",
                Config.getConf().getInteger("max-folder-depth", Zimbra.DEFAULT_FOLDER_DEPTH)));
    }

    /**
     * List folders at root level, under parent folder, or trashed folders at depth 1 only.
     * In case of success, return Json Array of folders :
     * [
     * {
     * MESSAGE_ID : "folder-id",
     * "parent_id : "parent-folder-id" or null,
     * "user_id" : "id of owner of folder",
     * "name" : "folder-name",
     * "depth" : "folder-depth",
     * "trashed" : "is-folder-trashed"
     * }
     * ]
     *
     * @param request http request containing info
     *                Users infos
     *                parent id (optional)
     *                trash ? ignore parent id and get trashed folders
     */
    @Get("folders/list")
    @SecuredAction(value = "zimbra.folder.list", type = ActionType.AUTHENTICATED)
    public void listFolders(final HttpServerRequest request) {
        final String parentId = request.params().get("parentId");
        final String listTrash = request.params().get("trash");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            Boolean trashed = (listTrash != null);
            folderService.listFolders(parentId, trashed, user, arrayResponseHandler(request));
        });

    }

    /**
     * Create a new folder at root level or inside a user folder.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                Body :
     *                createFolder.json
     */
    @Post("folder")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void createFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "createFolder", body -> {
                final String name = body.getString(Field.NAME);
                final String parentId = body.getString("parentId", null);

                if (name == null || name.trim().length() == 0) {
                    badRequest(request);
                    return;
                }
                folderService.createFolder(name, parentId, user, defaultResponseHandler(request, 201));
            });
        });
    }

    /**
     * Update a folder.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                folder id
     *                Body :
     *                updateFolder.json
     */
    @Put("folder/:folderId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void updateFolder(final HttpServerRequest request) {
        final String folderId = request.params().get("folderId");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "updateFolder", body -> {
                final String name = body.getString(Field.NAME);

                if (name == null || name.trim().length() == 0) {
                    badRequest(request);
                    return;
                }
                folderService.updateFolder(folderId, name, user, defaultResponseHandler(request, 200));
            });
        });

    }

    /**
     * Move messages into a folder.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                folder id
     *                Body :
     *                [
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                ]
     */
    @Put("move/userfolder/:folderId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void move(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }

            RequestHelper.bodyToJson(request, body -> {
                final String folderId = request.params().get("folderId");
                final List<String> messageIds = body.getJsonArray(Field.ID, new JsonArray()).getList().isEmpty() ?
                        request.params().getAll(MESSAGE_ID) :
                        body.getJsonArray(Field.ID, new JsonArray()).getList();

                if (messageIds == null || messageIds.isEmpty()) {
                    badRequest(request);
                    return;
                }

                messageService.moveMessagesToFolder(messageIds, folderId, user)
                        .onSuccess(res -> Renders.renderJson(request, new JsonObject(), 200))
                        .onFailure(err -> {
                            JsonObject error = (new JsonObject()).put(Field.ERROR, err.getMessage());
                            log.error(String.format("[Zimbra@ZimbraController::moveMessagesToFolder] failed to moveMessagesToFolder: %s", err.getMessage()));
                            Renders.renderJson(request, error, 400);
                        });
            });
        });
    }

    /**
     * Move messages into a system folder (restore emails to inbox).
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                folder id
     *                Body :
     *                [
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                {MESSAGE_ID : "idmessage"},
     *                ]
     */
    @Put("move/root")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void rootMove(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }

            RequestHelper.bodyToJson(request, body -> {
                final List<String> messageIds = body.getJsonArray(Field.ID, new JsonArray()).getList().isEmpty() ?
                        request.params().getAll(MESSAGE_ID) :
                        body.getJsonArray(Field.ID, new JsonArray()).getList();

                if (messageIds == null || messageIds.isEmpty()) {
                    badRequest(request);
                    return;
                }

                messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_INBOX, user)
                        .onSuccess(res -> Renders.renderJson(request, new JsonObject(), 200))
                        .onFailure(err -> {
                            JsonObject error = (new JsonObject()).put(Field.ERROR, err.getMessage());
                            log.error(String.format("[Zimbra@ZimbraController::moveMessagesToFolder] failed to moveMessagesToFolder: %s", err.getMessage()));
                            Renders.renderJson(request, error, 400);
                        });
            });
        });
    }

    /**
     * Trash a folder.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                "folderId" : "folder Id"
     */
    @Put("folder/trash/:folderId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void trashFolder(final HttpServerRequest request) {
        final String folderId = request.params().get("folderId");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            folderService.trashFolder(folderId, user, defaultResponseHandler(request));
        });

    }

    /**
     * Restore a trashed folder in Inbox.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                folder id
     */
    @Put("folder/restore/:folderId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void restoreFolder(final HttpServerRequest request) {
        final String folderId = request.params().get("folderId");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            folderService.restoreFolder(folderId, user, defaultResponseHandler(request));
        });
    }

    /**
     * Delete a trashed folder.
     * In case of success, return empty Json Object.
     *
     * @param request http request containing info
     *                Users infos
     *                folder id
     */
    @Delete("folder/:folderId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void deleteFolder(final HttpServerRequest request) {
        final String folderId = request.params().get("folderId");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            folderService.deleteFolder(folderId, user, defaultResponseHandler(request));
        });
    }

    /**
     * Post an new attachment to a drafted message.
     * In case of success, return Json Object :
     * {
     * MESSAGE_ID : "new-zimbra-attachment-id"
     * }
     *
     * @param request http request
     */
    @Post("message/:id/attachment")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void postAttachment(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }

            request.pause();
            attachmentService.addAttachmentBuffer(messageId, user, request, defaultResponseHandler(request));
        });
    }

    /**
     * Download an attachment
     *
     * @param request http request containing info
     *                Users infos
     *                id : message Id
     *                attachmentId : attachment Id
     */
    @Get("message/:id/attachment/:attachmentId")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getAttachment(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);
        final String attachmentId = request.params().get("attachmentId");
        if (messageId == null || messageId.isEmpty() || attachmentId == null || attachmentId.isEmpty()) {
            notFound(request, "invalid.file.id");
            return;
        }
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            attachmentService.getAttachmentToComputer(messageId, attachmentId, user,false, request,
                    defaultResponseHandler(request));
        });


    }

    /**
     * Download an attachment in workspace
     *
     * @param request http request containing info
     *                Users infos
     *                id : message Id
     *                attachmentId : attachment Id
     */
    @Get("message/:id/attachment/:attachmentId/workspace")
    @SecuredAction("zimbra.downloadInWorkspace")
    public void getAttachmentToWorkspace(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);
        final String attachmentId = request.params().get("attachmentId");
        if (messageId == null || messageId.isEmpty() || attachmentId == null || attachmentId.isEmpty()) {
            notFound(request, "invalid.file.id");
            return;
        }
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            attachmentService.getAttachmentToWorkspace(messageId, attachmentId, user, false, storage, workspaceHelper,
                    defaultResponseHandler(request));
        });


    }

    //Download all attachments
    @Get("message/:id/allAttachments")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getAllAttachment(final HttpServerRequest request) {
        // todo implement getAllAttachment
    }

    //Delete an attachment
    @Delete("message/:id/attachment/:attachmentId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void deleteAttachment(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);
        final String attachmentId = request.params().get("attachmentId");
        if (messageId == null || messageId.isEmpty() || attachmentId == null || attachmentId.isEmpty()) {
            notFound(request, "invalid.file.id");
            return;
        }
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            attachmentService.removeAttachment(messageId, attachmentId, user, defaultResponseHandler(request));
        });
    }

    @Put("message/:id/forward/:forwardedId")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void forwardAttachments(final HttpServerRequest request) {
        final String messageId = request.params().get(MESSAGE_ID);
        final String forwardedId = request.params().get("forwardedId");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            attachmentService.forwardAttachments(forwardedId, messageId, user, defaultResponseHandler(request));
        });
    }

    @Get("/print")
    @SecuredAction(value = "zimbra.print", type = ActionType.AUTHENTICATED)
    public void print(final HttpServerRequest request) {
        renderView(request, null, "print.html", null);
    }


    /**
     * Replace Conversation Event Bus
     * send API
     * Send an email via Conversation Event Bus
     * In case of success, return empty Json Object.
     *
     * @param message JsonObject containing infos
     *                body : message body
     *                subject : message subject
     *                to : id of each recipient
     *                cc : id of each cc recipient
     */
    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @BusAddress("org.entcore.conversation")
    public void conversationEventBusHandler(Message<JsonObject> message) {
        switch (message.body().getString("action", "")) {
            case "send":
                JsonObject messageToSend = message.body().getJsonObject("message", new JsonObject());
                String userIdOfExpeditor = message.body().getString("userId");
                UserUtils.getUserInfos(eb, userIdOfExpeditor, user -> {
                    if (user == null) {
                        message.reply(new JsonObject().put(Field.STATUS, "ko")
                                .put("message", "userId of expeditor is not defined or doesn't exists in Neo4j"));
                        return;
                    }
                    messageService.sendMessage(null, messageToSend, user, null, res -> {
                                if (res.isLeft()) {
                                    message.reply(new JsonObject().put(Field.STATUS, "ko")
                                            .put("message", res.left().getValue()));
                                } else {
                                    message.reply(new JsonObject().put(Field.STATUS, "ok")
                                            .put("message", res.right().getValue()));
                                }
                            }
                    );
                });
                break;
            default:
                message.reply(new JsonObject().put(Field.STATUS, Field.ERROR)
                        .put("message", "invalid.action"));
        }
    }


    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @BusAddress("fr.openent.zimbra")
    public void zimbraEventBusHandler(Message<JsonObject> message) {
        String action = message.body().getString("action", "");
        switch (action) {
            case "getMailUser":
                JsonArray idList = message.body().getJsonArray("idList", new JsonArray());
                userService.getMailAddresses(idList, res ->
                        message.reply(new JsonObject().put(Field.STATUS, "ok")
                                .put("message", res))
                );
                break;
            default:
                conversationEventBusHandler(message);
        }
    }


    /**
     * Quota for a user.
     * In case of success, return a Json Object :
     * {
     * "storage" : "quotaUsed"
     * "quota" : "quotaTotalAllowed"
     * }
     *
     * @param request http request containing info
     *                Users infos
     */
    @Get("quota")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void quotaUser(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            userService.getQuota(user, defaultResponseHandler(request));
        });
    }

    /**
     * Get user signature
     *
     * @param request http request containing info
     *                Users infos
     *                In case of success, return Json Object :
     *                {
     *                "preference" : {
     *                "useSignature": boolean,
     *                "signature": signature Body
     *                },
     *                MESSAGE_ID : signatureID,
     *                "zimbraENTSignatureExists" : boolean
     *                }
     */
    @Get("signature")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getSignatureUser(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            signatureService.getSignature(user, defaultResponseHandler(request));
        });
    }

    /**
     * Edit a user signature
     * In case of success, return an empty Json Array
     *
     * @param request http request containing info
     *                Users infos
     */
    @Put("signature")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void putSignatureUser(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, body -> {
                final String signatureBody = body.getString("signature", null);
                final Boolean useSignature = body.getBoolean("useSignature");

                signatureService.getSignature(user, resp -> {
                    if (resp.isRight()) {
                        Boolean signatureExists = resp.right().getValue().getBoolean("zimbraENTSignatureExists");
                        if (signatureExists) {
                            if (signatureBody == null || signatureBody.trim().length() == 0) {
                                signatureService.deleteSignature(user, false,
                                        defaultResponseHandler(request));
                            } else {
                                signatureService.modifySignature(user, signatureBody, useSignature,
                                        defaultResponseHandler(request));
                            }
                        } else {
                            if (signatureBody == null || signatureBody.trim().length() == 0) {
                                badRequest(request);
                            } else {
                                signatureService.createSignature(user, signatureBody, useSignature,
                                        defaultResponseHandler(request));
                            }
                        }
                    }
                });
            });
        });

    }

    @SuppressWarnings("UnnecessaryReturnStatement")
    @Get("/zimbraOutside")
    @SecuredAction("zimbra.outsideCommunication")
    public void zimbraOutside(final HttpServerRequest request) {
        // This route is used to create zimbraOutsideCommunication Workflow right, nothing to do
        return;
    }

    @Get("/preferences")
    @ResourceFilter(ExpertAccess.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void zimbraSetting(HttpServerRequest request) {
        renderView(request);
    }

    @Get("/mailconfig")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(ExpertAccess.class)
    public void getMailConfig(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            userService.getMailConfig(user.getUserId(),
                    AsyncHelper.getJsonObjectAsyncHandler(defaultResponseHandler(request)));
        });
    }

    @Get("/idToCheck/:id")
    @ResourceFilter(DevLevelFilter.class)
    public void checkIfIdGroup(HttpServerRequest request) {
        String idToCheck = request.params().get(MESSAGE_ID);
        userService.requestIfIdGroup(idToCheck, defaultResponseHandler(request));
    }


    @Post("/message/:id/deliveryReport")
    @ApiDoc("Send mail to acknowledge receipt to original sender")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(DevLevelFilter.class)
    public void sendDeliveryReport(HttpServerRequest request) {
        getUserInfos(eb, request, user ->
                messageService.sendDeliveryReport(user, request.getParam(MESSAGE_ID))
                        .onFailure(err -> renderError(request))
                        .onSuccess(result -> renderJson(request, result)));
    }
}
