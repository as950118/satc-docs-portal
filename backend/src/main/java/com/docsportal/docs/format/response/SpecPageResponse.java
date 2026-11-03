package com.docsportal.docs.format.response;

import java.util.List;

public record SpecPageResponse(List<SpecSummaryResponse> items, long total, int page, int size) {
}
