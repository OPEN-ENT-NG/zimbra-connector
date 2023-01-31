package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.service.QueueService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public class QueueHelper {

//    public Future<Void> createActionInQueue(UserInfos user, String type, JsonObject info) {
//        Promise promise = Promise.promise();
//
//        createTaskInQueue(type, info);
//
//        return promise.future();
//    }
//
//    public Future<Void> createTaskInQueue(String type, JsonObject info) {
//        Promise promise = Promise.promise();
//
//        //create task
//        switch (type) {
//            case Field.ICAL:
//                //todo
//                //create task
//                QueueService.
//                        //create ical request
//                        .compose()
//                        .onSuccess()
//                        .onFailure();
//
////                QueueService.putICalRequestInQueue(info);
//                break;
//            default:
//                //error log
//        }
//
//        return promise.future();
//    }

}
