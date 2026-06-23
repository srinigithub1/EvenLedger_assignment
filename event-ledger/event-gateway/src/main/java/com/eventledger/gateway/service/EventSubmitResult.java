package com.eventledger.gateway.service;

import com.eventledger.gateway.domain.Event;

public class EventSubmitResult {
    private final Event event;
    private final boolean duplicate;

    public EventSubmitResult(Event event, boolean duplicate) {
        this.event = event;
        this.duplicate = duplicate;
    }

    public Event getEvent() {
        return event;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
