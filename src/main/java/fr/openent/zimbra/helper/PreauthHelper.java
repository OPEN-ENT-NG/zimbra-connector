package fr.openent.zimbra.helper;

import fr.wseduc.webutils.http.Renders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class PreauthHelper {

    private static final char[] hex =
            { '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' ,
                    '8' , '9' , 'a' , 'b' , 'c' , 'd' , 'e' , 'f'};

    private static  String computeDefaultPreAuth(String id, String timestamp, String key) {
        return computePreAuth(id, "name", timestamp, "0", "0", key);
    }


    private static String computePreAuth(String id, String by, String timestamp, String expires,
                                         String admin, String key)
    {
        StringBuilder prepraredPreauth = new StringBuilder();
        prepraredPreauth.append(id);
        if("1".equals(admin)) {
            prepraredPreauth.append("|").append(admin);
        }
        prepraredPreauth.append("|").append(by);
        prepraredPreauth.append("|").append(expires);
        prepraredPreauth.append("|").append(timestamp);
        return getHmac(prepraredPreauth.toString(), key.getBytes());
    }

    public static JsonObject generatePreauth(String emailAddress, String preauthKey) {
        String timestamp = System.currentTimeMillis()+"";
        String computedPreauth = computeDefaultPreAuth(emailAddress, timestamp, preauthKey);

        if(computedPreauth == null) {
            return null;
        }

        return new JsonObject()
                .put("id", emailAddress)
                .put("timestamp", timestamp)
                .put("preauthkey", computedPreauth);
    }

    private static String getHmac(String data, byte[] key) {
        try {
            ByteKey bk = new ByteKey(key);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(bk);
            return toHex(mac.doFinal(data.getBytes()));
        } catch (NoSuchAlgorithmException|InvalidKeyException e) {
            Logger log = LoggerFactory.getLogger(Renders.class);
            log.fatal("Error when computing preauth key ", e);
            return null;
        }
    }
    static class ByteKey implements SecretKey {
        private byte[] mKey;

        ByteKey(byte[] key) {
            mKey = key.clone();
        }

        public byte[] getEncoded() {
            return mKey;
        }

        public String getAlgorithm() {
            return "HmacSHA1";
        }

        public String getFormat() {
            return "RAW";
        }
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(hex[(b & 0xf0) >>> 4]);
            sb.append(hex[b & 0x0f] );
        }
        return sb.toString();
    }



}
