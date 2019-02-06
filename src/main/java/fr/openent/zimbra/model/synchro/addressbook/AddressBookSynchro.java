package fr.openent.zimbra.model.synchro.addressbook;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static fr.openent.zimbra.model.constant.SynchroConstants.*;


@SuppressWarnings("WeakerAccess")
public abstract class AddressBookSynchro {

    protected String uai;
    protected Map<String,AddressBookUser> guestList = new HashMap<>();
    protected AddressBookFolder directoryPersonnel = new AddressBookFolder();
    protected List<AddressBookFolder> studentList = new ArrayList<>();
    protected List<AddressBookFolder> relativeList = new ArrayList<>();
    protected List<AddressBookFolder> teacherList = new ArrayList<>();

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

    private String getRootAbookFolder() {
        return ABOOK_ROOT_FOLDER + "/" + uai;
    }

    private static List<AddressBookModifications> compareFolderContent(Map<String,AddressBookUser> sourceMap,
                                                                Map<String,AddressBookUser> targetMap,
                                                                String folder) {

        List<AddressBookModifications> folderModifications = new ArrayList<>();

        List<Map.Entry<String,AddressBookUser>> commonList = sourceMap.entrySet().stream()
                .filter(b -> targetMap.containsKey(b.getKey())).collect(Collectors.toList());

        for(Map.Entry<String, AddressBookUser> entry : commonList) {
            String key = entry.getKey();
            if(!sourceMap.get(key).equals(targetMap.get(key))) {
                folderModifications.add(
                        new AddressBookModifications(folder, entry.getValue(),AddressBookAction.MODIFY)
                );
            }
            sourceMap.remove(entry.getKey());
            targetMap.remove(entry.getKey());
        }
        for(Map.Entry<String, AddressBookUser> entry : sourceMap.entrySet()) {
            folderModifications.add(
                    new AddressBookModifications(folder, entry.getValue(),AddressBookAction.CREATE)
            );
        }
        for(Map.Entry<String, AddressBookUser> entry : targetMap.entrySet()) {
            folderModifications.add(
                    new AddressBookModifications(folder, entry.getValue(),AddressBookAction.DELETE)
            );
        }
        return folderModifications;
    }

}
