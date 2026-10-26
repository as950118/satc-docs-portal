package com.docsportal.docs.format.response;

import java.time.Instant;

import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecStatus;

public record SpecEventResponse(
        SpecAction action, SpecStatus fromStatus, SpecStatus toStatus,
        String actor, String actorRole, String comment, Instant at) {

    public static SpecEventResponse from(SpecEvent e) {
        return new SpecEventResponse(e.getAction(), e.getFromStatus(), e.getToStatus(),
            e.getActor(), e.getActorRole(), e.getComment(), e.getAt());
    }
}
