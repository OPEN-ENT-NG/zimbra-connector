package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.model.synchro.addressbook.contacts.Contact;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;


class AddressBookFolder {


    private SortedSet<Contact> contacts = new TreeSet<>(Contact.getComparator());
    private Map<String,AddressBookFolder> subFolders = new HashMap<>();

    private static Logger log = LoggerFactory.getLogger(AddressBookFolder.class);


    void addContact(Contact contact) {
        contacts.add(contact);
    }

    AddressBookFolder getSubFolder(String folderName) {
        AddressBookFolder folder = subFolders.get(folderName);
        if(folder == null) {
            folder = new AddressBookFolder();
            subFolders.put(folderName, folder);
        }
        return folder;
    }

    Map<String,AddressBookFolder> getSubFolders() {
        return subFolders;
    }

    String getCsv() {
        StringBuilder stringBuilder = new StringBuilder(SynchroConstants.ABOOK_CSV_COLUMNS);
        contacts.forEach( currentContact -> {
            addCsvElem(stringBuilder, currentContact.getClasses());
            addCsvElem(stringBuilder, currentContact.getStructure());
            addCsvElem(stringBuilder, currentContact.getEmail());
            addCsvElem(stringBuilder, currentContact.getFirstName());
            addCsvElem(stringBuilder, currentContact.getDisplayName());
            addCsvElem(stringBuilder, currentContact.getFunctions());
            addLastCsvElem(stringBuilder, currentContact.getLastName());
        });
        return stringBuilder.toString();
    }

    private void addCsvElem(StringBuilder sb, String elem) {
        addCsvElem(sb, elem, false);
    }

    private void addLastCsvElem(StringBuilder sb, String elem) {
        addCsvElem(sb, elem, true);
    }

    private void addCsvElem(StringBuilder sb, String elem, boolean last) {
        sb.append("\"").append(elem.replace('"','\'')).append("\"").append(last ? "\n" : ",");
    }
}
