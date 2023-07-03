package fr.openent.zimbra.helper;

import fr.openent.zimbra.model.IModel;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ⚠ If you use this helper you must have the tests that go with it.
 * ⚠ These tests will make it possible to verify the correct implementation of all the classes that implement IModel.
 * ⚠ This will guarantee the correct execution of the line modelClass.getConstructor(JsonObject.class).newInstance(iModel).
 */
public class IModelHelper {
    private final static List<Class<?>> validJsonClasses = Arrays.asList(String.class, boolean.class, Boolean.class,
            double.class, Double.class, float.class, Float.class, Integer.class, int.class, CharSequence.class,
            JsonObject.class, JsonArray.class, Long.class, long.class);
    private final static Logger log = LoggerFactory.getLogger(IModelHelper.class);

    private IModelHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Convert a JsonArray containing JsonObject with model data to list of instances of this model.
     * @param data          The JsonArray containing JsonObject with model data
     * @param convertor     The convertor (usually a constructor) to convert JsonObject to model instance.
     * @param <T>           Model class.
     * @return              List of model instances if all elements can be converted
     * @throws Exception    If conversion failed.
     */
    public static <T> List<T> toList(JsonArray data, Function<JsonObject, T> convertor) throws Exception {
        List<T> elementList = new ArrayList<>();
        Iterator<JsonObject> elementIterator = data.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast).iterator();

        while (elementIterator.hasNext()) {
            try {
                elementList.add(convertor.apply(elementIterator.next()));
            } catch (Exception e) {
                String errMessage = String.format("[Zimbra@%s::toList]:  " +
                                "error while fetching model: %s",
                        IModelHelper.class.getSimpleName(), e.getMessage());
                log.error(errMessage);
                throw new Exception(e);
            }
        }

        return elementList;
    }

    @SuppressWarnings("unchecked")
    public static <T extends IModel<T>> List<T> toList(JsonArray results, Class<T> modelClass) {
        return results.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(iModel -> {
                    try {
                        return modelClass.getConstructor(JsonObject.class).newInstance(iModel);
                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                             InvocationTargetException e) {
                        return null;
                    }
                }).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static JsonArray toJsonArray(List<? extends IModel<?>> dataList) {
        return new JsonArray(dataList.stream().map(IModel::toJson).collect(Collectors.toList()));
    }

    /**
     * Generic convert an {@link IModel} to {@link JsonObject}.
     * Classes that do not implement any {@link #validJsonClasses} class or iModel implementation will be ignored.
     * Except {@link List} and {@link Enum}
     *
     * @param ignoreNull If true ignore, fields that are null will not be put in the result
     * @param iModel Instance of {@link IModel} to convert to {@link JsonObject}
     * @return {@link JsonObject}
     */
    public static JsonObject toJson(IModel<?> iModel, boolean ignoreNull, boolean snakeCase) {
        JsonObject statisticsData = new JsonObject();
        final Field[] declaredFields = iModel.getClass().getDeclaredFields();
        Arrays.stream(declaredFields).forEach(field -> {
            boolean accessibility = field.isAccessible();
            field.setAccessible(true);
            try {
                Object object = field.get(iModel);
                String fieldName = snakeCase ? StringHelper.camelToSnake(field.getName()) : field.getName();
                if (object == null) {
                    if (!ignoreNull) statisticsData.putNull(fieldName);
                }
                else if (object instanceof IModel) {
                    statisticsData.put(fieldName, ((IModel<?>)object).toJson());
                } else if (validJsonClasses.stream().anyMatch(aClass -> aClass.isInstance(object))) {
                    statisticsData.put(fieldName, object);
                } else if (object instanceof Enum) {
                    statisticsData.put(fieldName, (Enum) object);
                } else if (object instanceof List) {
                    statisticsData.put(fieldName, listToJsonArray(((List<?>)object)));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            field.setAccessible(accessibility);
        });
        return statisticsData;
    }

    /**
     * Generic convert a list of {@link Object} to {@link JsonArray}.
     * Classes that do not implement any {@link #validJsonClasses} class or iModel implementation will be ignored.
     * Except {@link List} and {@link Enum}
     *
     * @param objects List of object
     * @return {@link JsonArray}
     */
    private static JsonArray listToJsonArray(List<?> objects) {
        JsonArray res = new JsonArray();
        objects.stream()
                .filter(Objects::nonNull)
                .forEach(object -> {
                    if (object instanceof IModel) {
                        res.add(((IModel<?>) object).toJson());
                    }
                    else if (validJsonClasses.stream().anyMatch(aClass -> aClass.isInstance(object))) {
                        res.add(object);
                    } else if (object instanceof Enum) {
                        res.add((Enum)object);
                    } else if (object instanceof List) {
                        res.add(listToJsonArray(((List<?>) object)));
                    }
                });
        return res;
    }

    public static <T extends IModel<T>> Handler<Either<String, JsonArray>> sqlResultToIModel(Promise<List<T>> promise, Class<T> modelClass) {
        return sqlResultToIModel(promise, modelClass, null);
    }

    public static <T extends IModel<T>> Handler<Either<String, JsonArray>> sqlResultToIModel(Promise<List<T>> promise, Class<T> modelClass, String errorMessage) {
        return event -> {
            if (event.isLeft()) {
                if (errorMessage != null) {
                    log.error(errorMessage + " " + event.left().getValue());
                }
                promise.fail(event.left().getValue());
            } else {
                promise.complete(toList(event.right().getValue(), modelClass));
            }
        };
    }
}