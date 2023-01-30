package fr.openent.zimbra.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public interface QueueService {
    /**
     * Creates actions and tasks for request queue
     * @param user the user {@link UserInfos}
     * @param info the object with the needed info {@link JsonObject}
     *             {
     *                 type: String,
     *                 data: JsonObject,
     *                 approved: Boolean //default = false
     *             }
     * @return {@link Future<Void>}
     */
    Future<Void> putRequestInQueue(UserInfos user, JsonObject info);

    /**
     * Creates new action in related table for request queue
     * @param user the user {@link UserInfos}
     * @param type the request type {@link String}
     * @param approved if the request has been approved (default = false) {@link Boolean}
     * @return {@link Future<Integer>} the id of the created action
     */
    Future<Integer> createActionInQueue(UserInfos user, String type, Boolean approved);

    /**
     * Creates ICal type task in related table for request queue
     * @param actionId the id of the parent action {@link Integer}
     * @param requestInfo the data needed {@link JsonObject}
     *              {
     *                 name: "GetICalRequest",
     *                 content: {
     *                      _jsns: "urn:zimbraMail"
     *                 }
     *               }
     * @return {@link Future<Void>}
     */
    Future<Void> createICalTask(Integer actionId, JsonObject requestInfo);
}
