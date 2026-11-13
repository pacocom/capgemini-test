# Final Verification Report

I performed this verification on 2026-11-13 (Europe/Madrid), starting with clean
project containers, images and database volume. I reviewed the code against the
README, built the executable JAR, ran the automated tests, exercised the real API
with curl, checked PostgreSQL and repeated the load profiles. I did not change
production code or make a commit or push.

I tested the production code at commit `7dc6f181384031295892c986c103c943c067b05d` on `main`.
The JAR SHA-256 was `4abfc325aa3ef2ebcf81e8320aad98f6b61441d8bcc81a6856ccd24930a53bb9`.

## Outcome

- I passed all 32 Maven tests: unit, integration and real-HTTP acceptance tests.
- My additional curl runner passed 90 HTTP requests and 73 SQL observations,
  including invalid input, external failures, timeout and concurrent creation.
- I passed 13 schema/data checks in a transaction that was rolled back.
- I retrieved both created roles after restarting the packaged JAR.
- All three load profiles passed their thresholds, including 1,000,000 reads.
- I found no blocking mismatch with the mandatory README behavior in these checks.
  The optional requirements and known limits are described below.

## README coverage

| Requirement | How I checked it | Result |
| --- | --- | --- |
| Incremental long room IDs | I checked `rooms.id`, its sequence and `users.room_id` are bigint. | PASS |
| Multiple users per room; one room per user | I created users in room 1 and checked the mandatory foreign key. | PASS |
| Name has at most 6 characters | I accepted 6, rejected 7, and checked the database length constraint. | PASS |
| Email contains an @ and a dot | I rejected each missing character separately. I used the literal README rule. | PASS |
| Only admin and superadmin | I created both and rejected USER, ADMIN, blank, null and missing roles. | PASS |
| Duplicate email raises an error | I checked normal duplicates and 20 simultaneous requests sharing only email. | PASS |
| External DNI validation | I verified the outgoing PATCH body, valid DNI, rejected DNI and external failures. | PASS |
| Save in room 1 and return ID | I checked HTTP 201, a positive generated numeric ID, persisted fields and roomId=1. | PASS |
| Notify admin by email | I verified the recipient and exact message usuario guardado, with no SMS. | PASS |
| Notify superadmin by SMS | I verified the recipient and exact message usuario guardado, with no email. | PASS |
| Validation error body | I checked HTTP 409, code=409 and the email/userName/dni messages. | PASS |
| Retrieve a user within room 1 | I checked success, room-2 exclusion and missing users. | PASS |
| Unit, integration and acceptance tests | I ran every Maven test and the separate curl/SQL matrix. | PASS |
| Spring and architecture | I reviewed constructor injection, service transactions, repositories, DTOs and Feign interfaces. | REVIEWED |
| Optional capacity | I repeated a cumulative million-read test; I did not test a million simultaneous clients. | LIMITED TO MEASURED PROFILE |
| Optional additional business contexts | I reviewed separation of concerns, but did not implement payments or orders. | NOT DEMONSTRATED |
| Delivery on main and public repository | I confirmed main; publication remains the repository owner's task. | PUBLICATION NOT VERIFIED |

## Automated test results

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| CodeApplicationTests | 1 | 0 | 0 | 0 |
| CreateUserExternalFailureTests | 2 | 0 | 0 | 0 |
| CreateUserTests | 16 | 0 | 0 | 0 |
| UserAcceptanceTests | 5 | 0 | 0 | 0 |
| UserControllerTests | 4 | 0 | 0 | 0 |
| UserExceptionHandlerTests | 4 | 0 | 0 | 0 |

I ran `mvn -B clean verify`, which compiled the application and tests, ran all
32 tests, and packaged `target/code-0.0.1-SNAPSHOT.jar`. Maven returned
`BUILD SUCCESS`. I did not calculate a line-coverage percentage.

## Additional curl, concurrency and failure checks

I used `tests/acceptance/curl_checks.py`, which invokes curl for every HTTP
request and records the exact commands, statuses, response bodies and timings.
I checked that rejected users did not increase the database row count.

For the concurrent case, I submitted 20 requests with the same email and distinct
DNI and phone values. I received one 201 and nineteen 409 responses, found one
persisted row, and verified one email notification. The remaining requests all
returned `error validation email`.

I injected 503 responses separately into DNI, email and SMS endpoints. The API
returned the existing 409 contract and did not retain a user. I then delayed the
DNI endpoint by 6 seconds; the configured 5-second read timeout produced a 409
after about 5.02 seconds without saving a user. I restored the original
MockServer expectations after each injected failure.

| Curl check or setup action | Actual HTTP status | Result |
| --- | ---: | --- |
| MockServer accepts valid DNI | 200 | PASS |
| MockServer rejects invalid DNI | 409 | PASS |
| MockServer email endpoint | 200 | PASS |
| MockServer SMS endpoint | 200 | PASS |
| Seed user in room 1 | 200 | PASS |
| Room 2 is outside the lookup scope | 404 | PASS |
| Missing user | 404 | PASS |
| Nonnumeric ID | 400 | PASS |
| Create admin | 201 | PASS |
| Read persisted admin | 200 | PASS |
| DNI checked | 200 | PASS |
| Correct admin notification | 200 | PASS |
| No wrong-channel notification | 200 | PASS |
| Create superadmin | 201 | PASS |
| Read persisted superadmin | 200 | PASS |
| DNI checked | 200 | PASS |
| Correct superadmin notification | 200 | PASS |
| No wrong-channel notification | 200 | PASS |
| Reject name='1234567' | 409 | PASS |
| Reject email='no-at.test' | 409 | PASS |
| Reject email='no@dot' | 409 | PASS |
| Reject rol='USER' | 409 | PASS |
| Reject rol='ADMIN' | 409 | PASS |
| Reject email='juan@example.com' | 409 | PASS |
| Reject dni='12345678Z' | 409 | PASS |
| Reject phone='600000001' | 409 | PASS |
| Reject dni='99999999w' | 409 | PASS |
| Rejected DNI sends no email | 200 | PASS |
| Reject email='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa... | 409 | PASS |
| Reject dni='1111111111111111' | 409 | PASS |
| Reject phone='1111111111111111' | 409 | PASS |
| Reject blank/null name | 409 | PASS |
| Reject blank/null name | 409 | PASS |
| Reject blank/null name | 409 | PASS |
| Reject missing name | 409 | PASS |
| Reject blank/null email | 409 | PASS |
| Reject blank/null email | 409 | PASS |
| Reject blank/null email | 409 | PASS |
| Reject missing email | 409 | PASS |
| Reject blank/null phone | 409 | PASS |
| Reject blank/null phone | 409 | PASS |
| Reject blank/null phone | 409 | PASS |
| Reject missing phone | 409 | PASS |
| Reject blank/null rol | 409 | PASS |
| Reject blank/null rol | 409 | PASS |
| Reject blank/null rol | 409 | PASS |
| Reject missing rol | 409 | PASS |
| Reject blank/null dni | 409 | PASS |
| Reject blank/null dni | 409 | PASS |
| Reject blank/null dni | 409 | PASS |
| Reject missing dni | 409 | PASS |
| Accept exact name/email/phone/DNI length limits | 201 | PASS |
| Wrong JSON shape is rejected | 400 | PASS |
| Concurrent duplicate 2 | 409 | PASS |
| Concurrent duplicate 9 | 409 | PASS |
| Concurrent duplicate 1 | 409 | PASS |
| Concurrent duplicate 5 | 409 | PASS |
| Concurrent duplicate 7 | 409 | PASS |
| Concurrent duplicate 0 | 201 | PASS |
| Concurrent duplicate 6 | 409 | PASS |
| Concurrent duplicate 4 | 409 | PASS |
| Concurrent duplicate 13 | 409 | PASS |
| Concurrent duplicate 3 | 409 | PASS |
| Concurrent duplicate 12 | 409 | PASS |
| Concurrent duplicate 14 | 409 | PASS |
| Concurrent duplicate 11 | 409 | PASS |
| Concurrent duplicate 8 | 409 | PASS |
| Concurrent duplicate 10 | 409 | PASS |
| Concurrent duplicate 15 | 409 | PASS |
| Concurrent duplicate 16 | 409 | PASS |
| Concurrent duplicate 18 | 409 | PASS |
| Concurrent duplicate 17 | 409 | PASS |
| Concurrent duplicate 19 | 409 | PASS |
| Concurrent creation sends one notification | 200 | PASS |
| Inject failure on /check-dni | 201 | PASS |
| Rollback when /check-dni fails | 409 | PASS |
| Reset MockServer | 200 | PASS |
| Restore original expectations | 201 | PASS |
| Inject failure on /email | 201 | PASS |
| Rollback when /email fails | 409 | PASS |
| Reset MockServer | 200 | PASS |
| Restore original expectations | 201 | PASS |
| Inject failure on /sms | 201 | PASS |
| Rollback when /sms fails | 409 | PASS |
| Reset MockServer | 200 | PASS |
| Restore original expectations | 201 | PASS |
| Inject slow DNI response | 201 | PASS |
| DNI read timeout | 409 | PASS |
| Reset MockServer | 200 | PASS |
| Restore original expectations | 201 | PASS |

I also tested the exact maximum lengths (name 6, email 150, DNI and phone 15),
null, blank and missing mandatory fields, and an invalid JSON shape. The latter
returned Spring's normal 400, as did a nonnumeric lookup ID; these malformed-input
statuses are outside the README's specified validation examples.

## Database and restart checks

I ran `tests/acceptance/schema_checks.sql` using psql with ON_ERROR_STOP.
The checks covered bigint room identifiers and sequence, three unique constraints,
valid current rows, duplicate email/DNI/phone, overlong name, unsupported role,
missing/nonexistent room and deletion of a room containing users. PostgreSQL
returned `BEGIN`, `DO`, `ROLLBACK`; no inserted test rows were retained. Sequence
values may advance during rejected or rolled-back inserts, which is normal.

I stopped and restarted the same JAR without deleting the database. Curl returned
users 9 and 10 with their original fields and roles (admin/superadmin), both in
room 1. Flyway validated V1 and V2 and reported that the schema was up to date.
I intentionally retained state within this run to check persistence; I did not
manually edit Flyway history.

## Repeated load results

I ran reads and creation separately with the normal application configuration.
The gradual profile used up to 10 virtual users for 30 seconds, with 100 ms
pauses. I then restarted the JAR with per-query SQL and verbose logging disabled
for the million-read profile (50 virtual users, no pauses).

| Workload | Operations | Operations/s | p95 ms | HTTP errors |
| --- | ---: | ---: | ---: | ---: |
| Gradual read | 1,236 | 41.12 | 4.40 | 0% |
| Gradual create | 1,178 | 39.24 | 11.42 | 0% |
| Read after creation | 1,178 | 39.24 | 1.61 | 0% |
| Million read | 1,000,000 | 15,735.71 | 5.96 | 0% |

I completed exactly 1,000,000 measured reads in 63.55 seconds.
Every response check passed. Including its setup call, k6 made 1,000,001 requests.
The database contained 1,184 users at that point; the million-read workload still
repeatedly requested the same user in room 1. The 1,178 load-created users were
persisted as 592 admins and 586 superadmins.

I used the same shared local machine as the earlier baseline (Java 17, Ryzen 7
PRO 8840HS, 16 logical CPUs, approximately 27 GiB RAM), local PostgreSQL and
MockServer. I sampled resources but did not measure continuous peaks or establish
a production capacity limit. All k6 thresholds passed.

## Commands and reproduction

I inspected the README, production/test source, migrations, Docker configuration
and load scripts with `cat`, `rg` and `sed`. I checked Git status, branch, history
and diffs without changing the index or commit history. My final run used:

```bash
docker compose -f docker/docker-compose.yml down -v --rmi all > /tmp/capgemini-final-reset.log 2>&1
docker compose -f docker/docker-compose.yml up -d > /tmp/capgemini-final-docker.log 2>&1
docker exec postgres_db pg_isready -U user -d users
mvn -B clean verify > /tmp/capgemini-final-verify.log 2>&1
mkdir -p target/final-verification target/load-results
java -jar target/code-0.0.1-SNAPSHOT.jar --server.port=18080 > /tmp/capgemini-final-app.log 2>&1
```

I wait for accepting connections before Maven and Started CodeApplication before
curl. I leave Java running in its terminal and run the following in another one:

```bash
curl --fail --retry 10 --retry-connrefused --retry-delay 1 --max-time 5 http://localhost:18080/users/1
python3 tests/acceptance/curl_checks.py > /tmp/capgemini-final-curl.log 2>&1
docker exec -i postgres_db psql -v ON_ERROR_STOP=1 -U user -d users < tests/acceptance/schema_checks.sql > /tmp/capgemini-final-schema.log 2>&1
```

I stop Java with Ctrl+C and start the same JAR again to check persistence. In my
run, the two returned IDs were 9 and 10; I use the IDs recorded in
`target/final-verification/persisted-ids.json` on a new run.

```bash
curl --fail http://localhost:18080/users/9
curl --fail http://localhost:18080/users/10
docker run --rm --network host --user "$(id -u):$(id -g)" -e MODE=read -e BASE_URL=http://localhost:18080 -v "$PWD/target/load-results:/results" -i grafana/k6:2.4.0 run - < tests/load/users.js > /tmp/capgemini-final-read.log 2>&1
docker run --rm --network host --user "$(id -u):$(id -g)" -e MODE=create -e RUN_ID=200001 -e BASE_URL=http://localhost:18080 -v "$PWD/target/load-results:/results" -i grafana/k6:2.4.0 run - < tests/load/users.js > /tmp/capgemini-final-create.log 2>&1
```

I stop the normal Java process, restart it with these logging overrides and wait
for readiness before executing the million profile in another terminal:

```bash
java -jar target/code-0.0.1-SNAPSHOT.jar --server.port=18080 --spring.jpa.show-sql=false --logging.level.root=WARN --logging.level.org.hibernate.SQL=OFF > /tmp/capgemini-final-million-app.log 2>&1
curl --fail --retry 10 --retry-connrefused --retry-delay 1 --max-time 5 http://localhost:18080/users/1
docker run --rm --network host --user "$(id -u):$(id -g)" -e PROFILE=million -e MODE=read -e BASE_URL=http://localhost:18080 -v "$PWD/target/load-results:/results" -i grafana/k6:2.4.0 run - < tests/load/users.js > /tmp/capgemini-final-million.log 2>&1
```

I additionally executed these inspections and assertions:

```bash
docker image inspect postgres:18 mockserver/mockserver:latest grafana/k6:2.4.0 --format '{{.RepoTags}} {{.RepoDigests}}'
docker exec postgres_db psql -U user -d users -c "SELECT count(*) AS total_users, count(DISTINCT room_id) AS rooms_with_users FROM users;"
docker exec postgres_db psql -U user -d users -c "SELECT version, description, script, success FROM flyway_schema_history ORDER BY installed_rank;"
docker exec postgres_db psql -U user -d users -c "\d users" -c "\d rooms"
docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} MEM={{.MemUsage}}' postgres_db mockserver
ps -p 520187 -o pid,pcpu,rss,etime
sha256sum target/code-0.0.1-SNAPSHOT.jar
git diff --check
git status --short
git branch --show-current
git log -10 --oneline
```

I also queried the load-created role totals and checked the XML test reports and
JSON summaries with Python assertions. The precise curl and SQL commands from
every runner action are in `target/final-verification/curl-evidence.json`.
The PID above was specific to my run. I used `kill -TERM` only for the four Java
processes I started (495837, 506394, 511938 and 520187), and confirmed they exited. I do
not reuse these historical PIDs when reproducing the checks.

## Evidence and logs

| Artifact | Contents |
| --- | --- |
| `target/surefire-reports/` | Maven XML and text results for all 32 tests |
| `target/final-verification/curl-evidence.json` | All 163 runner operations, commands and returned bodies |
| `target/final-verification/load-results.json` | All thresholds and metrics from the three load runs |
| `target/final-verification/restart-*.json` | Users retrieved after the JAR restart |
| `target/final-verification/jar.sha256` | Hash of the tested executable JAR |
| `/tmp/capgemini-final-verify.log` | Compilation, tests and packaging |
| `/tmp/capgemini-final-curl.log` | Successful runner summary |
| `/tmp/capgemini-final-schema.log` | Successful SQL assertions and rollback |
| `/tmp/capgemini-final-app.log`, `/tmp/capgemini-final-restart.log` | Normal application startup and request logs |
| `/tmp/capgemini-final-read.log`, `/tmp/capgemini-final-create.log`, `/tmp/capgemini-final-million.log` | k6 execution logs |
| `/tmp/capgemini-final-db.log`, `/tmp/capgemini-final-images.log` | Schema/data snapshot and image digests |
| `/tmp/capgemini-final-resources.log`, `/tmp/capgemini-final-java-resources.log` | Point-in-time resource samples |

I keep reusable verification scripts and this report in the repository. Generated
raw artifacts under target are removed by mvn clean; the scripts recreate them.
I can inspect logs with `tail -f /tmp/capgemini-final-million.log` or
`docker compose -f docker/docker-compose.yml logs -f postgres mockserver`.

Exact image digests used:

```text
[postgres:18] [postgres@sha256:b9f6b8ff03b62f3cfd9d9ba3fe56226a9991c79d3fb46fd46536263bf02be553]
[mockserver/mockserver:latest] [mockserver/mockserver@sha256:016f7d16bc88d2e1aa52d5a177f1aafee758cf70bea5e0624b0ab2c32f394df1]
[grafana/k6:2.4.0] [grafana/k6@sha256:d41bb79cd8de5a7ad619b53570e3712633e8bff0937010a5c6c57a4747e7a276]
```

## Initial check corrections and known limits

I corrected two assumptions in my new verification scripts after the first pass:
a request duplicating email, DNI and phone can legitimately report any of those
constraints under a race, so I isolated email in the concurrent test; PostgreSQL
also returned SQLSTATE 23001 for RESTRICT, so I accepted that rejection in the
deletion check. I reset the project again and reran the full verification.
I did not need to change production code.

I reviewed the application warnings: the Flyway/PostgreSQL compatibility warning
is still deferred, Hibernate reports a redundant explicit dialect and the default
open-in-view setting. Expected duplicate-key errors were logged during the race
and were returned as 409, not unexpected HTTP 500 responses.

I preserve the agreed uniqueness of DNI and phone in addition to email. Role
and phone error labels, 404 lookup responses and malformed-input 400 responses
are conventions already chosen where the README is silent. External service
failures still use the agreed 409 contract rather than a new 502 status.

I do not claim the application is perfect for every possible input or failure:

- Notification delivery and the final database commit are not atomic; durable
  delivery would require a persistent outbox/retry mechanism.
- The million profile measures cumulative reads of one cache-friendly user, not
  a million simultaneous clients, a million creations or production providers.
- Other rooms and other business contexts remain unimplemented extensions.
- I have not performed a dependency security audit, long endurance test or
  production deployment, and have not published the repository.

I stopped my test application after verification. I left the project containers
running with 1,184 users from this verification; the next full validation begins
with the cleanup command above. I did not modify TEST_RESOLUTION_ES.md, commit,
stage files or push.
