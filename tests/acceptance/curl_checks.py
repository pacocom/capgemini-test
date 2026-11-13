"""Verify the running application with curl and PostgreSQL; no Python packages required."""

import concurrent.futures
import json
import os
from pathlib import Path
import shlex
import subprocess
import threading
import time


API = os.environ.get("API_URL", "http://localhost:18080")
MOCK = os.environ.get("MOCK_URL", "http://localhost:1080")
OUTPUT = Path("target/final-verification")
OUTPUT.mkdir(parents=True, exist_ok=True)
evidence = []
lock = threading.Lock()


def curl(label, method, path, body=None, expected=200, mock=False):
    args = ["curl", "--silent", "--show-error", "--max-time", "15", "-X", method,
            (MOCK if mock else API) + path, "-w", "\n%{http_code}"]
    if body is not None:
        args += ["-H", "Content-Type: application/json", "--data", json.dumps(body)]
    started = time.monotonic()
    result = subprocess.run(args, capture_output=True, text=True, check=True, timeout=20)
    raw, status = result.stdout.rsplit("\n", 1)
    parsed = json.loads(raw) if raw.strip() else None
    with lock:
        evidence.append({"label": label, "command": shlex.join(args),
                         "status": int(status), "expected_status": expected,
                         "duration_seconds": time.monotonic() - started, "body": parsed})
    assert int(status) == expected, (label, status, raw)
    return parsed


def sql(statement):
    args = ["docker", "exec", "postgres_db", "psql", "-v", "ON_ERROR_STOP=1",
            "-U", "user", "-d", "users", "-At", "-c", statement]
    result = subprocess.run(args, capture_output=True, text=True, check=True, timeout=20)
    with lock:
        evidence.append({"label": "SQL verification", "command": shlex.join(args),
                         "body": result.stdout.strip()})
    return result.stdout.strip()


def payload(number, role="admin"):
    return {"name": "pablo", "email": f"curl-{number}@verify.test", "phone": f"612{number:06d}",
            "dni": f"VERIFY{number:06d}", "rol": role}


def recorded(label, path, body, method="POST"):
    return curl(label, "PUT", "/mockserver/retrieve?type=REQUESTS", {
        "method": method, "path": path,
        "body": {"type": "JSON", "matchType": "ONLY_MATCHING_FIELDS", "json": json.dumps(body)},
    }, mock=True)


def conflict(label, request, field):
    before = sql("SELECT count(*) FROM users")
    result = curl(label, "POST", "/users", request, 409)
    assert result == {"code": 409, "message": f"error validation {field}"}, (label, result)
    assert sql("SELECT count(*) FROM users") == before, label


def reset_mock():
    curl("Reset MockServer", "PUT", "/mockserver/reset", mock=True)
    expectations = json.loads(Path("docker/mockserver-config/init.json").read_text())
    curl("Restore original expectations", "PUT", "/mockserver/expectation", expectations, 201, True)


def main():
    curl("MockServer accepts valid DNI", "PATCH", "/check-dni", {"dni": "DIRECT123"}, mock=True)
    curl("MockServer rejects invalid DNI", "PATCH", "/check-dni", {"dni": "99999999w"}, 409, True)
    curl("MockServer email endpoint", "POST", "/email",
         {"email": "direct@verify.test", "message": "usuario guardado"}, mock=True)
    curl("MockServer SMS endpoint", "POST", "/sms",
         {"phone": "619000000", "message": "usuario guardado"}, mock=True)
    initial = curl("Seed user in room 1", "GET", "/users/1")
    assert initial["name"] == "Juan" and initial["roomId"] == 1
    curl("Room 2 is outside the lookup scope", "GET", "/users/2", expected=404)
    curl("Missing user", "GET", "/users/999999", expected=404)
    curl("Nonnumeric ID", "GET", "/users/abc", expected=400)

    ids = []
    for number, role in [(1, "admin"), (2, "superadmin")]:
        request = payload(number, role)
        response = curl(f"Create {role}", "POST", "/users", request, 201)
        assert isinstance(response["id"], int) and response["id"] > 0
        ids.append(response["id"])
        saved = curl(f"Read persisted {role}", "GET", f"/users/{response['id']}")
        assert all(saved[key] == value for key, value in request.items()) and saved["roomId"] == 1
        assert len(recorded("DNI checked", "/check-dni", {"dni": request["dni"]}, "PATCH")) == 1
        path, field = ("/email", "email") if role == "admin" else ("/sms", "phone")
        assert len(recorded(f"Correct {role} notification", path,
                            {field: request[field], "message": "usuario guardado"})) == 1
        other_path, other_field = ("/sms", "phone") if role == "admin" else ("/email", "email")
        assert not recorded("No wrong-channel notification", other_path, {other_field: request[other_field]})

    cases = [("name", "1234567", "userName"), ("email", "no-at.test", "email"),
             ("email", "no@dot", "email"), ("rol", "USER", "rol"), ("rol", "ADMIN", "rol"),
             ("email", "juan@example.com", "email"), ("dni", "12345678Z", "dni"),
             ("phone", "600000001", "phone"), ("dni", "99999999w", "dni"),
             ("email", "a" * 151 + "@x.test", "email"),
             ("dni", "1" * 16, "dni"), ("phone", "1" * 16, "phone")]
    for number, (field, value, error) in enumerate(cases, 10):
        request = payload(number)
        request[field] = value
        conflict(f"Reject {field}={value!r}", request, error)
        if value == "99999999w":
            assert not recorded("Rejected DNI sends no email", "/email", {"email": request["email"]})
    for number, field in enumerate(("name", "email", "phone", "rol", "dni"), 30):
        for value in (None, "", "   "):
            request = payload(number)
            request[field] = value
            conflict(f"Reject blank/null {field}", request, "userName" if field == "name" else field)
        request = payload(number)
        del request[field]
        conflict(f"Reject missing {field}", request, "userName" if field == "name" else field)
    request = payload(40)
    request["name"] = "carlos"
    request["email"] = "a" * 143 + "@x.test"
    request["phone"] = "7" * 15
    request["dni"] = "8" * 15
    curl("Accept exact name/email/phone/DNI length limits", "POST", "/users", request, 201)
    curl("Wrong JSON shape is rejected", "POST", "/users", [], 400)

    # Concurrent requests must exercise the database uniqueness constraint as well as prechecks.
    request = payload(50)
    def concurrent_create(index):
        candidate = dict(request, dni=f"RACE{index:06d}", phone=f"615{index:06d}")
        args = ["curl", "--silent", "--show-error", "--max-time", "15", "-X", "POST", API + "/users",
                "-H", "Content-Type: application/json", "--data", json.dumps(candidate), "-w", "\n%{http_code}"]
        result = subprocess.run(args, capture_output=True, text=True, check=True, timeout=20)
        raw, status = result.stdout.rsplit("\n", 1)
        with lock:
            evidence.append({"label": f"Concurrent duplicate {index}", "command": shlex.join(args),
                             "status": int(status), "body": json.loads(raw)})
        return int(status), json.loads(raw)
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
        responses = list(pool.map(concurrent_create, range(20)))
    assert [status for status, _ in responses].count(201) == 1, responses
    assert [status for status, _ in responses].count(409) == 19, responses
    assert all(body == {"code": 409, "message": "error validation email"}
               for status, body in responses if status == 409), responses
    assert sql("SELECT count(*) FROM users WHERE email = 'curl-50@verify.test'") == "1"
    assert len(recorded("Concurrent creation sends one notification", "/email",
                        {"email": request["email"], "message": "usuario guardado"})) == 1

    for number, path, matcher, error in [
        (60, "/check-dni", {"dni": payload(60)["dni"]}, "dni"),
        (61, "/email", {"email": payload(61)["email"]}, "email"),
        (62, "/sms", {"phone": payload(62)["phone"]}, "phone"),
    ]:
        expectation = {"priority": 100, "httpRequest": {"method": "PATCH" if path == "/check-dni" else "POST",
            "path": path, "body": {"type": "JSON", "matchType": "ONLY_MATCHING_FIELDS",
                                    "json": json.dumps(matcher)}}, "httpResponse": {"statusCode": 503}}
        try:
            curl(f"Inject failure on {path}", "PUT", "/mockserver/expectation", expectation, 201, True)
            conflict(f"Rollback when {path} fails", payload(number, "superadmin" if path == "/sms" else "admin"), error)
        finally:
            reset_mock()

    request = payload(70)
    delay = {"priority": 100, "httpRequest": {"method": "PATCH", "path": "/check-dni",
        "body": {"type": "JSON", "json": json.dumps({"dni": request["dni"]})}},
        "httpResponse": {"statusCode": 200, "delay": {"timeUnit": "MILLISECONDS", "value": 6000}}}
    try:
        curl("Inject slow DNI response", "PUT", "/mockserver/expectation", delay, 201, True)
        conflict("DNI read timeout", request, "dni")
    finally:
        reset_mock()

    Path(OUTPUT / "persisted-ids.json").write_text(json.dumps(ids))
    print(f"PASS: {len(evidence)} curl/SQL operations, including 20 concurrent requests")


if __name__ == "__main__":
    try:
        main()
    finally:
        (OUTPUT / "curl-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
