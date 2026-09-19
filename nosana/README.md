# Nosana GPU 배포 (LLM 추론 서버)

이 폴더는 **Nosana GPU 노드에서 돌아가는 추론 서버**만 정의합니다. 크롤러/DB/Spring Boot API는
여기 포함되지 않고 일반 서버에서 실행되며, HTTP로 이 서버를 호출합니다 (`LLM_BASE_URL`).

## 컨테이너에 절대 포함하지 않는 것
- DB 접속 정보, `DAYTONA_API_KEY`, `OPENAI_API_KEY` 등 백엔드 시크릿
- 사용자 프로필/피드백 등 사용자 데이터

컨테이너에는 모델 가중치와 Ollama 서버만 들어있고, `CHAT_MODEL`/`EMBEDDING_MODEL` 환경변수만 받습니다.

## 사전 준비물 (Nosana 대시보드/CLI에서 직접 해야 하는 것)
1. **Solana 지갑 키페어** — `nosana address` 또는 `solana-keygen new`로 생성. 이 리포지토리나 GPU 이미지에 절대 커밋하지 마세요.
2. **NOS 토큰** — GPU 사용료 결제용
3. **SOL** — 트랜잭션 수수료용 (소량)
4. **Nosana CLI**: `npm i -g @nosana/cli`
5. **Docker Hub 계정** (또는 인증 가능한 프라이빗 레지스트리) — Nosana 호스트 노드가 이미지를 pull하므로 로컬 이미지는 사용 불가
6. **GPU 마켓 주소** — https://dashboard.nosana.com/markets 에서 필요한 VRAM/GPU 종류 확인 후 마켓 주소 확보

## 배포 절차
```bash
# 1) 이미지 빌드 & 푸시 (공개 pull 가능해야 함)
docker build -t <DOCKERHUB_USERNAME>/matchup-ollama:latest -f nosana/Dockerfile.ollama nosana/
docker push <DOCKERHUB_USERNAME>/matchup-ollama:latest

# 2) job-definition.json의 <YOUR_DOCKERHUB_USERNAME> 을 실제 값으로 치환

# 3) job 게시 (지갑/NOS/SOL 필요)
nosana job post ./nosana/job-definition.json --market <MARKET_ADDRESS> --timeout 60
```

`nosana job post`가 완료되면 콘솔에 `job id`와 함께 접근 URL이 출력됩니다
(형태: `https://<job-id>.<node>.node.k8s.prd.nos.ci`). 이 URL을 백엔드의 환경변수로 설정합니다.

```bash
export LLM_BASE_URL=https://<job-id>.<node>.node.k8s.prd.nos.ci
```

## 운영 시 주의
- Nosana job은 **결제한 시간만큼만 실행**됩니다 (상시 서비스가 아님). 항상 켜져 있어야 한다면
  만료 전에 재게시하는 절차를 별도로 마련해야 합니다 (MVP 범위 밖 — 운영 스크립트/알림 추가 필요).
- job이 재게시되면 URL이 바뀌므로, 배포 자동화 시 `LLM_BASE_URL`을 갱신하는 단계를 반드시 포함하세요.
- `LLM_BASE_URL`이 비어 있으면 백엔드는 자동으로 휴리스틱(규칙 기반) 추출/추천 로직으로 폴백합니다 —
  즉 Nosana 배포 전에도 서비스 전체는 정상 동작합니다 (정확도만 낮아짐).
