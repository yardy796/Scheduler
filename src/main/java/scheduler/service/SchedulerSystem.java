package scheduler.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import scheduler.Role;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.persistence.FileManager;
import scheduler.user.Admin;
import scheduler.user.Guest;
import scheduler.user.RegularUser;
import scheduler.user.Scheduler;
import scheduler.user.User;
import scheduler.util.ConflictChecker;

/**
 * Coordinates operations on users, rooms, and bookings while enforcing permissions.
 */
public final class SchedulerSystem {

	private final FileManager fileManager;
	private final List<User> users;
	private final List<Room> rooms;
	private final List<Booking> bookings;

	public SchedulerSystem(FileManager fileManager) {
		this.fileManager = fileManager;
		this.users = new ArrayList<>(fileManager.loadUsers());
		this.rooms = new ArrayList<>(fileManager.loadRooms());
		this.bookings = new ArrayList<>(fileManager.loadBookings());
		ensureDefaultAdmin();
	}

	public Optional<User> authenticate(String username, String password) {
		return users
			.stream()
			.filter(user -> user.getUsername().equalsIgnoreCase(username))
			.filter(user -> user.verifyPassword(password))
			.findFirst();
	}

	public User createUser(
		User actingUser,
		String username,
		String password,
		Role role
	) {
		requirePermission(actingUser, Role::canManageUsers, "create users");
		ensureUniqueUsername(username);
		User newcomer = instantiateUser(role, username, password);
		users.add(newcomer);
		persist();
		return newcomer;
	}

	public void deleteUser(User actingUser, String username) {
		requirePermission(actingUser, Role::canManageUsers, "delete users");
		User target = findUser(username).orElseThrow(() ->
			new IllegalArgumentException("User not found: " + username)
		);
		if (target.getRole() == Role.ADMIN && countAdmins() == 1) {
			throw new IllegalStateException(
				"Cannot remove the last admin user"
			);
		}
		users.remove(target);
		persist();
	}

	public List<User> listUsers(User actingUser) {
		requirePermission(
			actingUser,
			role -> role.canManageUsers() || role == Role.SCHEDULER,
			"view users"
		);
		return Collections.unmodifiableList(users);
	}

	public Room createRoom(
		User actingUser,
		String name,
		int capacity,
		String description
	) {
		requirePermission(actingUser, Role::canManageRooms, "create rooms");
		ensureRoomNameUnique(name);
		Room room = new Room(name, capacity, description);
		rooms.add(room);
		persist();
		return room;
	}

	public Room updateRoom(
		User actingUser,
		String name,
		int capacity,
		String description
	) {
		requirePermission(actingUser, Role::canManageRooms, "update rooms");
		Room room = getRoomByName(name);
		room.setCapacity(capacity);
		room.setDescription(description);
		persist();
		return room;
	}

	public void deleteRoom(User actingUser, String name) {
		requirePermission(actingUser, Role::canManageRooms, "delete rooms");
		Room room = getRoomByName(name);
		boolean inUse = bookings
			.stream()
			.anyMatch(booking ->
				booking.getRoom().getName().equalsIgnoreCase(name)
			);
		if (inUse) {
			throw new IllegalStateException("Cannot delete room with bookings");
		}
		rooms.remove(room);
		persist();
	}

	public List<Room> listRooms() {
		return Collections.unmodifiableList(rooms);
	}

	public Booking createBooking(
		User actingUser,
		String roomName,
		LocalDateTime start,
		LocalDateTime end
	) {
		requireBookingCreationPermission(actingUser);
		Room room = getRoomByName(roomName);
		ConflictChecker.findConflict(
			bookings,
			room,
			start,
			end,
			null
		).ifPresent(conflict -> {
			throw new IllegalStateException(
				"Requested slot conflicts with booking " + conflict.getId()
			);
		});
		Booking booking = new Booking(
			room,
			start,
			end,
			actingUser.getUsername()
		);
		bookings.add(booking);
		persist();
		return booking;
	}

	public List<Booking> listBookings(User actingUser) {
		if (
			actingUser.getRole().canManageAllBookings() ||
			actingUser.getRole() == Role.GUEST
		) {
			return Collections.unmodifiableList(bookings);
		}
		return Collections.unmodifiableList(
			bookings
				.stream()
				.filter(booking -> booking.isOwnedBy(actingUser.getUsername()))
				.toList()
		);
	}

	public void cancelBooking(User actingUser, UUID bookingId) {
		Booking booking = findBooking(bookingId);
		ensureBookingAccess(actingUser, booking);
		bookings.remove(booking);
		persist();
	}

	public Booking updateBooking(
		User actingUser,
		UUID bookingId,
		LocalDateTime start,
		LocalDateTime end
	) {
		Booking booking = findBooking(bookingId);
		ensureBookingAccess(actingUser, booking);
		ConflictChecker.findConflict(
			bookings,
			booking.getRoom(),
			start,
			end,
			booking.getId()
		).ifPresent(conflict -> {
			throw new IllegalStateException(
				"Requested slot conflicts with booking " + conflict.getId()
			);
		});
		booking.setStart(start);
		booking.setEnd(end);
		persist();
		return booking;
	}

	private void ensureDefaultAdmin() {
		boolean hasAdmin = users
			.stream()
			.anyMatch(user -> user.getRole() == Role.ADMIN);
		if (!hasAdmin) {
			users.add(new Admin("admin", "admin"));
			persist();
		}
	}

	private void ensureUniqueUsername(String username) {
		findUser(username).ifPresent(user -> {
			throw new IllegalArgumentException(
				"Username already exists: " + username
			);
		});
	}

	private void ensureRoomNameUnique(String name) {
		boolean exists = rooms
			.stream()
			.anyMatch(room -> room.getName().equalsIgnoreCase(name));
		if (exists) {
			throw new IllegalArgumentException("Room already exists: " + name);
		}
	}

	private Room getRoomByName(String name) {
		return rooms
			.stream()
			.filter(room -> room.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() ->
				new IllegalArgumentException("Room not found: " + name)
			);
	}

	private Booking findBooking(UUID id) {
		return bookings
			.stream()
			.filter(b -> b.getId().equals(id))
			.findFirst()
			.orElseThrow(() ->
				new IllegalArgumentException("Booking not found: " + id)
			);
	}

	private Optional<User> findUser(String username) {
		return users
			.stream()
			.filter(user -> user.getUsername().equalsIgnoreCase(username))
			.findFirst();
	}

	private int countAdmins() {
		return (int) users
			.stream()
			.filter(user -> user.getRole() == Role.ADMIN)
			.count();
	}

	private void persist() {
		fileManager.persistAll(users, rooms, bookings);
	}

	private void requirePermission(
		User actingUser,
		java.util.function.Predicate<Role> predicate,
		String action
	) {
		if (actingUser == null || !predicate.test(actingUser.getRole())) {
			throw new SecurityException(
				"Insufficient permissions to " + action
			);
		}
	}

	private void requireBookingCreationPermission(User actingUser) {
		if (actingUser == null) {
			throw new SecurityException("User must be logged in");
		}
		Role role = actingUser.getRole();
		if (role == Role.GUEST) {
			throw new SecurityException("Guests cannot create bookings");
		}
	}

	private void ensureBookingAccess(User actingUser, Booking booking) {
		if (actingUser.getRole().canManageAllBookings()) {
			return;
		}
		if (!booking.isOwnedBy(actingUser.getUsername())) {
			throw new SecurityException(
				"Cannot modify bookings for other users"
			);
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
}
