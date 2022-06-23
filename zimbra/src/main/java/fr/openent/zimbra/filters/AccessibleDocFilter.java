package fr.openent.zimbra.filters;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

import static org.entcore.common.mongodb.MongoDbResult.validActionResultHandler;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.List;

public class AccessibleDocFilter implements ResourcesProvider {

    private MongoDb mongo = MongoDb.getInstance();
    private static final Logger log = LoggerFactory.getLogger(AccessibleDocFilter.class);

    /**
     * Used for shared documents. Gets the documents the user is allowed to send and get from workspace (security).
     * Takes into account if the user is part of a group
     * @param request
     * @param binding
     * @param user
     * @param handler
     */
    @Override
    public void authorize(HttpServerRequest request, Binding binding, UserInfos user, Handler<Boolean> handler) {

        QueryBuilder builder = new QueryBuilder();
        String id = request.params().get("idAttachment");

        List<DBObject> groups = new ArrayList<>();
        groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
        for (String gpId : user.getGroupsIds()) {
            groups.add(QueryBuilder.start("groupId").is(gpId).get());
        }
        DBObject subQuery = new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get();
        builder.and(QueryBuilder.start("_id").is(id).get())
                .and(new QueryBuilder().or(
                        QueryBuilder.start("owner").is(user.getUserId()).get(),
                        QueryBuilder.start("shared").elemMatch(subQuery).get()).get());

        mongo.count("documents", MongoQueryBuilder.build(builder), validActionResultHandler(result -> {
            if (result.isLeft()) {
                log.error("An error has occured while finding targeted document: ", result.left().getValue());
                handler.handle(false);
            } else {
                if (result.right().getValue().getInteger("count") <= 0) {
                    log.error("An error has occured while finding targeted document. Access denied");
                    handler.handle(false);
                } else {
                    handler.handle(true);
                }
            }
        }));
    }};