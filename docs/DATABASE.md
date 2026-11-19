# Database changes (read before touching any `@Entity`)

Kharcha Check stores entries in an encrypted Room database. **Real users have data in it**, so:

1. **Never** change an entity (add/rename/remove a field, table or index) without bumping
   `version` in `KharchaDatabase` and adding a `Migration` to `MIGRATIONS`.
2. **Never** add `fallbackToDestructiveMigration()`. It "fixes" a missing migration by deleting everyone's data.
3. Prefer additive changes: `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …`. Give the entity field
   `@ColumnInfo(defaultValue = "…")` with the same default, or Room's schema check fails at launch.
4. After building, commit the new `app/schemas/com.kharchacheck.app.data.db.KharchaDatabase/<version>.json`.
   CI's **Database schema guard** fails the build if the schema changed without a new version file.

**First time:** CI uploads the current schema as the `room-schema` artifact. Download it and commit
`1.json` under `app/schemas/com.kharchacheck.app.data.db.KharchaDatabase/` so the guard has a baseline.

Settings-style data (hidden entries, practised tips, salary-plan steps) lives in DataStore and needs no migration.

## Version history

| Version | Change | Migration |
| --- | --- | --- |
| 1 | First Play Store schema | — |
| 2 | Compact `transactions`: `type`, `mode`, `worth`, `source`, `review` stored as 1-byte integer codes; India time zone stored as `""` | `MIGRATION_1_2` in `KharchaDatabase.kt` (copies every row, same fallbacks as before) |

## Keeping the database small (and offline)

Everything is stored on the phone; nothing needs a server. The rules that keep it compact:

- **Money is integer paise** (`Long`), never floating point, never text.
- **Small enums are integer codes** (`Codes` in `Entities.kt`). Never reorder or remove a code; **append** new enum values to the end of the list. Backups keep readable names, so any version restores any backup.
- **Defaults are stored empty**: the India zone `Asia/Kolkata` is `""`; empty note/tags/refNo are `""` (SQLite stores an empty string in 1 byte).
- **No derived columns** (month, totals, bucket): they're computed in memory from `epochMillis` and the category, so nothing goes stale and nothing is stored twice.
- **Files never go in the database.** Screenshots and statements live in the encrypted vault; images are saved as WebP (quality 80, max 2200 px).
- **Upkeep**: the WAL is capped at 512 KB (`journal_size_limit`), and `UpkeepWorker` checkpoints daily and runs `VACUUM` only when 10%+ of the file is free pages. Settings → Storage shows sizes and has manual clean-up.

Measured on 5,000 sample entries, v2 is **536 KB vs 804 KB** in v1 (33% smaller), about 107 bytes per entry including indexes.
