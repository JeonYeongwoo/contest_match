# MatchUp — AI 개인 맞춤형 공모전 추천 플랫폼

MatchUp은 사용자의 관심 분야·전공/직업·지역·자격·개인/팀 선호·상금/마감일 선호를 바탕으로
공모전·해커톤·창업경진대회를 수집하고 개인화 추천하는 서비스입니다.

## 아키텍처 (확장판)

```
[RSS/HTML Sources] → Collector(Java) → raw_documents(Postgres)
                                            │
                                ExtractionService (LLM 또는 휴리스틱 폴백)
                                            │
                            ContestDraft → DedupService(pgvector 코사인 유사도)
                                            │
                        contests + contest_sources + contest_embeddings
                                            │
   UserProfile + Feedback ──▶ RecommendationService(0~100점 스코어링 + 근거 문장)
                                            │
                                REST API (Spring Boot)

[ollama] ── Docker: 로컬 CPU 컨테이너 (OpenAI 호환 /v1/chat, /v1/embeddings) ← LLM_BASE_URL로 호출
```

- 크롤러 / DB / API: 일반 서버 (Spring Boot 단일 프로세스, `docker-compose.yml`)
- LLM 추론: 같은 스택에서 함께 뜨는 로컬 `ollama` 컨테이너가 기본값이며, GPU 없이 CPU로 동작합니다.
  외부 API 키가 전혀 필요 없습니다. `LLM_BASE_URL`을 다른 OpenAI 호환 서버로 바꿔치기할 수도 있습니다.
- `LLM_BASE_URL`을 완전히 비워두면 자동으로 규칙 기반(휴리스틱) 추출/추천으로 폴백합니다.

## 실행

```bash
docker compose up --build
```

Postgres(+pgvector), 로컬 LLM(`ollama`), API가 함께 뜹니다. `ollama-init` 컨테이너가 최초 1회
`llama3.1`/`nomic-embed-text` 모델을 자동으로 내려받은 뒤 종료되며(모델은 볼륨에 캐시되어 재실행 시
즉시 완료됩니다), 그 다음 API가 기동됩니다. 시작 시 샘플 공모전 5건도 자동으로 시드됩니다
(`SEED_ENABLED=false`로 끌 수 있음). API: http://localhost:8080

첫 실행은 모델 다운로드(수 GB) 때문에 몇 분 걸릴 수 있습니다. CPU로 추론하므로 응답이 느리면
`LLM_BASE_URL=https://api.openai.com`, `LLM_API_KEY=<OpenAI 키>`로 환경변수를 바꿔 OpenAI를
대신 쓰거나, `LLM_BASE_URL`을 다른 OpenAI 호환 서버로 지정하세요.

### 웹 데모
`web/`는 사이트 빌더로 생성된 기존 스캐폴드로, 현재 이 백엔드 API를 호출하지 않습니다
(`/api/maps-config`만 자체 라우트로 사용). 새 API(`/api/contests`, `/api/recommendations`,
`/api/feedback` 등)와 연동하는 화면은 별도 작업이 필요합니다 — 필요하시면 이어서 진행해 드립니다.

### Gradle만으로 API 테스트하기 (DB 없이)
```powershell
gradle compileJava
gradle test --tests "com.contestmate.ai.ExtractionServiceTest" --tests "com.contestmate.recommendation.RecommendationServiceTest"
```
전체 `gradle test`는 `ContestMateApplicationTests`가 실제 Postgres 연결을 필요로 하므로 DB 없이는 실패합니다.

## API 엔드포인트

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/contests` | 목록/필터(`region`,`category`,`organizer`,`onlineOnly`,`individualOrTeam`,`minPrize`,`deadlineWithinDays`,`keyword`) |
| GET | `/api/contests/{id}` | 상세 (원본 출처 URL 전체 포함) |
| POST | `/api/profile` | 프로필 저장 (`X-User-Id` 헤더로 익명 사용자 식별) |
| GET | `/api/profile` | 저장된 프로필 조회 |
| POST | `/api/profile/extract` | 텍스트에서 관심사 키워드 추출 (LLM 불필요, 데모용) |
| POST | `/api/recommendations` | 개인화 추천 (점수+근거+자격 경고) |
| GET | `/api/recommendations/home` | 홈: 오늘의 추천 / 마감 임박 / 인기 분야 |
| POST | `/api/feedback` | `NOT_INTERESTED`/`SAVED`/`PLAN_TO_APPLY`/`APPLIED` 기록, 추천에 반영 |
| GET | `/api/feedback` | 사용자 피드백 목록 |
| GET/POST/DELETE | `/api/bookmarks` | 구버전 호환용 (`SAVED` 피드백의 얇은 래퍼) |
| GET/POST | `/api/admin/sources` | 수집 소스 등록/조회 (등록 시 robots.txt 자동 확인) |
| POST | `/api/admin/collectors/run`, `/run/{sourceId}` | 수집 파이프라인 수동 트리거 |

## 데이터 신뢰성 원칙
- 모든 공모전 레코드는 병합 이전 원본 URL을 `contest_sources`에 전부 보존합니다.
- 마감일/지원자격이 원문에서 명확히 확인되지 않으면 임의로 채우지 않고 `deadlineConfirmed=false` /
  `eligibilityConfirmed=false`로 남겨, 추천 응답의 `eligibilityWarning`으로 사용자에게 알립니다.
- 등록되는 모든 소스는 robots.txt를 자동 확인하며, 허용되지 않으면 비활성 상태로만 등록됩니다.

## 보안과 개인정보
- 사용자 식별은 클라이언트가 생성한 익명 UUID(`X-User-Id`)만 사용하며 이메일/이름 등 PII를 수집하지 않습니다.
- `OPENAI_API_KEY`, `LLM_API_KEY`, DB 접속정보는 서버 환경변수로만 설정하고 Git/이미지에 포함하지 않습니다.
- 별도로 배포하는 LLM 추론 서버에는 모델 가중치와 추론 서버만 포함되며 DB/사용자 데이터는 전혀 전달되지 않습니다.

## 제출물
- [제출 정보](SUBMISSION.md)
- [발표 자료 PDF](MatchUp_Project_Deck.pdf)
- [발표 자료 HTML 원본](slides/MatchUp_Project_Deck.html)
