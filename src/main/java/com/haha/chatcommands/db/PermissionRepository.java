package com.haha.chatcommands.db;

import com.haha.chatcommands.MikasaTpMod;
import com.haha.chatcommands.config.LocalJsonConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Author: Mikasa */
public final class PermissionRepository {
	public static final String DEFAULT_ROLE = "player";

	private final Database database;
	private final LocalJsonConfig local;

	public PermissionRepository(Database database, LocalJsonConfig local) {
		this.database = database;
		this.local = local;
	}

	public String findRoleName(UUID playerUuid) {
		if (database != null) {
			String sql = "SELECT role_name FROM players WHERE uuid = ?";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				database.bindUuid(statement, 1, playerUuid);
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						String role = rs.getString("role_name");
						return role == null || role.isBlank() ? DEFAULT_ROLE : role;
					}
				}
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB role lookup failed, falling back to JSON", e);
			}
		}
		return local.findRole(playerUuid);
	}

	public boolean hasPermission(UUID playerUuid, String commandName) {
		if (database != null) {
			String roleName = findRoleName(playerUuid);
			String sql = """
					SELECT allowed
					FROM role_permissions
					WHERE role_name = ? AND command_name = ?
					""";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, roleName);
				statement.setString(2, commandName);
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						return rs.getBoolean("allowed");
					}
				}
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB permission check failed, falling back to JSON", e);
			}
		}
		return local.hasPermission(playerUuid, commandName);
	}
}
