# 아키텍처

문서 포털이 어떤 구성요소로 이루어져 있고, 문서가 올라올 때·검색할 때·바뀔 때 데이터가 어떻게 흐르는지 설명합니다. 다이어그램은 Mermaid라서 GitHub와 Notion에서 그대로 렌더링됩니다. 모든 호출 관계는 코드에서 확인한 것입니다.

## 1. 구성요소

```mermaid
flowchart LR
  subgraph Client["클라이언트"]
    FE["프론트엔드<br/>Vue 3 · :3000"]
    MCPC["MCP 클라이언트<br/>Claude Code"]
  end

  subgraph Portal["문서 포털 (docker compose)"]
    BE["백엔드<br/>Spring Boot · :8080"]
    IDX["인덱싱 서비스<br/>FastAPI · :8000"]
    PG[("Postgres<br/>document, spec_*, app_user")]
    MINIO[("MinIO<br/>업로드 파일 원본")]
  end

  subgraph Shared["공유 (MCP 서버 compose)"]
    ES[("Elasticsearch<br/>document_chunks · full_documents")]
    MCPS["MCP 서버<br/>:5555"]
  end

  FE -- "REST · SSE" --> BE
  MCPC -- "MCP" --> MCPS
  MCPS -- "스펙 API<br/>사용자 토큰 전달" --> BE
  MCPC -. "OAuth 로그인" .-> BE

  BE --> PG
  BE --> MINIO
  BE -- "색인 요청 · 삭제 · 검색 · 본문 조회" --> IDX
  IDX -- "원본 다운로드" --> MINIO
  IDX -- "청크 · 전체 문서 색인" --> ES
  IDX -- "상태 콜백<br/>POST /docs/status" --> BE
  MCPS -- "기능명세 검색" --> ES
```

| 구성요소 | 역할 |
|---|---|
| 프론트엔드 | 문서 트리, 검색, PRD·기술문서 화면. 백엔드에 직접 연결하고 SSE로 변경 알림을 받습니다 |
| 백엔드 | 문서·스펙 API, 인증(JWT)과 MCP용 OAuth 서버, SSE 이벤트 허브, 스펙 워크플로우 전이 규칙 |
| 인덱싱 서비스 | MinIO의 문서를 청크로 쪼개 ES에 색인하고, 끝나면 백엔드에 상태를 알립니다. 전체 문서 검색도 담당 |
| Postgres | 문서 트리(`document`), PRD·기술문서(`spec_document`, `spec_event`, `spec_pr_link`), 계정, OAuth 클라이언트 |
| MinIO | 업로드한 문서 파일 원본 |
| Elasticsearch | 포털이 `document_chunks`(청크)와 `full_documents`(전체 문서)를 씁니다. MCP 서버도 같은 클러스터를 공유합니다 |
| MCP 서버 | Claude Code 등이 쓰는 도구 모음. 스펙 워크플로우 도구는 로그인한 사용자의 토큰으로 백엔드를 호출합니다 |

**데이터 정본**: 업로드 문서는 MinIO 파일이 원본이고 ES는 검색용 파생물입니다. PRD·기술문서는 Postgres의 마크다운이 정본이며 ES에는 색인하지 않습니다.

## 2. 문서 업로드와 인덱싱

```mermaid
sequenceDiagram
  autonumber
  participant U as 사용자 A (브라우저)
  participant BE as 백엔드
  participant M as MinIO
  participant I as 인덱싱 서비스
  participant ES as Elasticsearch
  participant B as 사용자 B (브라우저)

  U->>BE: POST /docs (파일, X-Client-Id)
  BE->>BE: DB에 UPLOADING 저장
  BE->>M: 파일 업로드
  BE->>BE: 상태 INDEXING
  BE-->>B: SSE document.created (originId=A)
  BE-)I: POST /process-document (비동기)
  I->>M: 원본 다운로드
  I->>I: 청크 분할
  I->>ES: 청크 색인 (document_chunks)
  I->>ES: 전체 문서 색인 (full_documents)
  alt 색인 성공 또는 청크 0개
    I->>BE: POST /docs/status COMPLETE
  else ES 청크 색인 실패 또는 예외
    I->>BE: POST /docs/status FAILED
  end
  BE-->>U: SSE document.status
  BE-->>B: SSE document.status
```

- A가 올린 `document.created`는 `originId`가 A 자신이라 A 화면에는 토스트가 뜨지 않고 트리만 갱신됩니다. B 화면에는 토스트가 뜹니다.
- 상태 콜백은 서버 간 호출이라 `X-Client-Id`가 없어 `originId`가 비어 있고, 모든 화면이 받습니다.
- 인덱싱 서비스는 상태 갱신 응답의 본문 `status`도 확인합니다(백엔드가 HTTP 200에 본문 `status: 500`으로 실패를 알리는 경우가 있음).

## 3. 검색

```mermaid
flowchart LR
  Q["검색어"] --> BE["백엔드<br/>GET /docs/search"]
  BE --> SPEC["스펙 검색<br/>Postgres: PRD·기술문서 제목·본문"]
  BE --> EXT["문서 검색<br/>인덱싱 서비스 → ES full_documents"]
  SPEC --> MERGE["결과 합치기<br/>스펙(제목 일치 우선) → 업로드 문서"]
  EXT --> MERGE
  MERGE --> R["프론트엔드<br/>배지: PRD / BE·FE·DATA 기술문서"]
```

- PRD·기술문서는 ES에 없으므로 Postgres에서 직접 찾습니다. 모든 검색어 단어가 제목 또는 본문에 있어야 하고, 새 버전이 있는 이전 PRD 버전은 제외합니다.
- 업로드 문서는 ES 제목을 쓰고, 포털 DB에 없는 색인 항목(`indexOnly`)은 `GET /docs/indexed/{id}`로 본문을 가져옵니다.
- 업로드 문서 본문은 `GET /docs/{id}/content`(MinIO)로 가져옵니다.

## 4. 실시간 이벤트 (SSE) 내부 구조

```mermaid
flowchart LR
  subgraph Producers["변경이 일어나는 곳"]
    DS["DocumentServiceImpl<br/>업로드·삭제·이동·상태"]
    SY["DocumentSyncServiceImpl<br/>동기화"]
    SP["SpecService.record<br/>스펙 상태 전이"]
  end

  PUB["LiveEventPublisher<br/>(커밋 후 발행)"]
  HUB["SseHub<br/>구독자 목록 · 하트비트"]
  EP["GET /api/v1/events"]
  C1["브라우저 A"]
  C2["브라우저 B"]

  DS --> PUB
  SY --> PUB
  SP --> PUB
  PUB -- "AFTER_COMMIT" --> HUB
  EP -- "구독 등록" --> HUB
  HUB -- "text/event-stream" --> C1
  HUB -- "text/event-stream" --> C2
```

- 이벤트는 DB 커밋 뒤에 나갑니다. 트랜잭션 없이 저장되는 경로도 있어서 `fallbackExecution`으로 즉시 보냅니다.
- 허브는 서버 메모리에 있어 **백엔드가 한 인스턴스일 때만** 모든 화면에 전달됩니다.

프론트엔드 쪽 흐름:

```mermaid
flowchart LR
  ES["EventSource<br/>/api/v1/events"] --> CORE["liveEventsCore<br/>이벤트 분기 · 백오프"]
  CORE -->|"document.*"| D500["500ms 디바운스"] --> TREE["문서 트리·통계 재조회"]
  CORE -->|"spec.changed"| SPECS["PRD 목록·스펙 트리 재조회"]
  CORE -->|"다른 사용자의 변경"| TOAST["토스트<br/>(2초 중복 제거 · 5건 초과 묶음)"]
  CORE -->|"열어 둔 문서와 같은 id"| BANNER["수정 배너 · 삭제 안내"]
  CORE -->|"연결 끊김 5초↑"| HEADER["헤더 '연결 끊김' 표시"]
```

## 5. 스펙 워크플로우 상태

PRD:

```mermaid
stateDiagram-v2
  direction LR
  [*] --> DRAFT: 작성
  DRAFT --> PRD_APPROVED: 기획자 승인
  PRD_APPROVED --> SPEC_APPROVED: 선언한 영역의 기술문서가 모두 승인되면 자동
  SPEC_APPROVED --> IMPLEMENTED: 모든 영역의 구현이 승인되면 자동
```

새 버전이 승인되면 이전 버전의 승인된 PRD는 시스템이 `SUPERSEDED`로 바꾸고 읽기 전용이 됩니다.

기술문서:

```mermaid
stateDiagram-v2
  direction LR
  [*] --> DRAFT: 작성
  DRAFT --> IN_REVIEW: 제출
  IN_REVIEW --> APPROVED: 영역 담당자 승인
  IN_REVIEW --> CHANGES_REQUESTED: 변경 요청
  CHANGES_REQUESTED --> DRAFT: 수정
  APPROVED --> IN_IMPLEMENTATION: 구현 시작
  IN_IMPLEMENTATION --> IMPL_IN_REVIEW: 구현 제출 (PR 링크 필요)
  IMPL_IN_REVIEW --> IMPLEMENTED: 리뷰어 승인
```

계정별 권한과 도구 호출 순서는 [spec-workflow.md](spec-workflow.md)를 보세요.

## 6. 문서 상태

```mermaid
stateDiagram-v2
  direction LR
  [*] --> UPLOADING
  UPLOADING --> INDEXING: MinIO 업로드 완료
  INDEXING --> COMPLETE: 색인 완료 (청크 0개 포함)
  INDEXING --> FAILED: ES 청크 색인 실패 · 처리 중 예외
  FAILED --> INDEXING: 동기화로 내용이 바뀌어 다시 들어옴
```
