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

public class ZimbraConstants {
    public static final String FOLDER_INBOX = "/Inbox";
    public static final String FOLDER_OUTBOX = "/Sent";
    public static final String FOLDER_DRAFT = "/Drafts";
    public static final String FOLDER_TRASH = "/Trash";

    public static final String FOLDER_ROOT_ID = "1";
    public static final String FOLDER_INBOX_ID= "2";
    public static final String FOLDER_OUTBOX_ID = "5";
    public static final String FOLDER_DRAFT_ID = "6";
    public static final String FOLDER_TRASH_ID = "3";

    public static final String OP_TRASH = "trash";
    public static final String OP_RENAME = "rename";
    public static final String OP_MOVE = "move";
    public static final String OP_DELETE = "delete";
    public static final String OP_EMPTY = "empty";
    public static final String OP_READ = "read";
    public static final String OP_UNREAD = "!read";
    public static final String OPERATION = "op";


    public static final String GETFOLDER_UNREAD = "u";
    public static final String GETFOLDER_NBMSG = "n";
    public static final String GETFOLDER_FOLDERPATH = "absFolderPath";

    public static final String MSG = "m";
    public static final String MSG_ID = "id";
    public static final String MSG_ORIGINAL_ID = "origid";
    public static final String MSG_REPLYTYPE = "rt";
    public static final String MSG_RT_REPLY = "r";
    public static final String MSG_RT_FORWARD = "w";
    public static final String MSG_DRAFT_ID = "did";
    public static final String MSG_CONVERSATION_ID = "id";
    public static final String MSG_SUBJECT = "su";
    public static final String MSG_DATE = "d";
    public static final String MSG_LOCATION = "l";
    public static final String MSG_MULTIPART = "mp";
    public static final String MSG_MPART_ISBODY = "body";

    public static final String MSG_CONSTRAINTS = "tcon";
    public static final String MSG_CON_TRASH = "t";

    public static final String MULTIPART_CONTENT_DISPLAY = "cd";
    public static final String MULTIPART_MSG_ATTACHED = "message/rfc822";
    public static final String MULTIPART_CONTENT_TYPE = "ct";
    public static final String MULTIPART_CT_TEXTPLAIN = "text/plain";
    public static final String MULTIPART_CONTENT_INLINE = "ci";
    public static final String MULTIPART_PART_ID = "part";
    public static final String MULTIPART_FILENAME = "filename";
    public static final String MULTIPART_MSG_ID = "mid";
    public static final String MULTIPART_SIZE = "s";

    public static final String MULTIPART_INLINE = "inline";
    public static final String MULTIPART_ATTACHMENT = "attachment";

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


    public static final String ACCT_STATUS_LOCKED = "locked";
    public static final String ACCT_STATUS_ACTIVE = "active";

    // Error codes can be found in /opt/zimbra/docs/soap.txt
    public static final String ERROR_GENERIC = "service.FAILURE";
    public static final String ERROR_BADREQUEST = "service.INVALID_REQUEST";
    public static final String ERROR_UNKNOWNREQ = "service.UNKNOWN_DOCUMENT";
    public static final String ERROR_PARSEERROR = "service.PARSE_ERROR";
    public static final String ERROR_PERMDENIED = "service.PERM_DENIED";
    public static final String ERROR_AUTHREQUIRED = "service.AUTH_REQUIRED";
    public static final String ERROR_AUTHEXPIRED = "service.AUTH_EXPIRED";
    public static final String ERROR_WRONGHOST = "service.WRONG_HOST";
    public static final String ERROR_PROXYERROR = "service.PROXY_ERROR";
    public static final String ERROR_TOOMANYHOPS = "service.TOO_MANY_HOPS";
    public static final String ERROR_INTERRUPTED = "service.INTERRUPTED";
    public static final String ERROR_NOTINPROGRESS = "service.NOT_IN_PROGRESS";
    public static final String ERROR_ALREADYINPROGRESS = "service.ALREADY_IN_PROGRESS";
    public static final String ERROR_NOSPELLCHECKURL = "service.NO_SPELL_CHECK_URL";
    public static final String ERROR_UNREACHABLE = "service.RESOURCE_UNREACHABLE";
    public static final String ERROR_TEMP_UNVAVAILABLE = "service.TEMPORARILY_UNAVAILABLE";
    public static final String ERROR_NONREADONLY_OPDENIED = "service.NON_READONLY_OPERATION_DENIED";

    public static final String ERROR_AUTHFAILED = "account.AUTH_FAILED";
    public static final String ERROR_CHANGEPWD = "account.CHANGE_PASSWORD";
    public static final String ERROR_PWDLOCK = "account.PASSWORD_LOCKED";
    public static final String ERROR_PWDTEMPLOCK = "account.PASSWORD_CHANGE_TOO_SOON";
    public static final String ERROR_PWDRECENTUSED = "account.PASSWORD_RECENTLY_USED";
    public static final String ERROR_INVALIDPWD = "account.INVALID_PASSWORD";
    public static final String ERROR_INVALIDATTRNAME = "account.INVALID_ATTR_NAME";
    public static final String ERROR_INVALIDATTRVALUE = "account.INVALID_ATTR_VALUE";
    public static final String ERROR_MULTIPLEACCOUNTMATCHED = "account.MULTIPLE_ACCOUNTS_MATCHED";
    public static final String ERROR_NOSUCHACCOUNT = "account.NO_SUCH_ACCOUNT";
    public static final String ERROR_NOSUCHALIAS = "account.NO_SUCH_ALIAS";
    public static final String ERROR_NOSUCHDOMAIN = "account.NO_SUCH_DOMAIN";
    public static final String ERROR_NOSUCHCOS = "account.NO_SUCH_COS";
    public static final String ERROR_NOSUCHID = "account.NO_SUCH_IDENTITY";
    public static final String ERROR_NOSUCHSIGNATURE = "account.NO_SUCH_SIGNATURE";
    public static final String ERROR_NOSUCHDATASRC = "account.NO_SUCH_DATA_SOURCE";
    public static final String ERROR_NOSUCHSERVER = "account.NO_SUCH_SERVER";
    public static final String ERROR_NOSUCHZIMLET = "account.NO_SUCH_ZIMLET";
    public static final String ERROR_NOSUCHDLIST = "account.NO_SUCH_DISTRIBUTION_LIST";
    public static final String ERROR_NOSUCHCALENDARRSC = "account.NO_SUCH_CALENDAR_RESOURCE";
    public static final String ERROR_NOSUCHMEMBER = "account.NO_SUCH_MEMBER";
    public static final String ERROR_MEMBEREXISTS = "account.MEMBER_EXISTS";
    public static final String ERROR_ACCOUNTEXISTS = "account.ACCOUNT_EXISTS";
    public static final String ERROR_DOMAINEXISTS = "account.DOMAIN_EXISTS";
    public static final String ERROR_COSEXISTS = "account.COS_EXISTS";
    public static final String ERROR_SERVEREXISTS = "account.SERVER_EXISTS";
    public static final String ERROR_DLISTEXISTS = "account.DISTRIBUTION_LIST_EXISTS";
    public static final String ERROR_IDEXISTS = "account.IDENTITY_EXISTS";
    public static final String ERROR_SIGNATUREEXISTS = "account.SIGNATURE_EXISTS";
    public static final String ERROR_DATASRCEXISTS = "account.DATA_SOURCE_EXISTS";
    public static final String ERROR_DOMAINNOTEMPTY = "account.DOMAIN_NOT_EMPTY";
    public static final String ERROR_MAINTENANCEMODE = "account.MAINTENANCE_MODE";
    public static final String ERROR_ACCOUNTINACTIVE = "account.ACCOUNT_INACTIVE";
    public static final String ERROR_TOOMANYACCOUNT = "account.TOO_MANY_ACCOUNTS";
    public static final String ERROR_TOOMANYIDS = "account.TOO_MANY_IDENTITIES";
    public static final String ERROR_TOOMANYSIGNATURE = "account.TOO_MANY_SIGNATURES";
    public static final String ERROR_TOOMANYDATASRC = "account.TOO_MANY_DATA_SOURCES";
    public static final String ERROR_TOOMANYSRCHRESULTS = "account.TOO_MANY_SEARCH_RESULTS";
    public static final String ERROR_2FAAUTHFAILED = "account.TWO_FACTOR_AUTH_FAILED";
    public static final String ERROR_2FASETUPFAILED = "account.TWO_FACTOR_SETUP_REQUIRED";


}
