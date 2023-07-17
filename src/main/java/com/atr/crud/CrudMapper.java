package com.atr.crud;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

public class CrudMapper<X, Y> {

    public void map(X entity, Y dto, List<String> columns) {

        List<String> rootColumns = null;
        if (columns != null && !columns.isEmpty()) {
            rootColumns = columns.stream().map(c -> c.split("\\.")[0]).toList();
        }

        Class<?> entityClass = entity.getClass();
        Class<?> dtoClass = dto.getClass();

        Field[] entityFields = entityClass.getDeclaredFields();


            for (Field entityField : entityFields) {
                try {
                    Field dtoField = dtoClass.getDeclaredField(entityField.getName());
                    entityField.setAccessible(true);
                    dtoField.setAccessible(true);

                    Object entityValue = entityField.get(entity);

                    if (entityValue != null && (rootColumns == null || rootColumns.contains(entityField.getName()))) {

                        List<String> subEntityRootColumns = null;
                        if (columns != null) {
                            subEntityRootColumns = columns.stream().filter(rc -> rc.contains(entityField.getName() + ".")).map(c -> c.split("\\.")[1]).toList();
                        }

                        Class<?> entityFieldType = entityField.getType();
                        if (!Collection.class.isAssignableFrom(entityFieldType)) {
                            dtoField.set(dto, entityValue);
                        }
                        else {
                            Collection<Object> dtoCollection = (Collection<Object>)dtoField.get(dto);
                            Class<?> dtoCollectionGenericType = getCollectionGenericType(dtoField);
                            Collection<?> entityCollection = (Collection<?>) entityValue;

                            CrudMapper<Object, Object> crudMapper = new CrudMapper<>();
                            for (Object entityCollectionItem:entityCollection) {
                                try {
                                    Object dtoCollectionItem = dtoCollectionGenericType.getDeclaredConstructor().newInstance();
                                    crudMapper.map(entityCollectionItem, dtoCollectionItem, subEntityRootColumns);
                                    dtoCollection.add(dtoCollectionItem);
                                } catch (InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    } else {
                        dtoField.set(dto, null);
                    }

                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // Do nothing, it's OK.
                }
            }


    }

    private Class<?> getCollectionGenericType(Field collectionField) {
        Type genericType = collectionField.getGenericType();
        ParameterizedType parameterizedType = (ParameterizedType)genericType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        Type collectionType = typeArguments[0];
        return (Class<?>)collectionType;
    }
}
