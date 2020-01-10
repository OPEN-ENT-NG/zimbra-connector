package fr.openent.zimbra.model.message;


import fr.openent.zimbra.helper.ZimbraFlags;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

import static fr.openent.zimbra.model.constant.ZimbraConstants.*;

public class Conversation {

    private String          id;
    private String          subject;
    private Integer         nbMsg;
    private Integer         nbUnread;
    private Integer         nbTotal;
    private Boolean         isRead;
    private Long            date;
    private String          zimbraFlags;
    private List<Message>   messageList = new ArrayList<>();
    private List<ZimbraEmail>   emailsList = new ArrayList<>();
    @SuppressWarnings("FieldCanBeLocal")
    private Map<String, Recipient> userMapping = new HashMap<>();

    public String getId() { return id; }
    public String getSubject() { return subject; }
    public Integer getNbMsg() { return nbMsg; }
    public Integer getNbUnread() { return nbUnread; }
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
        conversation.nbUnread = zimbraData.getInteger(CONVERSATION_NBUNREAD, 0);
        conversation.nbTotal = zimbraData.getInteger(CONVERSATION_NBTOTAL, 0);
        conversation.date = zimbraData.getLong(CONVERSATION_DATE, 0L);
        conversation.zimbraFlags = zimbraData.getString(CONVERSATION_FLAGS, "");
        conversation.isRead = ZimbraFlags.isRead(conversation.zimbraFlags);
        conversation.generateMessageListFromZimbra(zimbraData.getJsonArray(MSG, new JsonArray()));
        conversation.generateEmailListFromZimbra(zimbraData.getJsonArray(MSG_EMAILS, new JsonArray()));
        return conversation;
    }

    public Set<String> getAllAddresses() {
        Set<String> resultSet = new HashSet<>();
        emailsList.forEach( email -> resultSet.add(email.getAddress()));
        return resultSet;
    }

    public void setUserMapping(Map<String, Recipient> mapFromEmail) {
        userMapping = mapFromEmail;
        messageList.forEach( message -> message.setUserMapping(mapFromEmail));
    }

    public JsonArray getAllMessagesJson() {
        JsonArray result = new JsonArray();
        messageList.forEach( message -> result.add(message.getJsonObject()));
        return new JsonArray();
    }

    private void generateMessageListFromZimbra(JsonArray zimbraMessageList) {
        zimbraMessageList.forEach( item -> {
            if(item instanceof JsonObject) {
                JsonObject messageJson = (JsonObject)item;
                Message message = Message.fromZimbra(messageJson);
                message.setConversationId(id);
                messageList.add(message);
            }
        });
    }

    private void generateEmailListFromZimbra(JsonArray zimbraEmailList) {
        zimbraEmailList.forEach( item -> {
            if(item instanceof JsonObject) {
                JsonObject emailObject = (JsonObject)item;
                emailsList.add(ZimbraEmail.fromZimbra(emailObject));
            }
        });
    }
}
