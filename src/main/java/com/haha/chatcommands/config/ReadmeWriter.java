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

				Мод команд телепорта, домов и TPA для Fabric Minecraft 26.3.
				Достаточно положить jar в папку `mods` вместе с Fabric API.
				Полная документация (схема БД, роли, sync): %s

				## Быстрый старт (только jar)

				1. Установи Fabric Loader на сервер (Minecraft 26.3).
				2. Положи в `mods`:
				   - `Mikasa-tp-mod-%s.jar`
				   - `fabric-api` для 26.3
				3. Запусти сервер один раз.
				4. Появится:
				   - `config/Mikasa-tp-mod/config.json` - роли, права, дома (JSON)
				   - `config/Mikasa-tp-mod/README.md` - эта инструкция
				   - `config/Mikasa-mods-general/database/configdatabase.json` - общая БД
				5. В игре: `/tphelp` - полный список команд.

				Без базы мод уже работает на JSON.

				## База данных (опционально)

				Файл: `config/Mikasa-mods-general/database/configdatabase.json`
				(общий для модов Mikasa)

				Пример для PostgreSQL:

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

				Пример для MySQL:

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

				Поля:
				- `type` - `postgres` или `mysql` (также `sql` = mysql)
				- `enabled` - `true`, чтобы включить БД
				- `host`, `port`, `database`, `username`, `password`

				По умолчанию файл создаётся **пустым** (`enabled: false`). Пока БД не настроена, используются JSON-файлы.

				Если БД недоступна при старте - мод автоматически переключается на JSON.

				Нужные таблицы создаются при успешном подключении.

				## Синхронизация config.json -> база данных

				В `config.json` есть флаг:

				```json
				{
				  "sync_to_database": false,
				  "settings": { "max_homes": 5 },
				  "roles": { }
				}
				```

				1. Отредактируй роли/настройки в `config.json` как нужно.
				2. Поставь `"sync_to_database": true`.
				3. Перезапусти сервер (БД должна быть включена в shared `configdatabase.json`).
				4. Мод применит роли и settings в БД, затем **сам** вернёт флаг в `false`.

				**ВНИМАНИЕ:** при синхронизации старые роли, права команд и settings в БД
				**сносятся/заменяются** содержимым из `config.json`. Игроки с удалёнными ролями
				переводятся на `player`. Таблица домов (`homes`) не трогается.

				Python-скрипт для синхронизации не нужен.

				## Команды

				| Команда | Описание |
				|---------|----------|
				| `/tphelp` | Список всех команд мода |
				| `/tp <игрок>` | Телепорт к игроку (по правам роли) |
				| `/tpa <игрок>` | Запрос телепорта |
				| `/tpaccept` | Принять TPA |
				| `/tpdeny` | Отклонить TPA |
				| `/tpacancel` | Отменить свой TPA |
				| `/home set <имя>` | Сохранить дом |
				| `/home <имя>` | Телепорт домой |
				| `/home del <имя>` | Удалить дом |
				| `/home list` | Список своих домов |

				Права ролей и лимит домов (`max_homes`) настраиваются в `config.json`.
				Ключ права для всех подкоманд home: `home`.

				## Права ролей (config.json)

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

				Игрокам роли обычно назначает отдельный мод / запись в таблице `players` (или блок `players` в JSON).

				## Поддержка

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
