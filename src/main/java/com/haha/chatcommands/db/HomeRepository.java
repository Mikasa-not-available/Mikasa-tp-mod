package com.haha.chatcommands.db;

import com.haha.chatcommands.MikasaTpMod;
import com.haha.chatcommands.config.LocalJsonConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Author: Mikasa */
public final class HomeRepository {
	private final Database database;
	private final LocalJsonConfig local;

	public HomeRepository(Database database, LocalJsonConfig local) {
		this.database = database;
		this.local = local;
	}

	public int countHomes(UUID playerUuid) {
		if (database != null) {
			String sql = "SELECT COUNT(*) FROM homes WHERE player_uuid = ?";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				database.bindUuid(statement, 1, playerUuid);
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB count homes failed, using JSON", e);
			}
		}
		return local.countHomes(playerUuid);
	}

	public List<Home> listByPlayer(UUID playerUuid) {
		if (database != null) {
			String sql = """
					SELECT player_uuid, home_name, world, x, y, z, yaw, pitch
					FROM homes
					WHERE player_uuid = ?
					ORDER BY home_name
					""";
			List<Home> result = new ArrayList<>();
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				database.bindUuid(statement, 1, playerUuid);
				try (ResultSet rs = statement.executeQuery()) {
					while (rs.next()) {
						result.add(readHome(rs));
					}
					return result;
				}
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB list homes failed, using JSON", e);
			}
		}
		return local.listHomes(playerUuid);
	}

	public boolean exists(UUID playerUuid, String homeName) {
		if (database != null) {
			String sql = "SELECT 1 FROM homes WHERE player_uuid = ? AND home_name = ?";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				database.bindUuid(statement, 1, playerUuid);
				statement.setString(2, homeName);
				try (ResultSet rs = statement.executeQuery()) {
					return rs.next();
				}
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB home exists failed, using JSON", e);
			}
		}
		return local.homeExists(playerUuid, homeName);
	}

	public Optional<Home> find(UUID playerUuid, String homeName) {
		if (database != null) {
			String sql = """
					SELECT player_uuid, home_name, world, x, y, z, yaw, pitch
					FROM homes
					WHERE player_uuid = ? AND home_name = ?
					""";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				database.bindUuid(statement, 1, playerUuid);
				statement.setString(2, homeName);
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						return Optional.of(readHome(rs));
					}
					return Optional.empty();
				}
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB find home failed, using JSON", e);
			}
		}
		return local.findHome(playerUuid, homeName);
	}

	public boolean upsert(Home home) {
		if (database != null) {
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(database.homeUpsertSql())) {
				database.bindUuid(statement, 1, home.playerUuid());
				statement.setString(2, home.name());
				statement.setString(3, home.world());
				statement.setDouble(4, home.x());
				statement.setDouble(5, home.y());
				statement.setDouble(6, home.z());
				statement.setFloat(7, home.yaw());
				statement.setFloat(8, home.pitch());
				statement.executeUpdate();
				return true;
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB save home failed, writing JSON", e);
			}
		}
		local.upsertHome(home);
		return true;
	}

	public boolean delete(UUID playerUuid, String homeName) {
		if (database != null) {
			String sql = "DELETE FROM homes WHERE player_uuid = ? AND home_name = ?";
			try (Connection connection = database.connection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				database.bindUuid(statement, 1, playerUuid);
				statement.setString(2, homeName);
				return statement.executeUpdate() > 0;
			} catch (SQLException e) {
				MikasaTpMod.LOGGER.error("DB delete home failed, using JSON", e);
			}
		}
		return local.deleteHome(playerUuid, homeName);
	}

	private Home readHome(ResultSet rs) throws SQLException {
		UUID uuid;
		Object raw = rs.getObject("player_uuid");
		if (raw instanceof UUID u) {
			uuid = u;
		} else {
			uuid = UUID.fromString(String.valueOf(raw));
		}
		return new Home(
				uuid,
				rs.getString("home_name"),
				rs.getString("world"),
				rs.getDouble("x"),
				rs.getDouble("y"),
				rs.getDouble("z"),
				rs.getFloat("yaw"),
				rs.getFloat("pitch")
		);
	}
}
