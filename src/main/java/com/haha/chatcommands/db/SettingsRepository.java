package com.haha.chatcommands.db;

import com.haha.chatcommands.MikasaTpMod;
import com.haha.chatcommands.config.LocalJsonConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Author: Mikasa */
public final class SettingsRepository {
	public static final String MAX_HOMES = "max_homes";
	public static final int DEFAULT_MAX_HOMES = 5;

	private final Database database;
	private final LocalJsonConfig local;

	public SettingsRepository(Database database, LocalJsonConfig local) {
		this.database = database;
		this.local = local;
	}

	public int getInt(String key, int defaultValue) {
		if (database != null) {
			String sql = database.isMysql()
					? "SELECT value FROM settings WHERE `key` = ?"
					: "SELECT value FROM settings WHERE key = ?";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, key);
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						return Integer.parseInt(rs.getString("value").trim());
					}
				}
			} catch (SQLException | NumberFormatException e) {
				MikasaTpMod.LOGGER.error("DB settings read failed, using JSON", e);
			}
		}
		if (MAX_HOMES.equals(key)) {
			return local.maxHomes();
		}
		return defaultValue;
	}

	public int maxHomes() {
		return Math.max(0, getInt(MAX_HOMES, DEFAULT_MAX_HOMES));
	}
}
