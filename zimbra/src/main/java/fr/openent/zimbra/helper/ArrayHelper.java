package fr.openent.zimbra.helper;

import java.util.ArrayList;
import java.util.List;

public class ArrayHelper {
    public static <T> List<List<T>> split(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();

        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize,
                    list.size())));
        }

        return partitions;
    }
}
