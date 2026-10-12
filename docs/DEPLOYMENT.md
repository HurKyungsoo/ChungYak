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
# → {"pagesFetched":35,"received":3213,...}  (청약홈 + LH, 2026-10-12)
```

- **동기 응답이라 오래 걸린다.** LH 공고문 PDF 본문 추출이 건당 수 초라, 빈 DB 첫 수집은 20분 넘게,
  이후엔 수 분. curl 에 짧은 `-m` 을 걸면 응답만 못 받고 서버 수집은 계속 돈다 —
  서버 안에서 `nohup` 으로 돌리고 `docker compose logs app` 의 `전체 동기화 완료` 로 확인.
- **수집 중에 재빌드·재시작하지 말 것** (진행 중 수집이 끊긴다). 중복 호출은 `SyncAlreadyRunningException` 으로 막힌다.
- 수집이 정상 완료되기 전에는 `/actuator/health` 가 DOWN(503) 일 수 있다.

이후 매일 04시(Asia/Seoul) 자동 수집. `AnnouncementSyncScheduler`.

## 업데이트

**평소에는 `master` 에 push 하면 자동 배포된다**(아래 "자동 배포"). 수동으로 해야 할 때만:

```bash
git pull
docker compose up -d --build        # 무중단 아님 (수십 초 다운). Flyway 가 새 마이그레이션 적용
```

서버 사용자(`ubuntu`)는 docker 그룹이 아니라 `sudo docker compose ...` 로 실행한다. 저장소는 `~/ChungYak`.

## 스키마 마이그레이션 (Flyway)

- 마이그레이션 파일: `src/main/resources/db/migration/V{n}__설명.sql`
- **이미 적용된 파일은 절대 수정하지 않는다** (체크섬 불일치로 기동 실패). 항상 새 `V{n+1}` 추가.
- 로컬/테스트는 H2(MODE=MySQL), 운영은 MariaDB — 양쪽에서 도는 SQL 로 작성.
- 엔티티를 바꾸면: 마이그레이션 추가 → `./gradlew test`(H2 에 적용 + JPA validate) → 커밋.
- 기존 운영 DB(마이그레이션 이력 없음)에 처음 붙일 때는 `docker compose run --rm app \
  java -jar app.jar` 대신 Flyway `baseline` 필요 — 지금은 신규 배포 전제라 해당 없음.

## CI (GitHub Actions)

- `push`/`PR` 마다 `./gradlew build` (LLM 통합테스트는 키 없으면 skip).
- `master` push 시 Docker 이미지 빌드 검증 → 통과하면 **서버 자동 배포**(`deploy` 잡).
- 레지스트리(GHCR) 푸시는 하지 않는다 — 서버가 소스를 받아 직접 빌드한다(1~2분, 2GB 서버에서 충분).

## 자동 배포

`master` 에 push → CI `build` + `docker` 통과 → `deploy` 잡이 서버에 SSH → 서버가 아래를 수행:

1. `git pull --ff-only`
2. 앱에 영향 없는 변경(문서·`.github/`·`scripts/` 만)이면 **재빌드 생략**
3. **공고 수집이 진행 중이면 끝날 때까지 대기**(최대 30분) — 재시작하면 수집이 끊긴다.
   수동 수집은 로그로, 정기 수집은 04:00~04:50(KST) 창으로 판단한다.
4. `docker compose up -d --build` → app 컨테이너가 `healthy` 될 때까지 대기(최대 4분)
5. 바깥에서 `DEPLOY_HEALTH_URL` 이 `UP` 인지 한 번 더 확인

실패하면(빌드·기동·헬스체크) 잡이 빨갛게 되어 GitHub 알림이 온다. **자동 롤백은 하지 않는다** —
Flyway 마이그레이션은 되돌릴 수 없어, 코드만 이전 커밋으로 돌리면 새 스키마와 어긋날 수 있다.
원인을 보고 수정 커밋을 push 하거나 서버에서 수동 복구한다(복구 전 `~/backup-*.sql` 참고).

**수동 재배포**: Actions 탭 → CI → Run workflow (변경이 없어도 다시 빌드한다. 수집 때문에 보류됐을 때).

### 구성 요소

| 위치 | 이름 | 역할 |
|---|---|---|
| 저장소 | `.github/workflows/ci.yml` `deploy` 잡 | SSH 로 서버 진입점 호출 + 외부 헬스체크 |
| 저장소 | `scripts/deploy.sh` | 서버에서 실행: 수집 대기·빌드·기동·헬스 (버전 관리됨) |
| 저장소 | `scripts/deploy-entry.sh` | SSH 진입점 원본 — 서버 `~/deploy-entry.sh` 로 복사해 쓴다 |
| 서버 | `~/deploy-entry.sh` | `git pull` 후 `deploy.sh` 호출. 저장소 밖에 둔 이유: pull 이 실행 중인 스크립트를 바꾸지 않게 |
| 서버 | `~/.ssh/authorized_keys` | 배포 키 한 줄 — `restrict,command="/home/ubuntu/deploy-entry.sh"` 로 **이 스크립트만 실행 가능** |
| GitHub | secrets `DEPLOY_SSH_KEY` `DEPLOY_HOST` `DEPLOY_USER` `DEPLOY_KNOWN_HOSTS` | 접속 정보. 호스트 키는 고정(pinning) |
| GitHub | variable `DEPLOY_HEALTH_URL` | 배포 후 외부 헬스체크 주소 — **도메인을 바꾸면 이것도 바꾼다** |

배포 키는 셸을 열 수도, 다른 명령을 실행할 수도 없어서 유출돼도 "배포 한 번 실행"까지만 가능하다.
포크 PR 에는 GitHub 이 비밀값을 넘기지 않고, `deploy` 잡은 `master` push/수동 실행에서만 돈다.

### 설정을 바꿀 때

- **`scripts/deploy-entry.sh` 를 고쳤다면** 서버에서 한 번 복사:
  `cp ~/ChungYak/scripts/deploy-entry.sh ~/deploy-entry.sh && chmod 700 ~/deploy-entry.sh`
  (`deploy.sh` 는 push 때마다 pull 되므로 따로 할 일 없음)
- **배포 키 교체**: `ssh-keygen -t ed25519 -N "" -C github-actions-chungyak-deploy -f deploy_key` →
  서버 `authorized_keys` 의 기존 `chungyak-deploy` 줄을 새 공개키로 교체(`restrict,command=...` 접두 유지) →
  `gh secret set DEPLOY_SSH_KEY < deploy_key` → 로컬 `deploy_key` 삭제.
- **서버를 옮기거나 다시 만들면**: 호스트 키가 바뀐다 → `ssh-keyscan -t ed25519 <IP>` 결과를
  `DEPLOY_KNOWN_HOSTS` 로 다시 등록하고(서버의 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`
  지문과 대조), `DEPLOY_HOST` 도 바꾼다.
- **배포를 잠시 끄려면**: 저장소 Settings → Actions 에서 CI 를 끄지 말고, `ci.yml` 의 `deploy` 잡에
  `if: false` 를 넣어 push 한다(빌드·테스트는 계속 돈다).

## 백업

매일 03:30 KST 에 `scripts/backup-db.sh` 가 `~/backups/chungyak-YYYYMMDD-HHMM.sql.gz` 를 만들고
14일 지난 것은 지운다(개인정보 처리방침 5절과 같은 기간 — 바꾸면 `/privacy` 도 고칠 것).
크론 등록은 `scripts/deploy.sh` 가 배포 때마다 확인한다(없으면 등록, 백업이 하나도 없으면 바로 한 번 실행).
로그는 `~/backups/backup.log`.

```bash
bash ~/ChungYak/scripts/backup-db.sh          # 수동 백업
gunzip -c ~/backups/chungyak-20261013-0330.sql.gz \
  | sudo docker compose exec -T db sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"'   # 복원
```

공고 데이터는 공공 API 에서 다시 수집할 수 있지만 **알림 구독(이메일·조건)은 DB 에만 있다.**
같은 서버 디스크의 백업은 디스크·인스턴스가 통째로 사라지면 같이 사라지므로, Lightsail 콘솔에서
인스턴스 **자동 스냅샷**도 켜 둔다(서버 밖 사본).

## 모니터링

- `GET /actuator/health/liveness` — 앱 프로세스 생존. Caddy·compose 헬스체크가 이걸 본다.
- `GET /actuator/health` — 전체 상태. **수집이 실패·저조·30시간 이상 오래되면 DOWN(503)** 이 되므로
  uptime 모니터(UptimeRobot 등)를 여기에 걸어두면 데이터가 낡을 때 알림이 온다.
  (상세 `outcome`/`lastSuccessAt` 는 admin 인증 시 노출)
- 임계값은 `application.yml` `publicdata.sync` (min-expected-records, stale-after-hours).
- 그 외 actuator 는 차단. 로그는 `docker compose logs` — `공고 수집 실패` / `공고 수집 이상` ERROR 를
  로그 수집기(Loki/CloudWatch 등)에서 알림 규칙으로 잡을 수 있다.
