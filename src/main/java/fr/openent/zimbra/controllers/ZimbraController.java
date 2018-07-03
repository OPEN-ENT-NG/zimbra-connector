/* Copyright © WebServices pour l'Éducation, 2014
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 */

package fr.openent.zimbra.controllers;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.FrontConstants;
import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.filters.VisiblesFilter;

import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;

import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServer;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.Config;

import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.vertx.java.core.http.RouteMatcher;


import java.util.*;

import static fr.wseduc.webutils.request.RequestUtils.bodyToJson;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

public class ZimbraController extends BaseController {

	private TimelineHelper notification;
	private UserService userService;
	private FolderService folderService;
	private AttachmentService attachmentService;
	private MessageService messageService;
	private SignatureService signatureService;
	private SqlZimbraService sqlService;
	private NotificationService notificationService;


	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		notification = new TimelineHelper(vertx, eb, config);

		this.sqlService = new SqlZimbraService(vertx, config.getString("db-schema", "zimbra"));
        SoapZimbraService soapService = new SoapZimbraService(vertx, config);
		SynchroUserService synchroUserService = new SynchroUserService(soapService, sqlService);
		this.userService = new UserService(soapService, synchroUserService, sqlService);
		this.folderService = new FolderService(soapService);
		this.signatureService = new SignatureService(userService, soapService);
		this.messageService = new MessageService(soapService, folderService, sqlService, userService);
		this.attachmentService = new AttachmentService(soapService, messageService, vertx, config);
		this.notificationService = new NotificationService(soapService, pathPrefix, notification);

		soapService.setServices(userService, synchroUserService);
	}

	@Get("zimbra")
	@SecuredAction("zimbra.view")
	public void view(HttpServerRequest request) {
		renderView(request);
	}


	/**
	 * Create a Draft email
	 * In case of success, return Json Object :
	 * {
	 * 	    "id" : "new-zimbra-email-id"
	 * }
	 * @param request http request containing info
     * 	              Users infos
     *   	          body : message body
     *   	          subject : message subject
     * 	              to : id of each recipient
     * 	              cc : id of each cc recipient
	 */
	@Post("draft")
	@SecuredAction("zimbra.create.draft")
	public void createDraft(final HttpServerRequest request) {
		getUserInfos(eb, request, user -> {
				if (user != null) {
					bodyToJson(request, message ->
						messageService.saveDraft(message, user, null, defaultResponseHandler(request))
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
						messageService.saveDraft(message, user, messageId, defaultResponseHandler(request))
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

	@Post("notification")
	@SecuredAction("export.structure.users.all")
	public void sendNotification(HttpServerRequest request) {
		RequestUtils.bodyToJson(request, body -> {
				final String zimbraSender = body.getString("sender");
				final String zimbraRecipient = body.getString("recipient");
				final String messageId = body.getString("messageId");
				final String subject = body.getString("subject");

				if(zimbraSender == null || zimbraSender.isEmpty()
						|| zimbraRecipient == null || zimbraRecipient.isEmpty()
						|| messageId == null || messageId.isEmpty()) {
					badRequest(request);
				} else {
					notificationService.sendNewMailNotification(zimbraSender, zimbraRecipient, messageId, subject,
							defaultResponseHandler(request));
				}
			});
	}

	private void timelineNotification(HttpServerRequest request, JsonObject sentMessage, UserInfos user) {
		log.debug(sentMessage.encode());
		JsonArray r = sentMessage.getJsonArray("sentIds");
		String id = sentMessage.getString("id");
		String subject = sentMessage.getString("subject", "<span translate key=\"timeline.no.subject\"></span>");
		sentMessage.remove("sentIds");
		sentMessage.remove("id");
		sentMessage.remove("subject");
		if (r == null || id == null || user == null) {
			return;
		}
		final JsonObject params = new JsonObject()
				.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
				.put("username", user.getUsername())
				.put("subject", subject)
				.put("messageUri", pathPrefix + "/conversation#/read-mail/" + id);
		params.put("resourceUri", params.getString("messageUri"));
		List<String> recipients = new ArrayList<>();
		String idTmp;
		for (Object o : r) {
			if (!(o instanceof String)) continue;
			idTmp = (String) o;
			if(!user.getUserId().equals(idTmp))
				recipients.add(idTmp);
		}
		notification.notifyTimeline(request, "messagerie.send-message", user, recipients, id, params);
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

	private void translateGroupsNames(JsonObject message, HttpServerRequest request) {
		JsonArray d3 = new fr.wseduc.webutils.collections.JsonArray();
		for (Object o2 : message.getJsonArray("displayNames", new fr.wseduc.webutils.collections.JsonArray())) {
			if (!(o2 instanceof String)) {
				continue;
			}
			String [] a = ((String) o2).split("\\$");
			if (a.length != 4) {
				continue;
			}
			JsonArray d2 = new fr.wseduc.webutils.collections.JsonArray().add(a[0]);
			if (a[2] != null && !a[2].trim().isEmpty()) {
				final String groupDisplayName = (a[3] != null && !a[3].trim().isEmpty()) ? a[3] : null;
				d2.add(UserUtils.groupDisplayName(a[2], groupDisplayName, I18n.acceptLanguage(request)));
			} else {
				d2.add(a[1]);
			}
			d3.add(d2);
		}
		message.put("displayNames", d3);
		JsonArray toName = message.getJsonArray("toName");
		if (toName != null) {
			JsonArray d2 = new fr.wseduc.webutils.collections.JsonArray();
			message.put("toName", d2);
			for (Object o : toName) {
				if (!(o instanceof String)) {
					continue;
				}
				d2.add(UserUtils.groupDisplayName((String) o, null, I18n.acceptLanguage(request)));
			}
		}
		JsonArray ccName = message.getJsonArray("ccName");
		if (ccName != null) {
			JsonArray d2 = new fr.wseduc.webutils.collections.JsonArray();
			message.put("ccName", d2);
			for (Object o : ccName) {
				if (!(o instanceof String)) {
					continue;
				}
				d2.add(UserUtils.groupDisplayName((String) o, null, I18n.acceptLanguage(request)));
			}
		}
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
				String parentMessageId = request.params().get("In-Reply-To");
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
			attachmentService.getAttachment(messageId, attachmentId, user, true, request, defaultResponseHandler(request));
		});


	}

	//Download all attachments
	@Get("message/:id/allAttachments")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getAllAttachment(final HttpServerRequest request){

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
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void forwardAttachments(final HttpServerRequest request){

	}

	@Get("/print")
	@SecuredAction(value = "zimbra.print", type = ActionType.AUTHENTICATED)
	public void print(final HttpServerRequest request) {
		renderView(request, null, "print.html", null);
	}

	@BusAddress("org.entcore.conversation")
	public void conversationEventBusHandler(Message<JsonObject> message) {
		switch (message.body().getString("action", "")) {
			case "send" : send(message);
				break;
			default:
				message.reply(new JsonObject().put("status", "error")
						.put("message", "invalid.action"));
		}
	}

	private void send(final Message<JsonObject> message) {

	}

	private void notifyEmptySpaceIsSmall(String userId) {
		List<String> recipients = new ArrayList<>();
		recipients.add(userId);
		notification.notifyTimeline(new JsonHttpServerRequest(new JsonObject()),
				"messagerie.storage", null, recipients, null, new JsonObject());
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

				if(signatureBody == null || signatureBody.trim().length() == 0){
					badRequest(request);
					return;
				}
				signatureService.getSignature(user, resp -> {
					if (resp.isRight()) {
						Boolean signatureExists = resp.right().getValue().getBoolean("zimbraENTSignatureExists");
						if (signatureExists) {
							signatureService.modifySignature(user, signatureBody, useSignature, defaultResponseHandler(request));
						}
						else {
							signatureService.createSignature(user, signatureBody, useSignature, defaultResponseHandler(request));
						}
					}
				});
			});
		});

	}

}
