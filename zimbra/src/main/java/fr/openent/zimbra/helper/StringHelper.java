package fr.openent.zimbra.helper;

import java.util.List;

public class StringHelper {

    /**
     * Join a list of string with a separator
     * @param list Source String list
     * @param separator Separator to use
     * @return Contatenated String of all Strings in list, separated by separator
     */
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
