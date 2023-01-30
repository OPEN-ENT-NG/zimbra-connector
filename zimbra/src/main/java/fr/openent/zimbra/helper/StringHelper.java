package fr.openent.zimbra.helper;

import java.util.List;
import java.util.UUID;

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

    public static boolean isUUID(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Convert a string in CamelCase to snake_case
     */
    public static String camelToSnake(String str)
    {
        if (isNullOrEmpty(str)) {
            return str;
        }
        // Empty String
        StringBuilder result = new StringBuilder();

        // Append first character(in lower case)
        // to result string
        char c = str.charAt(0);
        result.append(Character.toLowerCase(c));

        // Traverse the string from
        // ist index to last index
        for (int i = 1; i < str.length(); i++) {

            char ch = str.charAt(i);

            // Check if the character is upper case
            // then append '_' and such character
            // (in lower case) to result string
            if (Character.isUpperCase(ch)) {
                result.append('_');
                result.append(Character.toLowerCase(ch));
            }

            // If the character is lower case then
            // add such character into result string
            else {
                result.append(ch);
            }
        }

        // return the result
        return result.toString();
    }

    /**
     * @return true if string is empty or null
     */
    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

}
