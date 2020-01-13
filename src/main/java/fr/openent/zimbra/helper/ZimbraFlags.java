package fr.openent.zimbra.helper;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class ZimbraFlags {

    public static Boolean isRead(String flags) {
        return !flags.contains(MSG_FLAG_UNREAD);
    }

    public static Boolean isDraft(String flags) {
        return flags.contains(MSG_FLAG_DRAFT);
    }

    public static Boolean isSentByMe(String flags) {
        return flags.contains(MSG_FLAG_SENTBYME);
    }

    public static Boolean hasAttachment(String flags) {
        return flags.contains(MSG_FLAG_HASATTACHMENT);
    }

    public static Boolean isReplied(String flags) {
        return flags.contains(MSG_FLAG_REPLIED);
    }
}
