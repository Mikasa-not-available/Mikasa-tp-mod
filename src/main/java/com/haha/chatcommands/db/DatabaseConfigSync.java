package com.haha.chatcommands.db;

import com.haha.chatcommands.MikasaTpMod;
import com.haha.chatcommands.config.LocalJsonConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies {@code config.json} roles/settings into the SQL database when
 * {@code sync_to_database} is true. Replaces role/permission/settings rows.
 * Author: Mikasa
 */
public final class DatabaseConfigSync {
	private record Pair(String role, String command) {
	}

	private DatabaseConfigSync() {
	}

	public static void maybeSync(Database database, LocalJsonConfig local) {
		if (!local.data().sync_to_database) {
			return;
		}

		if (database == null) {
			MikasaTpMod.log("sync_to_database=true but no database - skip (flag kept)");
			return;
		}

		MikasaTpMod.log("sync_to_database=true - applying config.json to database");
		MikasaTpMod.log("WARNING: roles, permissions and settings in DB will be replaced");

		try {
			apply(database, local);
			local.data().sync_to_database = false;
			local.save();
			MikasaTpMod.log("database sync complete - sync_to_database set to false");
		} catch (Exception e) {
			MikasaTpMod.log("database sync fail - " + e.getMessage());
			MikasaTpMod.LOGGER.error("Config->DB sync failed", e);
		}
	}

	private static void apply(Database database, LocalJsonConfig local) throws SQLException {
		LocalJsonConfig.Data data = local.data();
		Set<String> desiredRoles = new HashSet<>(data.roles.keySet());
		Set<String> desiredCommands = new HashSet<>();
		Set<Pair> desiredPerms = new HashSet<>();

		for (Map.Entry<String, LocalJsonConfig.RoleEntry> entry : data.roles.entrySet()) {
			String roleName = entry.getKey();
			Map<String, Boolean> commands = entry.getValue().commands;
			if (commands == null) {
				continue;
			}
			for (Map.Entry<String, Boolean> cmd : commands.entrySet()) {
				desiredCommands.add(cmd.getKey());
				desiredPerms.add(new Pair(roleName, cmd.getKey()));
			}
		}

		try (Connection connection = database.connection()) {
			connection.setAutoCommit(false);
			try {
				upsertRole(connection, database, "player");

				for (String role : desiredRoles) {
					upsertRole(connection, database, role);
				}
				for (String command : desiredCommands) {
					upsertCommand(connection, database, command);
				}

				for (Map.Entry<String, LocalJsonConfig.RoleEntry> entry : data.roles.entrySet()) {
					String roleName = entry.getKey();
					Map<String, Boolean> commands = entry.getValue().commands;
					if (commands == null) {
						continue;
					}
					for (Map.Entry<String, Boolean> cmd : commands.entrySet()) {
						upsertPermission(connection, database, roleName, cmd.getKey(), Boolean.TRUE.equals(cmd.getValue()));
					}
				}

				prunePermissions(connection, desiredPerms);
				pruneCommands(connection, desiredCommands);
				pruneRoles(connection, desiredRoles);

				for (Map.Entry<String, Object> setting : data.settings.entrySet()) {
					upsertSetting(connection, database, setting.getKey(), String.valueOf(setting.getValue()));
				}

				connection.commit();
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			} finally {
				connection.setAutoCommit(true);
			}
		}
	}

	private static void upsertRole(Connection connection, Database database, String name) throws SQLException {
		String sql = database.isMysql()
				? "INSERT IGNORE INTO roles (name) VALUES (?)"
				: "INSERT INTO roles (name) VALUES (?) ON CONFLICT (name) DO NOTHING";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setString(1, name);
			ps.executeUpdate();
		}
	}

	private static void upsertCommand(Connection connection, Database database, String name) throws SQLException {
		String sql = database.isMysql()
				? "INSERT IGNORE INTO commands (name) VALUES (?)"
				: "INSERT INTO commands (name) VALUES (?) ON CONFLICT (name) DO NOTHING";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setString(1, name);
			ps.executeUpdate();
		}
	}

	private static void upsertPermission(
			Connection connection,
			Database database,
			String role,
			String command,
			boolean allowed
	) throws SQLException {
		String sql = database.isMysql()
				? """
					INSERT INTO role_permissions (role_name, command_name, allowed)
					VALUES (?, ?, ?)
					ON DUPLICATE KEY UPDATE allowed = VALUES(allowed)
					"""
				: """
					INSERT INTO role_permissions (role_name, command_name, allowed)
					VALUES (?, ?, ?)
					ON CONFLICT (role_name, command_name) DO UPDATE SET allowed = EXCLUDED.allowed
					""";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setString(1, role);
			ps.setString(2, command);
			ps.setBoolean(3, allowed);
			ps.executeUpdate();
		}
	}

	private static void upsertSetting(Connection connection, Database database, String key, String value) throws SQLException {
		String sql = database.isMysql()
				? """
					INSERT INTO settings (`key`, value) VALUES (?, ?)
					ON DUPLICATE KEY UPDATE value = VALUES(value)
					"""
				: """
					INSERT INTO settings (key, value) VALUES (?, ?)
					ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
					""";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setString(1, key);
			ps.setString(2, value);
			ps.executeUpdate();
		}
	}

	private static void prunePermissions(Connection connection, Set<Pair> desired) throws SQLException {
		List<Pair> stale = new ArrayList<>();
		try (Statement st = connection.createStatement();
			 ResultSet rs = st.executeQuery("SELECT role_name, command_name FROM role_permissions")) {
			while (rs.next()) {
				Pair pair = new Pair(rs.getString(1), rs.getString(2));
				if (!desired.contains(pair)) {
					stale.add(pair);
				}
			}
		}
		for (Pair pair : stale) {
			try (PreparedStatement del = connection.prepareStatement(
					"DELETE FROM role_permissions WHERE role_name = ? AND command_name = ?")) {
				del.setString(1, pair.role());
				del.setString(2, pair.command());
				del.executeUpdate();
			}
		}
	}

	private static void pruneCommands(Connection connection, Set<String> desired) throws SQLException {
		List<String> stale = new ArrayList<>();
		try (Statement st = connection.createStatement();
			 ResultSet rs = st.executeQuery("SELECT name FROM commands")) {
			while (rs.next()) {
				String name = rs.getString(1);
				if (!desired.contains(name)) {
					stale.add(name);
				}
			}
		}
		for (String name : stale) {
			try (PreparedStatement del = connection.prepareStatement("DELETE FROM commands WHERE name = ?")) {
				del.setString(1, name);
				del.executeUpdate();
			}
		}
	}

	private static void pruneRoles(Connection connection, Set<String> desired) throws SQLException {
		List<String> stale = new ArrayList<>();
		try (Statement st = connection.createStatement();
			 ResultSet rs = st.executeQuery("SELECT name FROM roles")) {
			while (rs.next()) {
				String name = rs.getString(1);
				if (!desired.contains(name) && !"player".equals(name)) {
					stale.add(name);
				}
			}
		}
		for (String name : stale) {
			try (PreparedStatement move = connection.prepareStatement(
					"UPDATE players SET role_name = 'player' WHERE role_name = ?")) {
				move.setString(1, name);
				move.executeUpdate();
			}
			try (PreparedStatement del = connection.prepareStatement("DELETE FROM roles WHERE name = ?")) {
				del.setString(1, name);
				del.executeUpdate();
			}
		}
	}
}
