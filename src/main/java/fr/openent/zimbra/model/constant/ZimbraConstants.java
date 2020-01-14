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

@SuppressWarnings("unused")
public class ZimbraConstants {
    public static final String FOLDER_ROOT = "/";
    public static final String FOLDER_INBOX = "/Inbox";
    public static final String FOLDER_OUTBOX = "/Sent";
    public static final String FOLDER_DRAFT = "/Drafts";
    public static final String FOLDER_TRASH = "/Trash";

    public static final String FOLDER_CONTACTS = "/Contacts";

    public static final String FOLDER_ROOT_ID = "1";
    public static final String FOLDER_INBOX_ID= "2";
    public static final String FOLDER_OUTBOX_ID = "5";
    public static final String FOLDER_DRAFT_ID = "6";
    public static final String FOLDER_TRASH_ID = "3";

    public static final String MSG = "m";
    public static final String MSG_ID = "id";
    public static final String MSG_ORIGINAL_ID = "origid";
    public static final String MSG_REPLYTYPE = "rt";
    public static final String MSG_RT_REPLY = "r";
    public static final String MSG_RT_FORWARD = "w";
    public static final String MSG_REPLIEDTO_ID = "irt";
    public static final String MSG_DRAFT_ID = "did";
    public static final String MSG_EMAILID = "mid";
    public static final String MSG_CONVERSATION_ID = "cid";
    public static final String MSG_SUBJECT = "su";
    public static final String MSG_DATE = "d";
    public static final String MSG_LOCATION = "l";
    public static final String MSG_MULTIPART = "mp";
    public static final String MSG_MPART_ISBODY = "body";

    public static final String MSG_CONSTRAINTS = "tcon";
    public static final String MSG_CON_TRASH = "t";

    public static final String MULTIPART_CONTENT_DISPLAY = "cd";
    public static final String MULTIPART_CD_ATTACHMENT = "attachment";
    public static final String MULTIPART_CD_INLINE = "inline";
    public static final String MULTIPART_MSG_ATTACHED = "message/rfc822";
    public static final String MULTIPART_CONTENT_TYPE = "ct";
    public static final String MULTIPART_CT_ALTERNATIVE = "multipart/alternative";
    public static final String MULTIPART_CT_TEXTPLAIN = "text/plain";
    public static final String MULTIPART_CT_IMAGE = "image/";
    public static final String MULTIPART_CT_TEXT = "text/";
    public static final String MULTIPART_CONTENT_INLINE = "ci";
    public static final String MULTIPART_PART_ID = "part";
    public static final String MULTIPART_FILENAME = "filename";
    public static final String MULTIPART_MSG_ID = "mid";
    public static final String MULTIPART_SIZE = "s";
    public static final String MULTIPART_CONTENT = "content";


    public static final String MSG_NEW_ATTACHMENTS = "attach";
    public static final String MSG_NEW_UPLOAD_ID = "aid";


    public static final String ATTR_MAIL_QUOTA = "zimbraMailQuota";



    public static final String MSG_EMAILS = "e";
    public static final String MSG_EMAIL_TYPE = "t";
    public static final String MSG_EMAIL_ADDR = "a";
    public static final String MSG_EMAIL_COMMENT = "p";

    public static final String MSG_FLAGS = "f";
    public static final String MSG_FLAG_UNREAD = "u";
    public static final String MSG_FLAG_FLAGGED = "f";
    public static final String MSG_FLAG_HASATTACHMENT = "a";
    public static final String MSG_FLAG_REPLIED = "r";
    public static final String MSG_FLAG_SENTBYME = "s";
    public static final String MSG_FLAG_FORWARDED = "w";
    public static final String MSG_FLAG_CALENDARINVITE = "v";
    public static final String MSG_FLAG_DRAFT = "d";
    public static final String MSG_FLAG_IMAP_DELETED = "x";
    public static final String MSG_FLAG_NOTIFICATIONSENT = "n";
    public static final String MSG_FLAG_URGENT = "!";
    public static final String MSG_FLAG_LOWPRIORITY = "?";
    public static final String MSG_FLAG_PRIORITY = "+";

    public static final String ADDR_TYPE_FROM = "f";
    public static final String ADDR_TYPE_TO = "t";
    public static final String ADDR_TYPE_CC = "c";
    public static final String ADDR_TYPE_BCC = "b";
    public static final String ADDR_TYPE_REPLYTO = "r";
    public static final String ADDR_TYPE_SENDER = "s";
    public static final String ADDR_TYPE_READRECEIPT = "n";
    public static final String ADDR_TYPE_RESENT_FROM = "rf";

    public static final String ACCT_NAME = "name";
    public static final String ACCT_ID = "id";
    public static final String ACCT_ATTRIBUTES = "a";
    public static final String ACCT_ATTRIBUTES_NAME = "n";
    public static final String ACCT_ATTRIBUTES_CONTENT = "_content";
    public static final String ACCT_ALIAS = "alias";
    public static final String ACCT_ISEXTERNAL = "isExternal";

    public static final String ACCT_INFO_ATTRIBUTES = "_attrs";
    public static final String ACCT_INFO_ATTRIBUTE_NAME = "name";
    public static final String ACCT_INFO_ZIMBRA_ID = "zimbraId";
    public static final String ACCT_INFO_ACCOUNT = "account";

    public static final String DISPLAY_INLINE = "i";
    public static final String DISPLAY_ATTACHMENT = "a";

    public static final String DISTRIBUTION_LIST = "dl";
    public static final String DLIST_LIMIT_MEMBERS = "limit";
    public static final String DLIST_DISPLAYNAME = "displayName";
    public static final String DLIST_MEMBER_URL = "memberURL";
    public static final String DLIST_IS_ACL_GROUP = "zimbraIsACLGroup";
    public static final String DLIST_DYNAMIC = "dynamic";
    public static final String DLIST_ID = "id";

    public static final String FOLDER = "folder";
    public static final String FOLDER_DEPTH = "depth";
    public static final String FOLDER_PATH = "path";
    public static final String FOLDER_NAME = "name";
    public static final String FOLDER_ABSPATH = "absFolderPath";
    public static final String FOLDER_PARENTID = "l";
    public static final String FOLDER_PARENTUUID = "luuid";
    public static final String FOLDER_FLAGS = "f";
    public static final String FOLDER_COLOR = "color";
    public static final String FOLDER_RGB = "rgb";
    public static final String FOLDER_NBUNREAD = "u";
    public static final String FOLDER_NBITEMS = "n";
    public static final String FOLDER_VIEW = "view";
    public static final String FOLDER_MODIFIED_DATE = "md";
    public static final String FOLDER_SIZEITEMS = "s";
    public static final String FOLDER_SHARE_PERM = "perm";
    public static final String FOLDER_GRANT = "grant";

    public static final String CONVERSATION = "c";
    public static final String CONVERSATION_ID = "id";
    public static final String CONVERSATION_SUBJECT = "su";
    public static final String CONVERSATION_NBMSG = "n";
    public static final String CONVERSATION_NBUNREAD = "u";
    public static final String CONVERSATION_NBTOTAL = "total";
    public static final String CONVERSATION_FLAGS = "f";
    public static final String CONVERSATION_DATE = "d";

    public static final String CONVERSATION_EXPAND_MESSAGES = "fetch";
    public static final String CONV_EXPAND_ALL = "all";

    public static final String SHARE_GRANTEE_TYPE = "gt";
    public static final String GRANTEE_TYPE_USER = "usr";
    public static final String GRANTEE_NAME = "d";

    public static final String FETCH_IF_EXISTS = "fie";

    public static final String MOUNTPOINT = "link";
    public static final String MOUNTPOINT_OWNER_ID = "zid";
    public static final String MOUNTPOINT_OWNER_MAIL = "owner";
    public static final String MOUNTPOINT_REMOTE_FOLDER_ID = "rid";

    public static final String IMPORT_CONTENT_TYPE = "ct";
    public static final String IMPORT_CT_CSV = "csv";
    public static final String IMPORT_FOLDER_ID = "l";
    public static final String IMPORT_CONTACTS_DATA = "content";

    public static final String VIEW = "view";
    public static final String VIEW_SEARCHFOLDER = "search folder";
    public static final String VIEW_TAG = "tag";
    public static final String VIEW_CONVERSATION = "conversation";
    public static final String VIEW_MESSAGE = "message";
    public static final String VIEW_CONTACT = "contact";
    public static final String VIEW_DOCUMENT = "document";
    public static final String VIEW_APPOINTMENT = "appointment";
    public static final String VIEW_VIRTUALCONV = "virtual conversation";
    public static final String VIEW_REMOTEFOLDER = "remote folder";
    public static final String VIEW_WIKI = "wiki";
    public static final String VIEW_TASK = "task";
    public static final String VIEW_CHAT = "chat";

    public static final String ACCT_STATUS_LOCKED = "locked";
    public static final String ACCT_STATUS_ACTIVE = "active";

    public static final String SEARCH_QUERY = "query";
    public static final String SEARCH_LIMIT = "limit";
    public static final String SEARCH_OFFSET = "offset";
    public static final String SEARCH_TYPES = "types";
    // Only ONE of message, conversation may be set. If both are set, the first is used
    public static final String SEARCH_TYPE_CONVERSATION = "conversation";
    public static final String SEARCH_TYPE_MESSAGE = "message";
    public static final String SEARCH_TYPE_CONTACT = "contact";
    public static final String SEARCH_TYPE_APPOINTMENT = "appointment";
    public static final String SEARCH_TYPE_TASK = "task";
    public static final String SEARCH_TYPE_WIKI = "wiki";
    public static final String SEARCH_TYPE_DOCUMENT = "document";
    public static final String SEARCH_RECIPIENTS_TO_RETURN = "recip";
    public static final String SEARCH_RECIP_FROM = "0";
    public static final String SEARCH_RECIP_TO = "1";
    public static final String SEARCH_RECIP_ALL = "2";
}
