# 배포 가이드

로컬 개발은 `./gradlew bootRun` (H2, 무설정). 아래는 **서버 배포**용이다.

## 구성

```
[인터넷] ──443──► Caddy ──► app:8080 (Spring Boot, prod 프로필)
                              │
                              └──► db:3306 (MariaDB 11)
```

- **Caddy** — `${DOMAIN}` 에 대해 Let's Encrypt 인증서를 자동 발급/갱신. HTTP→HTTPS 리다이렉트.
- **app** — 외부에 직접 노출 안 함(`expose` 만). Flyway 가 기동 시 스키마 마이그레이션.
- **db** — 볼륨(`db-data`)에 데이터 영속. compose 밖에서 접근 불가.

## 사전 준비 (한 번)

1. 서버 (Ubuntu 22.04+ 권장, 최소 1GB RAM — 2GB 권장) + Docker / Docker Compose v2
2. 도메인 등록 후 **A 레코드**를 서버 IP로
3. 서버 방화벽에서 **80, 443** 오픈 (22 는 SSH)
4. 공공데이터포털에서 청약홈 API 활용신청 → `PUBLICDATA_SERVICE_KEY` 발급

## 배포

```bash
git clone <repo> chungyak && cd chungyak
cp .env.example .env
vi .env          # DOMAIN, DB_PASSWORD, DB_ROOT_PASSWORD, PUBLICDATA_SERVICE_KEY,
                 # ADMIN_PASSWORD 최소 5개는 반드시 채운다. (ANTHROPIC_API_KEY 는 선택)

docker compose up -d --build
docker compose logs -f app        # "Started ChungyakApplication" 확인
curl https://$DOMAIN/actuator/health   # {"status":"UP"}
```

### 첫 데이터 수집

```bash
curl -u admin:$ADMIN_PASSWORD -X POST https://$DOMAIN/api/admin/sync
# → {"pagesFetched":29,"received":2861,...}  (약 5분)
```

이후 매일 04시(Asia/Seoul) 자동 수집. `AnnouncementSyncScheduler`.

## 업데이트

```bash
git pull
docker compose up -d --build        # 무중단 아님 (수십 초 다운). Flyway 가 새 마이그레이션 적용
```

## 스키마 마이그레이션 (Flyway)

- 마이그레이션 파일: `src/main/resources/db/migration/V{n}__설명.sql`
- **이미 적용된 파일은 절대 수정하지 않는다** (체크섬 불일치로 기동 실패). 항상 새 `V{n+1}` 추가.
- 로컬/테스트는 H2(MODE=MySQL), 운영은 MariaDB — 양쪽에서 도는 SQL 로 작성.
- 엔티티를 바꾸면: 마이그레이션 추가 → `./gradlew test`(H2 에 적용 + JPA validate) → 커밋.
- 기존 운영 DB(마이그레이션 이력 없음)에 처음 붙일 때는 `docker compose run --rm app \
  java -jar app.jar` 대신 Flyway `baseline` 필요 — 지금은 신규 배포 전제라 해당 없음.

## CI (GitHub Actions)

- `push`/`PR` 마다 `./gradlew build` (LLM 통합테스트는 키 없으면 skip).
- `master` push 시 Docker 이미지 빌드 검증.
- **레지스트리 푸시/자동 배포는 아직 미설정.** 붙이려면:
  1. `.github/workflows/ci.yml` 의 `docker` 잡에서 `push: true` + `docker/login-action`(GHCR)
  2. `tags: ghcr.io/<owner>/chungyak:latest,ghcr.io/<owner>/chungyak:${{ github.sha }}`
  3. 서버에서 `docker compose pull && docker compose up -d` 하는 배포 잡(SSH action 또는
     watchtower) 추가

## 백업

```bash
docker compose exec db sh -c 'mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" chungyak' > backup-$(date +%F).sql
```

공고 데이터는 공공 API 에서 다시 수집 가능하므로 치명적이지 않지만, 회원 기능 추가 후에는 정기 백업 필수.

## 모니터링

- `GET /actuator/health` — 로드밸런서/uptime 체크용 (공개)
- 그 외 actuator 는 차단. 필요 시 `application.yml` 의 `management.endpoints.web.exposure.include`
  확대 + `SecurityConfig` 에서 인증 걸고 노출.
- 로그는 `docker compose logs`. 수집기(Loki/CloudWatch 등)는 트래픽 생기면 추가.
