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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class JsonHelper {

    public static List<String> getStringList(JsonArray jsonArray) throws IllegalArgumentException {
        List<String> finalList = new ArrayList<>();
        for(Object o : jsonArray) {
            if(!(o instanceof String)) {
                throw new IllegalArgumentException("JsonArray is not a String list");
            }
            finalList.add((String)o);
        }
        return finalList;
    }

    public static List<String> extractValueFromJsonObjects(JsonArray jsonArray, String key) throws IllegalArgumentException {
        List<String> finalList = new ArrayList<>();
        for(Object o : jsonArray) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a JsonObject list");
            }
            JsonObject jo = (JsonObject)o;
            Object res = jo.getValue(key);
            if(res == null) {
                throw new IllegalArgumentException(key + " value of JsonObject is not a String or key does not exists");
            }
            finalList.add(res.toString());
        }
        return finalList;
    }
}
