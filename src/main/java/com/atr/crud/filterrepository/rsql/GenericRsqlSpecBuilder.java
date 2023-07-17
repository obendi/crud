package com.atr.crud.filterrepository.rsql;

import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.LogicalOperator;
import cz.jirutka.rsql.parser.ast.Node;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GenericRsqlSpecBuilder<T> {

    private Map<String, Class<?>> types;

    public GenericRsqlSpecBuilder(Map<String, Class<?>> types) {
        this.types = types;
    }

    public Specification<T> createSpecification(final Node node) {
        if (node instanceof LogicalNode) {
            return createSpecification((LogicalNode) node);
        }
        if (node instanceof ComparisonNode) {
            return createSpecification((ComparisonNode) node);
        }
        return null;
    }

    public Specification<T> createSpecification(final LogicalNode logicalNode) {
        
        List<Specification<T>> specs = logicalNode.getChildren()
                .stream()
                .map(node -> createSpecification(node))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        Specification<T> result = specs.get(0);
        if (logicalNode.getOperator() == LogicalOperator.AND) {
           for (int i = 1; i < specs.size(); i++) {
               result = Specification.where(result).and(specs.get(i));
           }
        }
        else if (logicalNode.getOperator() == LogicalOperator.OR) {
           for (int i = 1; i < specs.size(); i++) {
               result = Specification.where(result).or(specs.get(i));
           }
        }
        return result;
    }

    public Specification<T> createSpecification(final ComparisonNode comparisonNode) {
        return Specification.where(
            new GenericRsqlSpecification<>(
                comparisonNode.getSelector(),
                comparisonNode.getOperator(),
                comparisonNode.getArguments(),
                types
            )
        );
    }
}
