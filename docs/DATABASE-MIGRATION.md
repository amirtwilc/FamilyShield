# Database migration and server moves

FamilyShield has two distinct database operations:

- `npm run db:migrate` applies every SQL file under `drizzle/` exactly once.
  It is non-destructive and records filenames in `familyshield_migrations`.
- `npm run db:setup` drops and recreates the `public` schema. Use it only for a
  disposable local development database.

`db:prod:setup` is retained as a compatibility alias for `db:migrate`.

Older databases created before the migration ledger was introduced must be
baselined once. First review `npm run db:check`: checks belonging to migrations
already installed must pass, while checks for newer pending migrations may
fail. Then name the last migration already installed and run the migrator:

```powershell
$env:BASELINE_EXISTING_THROUGH = '0019_firebase_parent_auth.sql'
npm run db:migrate
Remove-Item Env:BASELINE_EXISTING_THROUGH
```

The explicit filename prevents a newly added migration from being accidentally
marked as installed without executing it.

## Move the complete database to another PostgreSQL/PostGIS server

The SQL migrations create an empty current schema. To move schema **and data**,
use PostgreSQL's standard custom-format backup. Keep the dump outside the repo
because it contains private family data.

```powershell
$sourceDatabase = 'postgresql://user:password@old-host/familyshield?sslmode=require'
$destinationDatabase = 'postgresql://user:password@new-host/familyshield?sslmode=require'
pg_dump --format=custom --no-owner --no-acl --file familyshield.dump $sourceDatabase
psql $destinationDatabase -c 'CREATE EXTENSION IF NOT EXISTS postgis;'
pg_restore --clean --if-exists --no-owner --no-acl --dbname $destinationDatabase familyshield.dump
```

Stop backend writes during the final dump, use compatible PostgreSQL client
tools, and verify the restored server before changing `DATABASE_URL`:

```powershell
$env:DATABASE_URL = $destinationDatabase
npm run db:check
```

Back up `MESSAGE_ENCRYPTION_KEY` separately and keep the exact same value on the
new server; database backups do not include deployment environment variables.
