package com.atr.crud.controller;

import com.atr.crud.Mapper;
import com.atr.crud.domain.Ticket;
import com.atr.crud.repository.TicketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/")
    List<TicketDTO> findTickets(
            @RequestParam(required = false) List<String> columns,
            @RequestParam(required = false, name = "q") String restSql,
            @RequestParam(required = false, name = "page_number") Integer pageNumber,
            @RequestParam(required = false, name = "page_size") Integer pageSize
    ) {
        List<Ticket> tickets = ticketRepository.search(columns, restSql, pageNumber, pageSize, "id", "asc");

        // Mapper
        List<TicketDTO> result = new ArrayList<>();
        for (Ticket ticket : tickets) {
            TicketDTO ticketDTO = TicketMapper.INSTANCE.map(ticket);
            result.add(ticketDTO);
        }

        return result;
    }
}
