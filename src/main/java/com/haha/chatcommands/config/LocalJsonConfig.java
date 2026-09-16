package com.haha.chatcommands.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.haha.chatcommands.MikasaTpMod;
import com.haha.chatcommands.db.Home;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Local JSON config under {@code config/Mikasa-tp-mod/config.json}.
 * Used for roles/homes when DB is off or unavailable.
 * Author: Mikasa
 */
public final class LocalJsonConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path configDir;
	private final Path configFile;
	private Data data;

	public LocalJsonConfig() {
		this.configDir = FabricLoader.getInstance().getConfigDir().resolve(MikasaTpMod.MOD_FOLDER);
		this.configFile = configDir.resolve("config.json");
		this.data = loadOrCreate();
	}

	public Path configDir() {
		return configDir;
	}

	public Path configFile() {
		return configFile;
	}

	public Data data() {
		return data;
	}

	public synchronized void save() {
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(configFile)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			MikasaTpMod.LOGGER.error("Failed to save {}", configFile, e);
		}
	}

	private Data loadOrCreate() {
		try {
			Files.createDirectories(configDir);
			if (!Files.exists(configFile)) {
				Data defaults = Data.defaults();
				try (Writer writer = Files.newBufferedWriter(configFile)) {
					GSON.toJson(defaults, writer);
				}
				MikasaTpMod.LOGGER.info("Created default config at {} (author: {})", configFile, MikasaTpMod.AUTHOR);
				return defaults;
			}
			try (Reader reader = Files.newBufferedReader(configFile)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded == null) {
					MikasaTpMod.LOGGER.warn("Config {} empty, recreating defaults", configFile);
					Data defaults = Data.defaults();
					saveData(defaults);
					return defaults;
				}
				loaded.normalize();
				return loaded;
			}
		} catch (JsonSyntaxException e) {
			MikasaTpMod.log("invalid JSON in config.json - " + e.getMessage());
			try {
				Path backup = configFile.resolveSibling("config.json.broken");
				Files.copy(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
			Data defaults = Data.defaults();
			saveData(defaults);
			MikasaTpMod.log("recreated default config.json (broken file backed up as .broken)");
			return defaults;
		} catch (IOException e) {
			MikasaTpMod.LOGGER.error("Failed to load {}, using in-memory defaults", configFile, e);
			return Data.defaults();
		}
	}

	private void saveData(Data d) {
		this.data = d;
		save();
	}

	public String findRole(UUID uuid) {
		PlayerEntry entry = data.players.get(uuid.toString());
		if (entry == null || entry.role == null || entry.role.isBlank()) {
			return "player";
		}
		return entry.role;
	}

	public boolean hasPermission(UUID uuid, String command) {
		String role = findRole(uuid);
		RoleEntry roleEntry = data.roles.get(role);
		if (roleEntry == null || roleEntry.commands == null) {
			roleEntry = data.roles.get("player");
		}
		if (roleEntry == null || roleEntry.commands == null) {
			return false;
		}
		Boolean allowed = roleEntry.commands.get(command);
		return allowed != null && allowed;
	}

	public int maxHomes() {
		Object value = data.settings.get("max_homes");
		if (value instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		if (value != null) {
			try {
				return Math.max(0, Integer.parseInt(value.toString()));
			} catch (NumberFormatException ignored) {
			}
		}
		return 5;
	}

	public synchronized int countHomes(UUID uuid) {
		Map<String, HomeEntry> map = data.homes.get(uuid.toString());
		return map == null ? 0 : map.size();
	}

	public synchronized boolean homeExists(UUID uuid, String name) {
		Map<String, HomeEntry> map = data.homes.get(uuid.toString());
		return map != null && map.containsKey(name);
	}

	public synchronized Optional<Home> findHome(UUID uuid, String name) {
		Map<String, HomeEntry> map = data.homes.get(uuid.toString());
		if (map == null) {
			return Optional.empty();
		}
		HomeEntry entry = map.get(name);
		if (entry == null) {
			return Optional.empty();
		}
		return Optional.of(entry.toHome(uuid, name));
	}

	public synchronized List<Home> listHomes(UUID uuid) {
		Map<String, HomeEntry> map = data.homes.get(uuid.toString());
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		return map.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> e.getValue().toHome(uuid, e.getKey()))
				.collect(Collectors.toList());
	}

	public synchronized void upsertHome(Home home) {
		Map<String, HomeEntry> map = data.homes.computeIfAbsent(home.playerUuid().toString(), k -> new ConcurrentHashMap<>());
		map.put(home.name(), HomeEntry.from(home));
		save();
	}

	public synchronized boolean deleteHome(UUID uuid, String name) {
		Map<String, HomeEntry> map = data.homes.get(uuid.toString());
		if (map == null) {
			return false;
		}
		HomeEntry removed = map.remove(name);
		if (removed != null) {
			save();
			return true;
		}
		return false;
	}

	public static final class Data {
		/** When true on startup: push roles/settings from this file into SQL DB, then set false. */
		public boolean sync_to_database = false;
		public Map<String, Object> settings = new LinkedHashMap<>();
		public Map<String, RoleEntry> roles = new LinkedHashMap<>();
		public Map<String, PlayerEntry> players = new ConcurrentHashMap<>();
		public Map<String, Map<String, HomeEntry>> homes = new ConcurrentHashMap<>();

		void normalize() {
			if (settings == null) {
				settings = new LinkedHashMap<>();
			}
			if (roles == null) {
				roles = new LinkedHashMap<>();
			}
			if (players == null) {
				players = new ConcurrentHashMap<>();
			}
			if (homes == null) {
				homes = new ConcurrentHashMap<>();
			}
			if (!settings.containsKey("max_homes")) {
				settings.put("max_homes", 5);
			}
			if (roles.isEmpty()) {
				roles.putAll(defaultRoles());
			}
			for (RoleEntry role : roles.values()) {
				if (role.commands == null) {
					continue;
				}
				if (!role.commands.containsKey("tphelp")) {
					role.commands.put("tphelp", true);
				}
			}
		}

		static Data defaults() {
			Data data = new Data();
			data.sync_to_database = false;
			data.settings.put("max_homes", 5);
			data.roles.putAll(defaultRoles());
			return data;
		}

		static Map<String, RoleEntry> defaultRoles() {
			Map<String, Boolean> playerCommands = new LinkedHashMap<>();
			playerCommands.put("tphelp", true);
			playerCommands.put("tp", false);
			playerCommands.put("home", true);
			playerCommands.put("tpa", true);
			playerCommands.put("tpaccept", true);
			playerCommands.put("tpdeny", true);
			playerCommands.put("tpacancel", true);

			Map<String, Boolean> staffCommands = new LinkedHashMap<>(playerCommands);
			staffCommands.put("tp", true);

			Map<String, RoleEntry> roles = new LinkedHashMap<>();
			RoleEntry player = new RoleEntry();
			player.commands = playerCommands;
			roles.put("player", player);

			for (String name : List.of("moderator", "admin")) {
				RoleEntry role = new RoleEntry();
				role.commands = new LinkedHashMap<>(staffCommands);
				roles.put(name, role);
			}
			return roles;
		}
	}

	public static final class RoleEntry {
		public Map<String, Boolean> commands = new HashMap<>();
	}

	public static final class PlayerEntry {
		public String name;
		public String role = "player";
	}

	public static final class HomeEntry {
		public String world;
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;

		static HomeEntry from(Home home) {
			HomeEntry e = new HomeEntry();
			e.world = home.world();
			e.x = home.x();
			e.y = home.y();
			e.z = home.z();
			e.yaw = home.yaw();
			e.pitch = home.pitch();
			return e;
		}

		Home toHome(UUID uuid, String name) {
			return new Home(uuid, name, world, x, y, z, yaw, pitch);
		}
	}
}
