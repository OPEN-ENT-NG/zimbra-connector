package fr.openent.zimbra.model.message;


import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

/**
 * Represents an email address as sent by Zimbra, with :
 *      address : actual email adress
 *      comment : displayed name for the address
 *      type : Type of recipient : (f)rom, (t)o, (c)c, (b)cc
 */
public class ZimbraEmail {
    private String address;
    private String comment;
    private String addrType;

    private ZimbraEmail() {}

    public static ZimbraEmail fromZimbra(JsonObject zimbraEmail) {
        ZimbraEmail recipient = new ZimbraEmail();
        recipient.address = zimbraEmail.getString(MSG_EMAIL_ADDR, "");
        recipient.addrType = zimbraEmail.getString(MSG_EMAIL_TYPE, "");
        recipient.comment = zimbraEmail.getString(MSG_EMAIL_COMMENT, "");
        return recipient;
    }

    public String getAddress() {
        return address;
    }

    public String getComment() {
        return comment;
    }

    public String getAddrType() {
        return addrType;
    }
}
