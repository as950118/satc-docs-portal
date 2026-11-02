package com.docsportal.docs.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docsportal.docs.format.response.SpecExportResponse;
import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;
import com.docsportal.docs.repository.SpecDocumentRepository;

/**
 * 승인된 PRD 와 승인된 기술문서를 OpenSpec change 폴더로 읽어 내보낸다. 아무것도 쓰지 않고 상태도 바꾸지 않는다.
 */
@Service
public class SpecExportService {

    private static final Set<SpecStatus> EXPORTABLE_PRD = EnumSet.of(SpecStatus.PRD_APPROVED, SpecStatus.SPEC_APPROVED, SpecStatus.IMPLEMENTED);
    private static final Set<SpecStatus> EXPORTABLE_TECH = EnumSet.of(SpecStatus.APPROVED, SpecStatus.IN_IMPLEMENTATION,
        SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED);

    private final SpecDocumentRepository documents;

    public SpecExportService(SpecDocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public SpecExportResponse exportOpenSpec(String prdId) {
        SpecDocument prd = find(prdId);
        if (prd.getKind() != SpecKind.PRD) {
            throw new SpecException(HttpStatus.NOT_FOUND, "PRD 를 찾을 수 없습니다: " + prdId);
        }
        if (!EXPORTABLE_PRD.contains(prd.getStatus())) {
            throw new SpecException(HttpStatus.CONFLICT,
                "현재 상태(" + prd.getStatus() + ")의 PRD 는 내보낼 수 없습니다. 승인된(PRD_APPROVED 이후) 버전만 내보냅니다.");
        }
        SpecDocument root = prd;
        while (root.getSupersedesId() != null) {
            root = find(root.getSupersedesId());
        }
        String previousBody = prd.getSupersedesId() == null ? null : find(prd.getSupersedesId()).getBody();

        Map<TechArea, String> tech = new EnumMap<>(TechArea.class);
        for (SpecDocument doc : documents.findByPrdIdOrderByCreatedAtAsc(prd.getId())) {
            if (EXPORTABLE_TECH.contains(doc.getStatus())) {
                TechArea.of(doc.getKind()).ifPresent(area -> tech.put(area, doc.getBody()));
            }
        }
        return OpenSpecConverter.convert(root.getId(), prd.getRevision(), prd.getTitle(), prd.getBody(), previousBody, tech);
    }

    private SpecDocument find(String id) {
        return documents.findById(id)
            .orElseThrow(() -> new SpecException(HttpStatus.NOT_FOUND, "스펙 문서를 찾을 수 없습니다: " + id));
    }
}
