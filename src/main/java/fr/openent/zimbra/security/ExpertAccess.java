package fr.openent.zimbra.security;


import fr.wseduc.webutils.http.Binding;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

public class ExpertAccess implements ResourcesProvider {

    @Override
    public void authorize(HttpServerRequest request, Binding binding, UserInfos user,
                          Handler<Boolean> handler) {

        if (WorkflowActionUtils.hasRight(user, WorkflowActions.EXPERT_ACCESS_RIGHT.toString())) {
            handler.handle(true);
        } else {
            handler.handle(false);
        }
    }

}
