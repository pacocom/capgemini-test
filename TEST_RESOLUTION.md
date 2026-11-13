# Test Resolution

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
