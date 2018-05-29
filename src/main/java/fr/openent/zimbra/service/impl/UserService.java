package fr.openent.zimbra.service.impl;

import org.entcore.common.user.UserInfos;

public class UserService {

    private String getUserDomain(UserInfos user) {
        return "";
    }

    public String getUserZimbraAddress(UserInfos user) {
        //todo replace placeholder
        return "thomas.lecocq2@ng.preprod-ent.fr";
        //return user.getUserId() + "@" + getUserDomain(user);
    }
}
