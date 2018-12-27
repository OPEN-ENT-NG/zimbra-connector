package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.EntUser;
import fr.openent.zimbra.helper.PreauthHelper;

import org.entcore.common.user.UserInfos;

import java.io.IOException;

public class ExpertModeService {



    public ExpertModeService() {
    }

    public String getPreauthUrl(UserInfos userInfos) throws IOException {
        EntUser user = new EntUser(userInfos);
        String preauthKey = Zimbra.appConfig.getPreauthKey();
        return PreauthHelper.generatePreauthUrl(user.getUserStrAddress(), preauthKey);
    }
}
