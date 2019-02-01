package fr.openent.zimbra.model.synchro.addressbook;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@SuppressWarnings("WeakerAccess")
public abstract class AddressBookSynchro {

    protected String uai;
    protected Map<String,AddressBookUser> guestList = new HashMap<>();
    protected List<AddressBookSubDir> persList = new ArrayList<>();
    protected List<AddressBookSubDir> studentList = new ArrayList<>();
    protected List<AddressBookSubDir> relativeList = new ArrayList<>();
    protected List<AddressBookSubDir> teacherList = new ArrayList<>();

    AddressBookSynchro(String uai) throws NullPointerException {
        if(uai == null || uai.isEmpty()) {
            throw new NullPointerException("AddrBook : Empty UAI");
        }
        this.uai = uai;
    }

    public abstract void load(Handler<AsyncResult<AddressBookSynchro>> handler);

    public AddressBookModifications compare(AddressBookSynchro abookSource, AddressBookSynchro abookTarget) {
        return null;
    }

    private String getFolder() {
        // todo get folder
        return "";
    }

    private List<AddressBookModifications> compareFolderContent(Map<String,AddressBookUser> sourceMap,
                                                                Map<String,AddressBookUser> targetMap,
                                                                String folder) {

        List<AddressBookModifications> folderModifications = new ArrayList<>();

        List<Map.Entry<String,AddressBookUser>> commonList = sourceMap.entrySet().stream()
                .filter(b -> targetMap.containsKey(b.getKey())).collect(Collectors.toList());

        for(Map.Entry<String, AddressBookUser> entry : commonList) {
            String key = entry.getKey();
            if(!sourceMap.get(key).equals(targetMap.get(key))) {
                applyModifications(entry, folderModifications, AddressBookAction.MODIFY, folder);
            }
            sourceMap.remove(entry.getKey());
            targetMap.remove(entry.getKey());
        }
        for(Map.Entry<String, AddressBookUser> entry : sourceMap.entrySet()) {
            applyModifications(entry, folderModifications, AddressBookAction.CREATE,folder);
        }
        for(Map.Entry<String, AddressBookUser> entry : targetMap.entrySet()) {
            applyModifications(entry, folderModifications, AddressBookAction.DELETE,folder);
        }
        return folderModifications;
    }

    private void applyModifications(Map.Entry<String, AddressBookUser> entry,
                                    List<AddressBookModifications> folderModifications,
                                    AddressBookAction action,
                                    String folder) {
            AddressBookModifications mod = new AddressBookModifications();
            mod.action = action;
            mod.folder = folder;
            mod.user = entry.getValue();
            folderModifications.add(mod);
    }
}
