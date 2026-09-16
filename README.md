# Mikasa-tp-mod

Fabric server teleport utility by **Mikasa**.

This mod brings player teleport, TPA, and homes to a **Fabric** server — without switching the core to Paper, Purpur, or similar. It can run on JSON alone or with the **database schema documented below**, and exposes all of the **basic commands listed in this README**. For TPA, a **stand-still warmup** blocks instant teleports during fights: the requester must stay still for several seconds, and any movement cancels the teleport.

| | |
|---|---|
| **Mod id** | `mikasa-tp-mod` |
| **Version** | `fabric-26.3-2.0` |
| **Minecraft** | `26.3` |
| **Loader** | Fabric **0.19.5+** (**IMPORTANT**) |
| **API** | Fabric API (required) |
| **Java** | 25+ |
| **License** | MIT |
| **Side** | Server (clients do not need the mod) |

Jar name: `Mikasa-tp-mod-fabric-26.3-2.0.jar`

---

## What it does

- Direct teleport: `/tp <player>` (role-gated; replaces vanilla `/tp` registration so non-ops can use it when allowed). **By default non-ops / role `player` get `tp: false`** and cannot use `/tp` until a higher role or config grants it
- TPA requests: `/tpa`, `/tpaccept`, `/tpdeny`, `/tpacancel` with a 5-second stand-still warmup (movement cancels — protection against TP mid-fight)
- Homes: `/home set`, `/home <name>`, `/home del`, `/home list`
- Help: `/tphelp`
- Role-based command permissions
- Storage: **JSON by default**, optional **PostgreSQL** or **MySQL** (schema below)
- One-shot config → database sync via `sync_to_database`

---

## Install

1. Install Fabric Loader for Minecraft **26.3** (**IMPORTANT:** **0.19.5+**).
2. Put this mod and **Fabric API** into the server `mods` folder.
3. Start the server once.
4. Edit files under `config/Mikasa-tp-mod/` (and shared DB under `config/Mikasa-mods-general/database/`) if needed.
5. In-game: `/tphelp`.

On first launch the mod creates:

```
config/Mikasa-tp-mod/
  config.json            # roles, permissions, players, homes, settings, sync flag
  README.md              # short install notes (auto-written)

config/Mikasa-mods-general/database/
  configdatabase.json    # optional shared DB connection (disabled by default)
  README.md              # short DB notes (created if missing)
```

If a config file is broken JSON, it is renamed to `*.broken` and a fresh default is created.

---

## Commands

| Command | Permission key | Purpose |
|--------|----------------|---------|
| `/tphelp` | `tphelp` | Private list of mod commands |
| `/tp <player>` | `tp` | Teleport to another online player |
| `/tpa <player>` | `tpa` | Send a teleport request |
| `/tpaccept` | `tpaccept` | Accept incoming TPA |
| `/tpdeny` | `tpdeny` | Deny incoming TPA |
| `/tpacancel` | `tpacancel` | Cancel your TPA / warmup (or deny your pending incoming) |
| `/home set <name>` | `home` | Save a home at your position |
| `/home <name>` | `home` | Teleport to a saved home |
| `/home del <name>` | `home` | Delete a home |
| `/home list` | `home` | List your homes (`count/max`) |

Permission key == command name used in role configs and in the `commands` / `role_permissions` tables. All home subcommands share the single key `home`.

### TPA flow (brief)

1. `/tpa <player>` creates a pending request (timeout ~60s).
2. Target runs `/tpaccept` → requester enters **5s** stand-still warmup with on-screen countdown.
3. Moving (or changing dimension) cancels the warmup.
4. After 5s the requester is teleported to the target.
5. Sessions are **in memory only** (lost on server restart).

---

## Storage modes

| Mode | When | Roles / settings / homes |
|------|------|---------------------------|
| **JSON** | DB disabled, misconfigured, or unreachable | `config.json` |
| **Database** | shared `configdatabase.json` usable and connect succeeds | SQL tables |

Startup prefers the database when connected. If a DB query fails at runtime, many paths fall back to JSON.

Launch log examples:

- `launch config: database (postgres|mysql)`
- `launch config: file`

---

## `config/Mikasa-mods-general/database/configdatabase.json`

Shared across Mikasa mods. Created empty / disabled by default. Example:

```json
{
  "type": "postgres",
  "enabled": true,
  "host": "127.0.0.1",
  "port": 5432,
  "database": "minecraft",
  "username": "minecraft",
  "password": "secret"
}
```

MySQL / MariaDB:

```json
{
  "type": "mysql",
  "enabled": true,
  "host": "127.0.0.1",
  "port": 3306,
  "database": "minecraft",
  "username": "minecraft",
  "password": "secret"
}
```

| Field | Notes |
|-------|--------|
| `type` | `postgres` / `postgresql` / `pg` → PostgreSQL; `mysql` / `mariadb` / `sql` → MySQL |
| `enabled` | Must be `true` to connect |
| `host`, `database`, `username` | Required when enabled |
| `port` | Defaults: Postgres `5432`, MySQL `3306` |
| `password` | Connection password |

JDBC drivers (PostgreSQL, MySQL) and HikariCP are **bundled** in the mod jar. You do not add extra jars for DB drivers.

On successful connect the mod runs `CREATE TABLE IF NOT EXISTS ...` and seeds defaults (`player` role, `max_homes = 5`).

---

## Database structure

Tables are created automatically. Logical schema:

### `roles`

| Column | Type (concept) | Description |
|--------|----------------|-------------|
| `name` | text / varchar PK | Role id, e.g. `player`, `moderator`, `admin` |

Seeded: `player`.

### `commands`

| Column | Type | Description |
|--------|------|-------------|
| `name` | text / varchar PK | Permission / command key, e.g. `tp`, `home` |

### `role_permissions`

| Column | Type | Description |
|--------|------|-------------|
| `role_name` | FK → `roles` | Role |
| `command_name` | FK → `commands` | Command key |
| `allowed` | boolean | `true` = allowed, `false` = denied |
| PK | (`role_name`, `command_name`) | |

If there is **no row** for a role+command, the mod treats it as **denied**.

### `players`

| Column | Type | Description |
|--------|------|-------------|
| `uuid` | UUID / CHAR(36) PK | Player UUID |
| `name` | text / varchar | Last known name (optional metadata) |
| `role_name` | FK → `roles`, default `player` | Assigned role |

**Important:** this mod **reads** player roles for permission checks. It does **not** create or update player rows during normal gameplay (no auto-register on join). You assign roles yourself (SQL, admin tool, or JSON — see below).

### `settings`

| Column | Type | Description |
|--------|------|-------------|
| `key` | text / varchar PK | Setting name |
| `value` | text / varchar | String value |

Known setting used by the mod:

| Key | Default | Meaning |
|-----|---------|---------|
| `max_homes` | `5` | Max homes per player for `/home set` (new homes only) |

### `homes`

| Column | Type | Description |
|--------|------|-------------|
| `player_uuid` | UUID / CHAR(36) | Owner |
| `home_name` | text / varchar | Home id |
| `world` | text / varchar | Dimension id (e.g. `minecraft:overworld`) |
| `x`, `y`, `z` | double | Position |
| `yaw`, `pitch` | float | Look direction |
| PK | (`player_uuid`, `home_name`) | |

Homes are upserted on `/home set` and deleted on `/home del`.

### PostgreSQL reference DDL

```sql
CREATE TABLE IF NOT EXISTS roles (
    name TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS commands (
    name TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_name TEXT NOT NULL REFERENCES roles(name) ON DELETE CASCADE,
    command_name TEXT NOT NULL REFERENCES commands(name) ON DELETE CASCADE,
    allowed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (role_name, command_name)
);

CREATE TABLE IF NOT EXISTS players (
    uuid UUID PRIMARY KEY,
    name TEXT,
    role_name TEXT NOT NULL DEFAULT 'player' REFERENCES roles(name)
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS homes (
    player_uuid UUID NOT NULL,
    home_name TEXT NOT NULL,
    world TEXT NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    PRIMARY KEY (player_uuid, home_name)
);

INSERT INTO roles (name) VALUES ('player') ON CONFLICT DO NOTHING;
INSERT INTO settings (key, value) VALUES ('max_homes', '5') ON CONFLICT DO NOTHING;
```

MySQL uses compatible types (`VARCHAR`, `CHAR(36)`, `DOUBLE`, `FLOAT`, etc.) with the same table/column names.

---

## `config.json` structure

```json
{
  "sync_to_database": false,
  "settings": {
    "max_homes": 5
  },
  "roles": {
    "player": {
      "commands": {
        "tphelp": true,
        "tp": false,
        "home": true,
        "tpa": true,
        "tpaccept": true,
        "tpdeny": true,
        "tpacancel": true
      }
    },
    "moderator": {
      "commands": {
        "tphelp": true,
        "tp": true,
        "home": true,
        "tpa": true,
        "tpaccept": true,
        "tpdeny": true,
        "tpacancel": true
      }
    },
    "admin": {
      "commands": {
        "tphelp": true,
        "tp": true,
        "home": true,
        "tpa": true,
        "tpaccept": true,
        "tpdeny": true,
        "tpacancel": true
      }
    }
  },
  "players": {},
  "homes": {}
}
```

### Default permission matrix

| Command | `player` | `moderator` | `admin` |
|---------|----------|-------------|---------|
| `tphelp` | yes | yes | yes |
| `tp` | **no** | yes | yes |
| `home` (`set` / `<name>` / `del` / `list`) | yes | yes | yes |
| `tpa` / `tpaccept` / `tpdeny` / `tpacancel` | yes | yes | yes |

### Players block (JSON mode)

```json
"players": {
  "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx": {
    "name": "Steve",
    "role": "moderator"
  }
}
```

Missing player entry → treated as role `player`.

### Homes block (JSON mode)

```json
"homes": {
  "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx": {
    "spawn": {
      "world": "minecraft:overworld",
      "x": 0.0,
      "y": 64.0,
      "z": 0.0,
      "yaw": 0.0,
      "pitch": 0.0
    }
  }
}
```

---

## How roles work

### Resolution order

1. Look up the player's role (`players` table if DB connected, else `config.json` → `players`).
2. If unknown / missing → role `player`.
3. Check permission for that role + command key.
4. Missing permission entry → **denied**.

Only **players** can use these commands (console is rejected by the permission gate).

### Creating / adding a role (JSON)

1. Open `config/Mikasa-tp-mod/config.json`.
2. Under `roles`, add a new object, for example:

```json
"vip": {
  "commands": {
    "tphelp": true,
    "tp": true,
    "home": true,
    "tpa": true,
    "tpaccept": true,
    "tpdeny": true,
    "tpacancel": true
  }
}
```

3. Assign a player to that role in `players` (JSON) **or** in the `players` SQL table (DB mode).
4. Restart is not always required for JSON edits depending on when the file is reloaded; safest is **restart the server** after role edits.
5. If you use a database and want SQL tables to match this file, use **`sync_to_database`** (next section).

### Creating / adding a role (SQL, manual)

```sql
INSERT INTO roles (name) VALUES ('vip')
  ON CONFLICT DO NOTHING;

INSERT INTO commands (name) VALUES
  ('tphelp'), ('tp'), ('home'),
  ('tpa'), ('tpaccept'), ('tpdeny'), ('tpacancel')
  ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_name, command_name, allowed) VALUES
  ('vip', 'tphelp', TRUE),
  ('vip', 'tp', TRUE),
  ('vip', 'home', TRUE),
  ('vip', 'tpa', TRUE),
  ('vip', 'tpaccept', TRUE),
  ('vip', 'tpdeny', TRUE),
  ('vip', 'tpacancel', TRUE)
ON CONFLICT (role_name, command_name) DO UPDATE SET allowed = EXCLUDED.allowed;
```

### Assigning a role to a player

**JSON:**

```json
"players": {
  "uuid-here": { "name": "Steve", "role": "vip" }
}
```

**SQL:**

```sql
INSERT INTO players (uuid, name, role_name)
VALUES ('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx', 'Steve', 'vip')
ON CONFLICT (uuid) DO UPDATE
SET name = EXCLUDED.name, role_name = EXCLUDED.role_name;
```

Remember: the mod does not auto-create `players` rows on join.

---

## `sync_to_database` — how overwrite / rewrite works

This is the built-in one-shot sync. **You do not need an external Python script** for normal use.

### When it runs

At server startup, after a successful DB connection:

1. If `sync_to_database` is `false` → nothing happens.
2. If `true` but DB is **not** connected → sync is **skipped**; the flag **stays `true`** (retry next start).
3. If `true` and DB is connected → sync runs inside a transaction, then the mod sets `sync_to_database` back to **`false`** and saves `config.json`.

### How to use it

1. Edit `roles` and `settings` in `config.json` the way you want the database to look.
2. Ensure shared `config/Mikasa-mods-general/database/configdatabase.json` is enabled and correct.
3. Set `"sync_to_database": true`.
4. Restart the server.
5. Check logs for sync success; flag should return to `false`.

### What gets written / overwritten

From `config.json` **into** the database:

| Data | Action |
|------|--------|
| Roles listed under `roles` | Upserted into `roles` |
| Role `player` | Always ensured to exist |
| Command keys found in role maps | Upserted into `commands` |
| Every role × command `allowed` flag | Upserted into `role_permissions` |
| `settings` (e.g. `max_homes`) | Upserted into `settings` |
| Roles / commands / permissions **not** present in config | **Pruned (deleted)** from DB |

### What is preserved

| Data | Behavior |
|------|----------|
| **`homes` table** | **Never touched** by sync |
| **`players` assignments** | Not rewritten from JSON `players` during sync |
| Players on a **deleted** role | Moved to role `player` before that role is removed |
| Role `player` | Never deleted by prune |

### Sync order (single transaction)

1. Ensure `player` role  
2. Upsert desired roles  
3. Upsert desired commands  
4. Upsert permission rows  
5. Delete stale permissions  
6. Delete stale commands  
7. Delete stale roles (after moving affected players to `player`)  
8. Upsert settings  
9. Commit  
10. Set `sync_to_database = false` and save JSON  

On failure: rollback, flag remains `true`, error is logged.

### Warning

Sync **replaces** the database role/permission/settings picture with whatever is in `config.json`. Old roles that you removed from JSON are deleted from SQL (with players on those roles demoted to `player`). Homes stay intact.

---

## Optional external sync (`shared-db`)

There is a separate folder `mods/shared-db` with Postgres helpers (`schema.sql`, `roles.json`, `sync_roles.py`). Behavior is similar (upsert + prune roles/permissions/settings; do not wipe homes).

For this mod, prefer **`sync_to_database`** in `config.json`. The Python tool is optional / shared tooling, not required to run Mikasa-tp-mod.

---

## Homes details

- Limit: `settings.max_homes` (JSON or DB).
- Enforced when creating a **new** home name; updating an existing name does not consume another slot.
- World is stored as a dimension id string.
- With DB connected, homes live in the `homes` table; otherwise in `config.json` → `homes`.

---

## Dependencies (summary)

| Dependency | Required? |
|------------|-----------|
| Minecraft 26.3 | Yes |
| Fabric Loader **0.19.5+** (**IMPORTANT**) | Yes |
| Fabric API | Yes |
| Java 25+ | Yes |
| PostgreSQL / MySQL server | No (optional storage) |

---

## Build

```bat
gradlew.bat build
```

Output: `build/libs/Mikasa-tp-mod-fabric-26.3-2.0.jar`

---

## License

MIT — Author: **Mikasa**
