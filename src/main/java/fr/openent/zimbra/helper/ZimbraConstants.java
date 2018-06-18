package fr.openent.zimbra.helper;

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
    public static final String OPERATION = "op";


    public static final String GETFOLDER_UNREAD = "u";
    public static final String GETFOLDER_NBMSG = "n";
    public static final String GETFOLDER_FOLDERPATH = "absFolderPath";

    public static final String MSG = "m";
    public static final String MSG_ID = "id";
    public static final String MSG_DRAFT_ID = "did";
    public static final String MSG_CONVERSATION_ID = "id";
    public static final String MSG_SUBJECT = "su";
    public static final String MSG_DATE = "d";
    public static final String MSG_LOCATION = "l";
    public static final String MSG_MULTIPART = "mp";
    public static final String MSG_MPART_ISBODY = "body";

    public static final String MULTIPART_CONTENT_DISPLAY = "cd";
    public static final String MULTIPART_CONTENT_TYPE = "ct";
    public static final String MULTIPART_CONTENT_INLINE = "ci";
    public static final String MULTIPART_PART_ID = "part";
    public static final String MULTIPART_FILENAME = "filename";
    public static final String MULTIPART_MSG_ID = "mid";
    public static final String MULTIPART_SIZE = "s";

    public static final String MULTIPART_INLINE = "inline";
    public static final String MULTIPART_ATTACHMENT = "attachment";

    public static final String MSG_NEW_ATTACHMENTS = "attach";
    public static final String MSG_NEW_UPLOAD_ID = "aid";




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

    public static final String NAMESPACE_ZIMBRA = "urn:zimbra";
    public static final String NAMESPACE_ADMIN = "urn:zimbraAdmin";
    public static final String NAMESPACE_ACCOUNT = "urn:zimbraAccount";
    public static final String NAMESPACE_MAIL = "urn:zimbraMail";

    public static final String ACCT_NAME = "name";
    public static final String ACCT_ATTRIBUTES = "a";
    public static final String ACCT_ATTRIBUTES_NAME = "n";
    public static final String ACCT_ATTRIBUTES_CONTENT = "_content";

    public static final String DISPLAY_INLINE = "i";
    public static final String DISPLAY_ATTACHMENT = "a";


}
