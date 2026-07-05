# Kubernetes Manifests

Git-managed Kubernetes manifests for deploying SReader with [Kustomize](https://kustomize.io/).

## Structure

```text
k8s/
  base/                 # Shared namespace, Deployment, ConfigMap
  overlays/
    local/              # Docker Desktop Kubernetes (app + in-cluster PostgreSQL)
    home/               # k3s home-server (app + host PostgreSQL via EndpointSlice)
```

- **base**: Namespace `sreader`, the `sreader` Deployment, and non-secret runtime settings in ConfigMap `sreader-config`.
- **local**: Adds PostgreSQL StatefulSet, PVCs, and a disposable local-only database Secret.
- **home**: Points `postgres` Service at the Ubuntu host PostgreSQL via EndpointSlice, uses `hostPath` text-export storage, and reads database credentials from a local Secret env file.

## Safe to commit

- All manifests under `k8s/base/` and `k8s/overlays/`
- `k8s/overlays/home/sreader-db.secret.env.example`
- Local overlay database credentials (`sreader` / `sreader`) - disposable development only
- Placeholder host IP `192.168.1.10` in `postgres-endpointslice.yaml`

## Do not commit

- `k8s/overlays/home/sreader-db.secret.env` (real home-server database password)
- Any `*.secret.env` or `*.local.env` under overlays (listed in `.gitignore`)

## Prerequisites

- `kubectl` with a working cluster context
- For local overlay: Docker Desktop Kubernetes enabled
- For home overlay: k3s on a trusted single-node home server

The application image is the public GHCR package `ghcr.io/sasasin/sreader`. No `imagePullSecrets` are required.

## Local deployment (Docker Desktop Kubernetes)

Deploys the app and PostgreSQL 18.4 inside the `sreader` namespace.

```bash
kubectl apply -k k8s/overlays/local
kubectl -n sreader get pods
kubectl -n sreader logs deployment/sreader --tail=120
```

The app connects to `jdbc:postgresql://postgres:5432/sreader` using local-only credentials `sreader` / `sreader`.

### Cleanup

```bash
kubectl delete -k k8s/overlays/local
```

This removes namespace resources managed by the overlay. PVCs created by the PostgreSQL StatefulSet may remain unless deleted separately.

## Home-server deployment (k3s)

Deploys only the app. PostgreSQL runs on the Ubuntu host outside Kubernetes.

### 1. Create the database Secret

```bash
cp k8s/overlays/home/sreader-db.secret.env.example \
   k8s/overlays/home/sreader-db.secret.env

vi k8s/overlays/home/sreader-db.secret.env
```

Set `SREADER_DATASOURCE_USERNAME` and `SREADER_DATASOURCE_PASSWORD` to match your host PostgreSQL user. Do not commit `sreader-db.secret.env`.

### 2. Point at host PostgreSQL

Edit `k8s/overlays/home/postgres-endpointslice.yaml` and replace the placeholder `192.168.1.10` with your home-server LAN IP (the address where PostgreSQL listens on port 5432).

The `postgres` Service has no selector; traffic is routed through the manually managed EndpointSlice.

### 3. Pin the container image (recommended)

Production deployments should pin a full commit SHA tag instead of `master`:

```bash
cd k8s/overlays/home

kustomize edit set image \
  ghcr.io/sasasin/sreader=ghcr.io/sasasin/sreader:sha-<full-commit-sha>
```

If `kustomize` is not installed, edit `kustomization.yaml` manually:

```yaml
images:
  - name: ghcr.io/sasasin/sreader
    newTag: sha-<full-commit-sha>
```

Alternatively, use `kubectl kustomize` to preview rendered manifests without applying.

### 4. Apply

```bash
kubectl apply -k k8s/overlays/home
kubectl -n sreader rollout status deployment/sreader --timeout=300s
kubectl -n sreader logs deployment/sreader --tail=120
```

## Runtime configuration

Non-secret settings are injected via ConfigMap `sreader-config`. Kustomize adds a content hash suffix to generated ConfigMap and Secret names (for example `sreader-config-ft2d7g5k2f`) and updates `Deployment` references automatically. ConfigMap or Secret changes therefore trigger a rollout via the changed Pod spec.

Keys match `app/src/main/resources/application.yml`:

| Variable | Default |
|----------|---------|
| `SREADER_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/sreader` |
| `SREADER_SCHEDULER_ENABLED` | `true` |
| `SREADER_SCHEDULER_CRON` | `0 */15 * * * *` |
| `SREADER_JOB_RUN_ONCE` | `false` |
| `SREADER_HTTP_USER_AGENT` | `SReader/0.1` |
| `SREADER_HTTP_CONNECT_TIMEOUT` | `5s` |
| `SREADER_HTTP_READ_TIMEOUT` | `20s` |
| `SREADER_HTTP_RETRY_COUNT` | `1` |
| `SREADER_PLAYWRIGHT_ENABLED` | `false` |
| `SREADER_PLAYWRIGHT_HEADLESS` | `true` |
| `SREADER_PLAYWRIGHT_VIEWPORT_WIDTH` | `1280` |
| `SREADER_PLAYWRIGHT_VIEWPORT_HEIGHT` | `1600` |
| `SREADER_PLAYWRIGHT_NAVIGATION_TIMEOUT` | `60s` |
| `SREADER_PLAYWRIGHT_NETWORK_IDLE_TIMEOUT` | `5s` |
| `SREADER_PLAYWRIGHT_INFY_EXTENSION_DIR` | (empty) |
| `SREADER_PLAYWRIGHT_INFY_USER_DATA_DIR` | (empty) |
| `SREADER_PLAYWRIGHT_INFY_MAX_SCROLLS` | `20` |
| `SREADER_PLAYWRIGHT_INFY_STABLE_ROUNDS` | `3` |
| `SREADER_PLAYWRIGHT_INFY_SCROLL_WAIT` | `2700ms` |
| `SREADER_TEXT_EXPORT_ENABLED` | `false` |
| `SREADER_TEXT_EXPORT_OUTPUT_DIR` | `/var/lib/sreader/content-text` |
| `SREADER_TEXT_EXPORT_BATCH_SIZE` | `100` |
| `SREADER_SEED_FEED_URLS` | (empty) |

Database username and password are injected via Secret `sreader-db-secret` (rendered as `sreader-db-secret-<hash>`) with `SREADER_DATASOURCE_USERNAME` and `SREADER_DATASOURCE_PASSWORD`.

To change non-secret values, edit literals in `k8s/base/kustomization.yaml` or add a Kustomize patch in an overlay.

## Text export storage

The app mounts `/var/lib/sreader/content-text` in all overlays.

- **local**: PVC `sreader-content-text` (5Gi, cluster default StorageClass)
- **home**: PV/PVC pair backed by `hostPath` at `/srv/sreader/content-text` on the k3s node, StorageClass `sreader-hostpath`, reclaim policy `Retain`

Ensure `/srv/sreader/content-text` exists on the home server (the PV uses `DirectoryOrCreate`). `hostPath` is intended for a trusted single-node k3s setup; do not use it casually in multi-tenant or untrusted clusters.

Set `SREADER_TEXT_EXPORT_ENABLED=true` in the ConfigMap when you want text export enabled.

## Single replica

The Deployment uses `replicas: 1` and `strategy: Recreate`. SReader is a scheduler-style workload; do not scale above one replica.

## Database migrations

The Spring Boot app runs Flyway migrations on startup. Back up the database before deploying image updates that include new migrations.

## Preview manifests

```bash
kubectl kustomize k8s/overlays/local

cp k8s/overlays/home/sreader-db.secret.env.example \
   k8s/overlays/home/sreader-db.secret.env

kubectl kustomize k8s/overlays/home
```

The home overlay requires `sreader-db.secret.env` to exist before `kubectl kustomize` succeeds.
