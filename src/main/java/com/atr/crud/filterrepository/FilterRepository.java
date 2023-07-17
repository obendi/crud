package com.atr.crud.filterrepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;

@NoRepositoryBean
public interface FilterRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {

    List<T> search(List<String> columns, String restSql, int pageNumber, int pageSize, String sortColumn, String sortDirection);

    // TODO: count

}
