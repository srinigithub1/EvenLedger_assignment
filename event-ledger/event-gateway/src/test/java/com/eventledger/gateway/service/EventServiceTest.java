package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.domain.Event;
import com.eventledger.gateway.domain.EventType;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AccountServiceClient accountServiceClient;

    @InjectMocks
    private EventService eventService;

    private EventRequest newRequest(String eventId) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId("acct-123");
        request.setType(EventType.CREDIT);
        request.setAmount(new BigDecimal("150.00"));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));
        return request;
    }

    private Event existingEvent(String eventId) {
        Event event = new Event();
        event.setId(1L);
        event.setEventId(eventId);
        event.setAccountId("acct-123");
        event.setType(EventType.CREDIT);
        event.setAmount(new BigDecimal("150.00"));
        event.setCurrency("USD");
        event.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));
        return event;
    }

    @Test
    void submitEvent_newEvent_savesAndReturnsNotDuplicate() {
        EventRequest request = newRequest("evt-001");
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventSubmitResult result = eventService.submitEvent(request);

        // Event is saved twice: once as PENDING before the downstream call, then
        // updated to PROCESSED after Account Service succeeds.
        verify(eventRepository, times(2)).save(any(Event.class));
        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getEvent().getEventId()).isEqualTo("evt-001");
        assertThat(result.getEvent().getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    void submitEvent_duplicateEventId_returnsExistingAndDoesNotSave() {
        Event existing = existingEvent("evt-001");
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existing));

        EventSubmitResult result = eventService.submitEvent(newRequest("evt-001"));

        verify(eventRepository, never()).save(any(Event.class));
        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getEvent()).isSameAs(existing);
    }

    @Test
    void getEvent_existingId_returnsEvent() {
        Event existing = existingEvent("evt-001");
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existing));

        Event result = eventService.getEvent("evt-001");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getEvent_unknownId_throwsEventNotFoundException() {
        when(eventRepository.findByEventId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent("missing"))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void getEventsByAccount_delegatesToRepository() {
        when(eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-123"))
                .thenReturn(List.of(existingEvent("evt-001"), existingEvent("evt-002")));

        List<Event> result = eventService.getEventsByAccount("acct-123");

        assertThat(result).hasSize(2);
    }
}
