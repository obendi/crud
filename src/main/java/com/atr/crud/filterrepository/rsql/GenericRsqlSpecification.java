package com.atr.crud.filterrepository.rsql;

import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenericRsqlSpecification<T> implements Specification<T> {

    private String property;
    private ComparisonOperator operator;
    private List<String> arguments;

    private Map<String, Class<?>> types;

    public GenericRsqlSpecification(final String property, final ComparisonOperator operator, final List<String> arguments, final Map<String, Class<?>> types) {
        super();
        this.property = property;
        this.operator = operator;
        this.arguments = arguments;
        this.types = types;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        List<Object> args = castArguments();
        Object argument = args.get(0);
        String[] path = property.split("\\.");

        switch (RsqlSearchOperation.getSimpleOperator(operator)) {
            case EQUAL: {
                if (argument instanceof String) {
                    if (path.length > 1) {
                        Join<T, ?> join = root.join(path[0], JoinType.INNER);
                        return builder.like(join.get(path[1]), argument.toString().replace('*', '%'));
                    }
                    else {
                        return builder.like(root.get(property), argument.toString().replace('*', '%'));
                    }
                } else if (argument == null) {
                    if (path.length > 1) {
                        Join<T, ?> join = root.join(path[0], JoinType.INNER);
                        return builder.isNull(join.get(path[1]));
                    }
                    else {
                        return builder.isNull(root.get(property));
                    }
                } else {
                    if (path.length > 1) {
                        Join<T, ?> join = root.join(path[0], JoinType.INNER);
                        return builder.equal(join.get(path[1]), argument);
                    }
                    else {
                        return builder.equal(root.get(property), argument);
                    }
                }
            }
            case NOT_EQUAL: {
                if (argument instanceof String) {
                    if (path.length > 1) {
                        Join<T, ?> join = root.join(path[0], JoinType.INNER);
                        return builder.notLike(join.get(path[1]), argument.toString().replace('*', '%'));
                    }
                    else {
                        return builder.notLike(root.get(property), argument.toString().replace('*', '%'));
                    }
                } else if (argument == null) {
                    if (path.length > 1) {
                        Join<T, ?> join = root.join(path[0], JoinType.INNER);
                        return builder.isNotNull(join.get(path[1]));
                    }
                    else {
                        return builder.isNotNull(root.get(property));
                    }
                } else {
                    if (path.length > 1) {
                        Join<T, ?> join = root.join(path[0], JoinType.INNER);
                        return builder.notEqual(join.get(path[1]), argument);
                    }
                    else {
                        return builder.notEqual(root.get(property), argument);
                    }
                }
            }
            case GREATER_THAN: {
                // TODO: implement join
                if (argument instanceof OffsetDateTime) {
                    return builder.greaterThan(root.get(property), (OffsetDateTime)argument);
                }
                else {
                    return builder.greaterThan(root.get(property), argument.toString());
                }
            }
            case GREATER_THAN_OR_EQUAL: {
                // TODO: implement join
                return builder.greaterThanOrEqualTo(root.<String> get(property), argument.toString());
            }
            case LESS_THAN: {
                // TODO: implement join
                if (argument instanceof OffsetDateTime) {
                    return builder.lessThan(root.get(property), (OffsetDateTime)argument);
                }
                else {
                    return builder.lessThan(root.get(property), argument.toString());
                }
            }
            case LESS_THAN_OR_EQUAL: {
                // TODO: implement join
                if (argument instanceof OffsetDateTime) {
                    return builder.lessThanOrEqualTo(root.<String>get(property), argument.toString());
                }
                else {
                    return builder.lessThanOrEqualTo(root.get(property), argument.toString());
                }
            }
            case IN:
                // TODO: implement joins
                return root.get(property).in(args);
            case NOT_IN:
                // TODO: implement join
                return builder.not(root.get(property).in(args));
        }

        return null;
    }

    private List<Object> castArguments() {

        Class<? extends Object> type = types.get(property);

        List<Object> args = arguments.stream().map(arg -> {
            if (type.equals(Integer.class)) {
                return Integer.parseInt(arg);
            } else if (type.equals(Long.class)) {
                return Long.parseLong(arg);
            } else if (type.equals(OffsetDateTime.class)) {
                return OffsetDateTime.parse(arg);
            } else {
                return arg;
            }
        }).collect(Collectors.toList());

        return args;
    }

    // standard constructor, getter, setter
}
