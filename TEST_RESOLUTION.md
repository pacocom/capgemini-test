# Test Resolution

## Step 5: Retrieve a user from room 1

**Goal:** I implemented the README's user lookup before adding user creation.

**Solution:** I mapped the existing `users` table with JPA, added a Spring Data repository and exposed
`GET /users/{id}`. I filter by both user ID and room 1 in the database. I return a response DTO with `id`, `name`,
`email`, `phone`, `rol`, `dni` and `roomId`, without exposing the JPA entity directly.

I return `200` for a user in room 1, `404` for a missing user or a user in another room, and `400` for a nonnumeric ID.
I chose these lookup statuses because the README does not specify them.

**Room scope:** I follow the README and currently restrict lookup to room 1. I will also create users in room 1 when I
implement creation. To support creating and retrieving users in other rooms, I will need to extend the API to accept a
room selection and validate it. In that future extension, I will use room 1 by default when no room is specified. I have
not implemented room selection yet.

**Quality and scalability:** I use the primary-key lookup instead of loading all users, reuse Spring Data JPA and keep
the HTTP response separate from persistence. I added integration tests through HTTP request handling and the real
PostgreSQL database for success, another room, a missing user and an invalid ID. I will add creation, validation, DNI
checks and notifications in later steps. I have not measured high-load capacity or claimed support for millions of
requests.

## Step 4: Fix initial data and align the database with the requirements

**Problem:** original users lacked mandatory DNI and phone values, their names exceeded 6 characters and their roles
were not `admin` or `superadmin`. Room IDs also used 32-bit integers instead of the required long values.

**Solution:**

- In `V1__init.sql`, I added only the missing DNI and phone values. This minimum fix is necessary because Flyway cannot
  run V2 if V1 fails first.
- In `V2__init.sql`, I changed sample users to `Juan` / `admin` and `María` / `superadmin`, limited names to 6
  characters and enforced valid roles.
- I changed room IDs, their sequence and the users' room reference to 64-bit integers.
- I required each user to belong to a room and prevented deleting rooms with users.
- I preserved the existing uniqueness constraints on email, DNI and phone.

The sample emails contain `@` and a dot. Multiple users can share a room. The sample user in room 2 is valid; the future
creation endpoint will use room 1. I will implement API validation, external DNI checks and notifications in later
steps. I have deferred the Flyway compatibility warning.

## Step 3: Fix the DNI client dependency

**Problem:** `mvn compile` failed because `DniClient` uses `OkHttpClient`, but the `feign-okhttp` dependency was missing
(`package feign.okhttp does not exist`).

**Solution:** I added `io.github.openfeign:feign-okhttp` to `pom.xml`. Spring Cloud manages its version. The existing
DNI client configuration remains unchanged. `mvn compile` result is `BUILD SUCCESS`.

## Step 2: Use modern Docker Compose

**Problem:**

```
Exception in thread Thread-7 (watch_events):
Traceback (most recent call last):
  File "/usr/lib/python3.12/threading.py", line 1073, in _bootstrap_inner
    self.run()
  File "/usr/lib/python3.12/threading.py", line 1010, in run
    self._target(*self._args, **self._kwargs)
  File "/usr/lib/python3/dist-packages/compose/cli/log_printer.py", line 202, in watch_events
    for event in event_stream:
  File "/usr/lib/python3/dist-packages/compose/project.py", line 626, in yield_loop
    yield build_container_event(event)
          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/usr/lib/python3/dist-packages/compose/project.py", line 594, in build_container_event
    container = Container.from_id(self.client, event['id'])
                                               ~~~~~^^^^^^
KeyError: 'id'\
```

`docker-compose` 1.29.2 failed with `KeyError: 'id'` while processing Docker events.

**Solution:** I switched to `docker compose` (with a space), which I already had installed, and updated the command in
README.md.

## Step 1: Fix PostgreSQL startup

**Problem:**

```
Error: in 18+, these Docker images are configured to store database data in a
       format which is compatible with "pg_ctlcluster" (specifically, using
       major-version-specific directory names).  This better reflects how
       PostgreSQL itself works, and how upgrades are to be performed.

       See also https://github.com/docker-library/postgres/pull/1259

       Counter to that, there appears to be PostgreSQL data in:
         /var/lib/postgresql/data (unused mount/volume)

       This is usually the result of upgrading the Docker image without
       upgrading the underlying database using "pg_upgrade" (which requires both
       versions).

       The suggested container configuration for 18+ is to place a single mount
       at /var/lib/postgresql which will then place PostgreSQL data in a
       subdirectory, allowing usage of "pg_upgrade --link" without mount point
       boundary issues.

       See https://github.com/docker-library/postgres/issues/37 for a (long)
       discussion around this process, and suggestions for how to do so.
```

The configuration used `postgres:latest` with the volume mounted at `/var/lib/postgresql/data`. The PostgreSQL 18 image
changed the directory structure, preventing the container from starting.

**Solution:** pinning the major version avoids unexpected major upgrades caused by `latest`. I pinned the image to
version 18 and changed the mount in `docker/docker-compose.yml`, following
https://github.com/docker-library/postgres/pull/1259:

```yaml
image: postgres:18
volumes:
  - postgres_data:/var/lib/postgresql
```

**Note:** there was no existing data, so no migration was needed.
