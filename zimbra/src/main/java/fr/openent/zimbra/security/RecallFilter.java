package fr.openent.zimbra.security;

import fr.openent.zimbra.core.constants.RecallRights;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;

public class RecallFilter implements ResourcesProvider {

    @Override
    public void authorize(HttpServerRequest request, Binding binding, UserInfos user,
                          Handler<Boolean> handler) {
        handler.handle(WorkflowActionUtils.hasRight(user, RecallRights.ZIMBRA_RECALL_ADMIN) || user.isADMC() || user.isADML());
    }

}
