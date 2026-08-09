# Deploying Karaka

Two containers and a Postgres. Both `Dockerfile`s build and run; verified locally with
rotated secrets before any hosting was attempted.

## The one hard requirement

**Keycloak needs at least 1 GB of memory and a full shared CPU.** Everything else about
hosting is a preference; this is not.

Established by trying to run it on 512 MB / 0.1 CPU and failing from both directions:

| Configuration | Outcome |
|---|---|
| Keycloak's own defaults | kernel OOM kill, exit 137 |
| `-XX:MaxMetaspaceSize=112m` | `OutOfMemoryError: Metaspace`, exit 3 |

Keycloak wants roughly 200 MB of metaspace *before* any heap, so no split of 512 MB
satisfies both — and it never reached Liquibase creating ~100 tables, the heaviest step.
The same logs showed a TCP bind blocked for over two seconds, so the CPU was inadequate
independently of the memory.

That rules out the free tiers of Render, Fly.io and Koyeb (256–512 MB). It does **not**
rule out those platforms for the Spring service, which runs comfortably in 512 MB.

## Recommended: Azure Container Apps

Chosen because a container can be given 2 GB, and because both apps scale to zero. Now
run end to end against a live trial subscription; everything below reflects that run.

```bash
brew install azure-cli
az login
docker info >/dev/null      # Docker must be running — images are built here, not in Azure
./scripts/azure-deploy.sh
```

One script, one resource group: Postgres Flexible Server, a container registry, the
Container Apps environment, and both apps. Idempotent — re-running updates rather than
duplicating. Takes 12–15 minutes, mostly image builds and Postgres provisioning.

Tear it all down with `./scripts/azure-deploy.sh --destroy`.

Two details the script exists to get right:

- **Images are built locally and pushed**, cross-compiled for `linux/amd64` with
  `docker buildx`. The original design used `az acr build --file`, because there are two
  Dockerfiles in one repo and that flag is the only way to select one; a trial
  subscription cannot run ACR builds at all. See below.
- **It is deliberately two-pass.** `KC_HOSTNAME` and `APP_BASE_URL` cannot be known until
  Azure has assigned the FQDNs, so the apps are created with placeholders and updated
  afterwards. `APP_BASE_URL` is the subtle one: it becomes the client's redirect URI
  inside the realm, so a wrong value makes Keycloak refuse the redirect *after* the
  password is accepted — which reads like a broken login rather than a URL mismatch.

### Problems you will hit on a trial subscription

Four of these, in order. Each cost a full deploy cycle to find, and not one of them fails
where you would go looking.

**1. `az acr build` is unusable.** Azure does not permit ACR Tasks on trial subscriptions:

```
ERROR: (TasksOperationsNotAllowed) ACR Tasks requests for the registry ... are not permitted
```

No flag, region or SKU changes this — server-side builds are simply off. The script builds
locally and pushes to the registry instead, which is why Docker has to be running.

**2. An arm64 image pushes successfully and then fails to start.** Container Apps runs
`linux/amd64`; an Apple Silicon machine builds arm64 by default. Nothing objects at build
or push time. The container starts and dies:

```
exec /app/docker-entrypoint.sh: exec format error
```

Every build therefore passes `--platform linux/amd64` via `docker buildx`. Because the
failure is at *runtime*, the symptom is a container app that crash-loops with no build
error anywhere to explain it.

**3. Do not cross-build the Maven stage — build the jar natively.** Java bytecode is
architecture-independent, so emulating an entire `mvn package` under QEMU costs many
minutes and buys nothing. `service/Dockerfile.prebuilt` exists for this: it copies an
already-built jar into a runtime-only image, so only that thin layer is cross-built while
the jar is compiled at native speed. `service/Dockerfile` remains the self-contained
build for anywhere that builds natively.

Related: the jar must be built with `clean package`, not `package`. A stale `target/`
containing a macOS duplicate (`KarakaApplication 2.class` beside the real one) makes the
Spring Boot plugin refuse to repackage:

```
Unable to find a single main class from the following candidates
```

Two jars in `target/` would make `Dockerfile.prebuilt`'s `COPY` glob ambiguous in the same
way. A `.dockerignore` also excludes `target/` and `.git` from the build context, which was
otherwise ~28 MB uploaded on every build.

**4. Never suppress an `az` error to make a step idempotent.** The script used to create
the Keycloak database with:

```bash
az postgres flexible-server db create -g "$RG" -s "$PG" -d "$PG_DB" -o none 2>/dev/null || true
```

The flag is `--name`, not `-d`. Given an unknown flag, `az` prints its help text and exits
**0** — so `|| true` was never even reached, the wrong flag went unnoticed, and the database
was never created. That surfaced fifteen minutes later as a Keycloak crash-loop:

```
FATAL: database "keycloak" does not exist
```

It now checks `db list` for the database first and lets a genuine failure fail. Idempotency
comes from asking, not from discarding errors.

**Cost.** Both apps scale to zero, and the always-free grant (~180k vCPU-seconds,
~360k GiB-seconds per month) is roughly 50 hours at Keycloak's 2 GB, so an intermittently
used demo costs nothing. **Postgres B1ms bills continuously** (~$13/mo), covered by the
12-month free tier on a new account and by credit after that. To avoid that entirely,
point `KC_DB_*` at a managed free Postgres such as Neon.

## Alternative: one VM

Fewer moving parts than any container platform, and no cold starts.

```bash
curl -fsSL https://raw.githubusercontent.com/HarshSingh21/karaka-ui/main/scripts/vm-bootstrap.sh \
  | bash -s -- <PUBLIC_IP>
```

Brings up Keycloak, the service **and** Postgres on one host, so no managed database is
needed. Generates its own secrets on the VM.

Oracle Cloud Always Free (Ampere A1: up to 4 cores and 24 GB, free indefinitely) is the
target; any VM with at least 2 GB works. Two things to know:

- **Ampere A1 capacity is often unavailable.** Try each availability domain, retry over a
  few hours, or drop to 1 OCPU / 6 GB.
- **Oracle's Ubuntu images use a default-DROP iptables policy.** Opening the ports in the
  VCN security list is *not* enough; the VM's own firewall must be opened too. The script
  prints the commands. This catches nearly everyone once.

## Secrets

Nothing credential-bearing is in git. The realm is a template with placeholders:

```
keycloak/realm-template/karaka-realm.template.json   in git, placeholders only
keycloak/realm/karaka-realm.json                     generated, gitignored
```

`keycloak/docker-entrypoint.sh` substitutes at container start and **refuses to launch if
any placeholder survives** — an unsubstituted secret would otherwise import cleanly and
then reject every token exchange as `invalid_client`.

The template lives outside the import directory on purpose: `--import-realm` reads every
`*.json` there, so a template alongside the realm gets imported too and Keycloak dies
with `A redirect URI is not a valid URI`.

Six values, generated with `openssl rand -base64 32`:

| Secret | Notes |
|---|---|
| Postgres password | From the provider, or generated by the deploy script |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | First boot only — remove the variable afterwards |
| `KARAKA_WEB_SECRET` | Substituted into the realm |
| `KEYCLOAK_CLIENT_SECRET` | **Must equal `KARAKA_WEB_SECRET`.** Keycloak stores it, the service presents it; divergence fails as `invalid_client` |
| `KARAKA_API_SECRET` | The machine client |
| `DEMO_USER_PASSWORD` | Shared by the six demo accounts |

**Avoid `|` and `&`** in values — substitution uses `sed` with `|` as its delimiter and
`&` is its backreference. Base64 output contains neither.

For local development, `scripts/render-realm.sh` performs the same substitution from
`.env`.

## Local

```bash
cp .env.example .env          # fill in the secrets
./scripts/render-realm.sh     # writes the gitignored realm file
docker compose up -d
cd service && ./mvnw spring-boot:run
```

Keycloak must be reachable before the service starts: Spring resolves the OIDC discovery
document at boot. In a container the entrypoint waits for it; run locally, start Keycloak
first.

## What differs from local

| | Local | Deployed |
|---|---|---|
| Keycloak command | `start-dev` | `start --optimized` |
| Hostname | inferred | `KC_HOSTNAME` explicit |
| TLS | none | terminated by the platform; `KC_PROXY_HEADERS=xforwarded` |
| `sslRequired` | `none` | `external` |
| Session cookie | plain | `KARAKA_COOKIE_SECURE=true` |
| Realm secrets | `.env` + render script | platform secrets + entrypoint |

`KC_HOSTNAME` and `KC_PROXY_HEADERS` are not optional. Without them Keycloak builds
redirect and issuer URLs from the internal request and sends the browser to the wrong
host.

Build-time options must not appear in the runtime environment. `KC_DB` and
`KC_HEALTH_ENABLED` are baked in by `kc.sh build`; with `--optimized`, supplying either
again at runtime makes Keycloak exit 2 with *"build time options have values that differ
from what is persisted"*.

## Still outstanding

- **Six demo accounts share one password.** Fine for a demo, not for anything else.
- **Old secrets remain in git history** (`dab357d`, `aacc091`). Rotated, so inert, but
  only a history rewrite removes them.
- **The register and audit trail reset on restart** — the ORBIT store is in memory.
- **`scripts/azure-deploy.sh` now requires a local Docker daemon**, because a trial
  subscription cannot build in ACR. On a subscription that permits ACR Tasks, the
  server-side path would be preferable and the script does not currently offer it.
