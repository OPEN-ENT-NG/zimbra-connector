package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.data.EntUser;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.PreauthHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.wseduc.webutils.Either;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.io.IOException;

public class ExpertModeService {



    public ExpertModeService() {
    }

    public String getPreauthUrl(UserInfos userInfos) throws IOException {
        EntUser user = new EntUser(userInfos);
        String preauthKey = ServiceManager.getServiceManager().getConfig().getPreauthKey();
        return PreauthHelper.generatePreauthUrl(user.getUserAddress(), preauthKey);
    }
}
