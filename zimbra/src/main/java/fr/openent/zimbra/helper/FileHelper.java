package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;

public class FileHelper {
    private static final Logger log = LoggerFactory.getLogger(FileHelper.class);

    private FileHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static Future<JsonObject> writeBuffer(Storage storage, Buffer buff, final String contentType, final String filename) {
        Promise<JsonObject> promise = Promise.promise();
        buff = buff == null ? Buffer.buffer("") : buff;
        storage.writeBuffer(buff, contentType, filename, res -> {
            if (!Field.ERROR.equals(res.getString(Field.STATUS))) {
                promise.complete(res);
            } else {
                String messageToFormat = "[Zimbra@%s::writeBuffer] Error while storing file to workspace : %s";
                PromiseHelper.reject(log, messageToFormat, FileHelper.class.getSimpleName(), new Exception(res.getString(Field.ERROR)), promise);
            }
        });

        return promise.future();
    }

    /**
     * Handler which adds document into the MongoDB after downloading it from the NC server
     * @param uploaded      Data about the download (metadata, title ...)
     * @param user          User infos
     * @param fileName      Name of the file on the NC server
     * @return              The handler
     */
    public static Future<JsonObject> addFileReference(JsonObject uploaded, UserInfos user, String fileName,
                                                      WorkspaceHelper workspaceHelper) {
        Promise<JsonObject> promise = Promise.promise();
        workspaceHelper.addDocument(uploaded, user, fileName, Field.APP, false, null,
                resDoc -> {
                    if (resDoc.succeeded()) {
                        promise.complete(resDoc.result().body());
                    } else {
                        String messageToFormat = "[Zimbra@%s::addDocument] Error while adding document : %s";
                        PromiseHelper.reject(log, messageToFormat, FileHelper.class.getSimpleName(), resDoc, promise);
                    }});

        return promise.future();
    }
}
