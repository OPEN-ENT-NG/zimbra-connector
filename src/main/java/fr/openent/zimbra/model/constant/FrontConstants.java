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

    public static final String REPLYTYPE_REPLY = "r";
    public static final String REPLYTYPE_FORWARD = "f";
    public static final String UNDEFINED = "undefined";

    public static final String MAIL_TO = "to";
    public static final String MAIL_CC = "cc";
    public static final String MAIL_BCC = "bcc";
    public static final String MAIL_BCC_MOBILEAPP = "cci";

    public static final String MAILCONFIG_IMAP = "imaps";
    public static final String MAILCONFIG_SMTP = "smtps";
    public static final String MAILCONFIG_LOGIN = "login";

    public static final String MESSAGE_ID = "id";
    public static final String MESSAGE_BODY = "body";
    public static final String MESSAGE_ATTACHMENTS = "attachments";
    public static final String MESSAGE_ATT_ID = "id";
    public static final String MESSAGE_ATT_FILENAME = "filename";
    public static final String MESSAGE_ATT_CONTENTTYPE = "contentType";
    public static final String MESSAGE_ATT_SIZE = "size";
    public static final String MESSAGE_UNREAD = "unread";
}
