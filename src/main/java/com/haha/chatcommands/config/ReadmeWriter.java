package com.haha.chatcommands.config;

import com.haha.chatcommands.MikasaTpMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes README.md into the mod config folder for jar-only installs.
 * Author: Mikasa
 */
public final class ReadmeWriter {
	private ReadmeWriter() {
	}

	public static void write(Path modConfigDir) {
		Path readme = modConfigDir.resolve("README.md");
		try {
			Files.createDirectories(modConfigDir);
			Files.writeString(readme, content(), StandardCharsets.UTF_8);
			MikasaTpMod.LOGGER.info("Wrote {}", readme);
		} catch (IOException e) {
			MikasaTpMod.LOGGER.error("Failed to write README at {}", readme, e);
		}
	}

	private static String content() {
		return """
				# Mikasa-tp-mod %s

				Author: **Mikasa**

				Repository: %s

				Teleport, homes, and TPA commands for Fabric Minecraft 26.3.
				Drop the jar into the `mods` folder together with Fabric API.
				Full documentation (DB schema, roles, sync): %s

				## Quick start (jar only)

				1. Install Fabric Loader on the server (Minecraft 26.3).
				2. Put into `mods`:
				   - `Mikasa-tp-mod-%s.jar`
				   - `fabric-api` for 26.3
				3. Start the server once.
				4. These files appear:
				   - `config/Mikasa-tp-mod/config.json` - roles, permissions, homes (JSON)
				   - `config/Mikasa-tp-mod/README.md` - this guide
				   - `config/Mikasa-mods-general/database/configdatabase.json` - shared DB
				5. In-game: `/tphelp` - full command list.

				Without a database the mod already works on JSON.

				## Database (optional)

				File: `config/Mikasa-mods-general/database/configdatabase.json`
				(shared across Mikasa mods)

				PostgreSQL example:

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

				MySQL example:

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

				Fields:
				- `type` - `postgres` or `mysql` (`sql` also means mysql)
				- `enabled` - set `true` to use the database
				- `host`, `port`, `database`, `username`, `password`

				By default the file is created **empty** (`enabled: false`). Until the DB is configured, JSON files are used.

				If the database is unavailable at startup, the mod falls back to JSON automatically.

				Required tables are created on a successful connection.

				## Sync config.json -> database

				`config.json` has a flag:

				```json
				{
				  "sync_to_database": false,
				  "settings": { "max_homes": 5 },
				  "roles": { }
				}
				```

				1. Edit roles/settings in `config.json` as needed.
				2. Set `"sync_to_database": true`.
				3. Restart the server (DB must be enabled in the shared `configdatabase.json`).
				4. The mod applies roles and settings to the DB, then sets the flag back to `false`.

				**WARNING:** sync **replaces** roles, command permissions, and settings in the DB
				with the contents of `config.json`. Players on removed roles are moved to `player`.
				The homes table is not touched.

				A Python sync script is not required.

				## Commands

				| Command | Description |
				|---------|-------------|
				| `/tphelp` | List all mod commands |
				| `/tp <player>` | Teleport to a player (role permission) |
				| `/tpa <player>` | Request teleport |
				| `/tpaccept` | Accept TPA |
				| `/tpdeny` | Deny TPA |
				| `/tpacancel` | Cancel your TPA |
				| `/home set <name>` | Save a home |
				| `/home <name>` | Teleport home |
				| `/home del <name>` | Delete a home |
				| `/home list` | List your homes |

				Role permissions and the home limit (`max_homes`) are configured in `config.json`.
				Permission key for all home subcommands: `home`.

				## Role permissions (config.json)

				```json
				{
				  "settings": { "max_homes": 5 },
				  "roles": {
				    "player": {
				      "commands": {
				        "tp": true,
				        "tpa": true,
				        "home": true,
				        "tphelp": true
				      }
				    }
				  }
				}
				```

				Player roles are usually assigned by another mod / a `players` table row (or the `players` block in JSON).

				## Support

				Author: Mikasa  
				Repository: %s  
				Version: %s (fabric / Minecraft %s / mod %s)  
				Mod id: mikasa-tp-mod
				Jar: Mikasa-tp-mod-%s.jar
				""".formatted(
				MikasaTpMod.VERSION,
				MikasaTpMod.SOURCE_REPO,
				MikasaTpMod.SOURCE_REPO,
				MikasaTpMod.VERSION,
				MikasaTpMod.SOURCE_REPO,
				MikasaTpMod.VERSION,
				MikasaTpMod.GAME_VERSION,
				MikasaTpMod.MOD_VERSION,
				MikasaTpMod.VERSION
		);
	}
}
