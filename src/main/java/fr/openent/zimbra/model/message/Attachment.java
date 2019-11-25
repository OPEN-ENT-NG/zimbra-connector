package fr.openent.zimbra.model.message;

import fr.openent.zimbra.Zimbra;
import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.model.constant.FrontConstants.*;
import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

class Attachment {

    private String id;
    private String messageId;
    private boolean isInline;
    private String inlineId;
    private String filename;
    private String contentType;
    private Long size;

    Attachment(String messageId, JsonObject part) {
        this.messageId = messageId;
        this.id = part.getString(MULTIPART_PART_ID, "");
        this.isInline = MULTIPART_CD_INLINE.equals(part.getString(MULTIPART_CONTENT_DISPLAY));
        this.inlineId= part.getString(MULTIPART_CONTENT_INLINE, "");
        this.filename = part.getString(MULTIPART_FILENAME, this.id);
        this.contentType = part.getString(MULTIPART_CONTENT_TYPE, "");
        this.size = part.getLong(MULTIPART_SIZE, 0L);
    }

    boolean isInline() {
        return isInline;
    }

    String getInlineId() {
        return inlineId;
    }

    JsonObject getFrontInfos() {
        return new JsonObject()
            .put(MESSAGE_ATT_ID,id)
            .put(MESSAGE_ATT_FILENAME, filename)
            .put(MESSAGE_ATT_CONTENTTYPE, contentType)
            .put(MESSAGE_ATT_SIZE, size);
    }

    String getUrl() {
        return Zimbra.URL + "/message/" + messageId + "/attachment/" + id;
    }
}
