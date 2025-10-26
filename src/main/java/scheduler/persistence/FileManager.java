package scheduler.persistence;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import scheduler.Role;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.user.Admin;
import scheduler.user.Guest;
import scheduler.user.RegularUser;
import scheduler.user.Scheduler;
import scheduler.user.User;

/**
 * Persists users, rooms, and bookings using an embedded SQLite database.
 */
public final class FileManager {

	private static final String DATABASE_NAME = "scheduler.db";

	private final Path dataDirectory;
	private final Path databaseFile;
	private final String jdbcUrl;

	public FileManager() {
		this(Paths.get("data"));
	}

	public FileManager(Path dataDirectory) {
		this.dataDirectory = dataDirectory;
		ensureDirectory();
		this.databaseFile = dataDirectory.resolve(DATABASE_NAME);
		this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
		initializeSchema();
	}

	public List<User> loadUsers() {
		List<User> results = new ArrayList<>();
		String sql = "SELECT username, password, role FROM users ORDER BY username";
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			enableForeignKeys(conn);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String username = rs.getString("username");
					String password = rs.getString("password");
					Role role = Role.valueOf(rs.getString("role"));
					results.add(instantiateUser(role, username, password));
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to load users from database", e);
		}
		return results;
	}

	public List<Room> loadRooms() {
		List<Room> results = new ArrayList<>();
		String sql = "SELECT name, capacity, description FROM rooms ORDER BY name";
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			enableForeignKeys(conn);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString("name");
					int capacity = rs.getInt("capacity");
					String description = rs.getString("description");
					results.add(new Room(name, capacity, description));
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to load rooms from database", e);
		}
		return results;
	}

	public List<Booking> loadBookings() {
		String sql =
			"SELECT b.id, b.room_name, b.start, b.end, b.booked_by, " +
			"r.capacity, r.description " +
			"FROM bookings b " +
			"JOIN rooms r ON r.name = b.room_name " +
			"ORDER BY b.start";
		Map<String, Room> roomCache = new HashMap<>();
		List<Booking> results = new ArrayList<>();
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			enableForeignKeys(conn);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String roomName = rs.getString("room_name");
					int capacity = rs.getInt("capacity");
					String description = rs.getString("description");
					String cacheKey = roomName.toLowerCase(Locale.ROOT);
					Room room = roomCache.get(cacheKey);
					if (room == null) {
						room = new Room(roomName, capacity, description);
						roomCache.put(cacheKey, room);
					}
					LocalDateTime start = LocalDateTime.parse(rs.getString("start"));
					LocalDateTime end = LocalDateTime.parse(rs.getString("end"));
					String bookedBy = rs.getString("booked_by");
					results.add(
						new Booking(
							java.util.UUID.fromString(rs.getString("id")),
							room,
							start,
							end,
							bookedBy
						)
					);
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to load bookings from database", e);
		}
		return results;
	}

	public void saveUsers(List<User> users) {
		persistAll(users, loadRooms(), loadBookings());
	}

	public void saveRooms(List<Room> rooms) {
		persistAll(loadUsers(), rooms, loadBookings());
	}

	public void saveBookings(List<Booking> bookings) {
		persistAll(loadUsers(), loadRooms(), bookings);
	}

	public void persistAll(
		List<User> users,
		List<Room> rooms,
		List<Booking> bookings
	) {
		Objects.requireNonNull(users, "users");
		Objects.requireNonNull(rooms, "rooms");
		Objects.requireNonNull(bookings, "bookings");
		try (Connection conn = getConnection()) {
			enableForeignKeys(conn);
			conn.setAutoCommit(false);
			try {
				clearTable(conn, "bookings");
				clearTable(conn, "rooms");
				clearTable(conn, "users");
				insertUsers(conn, users);
				insertRooms(conn, rooms);
				insertBookings(conn, bookings);
				conn.commit();
			} catch (SQLException e) {
				rollbackQuietly(conn);
				throw e;
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to persist scheduler data", e);
		}
	}

	private Connection getConnection() throws SQLException {
		try {
			return DriverManager.getConnection(jdbcUrl);
		} catch (SQLException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("No suitable driver")) {
				throw new IllegalStateException(
					"SQLite JDBC driver not found. Add sqlite-jdbc to the classpath.",
					ex
				);
			}
			throw ex;
		}
	}

	private void initializeSchema() {
		try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
			enableForeignKeys(conn);
			stmt.executeUpdate(
				"CREATE TABLE IF NOT EXISTS users (" +
				"username TEXT PRIMARY KEY," +
				"password TEXT NOT NULL," +
				"role TEXT NOT NULL" +
				")"
			);
			stmt.executeUpdate(
				"CREATE TABLE IF NOT EXISTS rooms (" +
				"name TEXT PRIMARY KEY," +
				"capacity INTEGER NOT NULL," +
				"description TEXT NOT NULL" +
				")"
			);
			stmt.executeUpdate(
				"CREATE TABLE IF NOT EXISTS bookings (" +
				"id TEXT PRIMARY KEY," +
				"room_name TEXT NOT NULL," +
				"start TEXT NOT NULL," +
				"end TEXT NOT NULL," +
				"booked_by TEXT NOT NULL," +
				"FOREIGN KEY(room_name) REFERENCES rooms(name) ON DELETE CASCADE," +
				"FOREIGN KEY(booked_by) REFERENCES users(username) ON DELETE CASCADE" +
				")"
			);
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to initialize database schema", e);
		}
	}

	private void ensureDirectory() {
		try {
			Files.createDirectories(dataDirectory);
		} catch (IOException e) {
			throw new IllegalStateException(
				"Unable to create data directory " + dataDirectory,
				e
			);
		}
	}

	private void enableForeignKeys(Connection conn) throws SQLException {
		try (Statement pragma = conn.createStatement()) {
			pragma.execute("PRAGMA foreign_keys = ON");
		}
	}

	private void clearTable(Connection conn, String table) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.executeUpdate("DELETE FROM " + table);
		}
	}

	private void rollbackQuietly(Connection conn) {
		if (conn == null) {
			return;
		}
		try {
			conn.rollback();
		} catch (SQLException ignored) {
			// ignore rollback failures
		}
	}

	private void insertUsers(Connection conn, List<User> users) throws SQLException {
		String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (User user : users) {
				ps.setString(1, user.getUsername());
				ps.setString(2, extractPassword(user));
				ps.setString(3, user.getRole().name());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void insertRooms(Connection conn, List<Room> rooms) throws SQLException {
		String sql = "INSERT INTO rooms (name, capacity, description) VALUES (?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (Room room : rooms) {
				ps.setString(1, room.getName());
				ps.setInt(2, room.getCapacity());
				ps.setString(3, room.getDescription());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void insertBookings(Connection conn, List<Booking> bookings) throws SQLException {
		String sql =
			"INSERT INTO bookings (id, room_name, start, end, booked_by) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (Booking booking : bookings) {
				ps.setString(1, booking.getId().toString());
				ps.setString(2, booking.getRoom().getName());
				ps.setString(3, booking.getStart().toString());
				ps.setString(4, booking.getEnd().toString());
				ps.setString(5, booking.getBookedBy());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private User instantiateUser(Role role, String username, String password) {
		return switch (role) {
			case ADMIN -> new Admin(username, password);
			case SCHEDULER -> new Scheduler(username, password);
			case USER -> new RegularUser(username, password);
			case GUEST -> new Guest(username, password);
		};
	}

	private String extractPassword(User user) {
		try {
			Field field = User.class.getDeclaredField("password");
			field.setAccessible(true);
			return (String) field.get(user);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to access user password for persistence", e);
		}
	}
}
