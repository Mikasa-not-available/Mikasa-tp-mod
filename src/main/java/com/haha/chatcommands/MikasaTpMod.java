package com.haha.chatcommands;

import com.haha.chatcommands.command.CommandRegistry;
import com.haha.chatcommands.command.HomeCommand;
import com.haha.chatcommands.command.HomeDelCommand;
import com.haha.chatcommands.command.HomeSetCommand;
import com.haha.chatcommands.command.HomesCommand;
import com.haha.chatcommands.command.TpAcceptCommand;
import com.haha.chatcommands.command.TpCommand;
import com.haha.chatcommands.command.TpDenyCommand;
import com.haha.chatcommands.command.TpHelpCommand;
import com.haha.chatcommands.command.TpaCancelCommand;
import com.haha.chatcommands.command.TpaCommand;
import com.haha.chatcommands.config.ConfigDatabase;
import com.haha.chatcommands.config.LocalJsonConfig;
import com.haha.chatcommands.config.ReadmeWriter;
import com.haha.chatcommands.db.Database;
import com.haha.chatcommands.db.DatabaseConfigSync;
import com.haha.chatcommands.db.HomeRepository;
import com.haha.chatcommands.db.PermissionRepository;
import com.haha.chatcommands.db.SettingsRepository;
import com.haha.chatcommands.tpa.TpaManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mikasa-tp-mod - teleport, homes and TPA.
 * Author: Mikasa
 * Version: fabric-26.3-1.5 (fabric engine / MC 26.3 / mod 1.5)
 */
public final class MikasaTpMod implements ModInitializer {
	public static final String AUTHOR = "Mikasa";
	public static final String MOD_ID = "mikasa-tp-mod";
	public static final String MOD_FOLDER = "Mikasa-tp-mod";
	public static final String MOD_NAME = "Mikasa-tp-mod";
	public static final String VERSION = "fabric-26.3-1.5";
	public static final String ENGINE = "fabric";
	public static final String GAME_VERSION = "26.3";
	public static final String MOD_VERSION = "1.5";
	public static final String SOURCE_REPO = "https://github.com/Mikasa-not-available/Mikasa-tp-mod";
	public static final String LOG_PREFIX = "[MikasaTP]";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static LocalJsonConfig localConfig;
	private static ConfigDatabase configDatabase;
	private static Database database;
	private static PermissionRepository permissions;
	private static HomeRepository homes;
	private static SettingsRepository settings;
	private static TpaManager tpaManager;

	/** Console line: [MikasaTP] message */
	public static void log(String message) {
		LOGGER.info("{} {}", LOG_PREFIX, message);
	}

	@Override
	public void onInitialize() {
		log("starting " + MOD_NAME + " " + VERSION + " by " + AUTHOR);

		localConfig = new LocalJsonConfig();
		ReadmeWriter.write(localConfig.configDir());
		configDatabase = new ConfigDatabase(localConfig.configDir());
		database = Database.tryConnect(configDatabase);
		DatabaseConfigSync.maybeSync(database, localConfig);

		permissions = new PermissionRepository(database, localConfig);
		homes = new HomeRepository(database, localConfig);
		settings = new SettingsRepository(database, localConfig);
		tpaManager = new TpaManager();

		CommandRegistry registry = new CommandRegistry(permissions);
		registry.register(new TpHelpCommand());
		registry.register(new TpCommand());
		registry.register(new HomeSetCommand(homes, settings));
		registry.register(new HomeCommand(homes));
		registry.register(new HomeDelCommand(homes));
		registry.register(new HomesCommand(homes, settings));
		registry.register(new TpaCommand(tpaManager));
		registry.register(new TpAcceptCommand(tpaManager));
		registry.register(new TpDenyCommand(tpaManager));
		registry.register(new TpaCancelCommand(tpaManager));

		CommandRegistrationCallback.EVENT.register(registry::registerAll);
		ServerTickEvents.END_SERVER_TICK.register(tpaManager::tick);

		if (database != null) {
			String kind = database.isMysql() ? "mysql" : "postgres";
			log("launch config: database (" + kind + ")");
		} else {
			log("launch config: file");
		}
	}

	public static PermissionRepository permissions() {
		return permissions;
	}

	public static HomeRepository homes() {
		return homes;
	}

	public static SettingsRepository settings() {
		return settings;
	}

	public static LocalJsonConfig localConfig() {
		return localConfig;
	}

	public static ConfigDatabase configDatabase() {
		return configDatabase;
	}

	public static TpaManager tpa() {
		return tpaManager;
	}

	public static Database database() {
		return database;
	}
}
