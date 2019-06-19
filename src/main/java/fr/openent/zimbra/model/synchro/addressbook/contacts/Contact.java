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

package fr.openent.zimbra.model.synchro.addressbook.contacts;

import fr.openent.zimbra.Zimbra;
import fr.wseduc.webutils.I18n;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Comparator;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;


public abstract class Contact {

    private String lastName;
    private String firstName;
    String displayName;
    String functions = "";
    String classes = "";

    private String email;
    private String structure;

    Contact(JsonObject json, String uai) {
        lastName = json.getString(LASTNAME, "");
        firstName = json.getString(FIRSTNAME, "");
        if(lastName.isEmpty() || firstName.isEmpty()) {
            displayName = lastName + firstName;
        } else {
            displayName = lastName + ", " + firstName;
        }
        email = json.getString(EMAIL, "");
        structure = uai;
    }

    public static Comparator<Contact> getComparator() {
        return (c1, c2) -> {
            if(c1 == null || c2 == null) {
                throw new NullPointerException();
            }
            if(c1.lastName.compareTo(c2.lastName) != 0) {
                return c1.lastName.compareTo(c2.lastName);
            } else if(c1.firstName.compareTo(c2.firstName) != 0) {
                return c1.firstName.compareTo(c2.firstName);
            } else {
                return c1.email.compareTo(c2.email);
            }
        };
    }

    String concatField(JsonArray arrayField) {
        StringBuilder sb = new StringBuilder();
        if(!arrayField.isEmpty()) {
            arrayField.forEach( o -> {
                if((o instanceof String)) {
                    sb.append(o);
                    sb.append(", ");
                }
            });
            sb.delete(sb.length()-2, sb.length());
        }
        return sb.toString();
    }

    public String getProfileFolderName() {
        String profile = getProfile();
        return I18n.getInstance().translate(
                "folder." + profile,
                "default-domain",
                Zimbra.appConfig.getSynchroLang());
    }

    public String getEmail() { return email; }
    public String getStructure() { return structure; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public String getDisplayName() { return displayName; }
    public String getFunctions() { return functions; }
    public String getClasses() { return classes; }
    public abstract String getProfile();


}
