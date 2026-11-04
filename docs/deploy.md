# 배포: Supabase + Render + Vercel

```
 브라우저 ── 화면 파일 ──▶ Vercel (frontend/, 정적 파일)
    │
    └── API, SSE ──▶ Render web: satc-docs-backend ── 사설망 ──▶ Render pserv: satc-docs-indexing
                          │      ▲                                     │
                          │      └──────── 상태 갱신 (사설망) ─────────┤
                          ▼                                            ▼
                Supabase Postgres (docs_portal 스키마) + Supabase Storage (S3 API)
```

- **Vercel**은 빌드한 화면 파일만 준다. API 호출과 실시간 알림(SSE, `GET /api/v1/events`)은 브라우저가 Render의 backend로 바로 보낸다. Vercel이 요청을 중계하지 않으므로 backend의 `CORS_ALLOWED_ORIGINS`에 Vercel 주소가 있어야 한다.
- **Render**에는 서비스가 두 개다. backend는 공개 웹 서비스다. indexing은 공개 주소가 없는 비공개 서비스(private service)다. indexing에는 인증 없는 삭제 API(`DELETE /api/v1/documents`, `DELETE /api/v1/clear-all`)가 있어서 인터넷에 열면 안 된다. backend만 사설망으로 indexing을 부르고, indexing은 색인이 끝나면 사설망으로 backend의 상태 API를 부른다.
- **Supabase**에는 DB와 원본 파일이 있다. 테이블은 `public`이 아니라 `docs_portal` 스키마에 둔다(1절).
- 설정은 저장소 루트의 `render.yaml`(Render Blueprint)과 `frontend/vercel.json`에 있다.

이 구성은 유료 인스턴스를 전제로 한다. Render 문서에 따르면 비공개 서비스에는 free 플랜이 없다. free 웹 서비스는 15분 동안 요청이 없으면 잠들고 깨는 데 1분쯤 걸린다. 또 사설망 요청을 받지 못하므로 indexing이 backend에 색인 결과를 알릴 수 없다. 유료 인스턴스는 잠들지 않으므로 keepalive 워크플로우는 두지 않는다.

## 1. Supabase

### 연결 정보

**Connect → Session pooler**에서 다음 값을 확인한다.

| 항목 | 값 |
|---|---|
| 호스트 | `aws-0-<region>.pooler.supabase.com` (Render의 `SUPABASE_DB_HOST`) |
| 포트 | `5432` |
| DB | `postgres` |
| 사용자 | `postgres.<project-ref>` (Render의 `SPRING_DATASOURCE_USERNAME`) |
| 비밀번호 | DB 비밀번호 (Render의 `SPRING_DATASOURCE_PASSWORD`) |

- **Transaction pooler(6543)는 쓰지 않는다.** backend와 indexing은 커넥션을 열 때 `search_path`를 `docs_portal`로 설정한다. Transaction pooler는 트랜잭션마다 서버 커넥션을 바꿀 수 있어서 이 설정이 유지되지 않는다.
- Direct 연결은 IPv6 전용이라 Render에서 접속되지 않을 수 있다.
- Session pooler는 사용자마다 기본 15개(Nano, Micro의 Pool Size)까지 서버 커넥션을 준다. Render 구성은 최대 9개를 쓴다. backend는 `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=5`, indexing은 작업자 수 + 2 = 4다. 같은 프로젝트에 로컬 docker compose 스택이 붙어 있으면 같은 15개를 나눠 쓴다. 2026-11-04에 읽기 전용으로 `pg_stat_activity`를 봤을 때 이 계정의 클라이언트 커넥션이 이미 7개(JDBC 5개 포함)였다. 운영과 로컬을 동시에 붙이려면 **Database Settings → Connection pooling**에서 Pool Size를 올리거나 로컬 스택을 내린다. 한도를 넘었을 때의 동작은 시험하지 않았다.

### SSL

- backend는 `SPRING_DATASOURCE_URL`의 `sslmode=require`, indexing은 `PGSSLMODE=require`로 접속한다(둘 다 `render.yaml`에 있다). 연결은 암호화하지만 서버 인증서는 검증하지 않는다.
- 이 프로젝트의 Session pooler에 읽기 전용으로 붙어 확인했다. psycopg `sslmode=require` 연결의 `ssl_in_use`가 `true`였고, pgjdbc 42.7.7 `sslmode=require` 연결과 `Connection.setSchema`(Hikari가 `search_path`를 바꾸는 방법)도 동작했다.

### 버킷과 S3 키

[storage.md](storage.md)의 "Supabase Storage 로 바꾸기" 1단계대로 비공개 버킷과 S3 액세스 키를 만든다. Render에는 `STORAGE_ENDPOINT`(`https://<project-ref>.storage.supabase.co/storage/v1/s3`), `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`를 넣는다. 나머지 저장소 설정(`STORAGE_MANAGE_BUCKET=false`, `STORAGE_PUBLIC_READ=false`, `STORAGE_ASCII_KEYS=true`, 리전 `eu-west-3`)은 `render.yaml`에 고정되어 있다. 다른 리전의 프로젝트라면 `STORAGE_REGION`과 Render `region`을 함께 바꾼다.

### 스키마와 RLS

Supabase는 `public` 스키마의 테이블을 Data API(`/rest/v1/<table>`)로 노출한다. RLS가 꺼진 테이블은 publishable 키만으로 행이 읽힌다(이 프로젝트에서 확인했다). `public`에는 `anon`, `authenticated` 역할에 모든 권한을 주는 기본 권한(default privileges)이 걸려 있다. backend는 `ddl-auto=update`로 새 엔티티의 테이블을 RLS 없이 만든다. 그래서 `public`에 두면 테이블이 생길 때마다 RLS를 직접 켜야 한다.

운영은 테이블을 `docs_portal` 스키마에 둔다. `render.yaml`이 backend의 `DOCS_DB_SCHEMA`와 indexing의 `SEARCH_PG_SCHEMA`를 `docs_portal`로 정한다. 두 값은 같아야 한다.

- 첫 기동 때 backend가 스키마를 만들고 테이블 9개를, indexing이 검색 테이블 3개를 그 안에 만든다.
- 새 스키마에는 `anon`, `authenticated` 권한이 없고 **API Settings → Exposed schemas**에도 없다. 이 프로젝트의 Data API에 publishable 키와 `Accept-Profile: docs_portal`로 요청하면 406 `Invalid schema: docs_portal`을 돌려준다(2026-11-04 확인). 이 스키마를 Exposed schemas에 넣지 않는다.
- PGroonga 확장은 이 프로젝트에서 `public`에 있다(연산자 `&@~`, `pgroonga_score`). indexing은 `search_path` 맨 앞에 `docs_portal`을 붙이고 나머지는 그대로 두므로 확장을 계속 찾는다. 확장이 없는 새 프로젝트에서는 indexing이 첫 기동 때 `docs_portal`에 확장을 만든다.
- 두 값을 비우면 지금처럼 `public`을 쓴다. 로컬 docker compose의 기본값이 이렇다.

임시 PGroonga 16 서버(슈퍼유저가 아닌 계정, 확장은 `public`)에서 테이블이 `docs_portal`에만 생기고 `public`에는 생기지 않는 것을 확인했다. Supabase에서는 쓰기 시험을 하지 않았다.

### 기존 `public` 데이터

로컬 스택이 지금 `public`에 둔 문서를 운영에 그대로 쓰려면 Render를 처음 배포하기 **전에** 테이블을 옮긴다. 빈 상태로 시작하려면 이 단계를 건너뛴다. 그러면 로컬 스택은 계속 `public`, 운영은 `docs_portal`을 쓰고 버킷만 같이 쓴다. 객체 키가 문서 ID라서 서로 겹치지 않는다.

1. 로컬 backend와 indexing을 멈춘다. 옮긴 뒤에도 로컬 스택을 쓰려면 저장소 루트 `.env`에 `DOCS_DB_SCHEMA=docs_portal`, `SEARCH_PG_SCHEMA=docs_portal`을 넣는다.
2. SQL Editor에서 실행한다.

   ```sql
   begin;
   create schema if not exists docs_portal;
   alter table public.app_user set schema docs_portal;
   alter table public.app_user_role set schema docs_portal;
   alter table public.document set schema docs_portal;
   alter table public.document_event set schema docs_portal;
   alter table public.oauth_client set schema docs_portal;
   alter table public.oauth_client_redirect_uri set schema docs_portal;
   alter table public.spec_document set schema docs_portal;
   alter table public.spec_event set schema docs_portal;
   alter table public.spec_pr_link set schema docs_portal;
   alter table public.search_document set schema docs_portal;
   alter table public.search_chunk set schema docs_portal;
   alter table public.search_reindex_state set schema docs_portal;
   commit;
   ```

인덱스, 제약, identity 시퀀스는 테이블을 따라 옮겨진다. 켜 둔 RLS도 그대로 남는다. 앱은 테이블 소유자로 접속하므로 RLS의 영향을 받지 않는다. 임시 DB에서 이 SQL을 테이블 소유자 계정으로 실행해 확인했다. 옮긴 뒤 같은 문서가 트리에 `COMPLETE`로 보였고, 검색, 관리자 로그인, 새 업로드가 동작했다. 12개 테이블에 RLS를 켠 상태에서도 같았다.

### 플랜

| | Free | Pro |
|---|---|---|
| 일시 중지 | 1주 동안 활동이 없으면 일시 중지 | 없음 |
| DB 크기 | 500 MB | 8 GB 디스크 포함 |
| 파일 저장소 | 1 GB | 100 GB 포함 |
| 자동 백업 | 없음 (`supabase db dump`로 직접 내보내기 권장) | 매일, 7일 보관 |

운영은 Pro를 권장한다. Free는 이용자가 없는 주에 프로젝트가 멈추고 백업이 없다. DB 백업에는 Storage의 파일이 들어가지 않는다. 출처는 Supabase 가격 페이지와 Backups 문서(2026-11-04)다.

## 2. Render

### Blueprint로 만들기

1. 두 공개 주소를 정한다. backend는 서비스 이름에서 `https://satc-docs-backend.onrender.com`이 된다. 만든 뒤 Render가 준 주소를 확인한다. Vercel은 프로젝트 이름에서 `https://<project>.vercel.app`이 된다.
2. Render Dashboard에서 **New → Blueprint**를 누르고 이 저장소를 연결한다. `main` 브랜치의 `render.yaml`을 읽는다.
3. 입력란 11개를 채운다. `sync: false`인 값은 Blueprint를 처음 만들 때만 묻는다. 나중에 바꾸려면 서비스의 **Environment** 탭에서 바꾼다.

   | 환경변수 (backend) | 값 |
   |---|---|
   | `SUPABASE_DB_HOST` | Session pooler 호스트 |
   | `SPRING_DATASOURCE_USERNAME` | `postgres.<project-ref>` |
   | `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |
   | `STORAGE_ENDPOINT` | `https://<project-ref>.storage.supabase.co/storage/v1/s3` |
   | `STORAGE_BUCKET` | 1절에서 만든 버킷 |
   | `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | Supabase S3 액세스 키 |
   | `CORS_ALLOWED_ORIGINS` | Vercel 주소(예: `https://satc-docs-portal.vercel.app`). 사용자 도메인을 쓰면 쉼표로 이어 붙인다 |
   | `DOCS_AUTH_ADMIN_USERNAME` / `DOCS_AUTH_ADMIN_PASSWORD` | 첫 관리자. 이미 있는 username이면 비밀번호를 바꾸지 않는다 |
   | `DOCS_OAUTH_CONSENT_URL` | `<Vercel 주소>/oauth/consent` |

4. **Apply**를 누른다. indexing은 위 값 가운데 DB 호스트, DB 계정, 저장소 값을 backend에서 참조(`fromService.envVarKey`)하므로 따로 넣지 않는다.

### Blueprint가 정하는 값

| 환경변수 | 값 | 이유 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${SUPABASE_DB_HOST}:5432/postgres?sslmode=require` | Spring이 `${...}`를 다른 환경변수로 채운다 |
| `DOCS_DB_SCHEMA` / `SEARCH_PG_SCHEMA` | `docs_portal` | 1절 "스키마와 RLS" |
| `DOCS_AUTH_TOKEN_SECRET` | Render가 만든 256비트 값 | 로그인 토큰 서명 키. 바꾸면 기존 토큰이 모두 무효가 된다 |
| `DOCS_AUTH_DEV_ACCOUNTS` | `false` | `true`면 `admin`/`admin123`처럼 비밀번호가 알려진 계정 9개를 만든다 |
| `DOCS_OAUTH_ISSUER` | `${RENDER_EXTERNAL_URL}` | Render가 넣는 backend 공개 주소. 사용자 도메인을 붙이면 그 주소로 바꾼다 |
| `EXTERNAL_API_HOST` / `EXTERNAL_API_PORT` | indexing의 사설망 호스트와 포트(`fromService`) | 호스트에 스킴이 없으면 backend가 `http://`를 붙인다 |
| `BACKEND_API_HOST` / `BACKEND_API_PORT` | backend의 사설망 호스트와 포트(`fromService`) | indexing이 색인 결과를 알린다 |
| `PORT` | backend `8080`, indexing `8000` | 이미지가 듣는 포트를 Render에 알린다 |
| `JAVA_TOOL_OPTIONS`, `MALLOC_ARENA_MAX` | `-Xmx200m -XX:MaxMetaspaceSize=128m -Xss512k`, `2` | 아래 "플랜과 메모리" |
| `DOCS_MAX_UPLOAD_SIZE` | `10MB` | 아래 "플랜과 메모리". 요청 전체 상한은 기본값 100MB |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE`, `QUEUE_MAX_WORKERS` | `5`, `2` | 1절 "연결 정보" |
| `SPRING_JPA_SHOW_SQL`, `DEBUG` | `false`, `false` | 기본값이 모든 SQL과 디버그 로그를 남기고, indexing은 500 응답에 예외 내용을 넣는다 |

`SPRING_PROFILES_ACTIVE`는 두지 않는다. `dev` 프로필은 개발용 계정과 임시 서명 키를 켠다. `DOCS_SYNC_TOKEN`(코드→문서 동기화)과 `DOCS_OAUTH_ALLOWED_REDIRECT_URIS`는 필요할 때 Environment 탭에서 추가한다. 나머지 항목은 [README](../README.md)의 환경변수 절에 있다.

### 플랜과 메모리

두 서비스 모두 `0.5c-512mb`(0.5 CPU, 512 MB)다. 아래 값은 로컬 Docker(arm64, Linux 6.8)에서 깨끗한 클론으로 만든 이미지를 `--memory=512m --cpus=0.5`로 띄우고 운영과 같은 환경변수(비밀은 더미)로 잰 것이다. backend만 잴 때는 indexing 호출을 껐다(`EXTERNAL_API_ENABLED=false`). Render(amd64)에서 직접 재지는 않았다. RSS는 프로세스, 최고값은 cgroup `memory.peak`다.

| 서비스, 설정 | 시나리오 | 결과 |
|---|---|---|
| backend, 기본 JVM(`-XX:MaxRAMPercentage=75`, 힙 최대 384 MiB), 상한 50MB | 기동 직후 | RSS 312~363 MiB (5회 기동) |
| | 200KB 문서 20개 업로드 뒤 | RSS 376 MiB |
| | 45MB 문서 1개 업로드 뒤 | RSS 414 MiB, 최고 485 MiB |
| | 45MB 문서 3개 동시 업로드 | 커널이 컨테이너를 종료(OOMKilled), 2회 모두 |
| backend, 힙 최대 384 MiB, 상한 10MB | 9.5MB 문서 5개 동시 업로드 | OOMKilled, 2회 모두. 두 번째는 `MALLOC_ARENA_MAX=2`, `-Djdk.nio.maxCachedBufferSize=262144`를 더했다 |
| backend, `render.yaml` 설정(힙 200 MiB), 상한 10MB | 기동 직후 | RSS 342 MiB |
| | 9.5MB 문서 5개 동시 업로드 3회 | RSS 408 MiB, 최고 444 MiB, 모두 성공 |
| | 9.5MB 문서 본문 15개 동시 조회 | RSS 411 MiB, 최고 444 MiB. 1건이 Java 힙 부족으로 실패하고 프로세스는 유지 |
| | 9.5MB 파일 10개를 담은 요청 하나(95MB) 2회 | 최고 442 MiB, 모두 성공 |
| indexing, 작업자 2개 | 기동 직후 | RSS 86~103 MiB (6회 기동) |
| | 10MB 문서 1개 색인 | 최고 202 MiB, 21초 |
| | 45MB 문서 1개 색인 | 커널이 컨테이너를 종료(OOMKilled) |
| | 9.5MB 문서 5개씩 3회 색인 | 최고 412 MiB. 회차가 끝난 뒤 RSS 281 → 292 → 315 MiB. 2회차의 3건은 앞 시험에서 원본이 저장되지 않아 실패했다 |

- backend는 업로드 파일 전체를 `byte[]`로 읽고 저장소에 넣을 때 한 번 더 복사한다. 본문 조회도 파일 전체를 메모리에 올린다. 기동 직후 JVM Native Memory Tracking 합계는 320 MiB였고 그중 힙이 148 MiB였다. 힙 밖에서 약 240 MiB(메타스페이스 78 MiB, 심볼 21 MiB, 코드 20 MiB, NMT 밖 약 65 MiB 등)를 쓰므로 512 MB 안에서 힙은 200 MiB 남짓이 한계다. 업로드 상한을 10MB로 낮춰도 힙 상한이 384 MiB면 죽었고, 힙을 200 MiB로 묶은 뒤에야 같은 부하에서 살아남았다. 힙이 모자라면 그 요청만 실패한다.
- Dockerfile의 `ENTRYPOINT`가 `-XX:MaxRAMPercentage=75.0`을 직접 넘기므로 `JAVA_TOOL_OPTIONS`의 `MaxRAMPercentage`는 무시된다. 그래서 `-Xmx`를 쓴다. compose의 `JAVA_OPTS`는 `ENTRYPOINT`가 읽지 않아 효과가 없다.
- indexing은 파일 전체를 문자열로 풀고 청크를 모두 메모리에 둔다. 512 MB에서는 10MB 상한 문서 두 개를 동시에 처리할 수 있다. RSS가 회차마다 조금씩 늘었으므로 Render의 Metrics에서 메모리를 지켜본다. 메모리 초과로 프로세스가 죽으면 대기 중인 작업이 사라진다(아래 재색인 절차).
- 업로드 상한을 50MB로 되돌리려면 `DOCS_MAX_UPLOAD_SIZE`를 지우고 두 서비스를 `1c-2g` 이상으로 올린다. 2 GB에서는 재지 않았다. 로컬 Docker VM에 남은 메모리가 약 0.7 GB라서 2 GB 컨테이너를 띄우지 않았다.
- backend는 0.5 CPU에서 기동에 약 21초 걸린다. 헬스 체크(`/hello`)는 기동이 끝난 뒤 통과한다.

### 한 대만 띄운다

- backend의 SSE 허브와 OAuth 동의 대기 요청, 인가 코드는 프로세스 메모리에 있다. 두 대를 띄우면 알림이 일부 화면에만 가고 OAuth 로그인이 실패한다.
- indexing의 작업 큐도 프로세스 메모리에 있다.
- `render.yaml`은 두 서비스 모두 `numInstances: 1`이다. 자동 확장(autoscaling)을 켜지 않는다.

### 재배포와 재색인

indexing이 재배포되거나 메모리 초과로 다시 뜨면 큐에서 기다리던 작업은 사라진다. 처리 중이던 작업은 종료 신호 뒤 최대 120초(`maxShutdownDelaySeconds`) 동안 끝나기를 기다린다. 작업이 사라진 문서는 `INDEXING` 상태로 남고 검색에 나오지 않는다. 다음 순서로 복구한다.

1. Supabase SQL Editor에서 남은 문서를 확인한다.

   ```sql
   select id, title, status, updated_at from docs_portal.document
   where status in ('UPLOADING', 'INDEXING') and coalesce(is_folder, false) = false and coalesce(is_deleted, false) = false;
   ```

2. Render에서 `satc-docs-indexing`의 **Shell**을 열고 재색인한다. 지우지 않은 모든 문서의 검색 행을 다시 쓰며, 다시 실행해도 결과가 같다.

   ```bash
   python -m scripts.reindex_pg --dry-run   # 대상 수와 앞 10개 ID
   python -m scripts.reindex_pg             # 마지막 줄 JSON 의 failures 가 비어 있어야 한다
   ```

3. 검색 행이 생긴 문서의 상태를 바꾼다.

   ```sql
   update docs_portal.document d set status = 'COMPLETE'
   where status in ('UPLOADING', 'INDEXING') and coalesce(is_folder, false) = false and coalesce(is_deleted, false) = false
     and exists (select 1 from docs_portal.search_document s where s.document_id = d.id);
   ```

임시 환경에서 indexing을 멈춘 채 올린 문서로 이 순서를 확인했다. 문서는 `INDEXING`으로 남았고 검색 결과가 0건이었다. 재색인 뒤 1건이 검색되었고, 3단계 뒤 화면 트리에 `COMPLETE`로 보였다. 3단계는 SQL로 바꾸므로 열려 있는 화면에 실시간 알림이 가지 않는다. 새로고침하면 반영된다.

## 3. Vercel

1. **Add New → Project**에서 이 저장소를 고르고 **Root Directory**를 `frontend`로 둔다. 나머지 빌드 설정은 `frontend/vercel.json`이 정한다.
2. **Environment Variables**에 다음을 넣고 배포한다.

| 환경변수 | 값 |
|---|---|
| `VITE_API_BASE_URL` | backend 공개 주소(예: `https://satc-docs-backend.onrender.com`). 비어 있으면 빌드가 실패한다 |
| `VITE_JENKINS_URL` / `VITE_ARGOCD_URL` / `VITE_COMMON_CODE_URL` | 선택. 비우면 상단 바로가기를 숨긴다 |

- 이 값은 빌드할 때 화면 코드에 들어간다. 바꾸면 다시 배포해야 한다. 컨테이너 배포처럼 기동할 때 `/config.js`를 다시 쓰는 단계가 없다. `public/config.js`는 빈 설정(`{}`)이라 `src/config.js`가 빌드 값을 읽는다.
- `vercel.json`은 `npm ci`와 `npm run build`로 `dist`를 만들고, 파일이 없는 경로를 `index.html`로 돌린다(vue-router history 모드). nginx 설정의 보안 헤더와 캐시 규칙(`/assets/*` 1년, `/config.js` 캐시 안 함)도 옮겨 두었다.
- Vercel은 화면 파일만 준다. API와 SSE는 backend로 바로 가므로 Vercel 리전 설정이나 함수가 필요 없다.
- backend의 `CORS_ALLOWED_ORIGINS`와 `DOCS_OAUTH_CONSENT_URL`에 이 프로젝트의 운영 주소를 넣는다. 미리보기 배포 주소는 CORS 목록에 없으므로 그 화면의 API 호출은 막힌다.

확인한 것과 하지 못한 것은 다음과 같다. 깨끗한 클론에서 `vercel.json`의 `buildCommand`를 그대로 실행해 빌드했다. `VITE_API_BASE_URL`이 비면 종료 코드 1로 멈췄다. `dist`를 `vercel.json`의 rewrite와 headers를 그대로 적용하는 정적 서버로 띄우고 headless Chrome으로 `/specs`를 열었다. 로그인 화면으로 이동했고, 실제 로그인 폼으로 로그인한 뒤 모든 API 요청(로그인, 트리, 통계, PRD 목록)과 `EventSource`가 빌드 값의 backend 주소로 갔다. 정적 서버 쪽 `/api` 요청은 0건이었다. `vercel.json`은 Vercel 공개 스키마 검증을 통과했다. Vercel에 실제로 배포해 보지는 않았다.

## 4. 배포 후 확인

`BE=https://satc-docs-backend.onrender.com`, `FE=https://<project>.vercel.app`로 두고 확인한다.

```bash
curl -s $BE/hello                                                      # HELLO, WORLD!
curl -s $BE/.well-known/oauth-authorization-server | jq -r .issuer     # $BE 와 같아야 한다
curl -s -o /dev/null -D - -X OPTIONS $BE/api/v1/docs/root \
  -H "Origin: $FE" -H 'Access-Control-Request-Method: GET' | grep -i access-control-allow-origin   # $FE
curl -s -X POST $BE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"<관리자>","password":"<비밀번호>"}' | jq -r '.data.token != null'             # true
curl -s -X POST $BE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token != null'                  # false (개발용 계정 없음)
curl -sN --max-time 3 $BE/api/v1/events | head -2                     # event:hello
```

업로드, 색인, 검색을 한 번에 확인한다. 끝나면 시험 문서를 지운다.

```bash
printf '# 배포 점검\n\nzebracrossing 은 배포 확인용 낱말이다.\n' > /tmp/check.md
ID=$(curl -s -F 'files=@/tmp/check.md;filename=배포 점검.md' $BE/api/v1/docs/multiple | jq -r '.data[0].documentId')
sleep 10
curl -s $BE/api/v1/docs/root | jq -r ".data[] | select(.id == \"$ID\") | .status"      # COMPLETE
curl -s "$BE/api/v1/docs/search?q=zebracrossing" | jq -r '.data.results[].title'        # 배포 점검
curl -s -X DELETE $BE/api/v1/docs/$ID | jq .data                                         # true
```

- indexing은 공개 주소가 없다. backend의 **Shell**에서 `curl -s http://$EXTERNAL_API_HOST:$EXTERNAL_API_PORT/api/v1/health`로 확인한다. ES가 없으므로 `"elasticsearch_healthy": false`가 정상이다. `"status": "healthy"`, `"postgres_healthy": true`, `"storage_healthy": true`를 본다.
- Supabase SQL Editor에서 테이블 위치를 본다. `select schemaname, count(*) from pg_tables where schemaname in ('public', 'docs_portal') group by 1;` 결과에서 `docs_portal`이 12여야 한다. 1절의 이관을 하지 않았다면 `public`에는 로컬 스택의 테이블이 그대로 있다.
- 노출 여부를 본다. `curl -s "$SUPABASE_URL/rest/v1/document?select=id&limit=1" -H "apikey: <publishable key>"`가 행을 돌려주면 안 된다. 이관 전의 `public.document`가 남아 있으면 RLS가 켜져 있는지 확인한다.
- `$FE/specs` 같은 깊은 경로를 브라우저에서 새로고침해 화면이 뜨는지, 로그인 뒤 상단에 "실시간 연결 끊김"이 나오지 않는지 본다.

`render.yaml`과 같은 환경변수 조합(비밀은 더미)으로 띄운 임시 컨테이너(backend, 포트를 열지 않은 indexing, SSL을 켠 PGroonga 16, MinIO)에서 위 curl 명령을 실행해 주석의 결과를 확인했다. 같은 내용을 스크립트로 21개 항목으로 나눠 확인했고 모두 통과했다. Render와 Vercel에서는 실행하지 않았다.

## 5. 롤백

- **Render.** 서비스의 **Events**에서 이전 배포를 골라 **Rollback**한다. backend는 `ddl-auto=update`로 테이블과 컬럼을 추가만 하므로, 앱을 되돌려도 새로 생긴 컬럼은 DB에 남는다. 새 컬럼에 NOT NULL 제약이 있으면 예전 코드의 저장이 실패할 수 있다(추정, 시험하지 않음).
- **Vercel.** **Deployments**에서 이전 배포를 골라 **Instant Rollback**(또는 **Promote to Production**)한다.
- **DB.** Pro 플랜은 **Database → Backups**에서 매일 백업으로 되돌린다. Free 플랜은 자동 백업이 없다. 파일 저장소는 DB 백업에 들어가지 않는다.
- `DOCS_DB_SCHEMA`나 `SEARCH_PG_SCHEMA`를 비우면 롤백이 아니라 다른 데이터(`public`)를 읽는다. 두 값은 바꾸지 않는다.

## 6. 알려진 제약

- **문서 API에 서버 인증이 없다.** `/api/v1/docs/**`(업로드, 조회, 본문, 검색, 이동, 삭제, 상태 변경)와 `/api/v1/events`는 토큰을 확인하지 않는다. 화면의 로그인은 브라우저에서만 막는다. backend 공개 주소를 아는 사람은 누구나 문서를 읽고 지울 수 있다. 임시 환경에서 `Authorization` 헤더 없이 업로드와 삭제가 성공했다. 실제 문서를 올리기 전에 이 점을 판단한다.
- **단일 파일 업로드(`POST /api/v1/docs`)가 파일을 잃는다.** 요청이 끝난 뒤 `@Async` 스레드에서 업로드 임시 파일을 읽는데, 그때 Tomcat이 이미 파일을 지운 경우가 있다(`NoSuchFileException`). 0.5 CPU 컨테이너에서 연속 20건 중 9건이 이렇게 사라졌고 API는 모두 성공으로 응답했다. 여러 파일 업로드(`POST /api/v1/docs/multiple`)는 요청 안에서 파일을 읽어 이 문제가 없다. 화면은 두 API를 모두 쓴다.
- 화면의 API 클라이언트 제한 시간이 10초다. 여러 파일 업로드는 저장소에 넣을 때까지 응답하지 않으므로 큰 파일은 화면에서 실패로 보여도 서버에서는 끝날 수 있다.
- indexing의 `/api/v1/health`는 ES 상태를 함께 보고 ES가 없으면 `elasticsearch_healthy: false`를 낸다. `/api/v1/stats`는 매번 ES에 접속을 시도해 오류 로그를 남긴다. `/api/v1/health`는 비동기 처리 안에서 `time.sleep`으로 최대 10초를 기다리므로 그동안 indexing의 다른 요청이 멈춘다.
- indexing은 FastAPI 문서 화면(`/docs`, `/redoc`)과 CORS `*`를 연다. 비공개 서비스라서 인터넷에는 보이지 않는다. 공개 서비스로 바꾸지 않는다.
- 작업 큐, SSE 허브, OAuth 대기 요청이 메모리에 있어 서비스마다 한 대만 띄운다.
- `ddl-auto=update`가 운영 DB 스키마를 바꾼다. 컬럼 이름 변경이나 삭제는 반영하지 않는다.
- Render의 `fromService`와 `envVarKey` 참조, `${RENDER_EXTERNAL_URL}` 값이 실제로 어떻게 풀리는지는 Render에 배포해 보지 않아 확인하지 못했다. 로컬 시험은 Render 문서에 적힌 대로 값을 풀어 넣었다. 첫 배포 뒤 backend의 Environment 탭과 4절의 `issuer`를 확인한다.
