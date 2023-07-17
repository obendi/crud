package com.atr.crud;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Mapper {
    public static <T, U> U map(T source, Class<U> destinationClass) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        U destination = destinationClass.newInstance();

        Field[] sourceFields = source.getClass().getDeclaredFields();
        Field[] destinationFields = destinationClass.getDeclaredFields();

        for (Field sourceField : sourceFields) {
            sourceField.setAccessible(true);
            Object value = sourceField.get(source);

            for (Field destinationField : destinationFields) {
                destinationField.setAccessible(true);

                if (destinationField.getName().equals(sourceField.getName())) {
                    if (Collection.class.isAssignableFrom(destinationField.getType())) {
                        // Handle list types
                        Class<?> genericType = (Class<?>) ((java.lang.reflect.ParameterizedType) destinationField.getGenericType()).getActualTypeArguments()[0];
                        Constructor<?> constructor = destinationField.getType().getDeclaredConstructor();
                        constructor.setAccessible(true);
                        Collection<Object> destinationList = (Collection<Object>)constructor.newInstance();
                        Collection<?> sourceList = (Collection<?>) value;
                        for (Object listItem : sourceList) {
                            Object mappedListItem = map(listItem, genericType);
                            destinationList.add(mappedListItem);
                        }
                        destinationField.set(destination, destinationList);
                    } else {
                        // Handle non-list types


                        if (value != null && destinationField.getType().getName().contains("DTO")) {
                            Object mappedObject = map(value, destinationField.getType());
                            destinationField.set(destination, mappedObject);
                        }
                        else {
                            destinationField.set(destination, value);
                        }
                    }
                    break;
                }
            }
        }

        return destination;
    }
}
