package fr.openent.zimbra.helper;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class ZimbraFlags {

    public static Boolean isRead(String flags) {
        return !flags.contains(MSG_FLAG_UNREAD);
    }
}
