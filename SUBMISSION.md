# MatchUp 제출 정보

## 프로젝트명
**MatchUp — AI 개인 맞춤형 공모전 추천 플랫폼**

## 팀명
**MatchUp**

의미: 각 학생의 관심사와 조건에 맞는 공모전 기회를 연결합니다.

## 팀원 정보

| 이름 | 이메일 | 역할 |
| --- | --- | --- |
| Jeon Yeongwoo | zbawoo2k@gmail.com | 기획 / 개발 전체 |

## 공개 GitHub 저장소
`https://github.com/JeonYeongwoo/matchup`

저장소를 만든 뒤 **Public**으로 설정하고 위 주소를 제출합니다. API 키, 실제 대화 내보내기 파일, 개인 정보는 저장소에 올리지 않습니다.

## 제출 파일
- `MatchUp_Project_Deck.pdf` (10MB 이하)
- `slides/MatchUp_Project_Deck.html` (편집 가능한 발표 자료 원본)

## 실행
```powershell
cd web
npm run dev
```

```powershell
.\gradlew.bat bootRun
```

API 키는 서버 환경 변수 `OPENAI_API_KEY`로만 설정하며, 클라이언트 앱이나 Git에 넣지 않습니다.
