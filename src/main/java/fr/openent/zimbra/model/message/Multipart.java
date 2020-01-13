package fr.openent.zimbra.model.message;

import fr.openent.zimbra.Zimbra;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

public class Multipart {

    private String body;
    private String messageId;
    private List<Attachment> attachments;

    private boolean fromConv = false;

    private static Logger log = LoggerFactory.getLogger(Multipart.class);

    public Multipart(String messageId, JsonArray zimbraMultiparts) {
        init(messageId, zimbraMultiparts);
    }
    public Multipart(String messageId, JsonArray zimbraMultiparts, boolean fromConv) {
        this.fromConv = fromConv;
        init(messageId, zimbraMultiparts);
    }

    private void init(String messageId, JsonArray zimbraMultiparts) {
        this.body = "";
        this.messageId = messageId;
        this.attachments = new ArrayList<>();
        processParts(zimbraMultiparts, false);
        processInlineAttachments();
    }

    public String getBody() {
        return body;
    }

    public JsonArray getAttachmentsJson() {
        JsonArray attchs = new JsonArray();
        for(Attachment att : attachments) {
            if(!att.isInline()) {
                attchs.add(att.getFrontInfos());
            }
        }
        return attchs;
    }

    private void processParts(JsonArray zimbraMultiparts, boolean isAlternative) {
        if(zimbraMultiparts == null) {
            return;
        }
        JsonObject lastPart = new JsonObject();
        for(Object obj : zimbraMultiparts) {
            if(!(obj instanceof JsonObject)) continue;
            JsonObject mpart = (JsonObject)obj;
            if(!isAlternative) {
                processPart(mpart);
            } else {
                if(fromConv && Zimbra.appConfig.getInvertAltPartInConvMsg()) {
                    if(lastPart.isEmpty()) {
                        lastPart = mpart;
                    }
                } else {
                    lastPart = mpart;
                }

            }
        }
        if(isAlternative) {
            processPart(lastPart);
        }
    }

    private void processPart(JsonObject mpart) {
        String mpartContentDisplay = mpart.getString(MULTIPART_CONTENT_DISPLAY, "");
        if(mpart.getBoolean(MSG_MPART_ISBODY, false)) {
            processBody(mpart);
        } else if (MULTIPART_CD_ATTACHMENT.equals(mpartContentDisplay)){
            this.addAttachment(mpart);
        } else if (mpart.containsKey(MSG_MULTIPART)) {
            JsonArray innerMultiparts = mpart.getJsonArray(MSG_MULTIPART);
            String contentType = mpart.getString(MULTIPART_CONTENT_TYPE, "");
            processParts(innerMultiparts, MULTIPART_CT_ALTERNATIVE.equals(contentType));
        } else {
            // If we can't recognize the type, add the part as attachment so it's not lost
            this.addAttachment(mpart);
        }
    }

    private void processBody(JsonObject mpart) {
        String contentType = mpart.getString(MULTIPART_CONTENT_TYPE, "");
        if(contentType.startsWith(MULTIPART_CT_IMAGE)) {
            Attachment bodyAttch = addAttachment(mpart);
            body += "<img src=\"" + bodyAttch.getUrl() + "\"/>";
        } else if (contentType.startsWith(MULTIPART_CT_TEXT)) {
            String content = mpart.getString(MULTIPART_CONTENT, "");
            if (MULTIPART_CT_TEXTPLAIN.equals(contentType)) {
                content = escapeHtml4(content);
                content = content.replace("\n","<br/>");
            }
            this.body += content;
        } else {
            log.error("Zimbra multipart : unknown body type : " + mpart.toString());
        }
    }

    private Attachment addAttachment(JsonObject part) {
        Attachment attch = new Attachment(messageId, part);
        attachments.add(attch);
        return attch;
    }

    private void processInlineAttachments() {
        for(Attachment attch : attachments) {
            if(attch.isInline() && !attch.getInlineId().isEmpty()) {
                String cid = attch.getInlineId();
                String regex = "<img([^>]*)\\ssrc=\"cid:" + cid.substring(1, cid.length() - 1);
                String replaceRegex = "<img$1 src=\"" + attch.getUrl();
                body = body.replaceAll(regex, replaceRegex);
            }
        }
    }
}
