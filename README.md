# 🤖 satc-docs-portal

## 📘 About
[SATC(Standard for AI Tools Commando)](https://okestro.atlassian.net/wiki/x/JAEff)에서 개발하는 MCP Server의 문서 데이터 기반을 관리하는 포탈입니다.  
참고 문서 최신화, 문서 관리 포인트 일원화를 통한 개발 생산성 제고 및 편의성 증대를 목표로 합니다.  
**[참고] 위 프로젝트는 Cursor IDE를 활용한 Vibe Coding 기반으로 개발하였습니다.**

---
## 🧰  Stacks & Dependencies
### Infrastructure
- Database: PostgreSQL 16.3
- Searching Engine: Elasticsearch 8.11.0
- Object Storage: MinIO RELEASE.2025-09-07T16-13-09Z
- Deployment: Docker

### Frontend Server
- Framework: Vue.js 3.3.4
- Build Tool: Vite 4.4.5
- UI Library: Element Plus 2.3.8
- HTTP Client: Axios 1.11.0
- Markdown Rendering: markdown-it 14.1.0
- MinioClient Client: MinIO 8.0.5

### Backend Server
- Framework: Spring Boot 3.5.5
- Java Version: 17
- Database: PostgreSQL (Spring Data JPA)
- MinIO Client: MinIO 8.5.17

### Indexing Server
- Framework: FastAPI 0.104.1
- Python Runtime: uvicorn 0.24.0
- Search Engine: Elasticsearch 8.11.0
- Minio Client Client: MinIO 7.2.0
- Text Processing: LangChain Text Splitters 0.0.1


## 🏗️  Pipeline Overview
![Document Processing Pipeline](./assets/document_processing.png)
<br/><br/>


## 🛠 Server Overview
| 순번 | Server |    Port    |                           역할                           |                                   비고                                    |
|:--:|:------:|:--------------:|:------------------------------------------------------:|:-----------------------------------------------------------------------:|
| 1  |  docs-front  |  3000  | 문서 관리 포탈 Frontend Server | |
| 2  |  docs-back  | 8000 | 문서 관리 Backend Server | |
| 3  |  docs-indexing  | 8080 | 문서 Indexing Server | |
| 4  |  elasticsearch  | 9200/9300   | Chunk 및 문서 적재, 검색 Engine |  |
| 5  |  minio   | 9000/9001 | 실제 문서 관리 Object Storage | [MinIO UI](http://localhost:9001), id: minioadmin / pw: minioadmin|
| 6  |  postgres  |  5432   | 문서 메타데이터 관리 DB | id: postgres / pw: okestro2018|

---
## 🚀  Getting Started
### Docker Compose(All-in One)
> Mac OS를 기준으로 설명합니다.  
> Docker Daemon이 실행되고 있는 상태를 전제로 합니다.  
> Container별 Port Custom은 **docker-compose.yml**에서 가능합니다.
<br/>

***Docker Compose를 활용한 전체 Image Build 및 Container 실행***  

- 이미지 빌드 및 컨테이너 실행 Command
```sh
docker compose up {service-name} -d  
[옵션]
{service-name}: 원하는 service만 올리고 싶은 경우  
-d: Background로 Container를 띄우는 설정
```  
![Image Build](./assets/build_docker_images.png)

- Container Running 단계  
![Container Running](./assets/container_running.png)

- 실행 중인 Container 확인
  ```sh
    docker ps -a | grep satc
  ```
  - 아래 6개 Container가 정상 실행 중인 상태  
    ![Container Check](./assets/container_check.png)

- [CMP 문서관리포탈](http://localhost:3000) 접속 확인


***Docker Compose를 활용한 전체 Image Build 및 Container 삭제***  

- 컨테이너 중지 및 이미지 삭제 Command
```sh
  docker compose down {service-name} -v --rmi all
  [옵션]
  {service-name}: 원하는 service만 내리고 싶은 경우
  -v: 해당 Container의 volume도 같이 삭제하고 싶은 경우
  --rmi all(optional): 관련된 이미지도 전부 지우고 싶은 경우
```


---
## 🌈  License
| 순번 | 사용 기술 |   버전   |           라이선스            |               용도               |  비고  |
|:--:|:----:|:--------:|:--------------------------:|:-------------------------------:|:----:|
| 1  | Elasticsearch | 8.11.0 | [Elastic License 2.0](https://www.elastic.co/kr/licensing/elastic-license) | 문서 Indexing 저장 및 Search Engine | [조직 내부적으로 사용 가능](https://www.elastic.co/kr/licensing/elastic-license/faq/) |
| 2  | PostgreSQL | 16.3 | [PostgreSQL License](https://www.postgresql.org/about/licence/) | 문서 메타데이터 관리 | BSD, MIT License와 유사 |
| 3  | MinIO | RELEASE.2025-09-07T16-13-09Z | [GNU AGPL v3](https://www.min.io/compliance#Licensing) | 실제 문서 데이터 관리 | [조직 내부적으로 사용 가능](https://www.gnu.org/licenses/gpl-faq.html#GPLRequireSourcePostedPublic) |

> 단, 위 License의 적용 범위는, 개인 및 사내 환경(내부)에서 사용하는 경우를 전제로 합니다.  
> 서비스를 공개적으로 배포하거나 외부 고객이 활용하는 경우, 라이센스 정책 위반·소스코드 공개 의무 발생 등의 문제가 발생할 수 있습니다. 

<br/>

## 📄 README Version Management
| 순번 |  작성일자  |   작성자   |           작성 내용            |               비고               |
|:--:|:----:|:--------:|:--------------------------:|:-------------------------------:|
| 1  | 25.09.10 | [신준](mailto:j.shin@okestro.com) | 개요, 기술, Getting Started, License 등 | 초안 |
| 2  | - | - | - | - |

