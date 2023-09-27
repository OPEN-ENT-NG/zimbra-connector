package fr.openent.zimbra.helper;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.FrontConstants;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.openent.zimbra.model.constant.FrontConstants.MESSAGE_BODY;

public class CidHelper {
    private static final Logger log = LoggerFactory.getLogger(CidHelper.class);
    private static final Pattern pattern = Zimbra.appConfig.getCidPattern();

    private CidHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static List<String> getMessageCids(JsonObject message){
        try {
            Matcher matcher = pattern.matcher(message.getString(MESSAGE_BODY));
            List<String> cids = new ArrayList<>();
            while (matcher.find()) {
                cids.add(matcher.group(1));
            }
            return cids;
        } catch (NullPointerException npe) {
            String errMessage = "[Zimbra@CidHelper::getMessageCids] Failed to get message cids for pattern " + pattern;
            log.error(errMessage + " : " + npe.getMessage());
            return new ArrayList<>();
        }
    }

    public static Map<String, String> mapCidToImageUrl(String messageId, List<String> cids){
        String zimbraUrlGetImage = Zimbra.appConfig.getZimbraUri() + Zimbra.appConfig.getZimbraDocumentConfig().getDocumentDownloadEndpoint();

        HashMap<String, String> mapCidToImageUrl = new HashMap<>();
        int partCount = 2;
        for(String cid : cids){
            String imageUrl = zimbraUrlGetImage + messageId + "&part=" + partCount;
            mapCidToImageUrl.put(cid, imageUrl);
            partCount++;
        }
        return mapCidToImageUrl;
    }

    public static Map<String,String> mapCidToBase64(Map<String,Buffer> mapCidToImageBuffer){
        HashMap<String, String> mapCidToBase64Images = new HashMap<>();

        mapCidToImageBuffer.forEach((cid, imageBuffer) -> mapCidToBase64Images.put(cid, Base64.getEncoder().encodeToString(imageBuffer.getBytes())));

        return mapCidToBase64Images;
    }

    public static JsonObject replaceCidByBase64(JsonObject message, Map<String, String> mapCidToBase64){
        String body = message.getString(MESSAGE_BODY);
        for (String cid : mapCidToBase64.keySet()){
            body = body.replace("cid:" + cid, "data:image/png;base64," + mapCidToBase64.get(cid));
        }
        message.put(MESSAGE_BODY, body);
        return message;
    }
}
