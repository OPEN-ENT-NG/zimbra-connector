package fr.openent.zimbra.helper;

import java.util.List;

public class StringHelper {

    public static String joinList(List<String> list, String separator) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for(String str : list){
            sb.append(str).append(sep);
            sep = separator;
        }
        return sb.toString();
    }
}
