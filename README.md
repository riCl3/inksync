# inksyc

**Offline-first personal notes for Android, backed by a FastAPI service.** The local Room database is the
single source of truth for every screen; all writes are committed locally first and synchronized to
the backend by a persistent WorkManager job. No read ever blocks on the network.

> Status: **Complete** — offline-first notes, JWT auth, bidirectional sync with optimistic
> concurrency, FTS4 search, folders, conflict resolution, encrypted credential storage,
> Room migrations v1→v5, failure-injection tests, and a release-ready (R8) build.

---

## Table of contents

1. [Build & toolchain](#build--toolchain)
2. [Module & package layout](#module--package-layout)
3. [Architecture & invariants](#architecture--invariants)
4. [Dependency injection graph](#dependency-injection-graph)
5. [Data model & schema](#data-model--schema)
6. [Room migrations](#room-migrations)
7. [Synchronization engine](#synchronization-engine)
8. [Authentication & token storage](#authentication--token-storage)
9. [Full-text search](#full-text-search)
10. [Conflict resolution](#conflict-resolution)
11. [Adaptive UI & theming](#adaptive-ui--theming)
12. [Build variants & configuration](#build-variants--configuration)
13. [Release, R8 & ProGuard](#release-r8--proguard)
14. [Testing strategy](#testing-strategy)
15. [Observability & logging](#observability--logging)
16. [Backend service](#backend-service)
17. [Contributing](#contributing)

---

## Build & toolchain

| Tool | Version | Notes |
|---|---|---|
| Android Gradle Plugin | 8.x | `compileSdk 35`, `minSdk 26`, `targetSdk 35` |
| Kotlin | 2.x | Compose compiler plugin enabled |
| JDK | 17 | Required by AGP |
| Gradle | 8.x (wrapper) | Configuration cache enabled |
| Room | 2.6.x | KSP (not kapt) for codegen |
| Hilt | 2.51.x | `@HiltViewModel`, `@AndroidEntryPoint` |
| WorkManager | 2.9.x | On-demand init via `Configuration.Provider` |
| Compose BOM | 2024.x | Material 3 |
| Retrofit | 2.11.x | OkHttp 4.12.x |
| Coroutines / Flow | 1.8.x | `StateFlow`, `Flow` from DAOs |
| Python (backend) | 3.11+ | FastAPI 0.115, SQLAlchemy 2.0, Pydantic v2 |

Commands:

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:installDebug             # install
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:connectedDebugAndroidTest # instrumented (device/emulator)
./gradlew :app:assembleRelease          # release APK (R8)
```

## Module & package layout

Single Gradle module (`:app`) plus a separate Python backend (`server/`). Package root
`com.inc.app`.

```
app/src/main/java/com/inc/app/
├── IncSyncApp.kt              # Application + WorkManager Configuration.Provider
├── MainActivity.kt            # single Activity, edge-to-edge, theme
├── domain/                    # framework-free models + repository interface
│   ├── Note.kt, Folder.kt     # data classes, SyncState enum
│   └── NotesRepository.kt     # interface; the only data contract for UI
├── data/
│   ├── local/
│   │   ├── NoteEntity.kt, FolderEntity.kt, PendingOpEntity.kt, NoteFtsEntity.kt
│   │   ├── *Dao.kt            # Room DAOs (suspend + Flow-returning)
│   │   ├── IncSyncDatabase.kt # @Database(entities=..., version=5, exportSchema=true)
│   │   └── Converters.kt      # SyncState ↔ String
│   ├── remote/
│   │   ├── IncSyncApi.kt      # Retrofit interface
│   │   ├── dto/               # serializable request/response models
│   │   ├── TokenStore.kt      # EncryptedSharedPreferences wrapper
│   │   └── AuthInterceptor.kt # Bearer token injection
│   ├── repository/
│   │   ├── RoomNotesRepository.kt  # implements NotesRepository
│   │   └── AuthRepository.kt
│   └── sync/
│       ├── SyncWorker.kt      # CoroutineWorker, drains pending_ops
│       └── SyncScheduler.kt   # unique work enqueue helper
├── di/
│   ├── RoomModule.kt          # DB, DAOs, repository binding
│   └── NetworkModule.kt       # OkHttp, Retrofit, API
└── ui/
    ├── IncSyncRoot.kt         # adaptive scaffold (bottom bar / nav rail)
    ├── navigation/            # routes + NavHost
    ├── notes/, search/, folders/, settings/, sync/, auth/
    ├── components/            # SyncStatusChip, EmptyState
    └── theme/                 # colors, type, shapes, Theme.kt
```

`exportSchema = true` writes JSON schemas to `app/schemas/` (checked in) for migration testing.

## Architecture & invariants

**Invariants the codebase enforces (do not break):**

1. **Local is the source of truth.** UI collects `Flow` from DAOs via ViewModels; no screen
   awaits a network call to render.
2. **Atomic writes.** Every repository mutation runs inside `db.withTransaction { }` and
   persists both the entity change and a `pending_ops` row. A crash between them is impossible
   because they share one transaction.
3. **Sync is persistent work.** Enqueued via WorkManager with a `CONNECTED` constraint; survives
   process death and reboot. Never fire-and-forget coroutines from the UI layer.
4. **Classification of failures.** IOException / 5xx → `Result.retry()` (WorkManager backoff).
   4xx → mark op `FAILED`/`CONFLICT`, remove from queue, surface in UI. 404 on DELETE → success.
5. **Idempotent CREATE.** Client-generated UUIDs; server treats a repeat `POST` with an existing
   id as a no-op success. This makes retries and multi-device safe.
6. **Domain isolation.** `domain/` has zero Android/framework imports. Mapping happens at the
   repository boundary (`NoteEntity.toDomain()`, `Note.toEntity()`).
7. **No blocking reads.** `NoteDao.getAll()` returns `Flow<List<NoteEntity>>`; ViewModels use
   `stateIn(WhileSubscribed(5000))`.

**Threading.** DAO suspend functions run on Room's executor (IO dispatcher internally). ViewModels
launch on `viewModelScope` (Main). `SyncWorker` is a `CoroutineWorker` (IO dispatcher). No
manual `withContext(Dispatchers.IO)` is needed for DAO calls; repository methods are `suspend`.

## Dependency injection graph

```
@Singleton
├── IncSyncDatabase (Room.databaseBuilder, "incsync.db")
│   ├── NoteDao, FolderDao, PendingOpDao, NoteFtsDao
├── TokenStore (EncryptedSharedPreferences)
├── OkHttpClient (AuthInterceptor + HttpLoggingInterceptor in debug)
├── Retrofit (baseUrl from BuildConfig.API_BASE_URL)
└── IncSyncApi (retrofit.create)

@HiltViewModel
├── NotesViewModel (NotesRepository, SyncScheduler)
├── SearchViewModel (NoteFtsDao)
├── FoldersViewModel (NotesRepository)
├── AuthViewModel (AuthRepository)
└── SettingsViewModel (TokenStore)

Worker (HiltWorker, @AssistedInject)
└── SyncWorker (IncSyncApi, IncSyncDatabase, TokenStore)
```

`SyncWorker` is created by Hilt's `HiltWorkerFactory` (registered in `IncSyncApp.getWorkManagerConfiguration()`).

## Data model & schema

### `notes`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | Client UUID (offline creation requires client-side id). |
| `userId` | TEXT | Owner; `"local-user"` until authenticated. |
| `title`, `content` | TEXT | |
| `folderId` | TEXT NULL | FK-ish to `folders.id` (not enforced; sync ordering). |
| `isPinned` | INTEGER | 0/1 |
| `createdAt`, `updatedAt` | INTEGER | Epoch millis. |
| `version` | INTEGER | Bumped on every accepted local/server edit. |
| `syncState` | TEXT | `PENDING/SYNCED/SYNCING/FAILED/CONFLICT`. |
| `deletedAt` | INTEGER NULL | Soft-delete until sync confirms. |
| `syncedVersion` | INTEGER | Last server-acknowledged version (0 = never). |

### `folders`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | Client UUID. |
| `userId` | TEXT | |
| `name` | TEXT | |
| `createdAt`, `updatedAt` | INTEGER | |
| `version` | INTEGER | |
| `syncState` | TEXT | |
| `syncedVersion` | INTEGER | |

### `pending_ops`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | Queue ordering. |
| `opType` | TEXT | `CREATE/UPDATE/DELETE/FOLDER_CREATE/FOLDER_RENAME/FOLDER_DELETE`. |
| `noteId` | TEXT | Target resource id. |
| `payload` | TEXT NULL | Reserved for JSON snapshots. |
| `createdAt` | INTEGER | Enqueue time. |
| `attempts` | INTEGER | Retry counter. |
| `lastError` | TEXT NULL | Last failure message. |

### `notes_fts` (FTS4, external content)

Virtual table backed by `notes`; Room keeps it in sync via triggers. `MATCH` over `title`/`content`.

### Indexes

- `notes(userId)`, `notes(folderId)`, `notes(updatedAt)`, `notes(syncState)`.
- `folders(userId)`.
- `pending_ops(createdAt)`.

## Room migrations

`IncSyncDatabase` is at **version 5**. All migrations are explicit (no
`fallbackToDestructiveMigration`). Schema JSON is exported to `app/schemas/` and replayed in
`MigrationTest`.

| Version | Change |
|---|---|
| 1 → 2 | Added `folders` table + `notes.folderId`. |
| 2 → 3 | Added `pending_ops` queue. |
| 3 → 4 | Added `syncState`, `version`, `syncedVersion` columns; backfilled defaults. |
| 4 → 5 | Added `notes_fts` FTS4 table + triggers; rebuilt from existing rows. |

AutoMigration is **not** used (manual `Migration` objects give control over data backfill).
`MigrationTest` (instrumented) loads the real v4 schema from assets, runs `MIGRATION_4_5`,
and asserts row counts + FTS queryability.

## Synchronization engine

### State machine (per note)

```
PENDING  --(2xx)--> SYNCED
PENDING  --(409)--> CONFLICT
PENDING  --(5xx/IO)--> PENDING (retry with backoff)
FAILED   --(manual retry)--> PENDING
CONFLICT --(keep mine / use server)--> PENDING (re-enqueued)
```

App-wide `SyncStatus` aggregation priority: `CONFLICT` > `FAILED` > `PENDING` > `SYNCING` >
`SYNCED` > `OFFLINE`.

### Algorithm (`SyncWorker.doWork()`)

1. If unauthenticated → `Result.success()` (ops stay queued).
2. Load all `pending_ops` ordered by `createdAt`.
3. For each op:
   - `CREATE` → `POST /notes`. 2xx → mark note `SYNCED`, store server version, delete op.
     409 → mark `FAILED`. IOException/5xx → `return Result.retry()`.
   - `UPDATE` → `PUT /notes/{id}` with `base_version = syncedVersion`. 2xx → `SYNCED`.
     409 → mark `CONFLICT`, store server note, delete op (resolution re-enqueues).
   - `DELETE` → `DELETE /notes/{id}`. 2xx or 404 → hard-delete row, delete op.
   - Folder ops follow the same pattern against `/folders`.
4. Return `Result.success()` when the queue drains.

### Enqueue (`SyncScheduler.schedule()`)

```kotlin
workManager.enqueueUniqueWork(
    "incsync-sync",
    ExistingWorkPolicy.KEEP,
    OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, SECONDS)
        .build()
)
```

`KEEP` policy avoids duplicate work when writes happen in bursts.

### Idempotency & concurrency

- Client-generated UUIDs make `CREATE` replay-safe.
- `base_version` on `UPDATE` gives optimistic concurrency; server returns 409 with the current
  server row, enabling three-way conflict resolution.
- `syncedVersion` on the client records the last accepted server version so retries don't
  clobber newer server state.

## Authentication & token storage

- `AuthRepository` calls `POST /auth/register` and `POST /auth/login`, stores the JWT via
  `TokenStore`.
- `TokenStore` wraps `EncryptedSharedPreferences` (AES-256-GCM data, AES-256-SIV keys) with a
  master key from `MasterKey.Builder` (Android Keystore, `AES256_GCM`).
- `AuthInterceptor` (OkHttp) injects `Authorization: Bearer <token>` when present.
- Logout clears the encrypted store; `SyncWorker` no-ops when unauthenticated.
- Debug builds log HTTP traffic via `HttpLoggingInterceptor` (body level); release builds do not.

## Full-text search

- `NoteFtsDao.search(query)` runs `SELECT … FROM notes JOIN notes_fts ON notes.rowid = notes_fts.rowid WHERE notes_fts MATCH :query`.
- `SearchViewModel` debounces input (300 ms) and exposes `StateFlow<List<Note>>`.
- FTS is rebuilt in the v4→v5 migration from existing rows.

## Conflict resolution

When `SyncWorker` receives 409 on `UPDATE`:
- Note's `syncState` → `CONFLICT`.
- Server's version of the note is persisted in a sidecar field (or fetched on demand via
  `GET /notes/{id}`) and shown in the editor.
- Editor offers two actions:
  - **Keep mine** — re-enqueue `UPDATE` with `base_version = serverVersion` (overwrite).
  - **Use server** — apply server content locally, mark `SYNCED`.
- `SyncStatus` chip turns red while any note is in `CONFLICT`.

## Adaptive UI & theming

- `calculateWindowSizeClass` drives navigation: `Compact` → bottom bar; `Medium`/`Expanded` → nav rail.
- Material 3 with a custom "ink on paper" palette; color is reserved for sync state
  (green = synced, amber = pending, red = conflict/failed).
- Dark theme via `values-night/themes.xml`.
- Single `Activity`; navigation via Compose `NavHost` with typed routes.

## Build variants & configuration

| Config | Debug | Release |
|---|---|---|
| `API_BASE_URL` | `http://10.0.2.2:8000` | `https://api.example.com` |
| `usesCleartextTraffic` | `true` | `false` |
| `isMinifyEnabled` | `false` | `true` |
| `isShrinkResources` | `false` | `true` |
| `applicationIdSuffix` | `.debug` | — |

Override `API_BASE_URL` in `app/build.gradle.kts` `buildConfigField` for staging.

## Release, R8 & ProGuard

- `proguard-rules.pro` keeps Kotlin serialization members and `com.inc.app.**` Companion/serializer.
- `-dontwarn com.google.errorprone.annotations.**` (Tink references them at compile time only).
- WorkManager on-demand init: manifest removes `androidx.startup.InitializationProvider`'s
  `WorkManagerInitializer` meta-data via `tools:node="remove"`; `IncSyncApp` implements
  `Configuration.Provider` returning a `Configuration` with `HiltWorkerFactory`.
- Lint vital checks run during `assembleRelease`; the `RemoveWorkManagerInitializer` lint rule
  is satisfied by the manifest removal above.

## Testing strategy

### Layers

| Layer | Tooling | Location |
|---|---|---|
| Unit (JVM) | JUnit 4, Turbine, fake repository | `app/src/test` |
| Instrumented | AndroidX Test, Room in-memory, WorkManager TestDriver | `app/src/androidTest` |
| Backend | pytest, in-memory SQLite | `server/tests` |

### Unit tests

- `NotesViewModelTest` — status aggregation across `SYNCED/PENDING/FAILED/CONFLICT` with a
  fake repository (no Room). Verifies `SyncStatus` priority ordering.

### Instrumented tests

- `RoomNotesRepositoryTest` — CRUD, version bumping, pending-op recording, DB reopen
  durability (uses on-disk DB, cleaned in `@After`).
- `NoteDaoTest` — DAO query correctness.
- `MigrationTest` — loads real v4 schema from `androidTest` assets, runs `MIGRATION_4_5`,
  asserts row preservation + FTS population + `MATCH` query.
- `SyncWorkerTest` — failure injection: fakes `IncSyncApi` returning 500/200; asserts
  `Result.retry()` vs `Result.success()` and queue drain. Uses `TestListenableWorkerBuilder`.

### Backend tests

12 pytest cases: auth (register/login/me), notes CRUD, folders, conflict detection (409 on
stale `base_version`).

### Schema exports & golden files

`app/schemas/com.inc.app.data.local.IncSyncDatabase/{1..5}.json` are checked in and used by
`MigrationTestHelper`. Regenerate via `./gradlew :app:exportSchemas` when bumping DB version.

## Observability & logging

- Debug: `HttpLoggingInterceptor` at `BODY` level; Room query logging via
  `RoomDatabase.Builder.setQueryCallback`.
- Release: no HTTP body logging; only `ERROR`-level logs.
- `SyncWorker` records `lastError` on each failed op for diagnostics (surfaced in settings).
- No PII (note content) is ever logged.

## Backend service

FastAPI 0.115, SQLAlchemy 2.0 (async), Pydantic v2, JWT (python-jose), password hashing
(passlib/bcrypt).

### Run

```bash
cd server
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
DATABASE_URL="sqlite:///./dev.db" uvicorn app.main:app --reload --port 8000
```

### API surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Create account, return JWT. |
| `POST` | `/auth/login` | Authenticate, return JWT. |
| `GET`  | `/auth/me` | Current user (Bearer). |
| `GET`  | `/notes` | List notes for the authenticated user. |
| `POST` | `/notes` | Create (client-supplied id; idempotent). |
| `GET`  | `/notes/{id}` | Fetch one (used for conflict resolution). |
| `PUT`  | `/notes/{id}` | Update with `base_version` optimistic concurrency. |
| `DELETE` | `/notes/{id}` | Delete (idempotent; 404 → success). |
| `GET/POST/PUT/DELETE` | `/folders[/{id}]` | Folder CRUD with versioning. |

`PUT /notes/{id}` returns `409 Conflict` with the server's current note when `base_version`
does not match.

### Data store

- Default: SQLite (dev). Production: PostgreSQL via `DATABASE_URL`.
- `docker-compose.yml` spins up Postgres + the API for local integration testing.

### Test

```bash
cd server
DATABASE_URL="sqlite:///:memory:" python -m pytest -q
```

## Contributing

1. Fork and branch from `main`.
2. Match conventions: transactional writes, `Flow`-based reads, no framework deps in `domain/`.
3. Add/update tests for behavior changes.
4. Run before PR:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
   cd server && DATABASE_URL="sqlite:///:memory:" python -m pytest -q
   ```
5. Open a PR with a description and verification steps.

## License

Provided as-is for educational and portfolio use. See repository owner for licensing terms.