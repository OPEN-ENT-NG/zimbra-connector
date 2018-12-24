package fr.openent.zimbra.model;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.soap.SoapError;
import fr.openent.zimbra.model.soap.SoapRequest;
import fr.openent.zimbra.service.synchro.SynchroGroupService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserUtils;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.service.data.Neo4jZimbraService.*;

public class Group {
    private String id;



    private static Logger log = LoggerFactory.getLogger(Group.class);

    public Group(JsonObject json) throws IllegalArgumentException {
        try {
            id = json.getString(GROUP_ID, "");
            String rawName = json.getString(GROUP_NAME, "");
            if(id.isEmpty() || rawName.isEmpty()) {
                throw new IllegalArgumentException("Invalid Json for Group");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Json for Group");
        }
    }

    protected Group(String groupId) throws IllegalArgumentException {
        if(groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("Invalid id for Group");
        }
        id = groupId;
    }

    public String getId() {
        return id;
    }

    public static List<Group> processJsonGroups(JsonArray groupArray) throws IllegalArgumentException {
        List<Group> resultList = new ArrayList<>();
        for(Object o : groupArray) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a Group list");
            }
            JsonObject groupJson = (JsonObject)o;
            resultList.add(new Group(groupJson));
        }
        return resultList;
    }
}
