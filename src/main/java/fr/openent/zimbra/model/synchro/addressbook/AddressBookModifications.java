package fr.openent.zimbra.model.synchro.addressbook;

public class AddressBookModifications {

    String folder;
    AddressBookUser user;
    AddressBookAction action;

    AddressBookModifications(String folder, AddressBookUser user, AddressBookAction action) {
        this.folder = folder;
        this.user = user;
        this.action = action;
    }

}
