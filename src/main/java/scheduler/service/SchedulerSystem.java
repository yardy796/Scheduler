package scheduler.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import scheduler.Role;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.model.TimeSlot;
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
		ensureNoConflict(room, start, end, null);
		Booking booking = createBookingInternal(
			room,
			start,
			end,
			actingUser.getUsername()
		);
		bookings.add(booking);
		persist();
		return booking;
	}

	public List<Booking> createBookings(
		User actingUser,
		String roomName,
		List<TimeSlot> slots
	) {
		requireBookingCreationPermission(actingUser);
		Objects.requireNonNull(slots, "slots");
		if (slots.isEmpty()) {
			throw new IllegalArgumentException("At least one time slot is required");
		}
		Room room = getRoomByName(roomName);
		List<Booking> newBookings = new ArrayList<>();
		for (TimeSlot slot : slots) {
			ensureNoConflict(room, slot.start(), slot.end(), null);
			newBookings.add(
				createBookingInternal(
					room,
					slot.start(),
					slot.end(),
					actingUser.getUsername()
				)
			);
		}
		bookings.addAll(newBookings);
		persist();
		return List.copyOf(newBookings);
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
		ensureNoConflict(booking.getRoom(), start, end, booking.getId());
		booking.setStart(start);
		booking.setEnd(end);
		persist();
		return booking;
	}

	public List<TimeSlot> generateRecurringSlots(
		LocalDate startDate,
		LocalDate endDate,
		LocalTime startTime,
		LocalTime endTime,
		Set<DayOfWeek> daysOfWeek
	) {
		Objects.requireNonNull(startDate, "startDate");
		Objects.requireNonNull(endDate, "endDate");
		Objects.requireNonNull(startTime, "startTime");
		Objects.requireNonNull(endTime, "endTime");
		Objects.requireNonNull(daysOfWeek, "daysOfWeek");
		if (!startTime.isBefore(endTime)) {
			throw new IllegalArgumentException("Start time must be before end time");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("End date cannot be before start date");
		}
		if (daysOfWeek.isEmpty()) {
			throw new IllegalArgumentException("At least one day of week must be selected");
		}
		List<TimeSlot> slots = new ArrayList<>();
		LocalDate cursor = startDate;
		while (!cursor.isAfter(endDate)) {
			if (daysOfWeek.contains(cursor.getDayOfWeek())) {
				LocalDateTime slotStart = cursor.atTime(startTime);
				LocalDateTime slotEnd = cursor.atTime(endTime);
				slots.add(new TimeSlot(slotStart, slotEnd));
			}
			cursor = cursor.plusDays(1);
		}
		if (slots.isEmpty()) {
			throw new IllegalArgumentException(
				"No slots generated; check selected days and date range"
			);
		}
		return Collections.unmodifiableList(slots);
	}

	public List<Booking> findConflicts(
		String roomName,
		List<TimeSlot> slots,
		UUID excludeId
	) {
		Objects.requireNonNull(slots, "slots");
		Room room = getRoomByName(roomName);
		Set<Booking> conflicts = new LinkedHashSet<>();
		for (TimeSlot slot : slots) {
			conflicts.addAll(
				ConflictChecker.findConflicts(
					bookings,
					room,
					slot.start(),
					slot.end(),
					excludeId
				)
			);
		}
		return List.copyOf(conflicts);
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
		if (findUser(username).isPresent()) {
			throw new IllegalArgumentException(
				"Username already exists: " + username
			);
		}
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

	private void ensureNoConflict(
		Room room,
		LocalDateTime start,
		LocalDateTime end,
		UUID excludeId
	) {
		ConflictChecker
			.findConflict(bookings, room, start, end, excludeId)
			.ifPresent(conflict -> {
				throw new IllegalStateException(
					"Requested slot conflicts with booking " + conflict.getId()
				);
			});
	}

	private Booking createBookingInternal(
		Room room,
		LocalDateTime start,
		LocalDateTime end,
		String username
	) {
		return new Booking(room, start, end, username);
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
