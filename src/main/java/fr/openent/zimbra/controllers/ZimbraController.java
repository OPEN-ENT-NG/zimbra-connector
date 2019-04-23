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
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.security.ExpertAccess;
import fr.openent.zimbra.service.data.SqlZimbraService;
import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.filters.VisiblesFilter;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;

import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.Config;

import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.http.RouteMatcher;


import java.io.IOException;
import java.util.*;

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
	private SignatureService signatureService;
	private SqlZimbraService sqlService;
	private ExpertModeService expertModeService;


	private static final Logger log = LoggerFactory.getLogger(ZimbraController.class);

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);

		ServiceManager serviceManager = ServiceManager.init(vertx, config, eb, pathPrefix);

		this.appConfig = Zimbra.appConfig;
		this.sqlService = serviceManager.getSqlService();
		this.userService = serviceManager.getUserService();
		this.folderService = serviceManager.getFolderService();
		this.signatureService = serviceManager.getSignatureService();
		this.messageService = serviceManager.getMessageService();
		this.attachmentService = serviceManager.getAttachmentService();
		this.expertModeService = serviceManager.getExpertModeService();

	}

	@Get("zimbra")
	@SecuredAction("zimbra.view")
	public void view(HttpServerRequest request) {
		renderView(request);
	}

	/**
	 * Redirect the connected user to an authenticated session of Zimbra
	 * @param request	http request containing user info
	 */
	@Get("preauth")
	@SecuredAction("zimbra.expert")
	public void preauth(HttpServerRequest request) {
		getUserInfos(eb, request, user -> {
			if (user != null) {
				try {
					String location = expertModeService.getPreauthUrl(user);
					redirect(request, appConfig.getZimbraUri(), location);
				} catch (IOException e) {
					renderError(request);
				}
			} else {
				unauthorized(request);
			}
		});
	}


	/**
	 * Create a Draft email
	 * In case of success, return Json Object :
	 * {
	 * 	    "id" : "new-zimbra-email-id"
	 * }
	 * @param request http request containing info
     * 	              Users infos
	 * 	              In-Reply-To : Id of the message being replied to
	 * 	              reply : type of reply (R for reply, F for Forward)
     *   	          body : message body
     *   	          subject : message subject
     * 	              to : id of each recipient
     * 	              cc : id of each cc recipient
	 */
	@Post("draft")
	@SecuredAction("zimbra.create.draft")
	public void createDraft(final HttpServerRequest request) {

		final String parentMessageId = request.params().get("In-Reply-To");
		String replyType = request.params().get("reply");

		if(replyType != null && !FrontConstants.REPLYTYPE_REPLY.equalsIgnoreCase(replyType)
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
	 * @param request http request containing info
	 *                :id : draft Id
	 * 	              	Users infos
	 *   	          	body : message body
	 *   	          	subject : message subject
	 * 	              	to : id of each recipient
	 * 	              	cc : id of each cc recipient
	 */
	@Put("draft/:id")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void updateDraft(final HttpServerRequest request) {
		final String messageId = request.params().get("id");
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
	 * @param request http request containing info
	 * 	              Users infos
	 *   	          body : message body
	 *   	          subject : message subject
	 * 	              to : id of each recipient
	 * 	              cc : id of each cc recipient
	 */
	@Post("send")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	@ResourceFilter(VisiblesFilter.class)
	public void send(final HttpServerRequest request) {
        final String messageId = request.params().get("id");
		getUserInfos(eb, request, user -> {
				if (user != null) {
					bodyToJson(request, message ->
						messageService.sendMessage(messageId, message, user, defaultResponseHandler(request))
					);
				} else {
					unauthorized(request);
				}
		});
	}

	/**
	 * List messages in folders
	 * If unread is true, filter only unread messages.
	 * If search is set, must be at least 3 characters. Then filter by search.
	 * @param request http request containing info
	 *                Users infos
  	 *                folder name or id
  	 *                unread ? filter only unread messages
	 *                search ? filter only searched messages
	 */
	@Get("list/:folder")
	@SecuredAction(value = "zimbra.list", type = ActionType.AUTHENTICATED)
	public void list(final HttpServerRequest request) {
		final String folder = request.params().get("folder");
		final String unread = request.params().get("unread");
		final String search = request.params().get("search");
		if(search != null  && search.trim().length() < 3){
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
				} catch (NumberFormatException e) { page = 0; }
				Boolean b = false;
				if (unread != null && !unread.isEmpty()) {
					b = Boolean.valueOf(unread);
				}
				messageService.listMessages(folder, b, user, page, search, arrayResponseHandler(request));
			} else {
				unauthorized(request);
			}
		});
	}


	/**
	 * Count number of messages in a folder.
	 * If unread is true, filter only unread messages.
	 * In case of success, return Json :
	 * {
	 *     data: count // number of (unread) messages
	 * }
	 * @param request http request containing info
	 *                Users infos
	 *                folder name or id
	 *                unread ? filter only unread messages
	 */
	@Get("count/:folder")
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
				sqlService.findVisibleRecipients(user, I18n.acceptLanguage(request), request.params().get("search"),
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
	 *     "id" : "message_id",
	 *     "subject" : "message_subject",
	 *     "from" : "user_id_from",
	 *     "to" : [
	 *      "user_id_to",
	 *     ],
	 *     "cc" : [
	 *      "user_id_cc",
	 *     ],
	 *     "display_names" : [
	 *      "user_id",
	 *      "user_display_name",
	 *     ],
	 *     "date" : datesent,
	 *     "unread" : boolean_unread,
     *     "attachments" : [{
     *       "id" : "attachment_id",
     *       "filename" : "attachment_filename",
     *       "contentType" : "attachment_type",
     *       "size" : "attachment_size"
     *      },
     *      ]
	 *
	 * }
	 * @param request http request containing info
	 *                id : message id
	 *                 Users infos
	 */
	@Get("message/:id")
	@SecuredAction(value = "zimbra.message", type = ActionType.AUTHENTICATED)
	public void getMessage(final HttpServerRequest request) {
		final String id = request.params().get("id");
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
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 *                Body :
	 *                	[
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                	]
	 */
	@Put("trash")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void trash(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if(messageIds == null || messageIds.size() == 0){
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_TRASH, user,
					defaultResponseHandler(request));
		});
	}

	/**
	 * Restore = Move messages from trash to inbox.
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                Body :
	 *                	[
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                	]
	 */
	@Put("restore")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void restore(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if(messageIds == null || messageIds.size() == 0){
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_INBOX, user,
					defaultResponseHandler(request));
		});
	}

	/**
	 * Delete definitively messages from trash
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                Body :
	 *                	[
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                	]
	 */
	@Delete("delete")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void delete(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if (messageIds == null || messageIds.isEmpty()) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			messageService.deleteMessages(messageIds, user, defaultResponseHandler(request));
		});
	}


    /**
     * Empty trash folder
     * @param request http request containing info
     *                 Users infos
     */
	@Delete("emptyTrash")
	@SecuredAction(value="zimbra.empty.trash", type = ActionType.AUTHENTICATED)
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
	 * @param request http request containing info
	 *                Users infos
	 *                Body :
	 *                  id :
	 *                	[
	 *                		{"idmessage"},
	 *                		{"idmessage"},
	 *                		{"idmessage"},
	 *                	]
	 *                  unread : boolean
	 */
	@Post("toggleUnread")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void toggleUnread(final HttpServerRequest request) {
		final List<String> ids = request.params().getAll("id");
		final String unread = request.params().get("unread");

		if (ids == null || ids.isEmpty() || unread == null || (!unread.equals("true") && !unread.equals("false"))) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
				if (user != null) {
					messageService.toggleUnreadMessages(ids, Boolean.valueOf(unread), user, defaultResponseHandler(request));
				} else {
					unauthorized(request);
				}
		});

	}

	//Get max folder depth
	@Get("max-depth")
	@SecuredAction(value="zimbra.max.depth", type=ActionType.AUTHENTICATED)
	public void getMaxDepth(final HttpServerRequest request){
		renderJson(request, new JsonObject().put("max-depth",
				Config.getConf().getInteger("max-folder-depth", Zimbra.DEFAULT_FOLDER_DEPTH)));
	}

	/**
	 * List folders at root level, under parent folder, or trashed folders at depth 1 only.
	 * In case of success, return Json Array of folders :
	 * [
	 * 	{
	 * 	    "id" : "folder-id",
	 * 	    "parent_id : "parent-folder-id" or null,
	 * 	    "user_id" : "id of owner of folder",
	 * 	    "name" : "folder-name",
	 * 	    "depth" : "folder-depth",
	 * 	    "trashed" : "is-folder-trashed"
	 * 	}
	 * ]
	 * @param request http request containing info
	 *                Users infos
	 *                parent id (optional)
	 *                trash ? ignore parent id and get trashed folders
	 */
	@Get("folders/list")
	@SecuredAction(value = "zimbra.folder.list", type = ActionType.AUTHENTICATED)
	public void listFolders(final HttpServerRequest request){
		final String parentId = request.params().get("parentId");
		final String listTrash = request.params().get("trash");

		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
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
	 * @param request http request containing info
	 *                Users infos
	 *                Body :
	 *                	createFolder.json
	 */
	@Post("folder")
	@SecuredAction(value = "zimbra.folder.create", type = ActionType.AUTHENTICATED)
	public void createFolder(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			RequestUtils.bodyToJson(request, pathPrefix + "createFolder", body -> {
					final String name = body.getString("name");
					final String parentId = body.getString("parentId", null);

					if(name == null || name.trim().length() == 0){
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
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 *                Body :
	 *                	updateFolder.json
	 */
	@Put("folder/:folderId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void updateFolder(final HttpServerRequest request) {
        final String folderId = request.params().get("folderId");

        UserUtils.getUserInfos(eb, request, user -> {
            if(user == null){
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "updateFolder", body -> {
				final String name = body.getString("name");

				if(name == null || name.trim().length() == 0){
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
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 *                Body :
	 *                	[
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                	]
	 */
	@Put("move/userfolder/:folderId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void move(final HttpServerRequest request) {
		final String folderId = request.params().get("folderId");
		final List<String> messageIds = request.params().getAll("id");
		if(messageIds == null || messageIds.size() == 0){
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			messageService.moveMessagesToFolder(messageIds, folderId, user, defaultResponseHandler(request));
		});
	}

	/**
	 * Move messages into a system folder (restore emails to inbox).
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 *                Body :
	 *                	[
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                		{"id" : "idmessage"},
	 *                	]
	 */
	@Put("move/root")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void rootMove(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if(messageIds == null || messageIds.size() == 0){
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			messageService.moveMessagesToFolder(messageIds, FrontConstants.FOLDER_INBOX, user,
					defaultResponseHandler(request));
		});
	}

	/**
	 * Trash a folder.
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                "folderId" : "folder Id"
	 */
	@Put("folder/trash/:folderId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void trashFolder(final HttpServerRequest request) {
		final String folderId = request.params().get("folderId");

		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			folderService.trashFolder(folderId, user, defaultResponseHandler(request));
		});

	}

	/**
	 * Restore a trashed folder in Inbox.
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 */
	@Put("folder/restore/:folderId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void restoreFolder(final HttpServerRequest request) {
		final String folderId = request.params().get("folderId");

        UserUtils.getUserInfos(eb, request, user -> {
				if(user == null){
					unauthorized(request);
					return;
				}
				folderService.restoreFolder(folderId, user, defaultResponseHandler(request));
			});
	}

	/**
	 * Delete a trashed folder.
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 */
	@Delete("folder/:folderId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void deleteFolder(final HttpServerRequest request) {
        final String folderId = request.params().get("folderId");

		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
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
	 * 	    "id" : "new-zimbra-attachment-id"
	 * }
	 * @param request http request
	 */
	@Post("message/:id/attachment")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void postAttachment(final HttpServerRequest request){
		final String messageId = request.params().get("id");

		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				unauthorized(request);
				return;
			}
			attachmentService.addAttachment(messageId, user, request, defaultResponseHandler(request));
		});
	}

	/**
	 * Download an attachment
	 * @param request http request containing info
	 *                 Users infos
	 *                 id : message Id
	 *                 attachmentId : attachment Id
	 */
	@Get("message/:id/attachment/:attachmentId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void getAttachment(final HttpServerRequest request){
		final String messageId = request.params().get("id");
		final String attachmentId = request.params().get("attachmentId");
		if(messageId == null || messageId.isEmpty() || attachmentId == null || attachmentId.isEmpty()) {
			notFound(request, "invalid.file.id");
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				unauthorized(request);
				return;
			}
			attachmentService.getAttachment(messageId, attachmentId, user, false, request, defaultResponseHandler(request));
		});


	}

	//Download all attachments
	@Get("message/:id/allAttachments")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getAllAttachment(final HttpServerRequest request){
		// todo implement getAllAttachment
	}

	//Delete an attachment
	@Delete("message/:id/attachment/:attachmentId")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void deleteAttachment(final HttpServerRequest request){
		final String messageId = request.params().get("id");
		final String attachmentId = request.params().get("attachmentId");
		if(messageId == null || messageId.isEmpty() || attachmentId == null || attachmentId.isEmpty()) {
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
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void forwardAttachments(final HttpServerRequest request){
		final String messageId = request.params().get("id");
		final String forwardedId = request.params().get("forwardedId");

		UserUtils.getUserInfos(eb, request, user -> {
			if(user == null){
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

	@BusAddress("org.entcore.conversation")
	public void conversationEventBusHandler(Message<JsonObject> message) {
		switch (message.body().getString("action", "")) {
			case "send" : log.error("BUS sending not implemented : " + message.toString());
				// send(message);
				break;
			default:
				message.reply(new JsonObject().put("status", "error")
						.put("message", "invalid.action"));
		}
	}


	@BusAddress("fr.openent.zimbra")
	public void zimbraEventBusHandler(Message<JsonObject> message) {
		String action = message.body().getString("action", "");
		switch(action) {
			case "getMailUser" :
				JsonArray idList = message.body().getJsonArray("idList", new JsonArray());
				userService.getMailAddresses(idList, res -> {
					message.reply(new JsonObject().put("status", "ok")
						.put("message", res));
				});
				break;
			default:
				conversationEventBusHandler(message);
		}
	}


	/**
	 * Quota for a user.
	 * In case of success, return a Json Object :
	 * {
	 * 	    "storage" : "quotaUsed"
	 * 	    "quota" : "quotaTotalAllowed"
	 * }
	 * @param request http request containing info
	 *                Users infos
	 */
	@Get("quota")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void quotaUser(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			userService.getQuota(user, defaultResponseHandler(request));
		});
	}

	/**
	 * Get user signature
	 * @param request http request containing info
	 *                 Users infos
	 * In case of success, return Json Object :
	 * {
	 * 	    "preference" : {
	 * 	        "useSignature": boolean,
	 * 	        "signature": signature Body
	 * 	    },
	 * 	    "id" : signatureID,
	 * 	    "zimbraENTSignatureExists" : boolean
	 * }
	 */
	@Get("signature")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void getSignatureUser(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
				unauthorized(request);
				return;
			}
			signatureService.getSignature(user, defaultResponseHandler(request));
		});
	}

	/**
	 * Edit a user signature
	 * In case of success, return an empty Json Array
	 * @param request http request containing info
	 *                Users infos
	 */
	@Put("signature")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void putSignatureUser(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request,  user -> {
			if(user == null){
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
							if(signatureBody == null || signatureBody.trim().length() == 0){
								signatureService.deleteSignature(user, false,
																defaultResponseHandler(request));
							}
							else {
								signatureService.modifySignature(user, signatureBody, useSignature,
																defaultResponseHandler(request));
							}
						}
						else {
							if(signatureBody == null || signatureBody.trim().length() == 0){
								badRequest(request);
							}
							else {
								signatureService.createSignature(user, signatureBody, useSignature,
																defaultResponseHandler(request));
							}
						}
					}
				});
			});
		});

	}
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
}
