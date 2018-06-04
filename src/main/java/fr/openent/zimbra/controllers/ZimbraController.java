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
import fr.openent.zimbra.service.impl.*;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;

import fr.wseduc.webutils.request.RequestUtils;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.Config;

import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;

import io.vertx.core.Handler;
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

	private Neo4jZimbraService neoConversationService;
	private TimelineHelper notification;
	private final String exportPath;
	private SoapZimbraService soapService;
	private UserService userService;
	private FolderService folderService;
	private MessageService messageService;

	public ZimbraController(String exportPath) {
		this.exportPath = exportPath;
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		notification = new TimelineHelper(vertx, eb, config);

		this.neoConversationService = new Neo4jZimbraService();
		SqlZimbraService sqlService = new SqlZimbraService(config.getString("db-schema", "zimbra"));
		this.soapService = new SoapZimbraService(vertx, config);
		this.userService = new UserService(log, soapService, sqlService);
		this.folderService = new FolderService(soapService);
		this.messageService = new MessageService(log, soapService, folderService, sqlService, userService);

		soapService.setUserService(userService);
	}

	@Get("zimbra")
	@SecuredAction("conversation.view")
	public void view(HttpServerRequest request) {
		renderView(request);
	}



	@Get("testcount")
	@SecuredAction("conversation.view")
	public void testCount(HttpServerRequest request) {
		getUserInfos(eb, request, user -> {
			folderService.countMessages("INBOX", false, user, event -> {
				if(event.isLeft()) {
					renderError(request, new JsonObject().put("error", event.left().getValue()));
				} else {
					renderJson(request, event.right().getValue());
				}
			});
		});
	}

	@Get("zimbra/testauth")
	public void testAuth(HttpServerRequest request) {
		getUserInfos(eb, request, user -> {
			defaultResponseHandler(request).handle(new Either.Right<>(new JsonObject()));
		});
	}

	@Post("draft")
	@SecuredAction("conversation.create.draft")
	public void createDraft(final HttpServerRequest request) {
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final String parentMessageId = request.params().get("In-Reply-To");
					bodyToJson(request, new Handler<JsonObject>() {
						@Override
						public void handle(final JsonObject message) {

						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Put("draft/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void updateDraft(final HttpServerRequest request) {
		final String messageId = request.params().get("id");

		if (messageId == null || messageId.trim().isEmpty()) {
			badRequest(request);
			return;
		}

		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					bodyToJson(request, new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject message) {

						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}


	@Post("send")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void send(final HttpServerRequest request) {
		final String messageId = request.params().get("id");

		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final String parentMessageId = request.params().get("In-Reply-To");
					bodyToJson(request, new Handler<JsonObject>() {
						@Override
						public void handle(final JsonObject message) {

						}
					});
				} else {
					unauthorized(request);
				}
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
				Boolean b = null;
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

	}

	@Get("message/:id")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getMessage(final HttpServerRequest request) {
		final String id = request.params().get("id");
		if (id == null || id.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {

			}
		});
	}

	@Put("trash")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void trash(final HttpServerRequest request) {

	}

	@Put("restore")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void restore(final HttpServerRequest request) {

	}

	@Delete("delete")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void delete(final HttpServerRequest request) {
		final List<String> ids = request.params().getAll("id");
		if (ids == null || ids.isEmpty()) {
			badRequest(request);
			return;
		}
		deleteMessages(request, ids, false);
	}

	private void deleteMessages(final HttpServerRequest request, final List<String> ids, final Boolean deleteAll) {
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {

			}
		});
	}

	@Delete("emptyTrash")
	@SecuredAction(value="zimbra.empty.trash", type = ActionType.AUTHENTICATED)
	public void emptyTrash(final HttpServerRequest request) {
	}

	//Mark messages as unread / read
	@Post("toggleUnread")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void toggleUnread(final HttpServerRequest request) {

	}

	//Get max folder depth
	@Get("max-depth")
	@SecuredAction(value="zimbra.max.depth", type=ActionType.AUTHENTICATED)
	public void getMaxDepth(final HttpServerRequest request){
		renderJson(request, new JsonObject().put("max-depth", Config.getConf().getInteger("max-folder-depth", Zimbra.DEFAULT_FOLDER_DEPTH)));
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

	//Move messages into a folder
	@Put("move/userfolder/:folderId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void move(final HttpServerRequest request) {

	}

	//Move messages into a system folder
	@Put("move/root")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void rootMove(final HttpServerRequest request) {

	}

	/**
	 * Trash a folder.
	 * In case of success, return empty Json Object.
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
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


	//Post an new attachment to a drafted message
	@Post("message/:id/attachment")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void postAttachment(final HttpServerRequest request){

	}

	//Download an attachment
	@Get("message/:id/attachment/:attachmentId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getAttachment(final HttpServerRequest request){

	}

	//Download all attachments
	@Get("message/:id/allAttachments")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void getAllAttachment(final HttpServerRequest request){

	}

	//Delete an attachment
	@Delete("message/:id/attachment/:attachmentId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void deleteAttachment(final HttpServerRequest request){

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

}
