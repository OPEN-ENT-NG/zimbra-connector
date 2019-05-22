package fr.openent.zimbra.model.synchro.addressbook;

import io.vertx.core.json.JsonObject;

import java.util.Objects;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

class Contact {

    private String id;
    private String displayName;

    Contact(JsonObject json) throws IllegalArgumentException {
        if(json == null
                ||  json.getString(USER_ID, "").isEmpty()
                ||  json.getString(USER_DISPLAYNAME, "").isEmpty()) {
            throw new IllegalArgumentException("Invalid AddressBookUser data");
        }
        id = json.getString(USER_ID);
        displayName = json.getString(USER_DISPLAYNAME);
    }

    public String getId() { return id;}
    public String getDisplayName() { return displayName;}
    public Contact getThis() { return this;}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contact)) return false;
        Contact that = (Contact) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName);
    }
}
