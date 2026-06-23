package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.Event;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public EventService(EventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventSubmitResult submitEvent(EventRequest request) {
        return eventRepository.findByEventId(request.getEventId())
                .map(existing -> new EventSubmitResult(existing, true))
                .orElseGet(() -> {
                    Event event = new Event();
                    event.setEventId(request.getEventId());
                    event.setAccountId(request.getAccountId());
                    event.setType(request.getType());
                    event.setAmount(request.getAmount());
                    event.setCurrency(request.getCurrency());
                    event.setEventTimestamp(request.getEventTimestamp());
                    event.setReceivedAt(Instant.now());
                    event.setStatus("PROCESSED");
                    if (request.getMetadata() != null) {
                        try {
                            event.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
                        } catch (JsonProcessingException e) {
                            event.setMetadata(null);
                        }
                    }
                    Event saved = eventRepository.save(event);
                    return new EventSubmitResult(saved, false);
                });
    }

    public Event getEvent(String eventId) {
        return eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public List<Event> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }
}
