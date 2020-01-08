package fr.openent.zimbra.helper;

import fr.openent.zimbra.model.message.Recipient;
import fr.openent.zimbra.model.message.ZimbraEmail;
import io.vertx.core.json.JsonArray;

import java.util.*;

public class RecipientHelper {

    public static JsonArray getUsersJson(List<ZimbraEmail> emailsList, Map<String, Recipient> userMapping,
                                         String zimbraType) {
        JsonArray users = new JsonArray();
        emailsList.forEach( email -> {
            if(zimbraType.equals(email.getAddrType())) {
                String emailAddr = email.getAddress();
                if(userMapping.containsKey(emailAddr)) {
                    users.add(userMapping.get(emailAddr).getUserId());
                }
            }
        });
        return users;
    }

    public static JsonArray getDisplayNamesJson(List<ZimbraEmail> emailsList, Map<String, Recipient> userMapping) {
        JsonArray displayNames = new JsonArray();
        Set<String> processedAddress = new HashSet<>();
        emailsList.forEach( email -> {
            String address = email.getAddress();
            if(!processedAddress.contains(address)) {
                JsonArray name = new JsonArray();
                if (userMapping.containsKey(address)) {
                    name.add(userMapping.get(address).getUserId());
                } else {
                    name.add(address);
                }
                String userName = email.getComment().isEmpty() ? address : email.getComment();
                name.add(userName);
                displayNames.add(name);
                processedAddress.add(address);
            }
        });
        return displayNames;
    }

}
