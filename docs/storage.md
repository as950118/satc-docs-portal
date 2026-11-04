# 파일 저장소 (S3 호환)

업로드한 문서 파일 원본은 S3 API 를 쓰는 저장소 하나에 둡니다. 기본값은 compose 의 `minio` 컨테이너이고, 같은 코드로 Supabase Storage 나 Cloudflare R2 에 붙일 수 있습니다. 백엔드는 AWS SDK for Java v2(`S3ObjectStorage`), 인덱싱 서비스는 boto3(`app/services/object_storage.py`)를 path-style 로 씁니다.

## 설정

백엔드와 인덱싱 서비스가 같은 `STORAGE_*` 환경변수를 읽습니다. 값이 비어 있으면 아래 기본값을 쓰고, 주소·버킷·계정은 예전 `MINIO_*` 값을 씁니다. 그래서 `MINIO_*` 만 설정한 기존 배포는 그대로 같은 MinIO 에 붙습니다. 잘못된 주소, 버킷 이름, `true`/`false` 가 아닌 값은 기동을 멈춥니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `STORAGE_ENDPOINT` | 백엔드 `http://{MINIO_HOST}:{MINIO_PORT}`, 인덱싱 `http(s)://{MINIO_ENDPOINT}`, compose `http://minio:9000` | S3 API 주소. 경로가 붙어도 됩니다 (Supabase `/storage/v1/s3`) |
| `STORAGE_REGION` | `us-east-1` | 서명에 쓰는 리전. Supabase 는 프로젝트 리전을 넣습니다 |
| `STORAGE_BUCKET` | `MINIO_BUCKET_NAME` (`docs`) | 문서 버킷 |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | 백엔드 `MINIO_CLIENT_ID`/`MINIO_CLIENT_SECRET`, 인덱싱·compose `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` | S3 액세스 키 |
| `STORAGE_PATH_STYLE` | `true` | 버킷을 주소의 경로에 넣습니다. MinIO 와 Supabase 는 `true` 여야 합니다 |
| `STORAGE_MANAGE_BUCKET` | `true` | 버킷이 없으면 만듭니다. 백엔드는 업로드마다 확인하고 실패하면 로그만 남깁니다. 인덱싱 서비스는 기동할 때 확인하고 실패하면 기동하지 않습니다 |
| `STORAGE_PUBLIC_READ` | `true` | 버킷을 새로 만들 때 익명 읽기(`s3:GetObject`) 정책을 겁니다. 이미 있는 버킷은 바꾸지 않습니다. 포털은 본문을 백엔드 API 로 읽으므로 이 정책이 필요하지 않습니다 |
| `STORAGE_ASCII_KEYS` | `false` | 백엔드만 씁니다. `true` 면 객체 키가 `documents/{id}/{id}.{ext}` 이고, `false` 면 지금처럼 `documents/{id}/{제목}.{ext}` 입니다 |
| `DOCS_MAX_UPLOAD_SIZE` / `DOCS_MAX_REQUEST_SIZE` | `50MB` / `100MB` | 백엔드 업로드 상한(파일 하나, 요청 전체). 넘으면 413 입니다 |

키에 제목을 넣지 않아도 검색 결과 제목은 그대로입니다. 백엔드는 색인 요청(`POST /api/v1/process-document`)에 `title` 을 함께 보내고, 인덱싱 서비스는 `title` 이 있으면 그 값을 청크와 전체 문서의 제목으로 씁니다. `title` 이 없는 요청(예전 백엔드)은 예전처럼 키에서 제목을 뽑습니다.

## Supabase Storage 로 바꾸기

Supabase Storage 의 S3 API 는 MinIO 와 세 가지가 다릅니다. 이 세 가지는 직접 확인했습니다.

- S3 API 로 버킷을 만들거나 버킷 정책을 걸 수 없습니다. 버킷은 Supabase 대시보드에서 미리 만들고 `STORAGE_MANAGE_BUCKET=false` 로 둡니다.
- 한글, `%`, `#`, 괄호가 든 키를 `400 InvalidKey` 로 거부합니다. 영문, 숫자, 공백, 하이픈은 받습니다. 그래서 `STORAGE_ASCII_KEYS=true` 가 필요합니다. 거부되는 키는 HEAD 도 404 가 아니라 400 을 돌려줍니다.
- 주소에 `/storage/v1/s3` 경로가 붙습니다. path-style 이 아니면 경로가 빠져 모든 요청이 404 가 됩니다.

1. Supabase 대시보드에서 문서 버킷을 만들고 S3 액세스 키를 발급합니다.
2. 저장소 루트 `.env` 에 다음을 넣습니다.

   ```sh
   STORAGE_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
   STORAGE_REGION=<프로젝트 리전, 예: ap-northeast-2>
   STORAGE_BUCKET=<1에서 만든 버킷>
   STORAGE_ACCESS_KEY=<액세스 키 ID>
   STORAGE_SECRET_KEY=<시크릿 키>
   STORAGE_MANAGE_BUCKET=false
   STORAGE_PUBLIC_READ=false
   STORAGE_ASCII_KEYS=true
   ```

3. 백엔드와 인덱싱 서비스를 다시 띄웁니다: `docker compose up -d --build backend indexing`
4. 아래 저장소 계약 시험을 Supabase 설정으로 돌려 연결을 확인합니다.

이미 MinIO 에 있는 문서는 Supabase 로 옮기지 않습니다. 저장소를 바꾸면 그 전 문서의 본문은 열리지 않으므로, 옮기려면 객체를 같은 키로 복사하는 작업이 따로 필요합니다. 그 전에 아래 이관 스크립트로 키를 ASCII 로 바꿔 두어야 Supabase 가 받아 줍니다.

Cloudflare R2 는 `STORAGE_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com`, `STORAGE_REGION=auto`, `STORAGE_MANAGE_BUCKET=false` 로 붙도록 만들었지만 실제 R2 로는 시험하지 않았습니다.

## 기존 문서 키를 ASCII 로 옮기기

`STORAGE_ASCII_KEYS=true` 로 바꾸면 새 업로드만 ASCII 키를 씁니다. 그 전에 올라온 문서는 `indexing/scripts/migrate_ascii_keys.py` 로 옮깁니다. 스크립트는 문서마다 객체를 새 키로 복사하고, 인덱싱 서비스에 `title` 과 함께 재색인을 요청한 뒤 `document.file_path` 를 바꿉니다. 예전 객체는 되돌릴 수 있게 지우지 않습니다.

1. 업로드와 코드 동기화를 멈춥니다.
2. 계획을 확인합니다. 옵션 없이 돌리면 아무것도 바꾸지 않고 문서마다 `[copy]`, `[copied]`, `[source-missing]` 상태를 출력합니다.

   ```sh
   cd indexing
   pip install -r scripts/requirements.txt
   DATABASE_URL=postgresql://postgres:<비밀번호>@localhost:5432/docs \
   STORAGE_ENDPOINT=http://localhost:9000 STORAGE_ACCESS_KEY=<키> STORAGE_SECRET_KEY=<시크릿> \
   INDEXING_URL=http://localhost:8000 \
   python -m scripts.migrate_ascii_keys
   ```

3. 같은 명령에 `--apply` 를 붙여 실행합니다. 모두 옮기면 종료 코드 0, 남은 문서가 있으면 1 입니다.
4. 남은 문서가 있으면 원인을 고치고 다시 실행합니다. 이미 옮긴 문서는 계획에서 빠지고, 복사까지만 끝난 문서는 복사를 건너뛰고 나머지를 합니다.

## 저장소 계약 시험

실제 저장소에 붙어 put, get, exists, delete(인덱싱은 copy 포함)와 한글 키 처리를 확인합니다. 객체는 버킷의 `compat-test/` 아래에만 만들고 끝나면 지웁니다. `STORAGE_CONTRACT_REJECTS_NON_ASCII=true` 면 한글 키가 `400 InvalidKey` 로 거부되기를 기대하고(Supabase), 아니면 저장되기를 기대합니다(MinIO).

```sh
export STORAGE_CONTRACT_TEST=true STORAGE_ENDPOINT=... STORAGE_REGION=... STORAGE_BUCKET=... \
       STORAGE_ACCESS_KEY=... STORAGE_SECRET_KEY=... STORAGE_MANAGE_BUCKET=false STORAGE_CONTRACT_REJECTS_NON_ASCII=true
(cd backend && ./gradlew test --tests 'com.docsportal.docs.config.StorageContractTest')
(cd indexing && pip install -r requirements-dev.txt && python -m pytest tests/test_object_storage_contract.py)
```
