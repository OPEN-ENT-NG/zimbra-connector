package fr.openent.zimbra.model.message;


import fr.openent.zimbra.helper.ZimbraFlags;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class Conversation {

    private String          id;
    private String          subject;
    private Integer         nbMsg;
    private Integer         nbMsgTotal;
    private Boolean         isRead;
    private String          zimbraFlags;
    private List<Message>   messageList = new ArrayList<>();

    public String getId() { return id; }
    public String getSubject() { return subject; }
    public Integer getNbMsg() { return nbMsg; }
    public Integer getNbMsgTotal() { return nbMsgTotal; }
    public Boolean getRead() { return isRead; }
    public String getZimbraFlags() { return zimbraFlags; }
    public List<Message> getMessageList() { return messageList; }

    private Conversation() {
    }

    public static Conversation fromZimbra(JsonObject zimbraData) throws IllegalArgumentException {
        Conversation conversation =  new Conversation();
        conversation.id = zimbraData.getString(CONVERSATION_ID, "");
        conversation.subject = zimbraData.getString(CONVERSATION_SUBJECT, "");
        conversation.nbMsg = zimbraData.getInteger(CONVERSATION_NBMSG, 0);
        conversation.nbMsgTotal = zimbraData.getInteger(CONVERSATION_NBMSTOTAL, 0);
        conversation.zimbraFlags = zimbraData.getString(CONVERSATION_FLAGS, "");
        conversation.isRead = ZimbraFlags.isRead(conversation.zimbraFlags);
        conversation.generateMessageListFromZimbra(zimbraData.getJsonArray(MSG, new JsonArray()));
        return conversation;
    }

    private void generateMessageListFromZimbra(JsonArray zimbraMessageList) {
        zimbraMessageList.forEach( item -> {
            if(item instanceof JsonObject) {
                JsonObject message = (JsonObject)item;
                messageList.add(Message.fromZimbra(message));
            }
        });
    }
}
