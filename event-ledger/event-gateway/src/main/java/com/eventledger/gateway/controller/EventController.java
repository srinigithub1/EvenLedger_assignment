package com.eventledger.gateway.controller;

import com.eventledger.gateway.domain.Event;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.service.EventService;
import com.eventledger.gateway.service.EventSubmitResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> submitEvent(@RequestBody @Valid EventRequest request) {
        EventSubmitResult result = eventService.submitEvent(request);
        EventResponse response = EventResponse.from(result.getEvent());
        if (result.isDuplicate()) {
            return ResponseEntity.ok(response);                              // 200 for duplicates
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);     // 201 for new
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable("id") String eventId) {
        Event event = eventService.getEvent(eventId);
        return ResponseEntity.ok(EventResponse.from(event));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> getEventsByAccount(@RequestParam("account") String accountId) {
        List<EventResponse> events = eventService.getEventsByAccount(accountId)
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(events);
    }
}
