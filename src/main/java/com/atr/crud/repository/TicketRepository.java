package com.atr.crud.repository;

import com.atr.crud.domain.Ticket;
import com.atr.crud.domain.User;
import com.atr.crud.filterrepository.FilterRepository;

public interface TicketRepository extends FilterRepository<Ticket, Long> {
}
