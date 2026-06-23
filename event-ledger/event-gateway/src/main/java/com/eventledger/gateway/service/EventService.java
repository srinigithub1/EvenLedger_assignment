package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.AccountTransactionRequest;
import com.eventledger.gateway.domain.Event;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final AccountServiceClient accountServiceClient;
    private final Counter eventsReceivedCounter;
    private final Counter eventsDuplicateCounter;
    private final Counter accountServiceFailureCounter;

    public EventService(EventRepository eventRepository, ObjectMapper objectMapper,
            AccountServiceClient accountServiceClient, MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.accountServiceClient = accountServiceClient;
        this.eventsReceivedCounter = meterRegistry.counter("events.received.total");
        this.eventsDuplicateCounter = meterRegistry.counter("events.received.duplicate");
        this.accountServiceFailureCounter = meterRegistry.counter("account.service.call.failure");
    }

    @Transactional(noRollbackFor = AccountServiceUnavailableException.class)
    public EventSubmitResult submitEvent(EventRequest request) {
        eventsReceivedCounter.increment();
        // Idempotency guard: a known eventId is returned as-is and the transaction
        // is never re-applied to the account.
        Optional<Event> existing = eventRepository.findByEventId(request.getEventId());
        if (existing.isPresent()) {
            eventsDuplicateCounter.increment();
            return new EventSubmitResult(existing.get(), true);
        }

        Event event = new Event();
        event.setEventId(request.getEventId());
        event.setAccountId(request.getAccountId());
        event.setType(request.getType());
        event.setAmount(request.getAmount());
        event.setCurrency(request.getCurrency());
        event.setEventTimestamp(request.getEventTimestamp());
        event.setReceivedAt(Instant.now());
        event.setStatus("PENDING");
        if (request.getMetadata() != null) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (JsonProcessingException e) {
                event.setMetadata(null);
            }
        }
        // Persist before the downstream call so the event is queryable from the
        // Gateway's own DB even if Account Service is unreachable.
        Event saved = eventRepository.save(event);

        try {
            accountServiceClient.applyTransaction(saved.getAccountId(),
                    new AccountTransactionRequest(saved.getEventId(), saved.getType(),
                            saved.getAmount(), saved.getCurrency()));
            saved.setStatus("PROCESSED");
            saved = eventRepository.save(saved);
            return new EventSubmitResult(saved, false);
        } catch (AccountServiceUnavailableException e) {
            accountServiceFailureCounter.increment();
            saved.setStatus("FAILED");
            eventRepository.save(saved);
            throw e;
        }
    }

    public Event getEvent(String eventId) {
        return eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public List<Event> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }
}
