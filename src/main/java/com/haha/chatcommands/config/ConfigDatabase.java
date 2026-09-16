package com.haha.chatcommands.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.haha.chatcommands.MikasaTpMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared DB settings: {@code config/Mikasa-mods-general/database/configdatabase.json}
 * Author: Mikasa
 */
public final class ConfigDatabase {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final String SHARED_ROOT = "Mikasa-mods-general";
	public static final String SHARED_SUBDIR = "database";
	public static final String FILE_NAME = "configdatabase.json";

	public enum DbType {
		POSTGRES,
		MYSQL,
		NONE;

		public static DbType fromConfig(String raw) {
			if (raw == null || raw.isBlank()) {
				return NONE;
			}
			return switch (raw.trim().toLowerCase()) {
				case "postgres", "postgresql", "pg" -> POSTGRES;
				case "mysql", "mariadb", "sql" -> MYSQL;
				default -> NONE;
			};
		}
	}

	private final Path file;
	private final Data data;

	public ConfigDatabase() {
		this.file = sharedDir().resolve(FILE_NAME);
		this.data = loadOrCreate();
	}

	public static Path sharedDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(SHARED_ROOT).resolve(SHARED_SUBDIR);
	}

	public static String sharedPathLog() {
		return SHARED_ROOT + "/" + SHARED_SUBDIR + "/" + FILE_NAME;
	}

	public Path file() {
		return file;
	}

	public Data data() {
		return data;
	}

	public DbType type() {
		return DbType.fromConfig(data.type);
	}

	public boolean isUsable() {
		if (!data.enabled) {
			return false;
		}
		DbType t = type();
		if (t == DbType.NONE) {
			return false;
		}
		return data.host != null && !data.host.isBlank()
				&& data.database != null && !data.database.isBlank()
				&& data.username != null && !data.username.isBlank()
				&& effectivePort() > 0;
	}

	public int effectivePort() {
		if (data.port != null && data.port > 0) {
			return data.port;
		}
		return switch (type()) {
			case POSTGRES -> 5432;
			case MYSQL -> 3306;
			case NONE -> 0;
		};
	}

	public String jdbcUrl() {
		int port = effectivePort();
		return switch (type()) {
			case POSTGRES -> "jdbc:postgresql://%s:%d/%s".formatted(data.host, port, data.database);
			case MYSQL -> "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
					.formatted(data.host, port, data.database);
			case NONE -> "";
		};
	}

	private Data loadOrCreate() {
		try {
			Files.createDirectories(file.getParent());
			writeSharedReadmeIfMissing();
			if (!Files.exists(file)) {
				Data empty = Data.empty();
				write(empty);
				MikasaTpMod.log("created empty " + sharedPathLog());
				return empty;
			}
			try (Reader reader = Files.newBufferedReader(file)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded == null) {
					Data empty = Data.empty();
					write(empty);
					return empty;
				}
				loaded.normalize();
				return loaded;
			}
		} catch (JsonSyntaxException e) {
			MikasaTpMod.log("invalid JSON in " + FILE_NAME + " - " + e.getMessage());
			backupBrokenFile();
			Data empty = Data.empty();
			try {
				write(empty);
				MikasaTpMod.log("recreated empty " + FILE_NAME + " (broken file backed up as .broken)");
			} catch (IOException io) {
				MikasaTpMod.LOGGER.error("Failed to recreate {}", file, io);
			}
			return empty;
		} catch (IOException e) {
			MikasaTpMod.log("failed to load " + FILE_NAME + " - using empty in-memory config");
			MikasaTpMod.LOGGER.error("Failed to load {}", file, e);
			return Data.empty();
		}
	}

	private void writeSharedReadmeIfMissing() {
		Path readme = sharedDir().resolve("README.md");
		if (Files.exists(readme)) {
			return;
		}
		String body = """
				# Mikasa-mods-general / database

				Shared database connection for Mikasa mods.

				File: `config/Mikasa-mods-general/database/configdatabase.json`

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

				Mods keep their own tables (e.g. TP uses `homes`, RPG uses `rpg_*`).
				""";
		try {
			Files.writeString(readme, body);
		} catch (IOException e) {
			MikasaTpMod.LOGGER.warn("Could not write {}", readme, e);
		}
	}

	private void backupBrokenFile() {
		try {
			Path backup = file.resolveSibling(FILE_NAME + ".broken");
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			MikasaTpMod.LOGGER.warn("Could not backup broken {}", file, e);
		}
	}

	private void write(Data d) throws IOException {
		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(d, writer);
		}
	}

	/** Empty defaults - user fills in type/host/port/login/password. */
	public static final class Data {
		public String type = "";
		public boolean enabled = false;
		public String host = "";
		public Integer port = null;
		public String database = "";
		public String username = "";
		public String password = "";

		static Data empty() {
			return new Data();
		}

		void normalize() {
			if (type == null) {
				type = "";
			}
			if (host == null) {
				host = "";
			}
			if (database == null) {
				database = "";
			}
			if (username == null) {
				username = "";
			}
			if (password == null) {
				password = "";
			}
		}
	}
}
