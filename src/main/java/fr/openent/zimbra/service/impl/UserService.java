package fr.openent.zimbra.service.impl;

import org.entcore.common.user.UserInfos;

public class UserService {

    private String getUserDomain(UserInfos user) {
        return "";
    }

    public String getUserZimbraAddress(UserInfos user) {
        return user.getUserId() + "@" + getUserDomain(user);
    }
}
