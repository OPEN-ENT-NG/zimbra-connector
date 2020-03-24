/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.helper;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class PreauthHelper {

    @SuppressWarnings("FieldCanBeLocal")
    private static String preauthUrl = "/service/preauth" + "" +
            "?account=%s" +
            "&by=name" +
            "&timestamp=%s" +
            "&expires=0" +
            "&preauth=%s";

    private static final char[] hex =
            { '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' ,
                    '8' , '9' , 'a' , 'b' , 'c' , 'd' , 'e' , 'f'};

    private static final Logger log = LoggerFactory.getLogger(PreauthHelper.class);

    private static  String computeDefaultPreAuth(String id, String timestamp, String key) {
        return computePreAuth(id, "name", timestamp, "0", "0", key);
    }


    @SuppressWarnings("SameParameterValue")
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

    public static String generatePreauthUrl(String emailAddress, String preauthKey) throws IOException{
        JsonObject preauthInfos = PreauthHelper.generatePreauth(emailAddress, preauthKey);
        if(preauthInfos == null) {
            log.error("Error when processing preauth url for " + emailAddress);
            throw new IOException("Error when processing preauth url");
        } else {
            return String.format(preauthUrl,
                    URLEncoder.encode(preauthInfos.getString("id"), "UTF-8"),
                    preauthInfos.getString("timestamp"),
                    preauthInfos.getString("preauthkey"));
        }
    }

    private static String getHmac(String data, byte[] key) {
        try {
            ByteKey bk = new ByteKey(key);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(bk);
            return toHex(mac.doFinal(data.getBytes()));
        } catch (NoSuchAlgorithmException|InvalidKeyException e) {
            Logger log = LoggerFactory.getLogger(PreauthHelper.class);
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
