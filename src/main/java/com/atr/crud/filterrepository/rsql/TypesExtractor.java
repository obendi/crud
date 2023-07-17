package com.atr.crud.filterrepository.rsql;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.HashMap;
import java.util.Map;

public class TypesExtractor<T> {

    public Map<String, Class<?>> extract(EntityManager entityManager, Class<T> type) {

        Map<String, Class<?>> types = new HashMap<>();

        Metamodel metamodel = entityManager.getMetamodel();
        for (SingularAttribute<? super T, ?> attribute : metamodel.entity(type).getSingularAttributes()) {
            types.put(attribute.getName(), attribute.getJavaType());
        }

        for (PluralAttribute<? super T, ?, ?> pluralAttribute : metamodel.entity(type).getPluralAttributes()) {
            for (SingularAttribute<?, ?> attribute : metamodel.entity(pluralAttribute.getBindableJavaType()).getSingularAttributes()) {
                types.put(pluralAttribute.getName() + "." + attribute.getName(), attribute.getJavaType());
            }
        }

        return types;
    }
}
