package com.haha.chatcommands.db;

import com.haha.chatcommands.MikasaTpMod;
import com.haha.chatcommands.config.ConfigDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** Author: Mikasa - PostgreSQL and MySQL */
public final class Database implements AutoCloseable {
	private final HikariDataSource dataSource;
	private final ConfigDatabase.DbType type;

	private Database(HikariDataSource dataSource, ConfigDatabase.DbType type) {
		this.dataSource = dataSource;
		this.type = type;
	}

	public ConfigDatabase.DbType type() {
		return type;
	}

	public boolean isPostgres() {
		return type == ConfigDatabase.DbType.POSTGRES;
	}

	public boolean isMysql() {
		return type == ConfigDatabase.DbType.MYSQL;
	}

	public static Database tryConnect(ConfigDatabase config) {
		if (!config.isUsable()) {
			MikasaTpMod.log("database disabled or incomplete - skip connect");
			return null;
		}

		String kind = config.type() == ConfigDatabase.DbType.MYSQL ? "mysql" : "postgres";
		MikasaTpMod.log("try to connect to database (" + kind + ")");

		try {
			HikariConfig hikari = new HikariConfig();
			hikari.setJdbcUrl(config.jdbcUrl());
			hikari.setUsername(config.data().username);
			hikari.setPassword(config.data().password == null ? "" : config.data().password);
			hikari.setMaximumPoolSize(5);
			hikari.setPoolName(MikasaTpMod.MOD_FOLDER);
			hikari.setConnectionTimeout(3_000);
			hikari.setInitializationFailTimeout(3_000);
			hikari.addDataSourceProperty("ApplicationName", MikasaTpMod.MOD_ID);

			if (config.type() == ConfigDatabase.DbType.MYSQL) {
				hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
			}

			HikariDataSource dataSource = new HikariDataSource(hikari);
			Database database = new Database(dataSource, config.type());
			try (Connection connection = dataSource.getConnection()) {
				if (!connection.isValid(2)) {
					dataSource.close();
					MikasaTpMod.log("fail - connection invalid");
					return null;
				}
				database.ensureSchema(connection);
			}

			MikasaTpMod.log("success");
			return database;
		} catch (Exception e) {
			MikasaTpMod.log("fail - " + e.getMessage());
			return null;
		}
	}

	public Connection connection() throws SQLException {
		return dataSource.getConnection();
	}

	public void bindUuid(PreparedStatement statement, int index, UUID uuid) throws SQLException {
		if (isPostgres()) {
			statement.setObject(index, uuid);
		} else {
			statement.setString(index, uuid.toString());
		}
	}

	public String homeUpsertSql() {
		if (isPostgres()) {
			return """
					INSERT INTO homes (player_uuid, home_name, world, x, y, z, yaw, pitch)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT (player_uuid, home_name) DO UPDATE SET
						world = EXCLUDED.world,
						x = EXCLUDED.x,
						y = EXCLUDED.y,
						z = EXCLUDED.z,
						yaw = EXCLUDED.yaw,
						pitch = EXCLUDED.pitch
					""";
		}
		return """
				INSERT INTO homes (player_uuid, home_name, world, x, y, z, yaw, pitch)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE
					world = VALUES(world),
					x = VALUES(x),
					y = VALUES(y),
					z = VALUES(z),
					yaw = VALUES(yaw),
					pitch = VALUES(pitch)
				""";
	}

	private void ensureSchema(Connection connection) throws SQLException {
		try (Statement st = connection.createStatement()) {
			if (isPostgres()) {
				st.execute("""
						CREATE TABLE IF NOT EXISTS roles (
						    name TEXT PRIMARY KEY
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS commands (
						    name TEXT PRIMARY KEY
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS role_permissions (
						    role_name TEXT NOT NULL REFERENCES roles(name) ON DELETE CASCADE,
						    command_name TEXT NOT NULL REFERENCES commands(name) ON DELETE CASCADE,
						    allowed BOOLEAN NOT NULL DEFAULT FALSE,
						    PRIMARY KEY (role_name, command_name)
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS players (
						    uuid UUID PRIMARY KEY,
						    name TEXT,
						    role_name TEXT NOT NULL DEFAULT 'player' REFERENCES roles(name)
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS settings (
						    key TEXT PRIMARY KEY,
						    value TEXT NOT NULL
						)
						""");
				st.execute("""
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
						)
						""");
				st.execute("INSERT INTO roles (name) VALUES ('player') ON CONFLICT DO NOTHING");
				st.execute("INSERT INTO settings (key, value) VALUES ('max_homes', '5') ON CONFLICT DO NOTHING");
			} else {
				st.execute("""
						CREATE TABLE IF NOT EXISTS roles (
						    name VARCHAR(64) PRIMARY KEY
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS commands (
						    name VARCHAR(64) PRIMARY KEY
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS role_permissions (
						    role_name VARCHAR(64) NOT NULL,
						    command_name VARCHAR(64) NOT NULL,
						    allowed BOOLEAN NOT NULL DEFAULT FALSE,
						    PRIMARY KEY (role_name, command_name),
						    FOREIGN KEY (role_name) REFERENCES roles(name) ON DELETE CASCADE,
						    FOREIGN KEY (command_name) REFERENCES commands(name) ON DELETE CASCADE
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS players (
						    uuid CHAR(36) PRIMARY KEY,
						    name VARCHAR(64),
						    role_name VARCHAR(64) NOT NULL DEFAULT 'player',
						    FOREIGN KEY (role_name) REFERENCES roles(name)
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS settings (
						    `key` VARCHAR(64) PRIMARY KEY,
						    value VARCHAR(255) NOT NULL
						)
						""");
				st.execute("""
						CREATE TABLE IF NOT EXISTS homes (
						    player_uuid CHAR(36) NOT NULL,
						    home_name VARCHAR(64) NOT NULL,
						    world VARCHAR(128) NOT NULL,
						    x DOUBLE NOT NULL,
						    y DOUBLE NOT NULL,
						    z DOUBLE NOT NULL,
						    yaw FLOAT NOT NULL,
						    pitch FLOAT NOT NULL,
						    PRIMARY KEY (player_uuid, home_name)
						)
						""");
				st.execute("INSERT IGNORE INTO roles (name) VALUES ('player')");
				st.execute("INSERT IGNORE INTO settings (`key`, value) VALUES ('max_homes', '5')");
			}
		}
	}

	@Override
	public void close() {
		dataSource.close();
	}
}
