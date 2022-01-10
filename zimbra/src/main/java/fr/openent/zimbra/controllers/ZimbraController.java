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
import fr.openent.zimbra.filters.DevLevelFilter;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.FrontConstants;
import fr.openent.zimbra.model.constant.ModuleConstants;
import fr.openent.zimbra.model.soap.SoapSearchHelper;
import fr.openent.zimbra.security.ExpertAccess;
import fr.openent.zimbra.service.data.SearchService;
import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.service.synchro.AddressBookService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.cache.Cache;
import org.entcore.common.cache.CacheOperation;
import org.entcore.common.cache.CacheScope;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.Config;
import org.vertx.java.core.http.RouteMatcher;

import java.io.IOException;
import java.util.*;

import static fr.openent.zimbra.helper.FutureHelper.handlerJsonObject;
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
	private NotificationService notificationService;


	private EventStore eventStore;

	private enum ZimbraEvent {ACCESS}


	private static final Logger log = LoggerFactory.getLogger(ZimbraController.class);

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);

		eventStore = EventStoreFactory.getFactory().getEventStore(Zimbra.class.getSimpleName());

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
		this.notificationService = serviceManager.getNotificationService();
		this.returnedMailService = serviceManager.getReturnedMailService();
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


	@Get("writeto")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void writeTo(final HttpServerRequest request) {
		final String id = request.params().get("id");
		final String name = request.params().get("name");
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
	 * "id" : "new-zimbra-email-id"
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
		final String messageId = request.params().get("id");
		final String parentMessageId = request.params().get("In-Reply-To");
		getUserInfos(eb, request, user -> {
			if (user != null) {
				bodyToJson(request, message ->
						messageService.sendMessage(messageId, message, user, parentMessageId, defaultResponseHandler(request))
				);
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
	@Get("/return")
	@SecuredAction("zimbra.return.mail")
	public void returnMails(final HttpServerRequest request) {
		String messagesId = request.params().get("id");
		String comment = request.params().contains("comment")
				? request.params().get("comment")
				: null;
		getUserInfos(eb, request, user -> {
			if (user != null) {
				// Etape 1 : récupérer le message à supprimer
				messageService.getMessage(messagesId, user, getMail -> {
					if (getMail.isRight()) {
						JsonObject mail = getMail.right().getValue();
						String mail_date = new java.text.SimpleDateFormat("MM/dd/yyyy")
								.format(new java.util.Date(mail.getLong("date")));
						// Etape 2 : récupérer la liste des utilisateurs des groupes
						userService.getUsers(mail.getJsonArray("to"), mail.getJsonArray("to"), usersFromGroup -> {
							Set<String> setUser = new HashSet<>();
							JsonArray usersGroup = usersFromGroup.right().getValue();
							for (int i = 0; i < usersGroup.size(); i++) {
								setUser.add(usersGroup.getJsonObject(i).getString("id"));
							}
							JsonArray to = new JsonArray(Arrays.asList(setUser.toArray()));
							JsonObject returnedMail = new JsonObject()
									.put("subject", mail.getString("subject"))
									.put("userId", user.getUserId())
									.put("userName", user.getLastName() + " " + user.getFirstName())
									.put("mailId", mail.getString("id"))
									.put("structureId", user.getStructures().get(0))
									.put("nb_messages", mail.getJsonArray("to").size())
									.put("to", to)
									.put("mail_date", mail_date)
									.put("comment", comment);

							// Etape 3 : Insérer les infos du message renvoyé en base de donnée
							returnedMailService.insertReturnedMail(returnedMail, insertMailLog -> {
								if (insertMailLog.isRight()) {
									final List<String> recipients = new ArrayList<>();
									// Etape 4 : Récuperer les ADML pour leur envoyer la notification de validation
									userService.getLocalAdministrators(user.getStructures().get(0), admins -> {
										if (admins != null) {
											for (Object adminObj : admins) {
												JsonObject userAdml = (JsonObject) adminObj;
												recipients.add(userAdml.getString("id"));
											}

											// Etape 5 : Envoie de la notification aux ADML
											notificationService.sendReturnMailNotification(user, mail.getString("subject"), recipients, request, notif -> {
												if (notif.isRight()) {
													renderJson(request, insertMailLog.right().getValue());
												}
											});
										} else {
											log.error("Error getting list adml");
										}
									});
								} else {
									log.error("[Zimbra]returnEmails : Insert in log problem");
								}
							});
						});
					} else {
						log.error("[Zimbra]returnEmails : Get message to remove problem");
					}
				});
			} else {
				unauthorized(request);
			}
		});
	}

	//Todo : add /return/list/structure
	@Get("/return/list")
	@SecuredAction("zimbra.return.list")
	public void getReturnedMails(final HttpServerRequest request) {
		String structureId = request.params().get("structureId");
		returnedMailService.getMailReturned(structureId, returnedMails -> {
			if (returnedMails.isRight()) {
				renderJson(request, returnedMails.right().getValue());
				log.info("[Zimbra]getReturnedMails: Collect returned mail successfully");
			} else {
				badRequest(request);
				log.error("[Zimbra]getReturnedMails: Collect returned mail failed : " + returnedMails.left().getValue());
			}
		});
	}

	@Get("/return/list/user")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void getReturnedMailsByIdsAndUser(final HttpServerRequest request) {
		final List<String> mailIds = request.params().getAll("id");
		getUserInfos(eb, request, user -> {
			if (user != null) {
				returnedMailService.getMailReturnedByMailsIdsAndUser(mailIds, user.getUserId(), returnedMails -> {
					if (returnedMails.isRight()) {
						renderJson(request, returnedMails.right().getValue());
						log.info("[Zimbra]getReturnedMails: Collect returned mail successfully");
					} else {
						badRequest(request);
						log.error("[Zimbra]getReturnedMails: Collect returned mail failed : " + returnedMails.left().getValue());
					}
				});
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
	 * "id" : "message_id",
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
	 * "id" : "attachment_id",
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
	 *
	 * @param request http request containing info
	 *                Users infos
	 *                folder id
	 *                Body :
	 *                [
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                ]
	 */
	@Put("trash")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void trash(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if (messageIds == null || messageIds.size() == 0) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
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
	 *
	 * @param request http request containing info
	 *                Users infos
	 *                Body :
	 *                [
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                ]
	 */
	@Put("restore")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void restore(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if (messageIds == null || messageIds.size() == 0) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
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
	 *
	 * @param request http request containing info
	 *                Users infos
	 *                Body :
	 *                [
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                ]
	 */
	@Delete("delete")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void delete(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if (messageIds == null || messageIds.isEmpty()) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				unauthorized(request);
				return;
			}
			messageService.deleteMessages(messageIds, user, defaultResponseHandler(request));
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
	 *                {"id" : "idReturnedMail"},
	 *                {"id" : "idReturnedMail"},
	 *                {"id" : "idReturnedMail"},
	 *                {"subject" : "toDelete"},
	 *                {"date" : "1640618300000"}
	 *                ]
	 */
	@Delete("delete/sent")
	@SecuredAction("zimbra.return.mail.delete")
	public void deleteSentEmail(final HttpServerRequest request) {
		final List<String> returnedMailsIds = request.params().getAll("id");
		List<Future> futures = new ArrayList<>();
		// Etape 1 : Récupérer toute les infos sur les mails à delete dans la base
		returnedMailService.getMailReturnedByIds(returnedMailsIds, returnedMailEvent -> {
			if (returnedMailEvent.isRight()) {
				JsonArray returnedMails = returnedMailEvent.right().getValue();
				for (int i = 0; i < returnedMails.size(); i++) {
					// Etape 2 : Creation de l'objet qui va nous permettre de filtrer dans les boites mails et de rechercher le bon mail
					JsonObject returnedMail = returnedMails.getJsonObject(i);
					JsonArray userIds = new JsonArray(returnedMail.getString("recipient"));
					String object = returnedMail.getString("object");
					String from = returnedMail.getString("user_id");
					String date = returnedMail.getString("mail_date");
					// Etape 3 : Pour chaque utilisateur à qui on a envoyé le mail, on ajoute la fonction de suppresion dans la liste de futures
					for (int j = 0; j < userIds.size(); j++) {
						boolean end = j == userIds.size() - 1 && i == returnedMails.size() - 1;
						deleteMail(userIds.getString(j), object, date, from, end, futures, isEndEvent -> {
							if (isEndEvent.isRight()) {
								// Etape 4 : Une fois que nous avons parcouru tout les mails, on supprime les mail de la boite de reception
								if (isEndEvent.right().getValue().getBoolean("end")) {
									if (futures.size() > 0) {
										CompositeFuture.all(futures).setHandler(event -> {
											if (event.succeeded()) {
												returnedMailService.updateStatut(returnedMailsIds, updateStatutEvent -> {
													if (updateStatutEvent.isRight()) {
														renderJson(request, new JsonObject());
													} else {
														log.error("Une erreur est survenue lors de la mise à jour des statuts des mails à retourner. ",
																updateStatutEvent.left().getValue());
														request.response().setStatusCode(400).end();
													}
												});
											} else {
												log.error("Une erreur est survenue lors de la suppresion des mails. ",
														event.cause());
												request.response().setStatusCode(400).end();
											}
										});
									} else {
										renderJson(request, new JsonObject().put("message", "Aucun mail supprimé"));
									}
								}
							}
						});
					}
				}
			} else {
				log.error("Une erreur est survenue lors de la récupération des mails à retourner. ",
						returnedMailEvent.left().getValue());
				request.response().setStatusCode(400).end();
			}
		});
	}


	private void deleteMail(String userId, String subject, String date, String from, boolean end, List<Future> futures, Handler<Either<String, JsonObject>> handler) {
		// Etape 1 : on récupère les infos de l'utilisateur
		UserUtils.getUserInfos(eb, userId, user -> {
			// Etape 2 : on récupère l'adresse mail de l'expéditeur
			userService.getMailAddresses(new JsonArray().add(from), fromMail -> {
				String from_mail = fromMail.getJsonObject(from).getString("email");
				// Etape 3 : On recherche en fonction de l'objet, de l'expéditeur, de la date et de la boite de réception le mail à supprimer
				String query = "in:\"" + "Inbox" + "\"" + " AND subject:\"" + subject + "\"" + " AND from:\"" + from_mail + "\"" + " AND date:\"" + date + "\"";
				SoapSearchHelper.searchAllMailedConv(user.getUserId(), 0, query, event -> {
					if (event.succeeded()) {
						if (event.result().size() > 0) {
							Future<JsonObject> deleteFuture = Future.future();
							futures.add(deleteFuture);
							List<String> ids = new ArrayList<>();
							ids.add(event.result().get(0).getMessageList().get(0).getId());
							// Etape 4 : On déplace le mail à supprimer vers la corbeille
							messageService.moveMessagesToFolder(ids, FrontConstants.FOLDER_TRASH, user,
									moveToTrash -> {
										if (moveToTrash.isRight()) {
											// Etape 5 : On supprime définitivement le message de la boite de réception
											messageService.deleteMessages(ids, user, handlerJsonObject(deleteFuture));
											if (end) {
												handler.handle(new Either.Right<>(new JsonObject().put("end", true)));
											}
										} else {
											log.error("Erreur lors du déplacement vers la corbeille du mail " + ids.get(0));
										}
									});
						}
					} else {
						if (end) {
							handler.handle(new Either.Right<>(new JsonObject().put("end", true)));
						}
						log.error("Erreur lors de la recupération du mail voulant être supprimé.");
					}
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
		final List<String> ids = request.params().getAll("id");
		final String unread = request.params().get("unread");

		if (ids == null || ids.isEmpty() || unread == null || (!unread.equals("true") && !unread.equals("false"))) {
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
	 * "id" : "folder-id",
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
				final String name = body.getString("name");
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
				final String name = body.getString("name");

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
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                ]
	 */
	@Put("move/userfolder/:folderId")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void move(final HttpServerRequest request) {
		final String folderId = request.params().get("folderId");
		final List<String> messageIds = request.params().getAll("id");
		if (messageIds == null || messageIds.size() == 0) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				unauthorized(request);
				return;
			}
			messageService.moveMessagesToFolder(messageIds, folderId, user, defaultResponseHandler(request));
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
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                {"id" : "idmessage"},
	 *                ]
	 */
	@Put("move/root")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void rootMove(final HttpServerRequest request) {
		final List<String> messageIds = request.params().getAll("id");
		if (messageIds == null || messageIds.size() == 0) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
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
	 * "id" : "new-zimbra-attachment-id"
	 * }
	 *
	 * @param request http request
	 */
	@Post("message/:id/attachment")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	@ResourceFilter(DevLevelFilter.class)
	public void postAttachment(final HttpServerRequest request) {
		final String messageId = request.params().get("id");

		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				unauthorized(request);
				return;
			}

			request.pause();
			attachmentService.addAttachment(messageId, user, request, defaultResponseHandler(request));
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
		final String messageId = request.params().get("id");
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
			attachmentService.getAttachment(messageId, attachmentId, user, false, request, defaultResponseHandler(request));
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
		final String messageId = request.params().get("id");
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
		final String messageId = request.params().get("id");
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
						message.reply(new JsonObject().put("status", "ko")
								.put("message", "userId of expeditor is not defined or doesn't exists in Neo4j"));
						return;
					}
					messageService.sendMessage(null, messageToSend, user, null, res -> {
								if (res.isLeft()) {
									message.reply(new JsonObject().put("status", "ko")
											.put("message", res.left().getValue()));
								} else {
									message.reply(new JsonObject().put("status", "ok")
											.put("message", res.right().getValue()));
								}
							}
					);
				});
				break;
			default:
				message.reply(new JsonObject().put("status", "error")
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
						message.reply(new JsonObject().put("status", "ok")
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
	 *                "id" : signatureID,
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
		String idToCheck = request.params().get("id");
		userService.requestIfIdGroup(idToCheck, defaultResponseHandler(request));
	}
}
