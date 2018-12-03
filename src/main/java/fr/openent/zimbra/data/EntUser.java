package fr.openent.zimbra.data;

import fr.openent.zimbra.Zimbra;
import org.entcore.common.user.UserInfos;

public class EntUser {

    private String userId;
    private MailAddress userZimbraAddress;

    public EntUser(UserInfos userInfos) throws IllegalArgumentException {
        userId = userInfos.getUserId();
        userZimbraAddress = MailAddress.createFromLocalpartAndDomain(userId, Zimbra.domain);
    }

    public String getUserId() {
        return userId;
    }

    public String getUserAddress() {
        return userZimbraAddress.toString();
    }
}
