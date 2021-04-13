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

package fr.openent.zimbra.model.constant;

public class FrontConstants {
    public static final String FOLDER_INBOX = "INBOX";
    public static final String FOLDER_OUTBOX = "OUTBOX";
    public static final String FOLDER_DRAFT = "DRAFT";
    public static final String FOLDER_TRASH = "TRASH";

    public static final String FOLDER_ID = "id";
    public static final String FOLDER_SIZE = "count";
    public static final String FOLDER_PATH = "path";
    public static final String FOLDER_NAME = "folderName";
    public static final String FOLDER_SUB_FOLDERS = "folders";

    public static final String STATE_DRAFT = "DRAFT";
    public static final String STATE_SENT = "SENT";

    public static final String REPLYTYPE_REPLY = "r";
    public static final String REPLYTYPE_FORWARD = "f";
    public static final String UNDEFINED = "undefined";

    public static final String MAIL_FROM = "from";
    public static final String MAIL_TO = "to";
    public static final String MAIL_CC = "cc";
    public static final String MAIL_BCC = "bcc";
    public static final String MAIL_BCC_MOBILEAPP = "cci";
    public static final String MAIL_DISPLAYNAMES = "displayNames";

    public static final String MAILCONFIG_IMAP = "imaps";
    public static final String MAILCONFIG_SMTP = "smtps";
    public static final String MAILCONFIG_LOGIN = "login";

    public static final String MESSAGE_ID = "id";
    public static final String MESSAGE_DATE = "date";
    public static final String MESSAGE_SUBJECT = "subject";
    public static final String MESSAGE_FOLDER_ID = "parent_id";
    public static final String MESSAGE_THREAD_ID = "thread_id";
    public static final String MESSAGE_BODY = "body";
    public static final String MESSAGE_STATE = "state";
    public static final String MESSAGE_UNREAD = "unread";
    public static final String MESSAGE_RESPONSE = "response";
    public static final String MESSAGE_HAS_ATTACHMENTS = "hasAttachment";
    public static final String MESSAGE_SYSTEM_FOLDER = "systemFolder";
    public static final String MESSAGE_ATTACHMENTS = "attachments";
    public static final String MESSAGE_ATT_ID = "id";
    public static final String MESSAGE_ATT_FILENAME = "filename";
    public static final String MESSAGE_ATT_CONTENTTYPE = "contentType";
    public static final String MESSAGE_ATT_SIZE = "size";
    public static final String MESSAGE_IS_DRAFT = "is_draft";
    public static final String MESSAGE_IS_TRASHED = "is_trashed";

    public static final String THREAD_ID = "id";
    public static final String THREAD_DATE = "date";
    public static final String THREAD_NB_UNREAD = "unread";
    public static final String THREAD_SUBJECT = "subject";

    public static final String FRONT_PAGE_FOLDERS = "folders";

    public static final String CONFIG_SAVE_DRAFT_AUTO_TIME = "configSaveDraftAutoTime";


    // Redirection Constants
    public static final String REDIR_MODE = "mode";
    public static final String REDIR_URL = "url";
    public static final String EXPERT_MODE = "expert";
    public static final String SIMPLE_MODE = "simplified";
}
