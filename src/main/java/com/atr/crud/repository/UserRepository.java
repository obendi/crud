package com.atr.crud.repository;

import com.atr.crud.domain.User;
import com.atr.crud.filterrepository.FilterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface UserRepository extends FilterRepository<User, Long> {

    @Query("SELECT u FROM User u JOIN FETCH u.roles ORDER BY u.id")
    Page<User> test(Pageable page);

}
