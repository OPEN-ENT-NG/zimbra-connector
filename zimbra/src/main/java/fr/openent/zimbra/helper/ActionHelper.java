package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.core.enums.ActionType;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.UUID;

public class ActionHelper {
    private static final Logger log = LoggerFactory.getLogger(ActionHelper.class);

    public static Action createActionFromJSON(UserInfos user, JsonObject info) {
        if (!JSONContainsActionData(info)) {
            throw new IllegalArgumentException(String.format("[Zimbra@%s::createActionFromJSON] JSON does not contain necessary fields",
                    ActionHelper.class.getSimpleName()));
        }
        Action action;
        try {
            ActionType actionType = ActionType.fromString(info.getString(Field.TYPE));
            boolean approved = info.getBoolean(Field.APPROVED);
            action = new Action(UUID.fromString(user.getUserId()), actionType, approved);
        } catch (Exception e) {
            String errMessage = String.format("[Zimbra@%s::createActionFromJSON]: JSON object does not contains action infos %s",
                    ActionHelper.class.getSimpleName(), e.getMessage());
            log.error(errMessage);
            action = null;
        }
        return action;
    }

    private static boolean JSONContainsActionData(JsonObject info) {
        return info.containsKey(Field.TYPE) &&
                info.containsKey(Field.DATA) &&
                info.containsKey(Field.APPROVED);
    }
}
