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
