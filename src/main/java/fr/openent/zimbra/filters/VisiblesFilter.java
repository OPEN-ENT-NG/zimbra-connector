package fr.openent.zimbra.filters;

import static org.entcore.common.user.UserUtils.findVisibles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.request.RequestUtils;


public class VisiblesFilter implements ResourcesProvider{

    private Neo4j neo;

    public VisiblesFilter() {
        neo = Neo4j.getInstance();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void authorize(HttpServerRequest request, Binding binding,
                          final UserInfos user, final Handler<Boolean> handler) {

        final String parentMessageId = request.params().get("In-Reply-To");
        final Set<String> ids = new HashSet<>();
        final String customReturn = "WHERE visibles.id IN {ids} RETURN DISTINCT visibles.id";
        final JsonObject params = new JsonObject();

        RequestUtils.bodyToJson(request, message -> {
                ids.addAll(message.getJsonArray("to", new fr.wseduc.webutils.collections.JsonArray()).getList());
                ids.addAll(message.getJsonArray("cc", new fr.wseduc.webutils.collections.JsonArray()).getList());

                final Handler<Void> checkHandler = v -> {
                        params.put("ids", new fr.wseduc.webutils.collections.JsonArray(new ArrayList<>(ids)));
                        findVisibles(neo.getEventBus(), user.getUserId(), customReturn, params, true, true, false, visibles -> {
                                handler.handle(visibles.size() == ids.size());
                        });
                };

                if(parentMessageId == null || parentMessageId.trim().isEmpty()){
                    checkHandler.handle(null);
                }
        });

    }

}
