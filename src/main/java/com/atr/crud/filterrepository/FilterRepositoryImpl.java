package com.atr.crud.filterrepository;

import com.atr.crud.filterrepository.rsql.CustomRsqlVisitor;
import com.atr.crud.filterrepository.rsql.TypesExtractor;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.Node;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class FilterRepositoryImpl<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> implements FilterRepository<T, ID> {

    private final EntityManager entityManager;

    private final Class<T> domainClass;

    public FilterRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.domainClass = entityInformation.getJavaType();
    }

    @Override
    public List<T> search(List<String> columns, String restSql, int pageNumber, int pageSize, String sortColumn, String sortDirection) {

        long start = System.currentTimeMillis();

        //
        // If no columns are specified, search for all columns.
        //
        if (columns == null || columns.isEmpty()) {
            List<String> cols = new ArrayList<>();
            entityManager.getMetamodel().entity(domainClass).getSingularAttributes().forEach(attribute -> cols.add(attribute.getName()));
            entityManager.getMetamodel().entity(domainClass).getPluralAttributes().forEach(attribute -> cols.add(attribute.getName()));
            columns = cols;
        }

        //
        // Analyze 'columns' parameter to get columns to be selected (root columns and related entity columns).
        //
        List<String> rootColumns = new ArrayList<>(columns);
        Map<String, List<String>> relatedEntityColumns = new HashMap<>();
        Map<String, Class<?>> relatedEntityType =  new HashMap<>();

        // Get related entities. For example in User entity: roles
        List<SingularAttribute<?, ?>> relatedEntitySingularAttributes = findRelatedEntitySingularAttributes(entityManager.getMetamodel(), domainClass, columns);
        List<PluralAttribute<?, ?, ?>> relatedEntityPluralAttributes = findRelatedEntityPluralAttributes(entityManager.getMetamodel(), domainClass, columns);

        //
        for (String column:columns) {
            String[] path =  column.split("\\.");
            if (path.length > 1) {
                // Related entity with specified column. For example: roles.code
                if (!relatedEntityColumns.containsKey(path[0])) {
                    relatedEntityColumns.put(path[0], new ArrayList<>());

                    List<String> entityColumns = new ArrayList<>();
                    entityColumns.add(path[1]);
                    relatedEntityColumns.put(path[0], entityColumns);
                }
                else {
                    relatedEntityColumns.get(path[0]).add(path[1]);
                }

                rootColumns.remove(column);
            }
            else {
                // Full related entity. For example in User entity: roles
                SingularAttribute<?, ?> singularAttribute = relatedEntitySingularAttributes.stream().filter(attribute -> attribute.getName().equals(column)).findFirst().orElse(null);
                if (singularAttribute != null) {
                    List<String> entityColumns = new ArrayList<>(Arrays.stream(singularAttribute.getBindableJavaType().getDeclaredFields()).map(Field::getName).toList());
                    List<SingularAttribute<?,?>> relatedEntitySingularAttributeAttributes = findRelatedEntitySingularAttributes(entityManager.getMetamodel(), singularAttribute.getBindableJavaType(), entityColumns);
                    List<PluralAttribute<?,?,?>> relatedEntityPluralAttributeAttributes = findRelatedEntityPluralAttributes(entityManager.getMetamodel(), singularAttribute.getBindableJavaType(), entityColumns);
                    entityColumns.removeIf(ec -> relatedEntitySingularAttributeAttributes.stream().map(Attribute::getName).toList().contains(ec));
                    entityColumns.removeIf(ec -> relatedEntityPluralAttributeAttributes.stream().map(Attribute::getName).toList().contains(ec));

                    rootColumns.remove(column);
                    relatedEntityColumns.put(column, entityColumns);
                    relatedEntityType.put(column, singularAttribute.getBindableJavaType());
                }

                PluralAttribute<?, ?, ?> pluralAttribute = relatedEntityPluralAttributes.stream().filter(attribute -> attribute.getName().equals(column)).findFirst().orElse(null);
                if (pluralAttribute != null) {
                    List<String> entityColumns = new ArrayList<>(Arrays.stream(pluralAttribute.getBindableJavaType().getDeclaredFields()).map(Field::getName).toList());
                    List<SingularAttribute<?,?>> relatedEntitySingularAttributeAttributes = findRelatedEntitySingularAttributes(entityManager.getMetamodel(), pluralAttribute.getBindableJavaType(), entityColumns);
                    List<PluralAttribute<?,?,?>> relatedEntityPluralAttributeAttributes = findRelatedEntityPluralAttributes(entityManager.getMetamodel(), pluralAttribute.getBindableJavaType(), entityColumns);
                    entityColumns.removeIf(ec -> relatedEntitySingularAttributeAttributes.stream().map(Attribute::getName).toList().contains(ec));
                    entityColumns.removeIf(ec -> relatedEntityPluralAttributeAttributes.stream().map(Attribute::getName).toList().contains(ec));

                    rootColumns.remove(column);
                    relatedEntityColumns.put(column, entityColumns);
                    relatedEntityType.put(column, pluralAttribute.getBindableJavaType());
                }
            }
        }

        //
        // Get related entities type
        //
        for (String relatedEntity:relatedEntityColumns.keySet()) {
            if (relatedEntityType.get(relatedEntity) == null) {
                Class<?> entityType = null;
                SingularAttribute<?, ?> singularAttribute = entityManager.getMetamodel().entity(domainClass).getSingularAttributes()
                        .stream().filter(sa -> sa.getName().equals(relatedEntity))
                        .findFirst().orElse(null);

                if (singularAttribute != null) {
                    entityType = singularAttribute.getBindableJavaType();
                }
                else {
                    PluralAttribute<?, ?, ?> pluralAttribute = entityManager.getMetamodel().entity(domainClass).getPluralAttributes()
                            .stream().filter(sa -> sa.getName().equals(relatedEntity))
                            .findFirst().orElse(null);

                    if (pluralAttribute != null) {
                        entityType = pluralAttribute.getBindableJavaType();
                    }
                }

                relatedEntityType.put(relatedEntity, entityType);
            }
        }

        //
        // Add 'id' column if not present
        //
        if (!rootColumns.contains("id")) {
            rootColumns.add("id");
        }

        for (String relatedEntity:relatedEntityColumns.keySet()) {
            String idColumn = relatedEntityColumns.get(relatedEntity).stream().filter(rc -> rc.contains("id")).toList().stream().findAny().orElse(null);
            if (idColumn == null) {
                relatedEntityColumns.get(relatedEntity).add("id");
            }
        }

        //
        // Root entity query
        //
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> criteriaQuery = criteriaBuilder.createTupleQuery();
        Root<T> root = criteriaQuery.from(domainClass);

        //
        // Selected root columns
        //
        List<Selection<?>> selections = new ArrayList<>();
        for (String rootColumn: rootColumns) {
            selections.add(root.get(rootColumn).alias(rootColumn));
        }
        criteriaQuery.multiselect(selections);

        if (!relatedEntityColumns.isEmpty()) {
            criteriaQuery.distinct(true);
        }

        //
        // Build where
        //
        if (restSql != null && !restSql.isEmpty()) {
            TypesExtractor<T> typesExtractor = new TypesExtractor<>();
            Map<String, Class<?>> types = typesExtractor.extract(entityManager, domainClass);

            Node rootNode = new RSQLParser().parse(restSql);

            Specification<T> spec = rootNode.accept(new CustomRsqlVisitor<>(types));

            Predicate predicate = Specification.where(spec).toPredicate(root, criteriaQuery, criteriaBuilder);
            criteriaQuery.where(predicate);
        }

        //
        // Sort
        //
        if (sortColumn != null && !sortColumn.isEmpty() && sortDirection != null && !sortDirection.isEmpty()) {
            if (sortDirection.equals("asc")) {
                criteriaQuery.orderBy(criteriaBuilder.asc(root.get(sortColumn)));
            }
            else {
                criteriaQuery.orderBy(criteriaBuilder.desc(root.get(sortColumn)));
            }
        }

        //
        // Main query
        //
        TypedQuery<Tuple> query = entityManager.createQuery(criteriaQuery);
        query.setFirstResult(pageNumber);
        query.setMaxResults(pageSize);

        long startMainQuery = System.currentTimeMillis();
        List<Tuple> results = query.getResultList();
        long endMainQuery = System.currentTimeMillis();

        // Map Tuple result list to entity list
        List<T> resultList = new ArrayList<>();
        for (Tuple tuple:results) {
            T entity = null;
            try {
                entity = domainClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            resultList.add(entity);

            for (TupleElement<?> element:tuple.getElements()) {
                try {
                    // Root columns
                    if (rootColumns.contains(element.getAlias())) {
                        Field field = domainClass.getDeclaredField(element.getAlias());
                        field.setAccessible(true);
                        field.set(entity, tuple.get(element.getAlias(), element.getJavaType()));
                    }
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        //
        // Related entities (N+1)
        //
        long elapsedRelatedQueries = 0;
        for (T result:resultList) {
            // Set to null related entities not requested in columns
            for (Attribute attribute:findSingularAttributes(entityManager.getMetamodel(), domainClass)) {
                if (!relatedEntityColumns.containsKey(attribute.getName())) {
                    try {
                        Field fieldToNull = domainClass.getDeclaredField(attribute.getName());
                        fieldToNull.setAccessible(true);
                        fieldToNull.set(result, null);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            for (Attribute attribute:findPluralAttributes(entityManager.getMetamodel(), domainClass)) {
                if (!relatedEntityColumns.containsKey(attribute.getName())) {
                    try {
                        Field fieldToNull = domainClass.getDeclaredField(attribute.getName());
                        fieldToNull.setAccessible(true);
                        fieldToNull.set(result, null);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // Build N+1 Queries
            for (String relatedEntity:relatedEntityColumns.keySet()) {
                CriteriaQuery<Tuple> relatedCriteriaQuery = criteriaBuilder.createTupleQuery();
                Root<?> relatedRoot = relatedCriteriaQuery.from(relatedEntityType.get(relatedEntity));

                // Build join
                String relatedRelatedEntity = null;
                for (SingularAttribute<?, ?> singularAttribute : entityManager.getMetamodel().entity(relatedEntityType.get(relatedEntity)).getSingularAttributes()) {
                    if (domainClass.equals(singularAttribute.getBindableJavaType())) {
                        relatedRelatedEntity = singularAttribute.getName();
                    }
                }

                if (relatedRelatedEntity == null) {
                    for (PluralAttribute<?, ?, ?> pluralAttribute : entityManager.getMetamodel().entity(relatedEntityType.get(relatedEntity)).getPluralAttributes()) {
                        if (domainClass.equals(pluralAttribute.getBindableJavaType())) {
                            relatedRelatedEntity = pluralAttribute.getName();
                        }
                    }
                }

                Join<?, T> relatedJoin = relatedRoot.join(relatedRelatedEntity, JoinType.INNER);

                // Selected related entity columns
                List<Selection<?>> relatedSelection = new ArrayList<>();
                for (String relatedColumn:relatedEntityColumns.get(relatedEntity)) {
                    relatedSelection.add(relatedRoot.get(relatedColumn).alias(relatedColumn));
                }
                relatedCriteriaQuery.multiselect(relatedSelection);

                // Where (join)
                Long id;
                try {
                    Field idField = domainClass.getDeclaredField("id");
                    idField.setAccessible(true);
                    id = (Long) idField.get(result);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                Predicate predicate = criteriaBuilder.equal(relatedJoin.get("id"), id);
                relatedCriteriaQuery.where(predicate);

                // Related entity query
                TypedQuery<Tuple> relatedQuery = entityManager.createQuery(relatedCriteriaQuery);

                long startRelatedQuery = System.currentTimeMillis();
                List<Tuple> relatedResultList = relatedQuery.getResultList();
                long endRelatedQuery = System.currentTimeMillis();
                elapsedRelatedQueries += (endRelatedQuery-startRelatedQuery);

                // Map Tuple result list to related entity list
                for (Tuple relatedResult : relatedResultList) {
                    // Process each of related entity columns requested
                    for (TupleElement<?> element:relatedResult.getElements()) {
                        boolean isPlural = entityManager.getMetamodel().entity(domainClass).getPluralAttributes().stream().anyMatch(a -> a.getName().equals(relatedEntity));
                        if (isPlural) {
                            // OneToMany or ManyToMany (Collection / Plural attribute)
                            Field collectionField = null;
                            try {
                                collectionField = domainClass.getDeclaredField(relatedEntity);
                            } catch (NoSuchFieldException e) {
                                throw new RuntimeException(e);
                            }
                            collectionField.setAccessible(true);

                            Type genericType = collectionField.getGenericType();
                            ParameterizedType parameterizedType = (ParameterizedType) genericType;
                            Type[] typeArguments = parameterizedType.getActualTypeArguments();
                            Type collectionType = typeArguments[0];
                            Class<?> elementClass = (Class<?>) collectionType;

                            Collection<Object> collection;
                            try {
                                collection = (Collection<Object>) collectionField.get(result);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }

                            // Check if related entity object exists, if not create new one
                            Object object = collection.stream().filter(u -> checkExistingInstance("id", relatedResult.get("id"), u, elementClass)).findFirst().orElse(null);
                            if (object == null) {
                                object = createInstance("id", relatedResult.get("id"), elementClass);
                            }

                            // Set selected column to field
                            Field objectField;
                            try {
                                objectField = elementClass.getDeclaredField(element.getAlias());
                            } catch (NoSuchFieldException e) {
                                throw new RuntimeException(e);
                            }
                            objectField.setAccessible(true);
                            try {
                                objectField.set(object, relatedResult.get(element.getAlias()));
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }

                            // Add related entity object to collection
                            if (!collection.contains(object)) {
                                collection.add(object);
                            }
                        }
                        else {
                            // ManyToOne or OneToOne (Singular attribute)
                            try {
                                Field relatedFieldObject = domainClass.getDeclaredField(relatedEntity);
                                relatedFieldObject.setAccessible(true);

                                Object relatedObject = relatedFieldObject.get(result);
                                if (relatedObject == null) {
                                    relatedObject = createInstance("id", relatedResult.get("id"), relatedEntityType.get(relatedEntity));
                                    relatedFieldObject.set(result, relatedObject);
                                }

                                Field relatedObjectField = relatedEntityType.get(relatedEntity).getDeclaredField(element.getAlias());
                                relatedObjectField.setAccessible(true);
                                relatedObjectField.set(relatedObject, relatedResult.get(element.getAlias(), element.getJavaType()));

                                // Always set null related entities of the related entities. For example in User: roles.users
                                for(Attribute attribute:findSingularAttributes(entityManager.getMetamodel(), relatedEntityType.get(relatedEntity))) {
                                    try {
                                        Field fieldToNull = relatedEntityType.get(relatedEntity).getDeclaredField(attribute.getName());
                                        fieldToNull.setAccessible(true);
                                        fieldToNull.set(relatedObject, null);
                                    } catch (NoSuchFieldException | IllegalAccessException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                for(Attribute attribute:findPluralAttributes(entityManager.getMetamodel(), relatedEntityType.get(relatedEntity))) {
                                    try {
                                        Field fieldToNull = relatedEntityType.get(relatedEntity).getDeclaredField(attribute.getName());
                                        fieldToNull.setAccessible(true);
                                        fieldToNull.set(relatedObject, null);
                                    } catch (NoSuchFieldException | IllegalAccessException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }

                        }
                    }
                }
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("Main query: " + (endMainQuery-startMainQuery));
        System.out.println("Related queries: " + elapsedRelatedQueries);
        System.out.println("Java process process: " + ((end - start) - (elapsedRelatedQueries + (endMainQuery-startMainQuery))));
        System.out.println("Full search process: " + (end - start));

        return resultList;
    }

    private <Y> Y createInstance(String idFieldName, Object idValue, Class<Y> clazz) {
        try {
            Y object = clazz.getDeclaredConstructor().newInstance();
            Field idObjectField = clazz.getDeclaredField(idFieldName);
            idObjectField.setAccessible(true);
            idObjectField.set(object, idValue);
            return object;
        } catch (InstantiationException | IllegalAccessException | NoSuchFieldException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkExistingInstance(String idFieldName, Object idValue, Object object, Class<?> type) {
        Field idField;
        try {
            idField = type.getDeclaredField(idFieldName);
            idField.setAccessible(true);
            Object id = idField.get(object);
            return id.equals(idValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private List<SingularAttribute<?, ?>> findRelatedEntitySingularAttributes(Metamodel metamodel, Class<?> entityClass, List<String> columns) {
        List<SingularAttribute<?, ?>> entitySingularAttributes = new ArrayList<>();

        List<String> entityColumns = columns.stream().map(c -> c.split("\\.")[0]).toList();

        for (SingularAttribute<?, ?> singularAttribute : metamodel.entity(entityClass).getSingularAttributes()) {
            if (entityColumns.contains(singularAttribute.getName()) &&
                    (singularAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                            singularAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE)) {
                entitySingularAttributes.add(singularAttribute);
            }
        }

        return entitySingularAttributes;
    }

    private List<PluralAttribute<?, ?, ?>> findRelatedEntityPluralAttributes(Metamodel metamodel, Class<?> entityClass, List<String> columns) {
        List<PluralAttribute<?, ?, ?>> entityPluralAttributes = new ArrayList<>();

        List<String> entityColumns = columns.stream().map(c -> c.split("\\.")[0]).toList();

        for (PluralAttribute<?, ?, ?> pluralAttribute : metamodel.entity(entityClass).getPluralAttributes()) {
            if (entityColumns.contains(pluralAttribute.getName()) &&
                    (pluralAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY ||
                            pluralAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_MANY)) {
                entityPluralAttributes.add(pluralAttribute);
            }
        }

        return entityPluralAttributes;
    }

    private List<SingularAttribute<?, ?>> findSingularAttributes(Metamodel metamodel, Class<?> entityClass) {
        List<SingularAttribute<?, ?>> entitySingularAttributes = new ArrayList<>();

        for (SingularAttribute<?, ?> singularAttribute : metamodel.entity(entityClass).getSingularAttributes()) {
            if (singularAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                    singularAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                entitySingularAttributes.add(singularAttribute);
            }
        }

        return entitySingularAttributes;
    }

    private List<PluralAttribute<?, ?, ?>> findPluralAttributes(Metamodel metamodel, Class<?> entityClass) {
        List<PluralAttribute<?, ?, ?>> entityPluralAttributes = new ArrayList<>();

        for (PluralAttribute<?, ?, ?> pluralAttribute : metamodel.entity(entityClass).getPluralAttributes()) {
            if (pluralAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_MANY ||
                    pluralAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_MANY) {
                entityPluralAttributes.add(pluralAttribute);
            }
        }

        return entityPluralAttributes;
    }
}
