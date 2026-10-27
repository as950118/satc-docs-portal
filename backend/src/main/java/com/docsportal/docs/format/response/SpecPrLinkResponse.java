package com.docsportal.docs.format.response;

import java.time.Instant;

import com.docsportal.docs.persistence.SpecPrLink;

public record SpecPrLinkResponse(String url, String linkedBy, Instant linkedAt) {

    public static SpecPrLinkResponse from(SpecPrLink link) {
        return new SpecPrLinkResponse(link.getUrl(), link.getLinkedBy(), link.getLinkedAt());
    }
}
