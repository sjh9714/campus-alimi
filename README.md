# 캠퍼스 알리미 (campus-alimi)

가톨릭대 학생에게 돈이 걸린 공지와 학생 혜택을, 출처와 확인일이 붙은 것만 보여주고 마감 전에 알려 주는 서비스입니다.

## 무엇을 하나요

- 학사일정(수강신청, 수강신청 변경, 등록기간, 휴학·복학, 성적 정정)과 공지사항의 장학·학사 글을 모아 마감이 급한 순서로 보여줍니다. 누르면 학교 원문으로 갑니다.
- 대학생 자격으로 받는 혜택(개발 도구, 구독, 장학·학비)을 체크리스트로 관리합니다. 목록은 `src/main/resources/benefits.yml`에 있고, 항목마다 출처와 확인일이 붙어 있습니다.
- 입학년도와 전공을 넣어 두면 내게 해당하는 항목이 위로 옵니다. 대상을 확인하지 못한 항목은 숨기지 않고 "대상 확인 필요"로 보여줍니다.
- 알림은 세 가지뿐입니다. 새 장학 공지, 저장한 공지의 마감 3일 전, 내 혜택의 만료 7일 전.

## 지키는 원칙

출처 링크, 확인일, 마감 또는 만료일이 모두 있는 항목만 화면에 나옵니다. 날짜를 읽지 못한 공지는 추측하지 않고 "마감 확인 필요"로 표시합니다. 자세한 규칙은 `docs/decisions/003-accuracy.md`에 있습니다.

## 실행

JDK 21, Docker가 필요합니다.

```bash
cp .env.example .env            # 로컬 기본값. 비밀번호를 바꾸려면 여기서
docker compose up -d --wait postgres
./gradlew test                  # 저장해 둔 학교 페이지로 파서와 수집기를 검사 (Testcontainers가 Postgres를 띄움)
RUN_ONCE=all ./gradlew bootRun  # 학사일정과 공지를 한 번 수집하고 종료
./gradlew bootRun               # 상주 실행. 학사일정은 매일 06:00, 공지는 매시 07분에 수집
```

화면은 `web/`에 있습니다. Node 22가 필요합니다.

```bash
cd web && npm install
NEXT_PUBLIC_API_BASE=http://localhost:8080 npm run dev   # http://localhost:3000
npm run build                                           # out/ 에 정적 파일
```

로그인은 이메일 링크입니다. 로컬(`MAIL_MODE=console`)에서는 링크가 API 로그에 찍히니 그 주소를 브라우저에 붙여 넣으면 됩니다.
로컬에서 링크가 API를 가리키게 하려면 API를 `API_URL=http://localhost:8080` 으로 띄웁니다. 검수 화면은 `ADMIN_EMAIL`로 지정한 이메일 하나만 들어갈 수 있습니다.

```bash
ADMIN_EMAIL=you@example.com API_URL=http://localhost:8080 ./gradlew bootRun
```

수집 결과는 `date_item`(날짜 항목), `notice`(공지, 검수 상태), `notice_raw`(원문), `fetch_log`(요청 기록)에 쌓입니다.

```bash
docker exec campusalimi-postgres psql -U campusalimi -d campusalimi \
  -c "select title, starts_on, ends_on from date_item order by ends_on"
```

## 문서

- `docs/decisions/` 결정 기록. 무엇을 왜 골랐는지 다섯 줄씩.
- `docs/architecture.md` 수집에서 알림까지의 흐름.
- `docs/ops-log.md` 운영 일지. 사용자 수, 발송 수, 오류, 정정.
- `docs/ai-log/` 계획과 리뷰 기록.
