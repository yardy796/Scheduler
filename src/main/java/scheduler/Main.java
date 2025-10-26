package scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.model.TimeSlot;
import scheduler.persistence.FileManager;
import scheduler.service.SchedulerSystem;
import scheduler.user.User;

/**
 * Entry point for the Scheduler application with a basic console UI.
 */
public final class Main {

	private static final DateTimeFormatter DATE_TIME_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter TIME_FORMAT =
		DateTimeFormatter.ofPattern("HH:mm");
	private static final int DEFAULT_RECURRING_WEEKS = 26;

	private final SchedulerSystem schedulerSystem;
	private final Scanner scanner;
	private boolean running = true;

	public Main() {
		this.schedulerSystem = new SchedulerSystem(new FileManager());
		this.scanner = new Scanner(System.in);
	}

	public static void main(String[] args) {
		new Main().run();
	}

	private void run() {
		System.out.println("Welcome to the Scheduler System");
		while (running) {
			User user = promptLogin();
			if (user == null) {
				break;
			}
			sessionLoop(user);
		}
		System.out.println("Goodbye.");
	}

	private User promptLogin() {
		while (running) {
			System.out.print("Username (or 'exit'): ");
			String username = scanner.nextLine().trim();
			if (username.equalsIgnoreCase("exit")) {
				running = false;
				return null;
			}
			System.out.print("Password: ");
			String password = scanner.nextLine().trim();
			return schedulerSystem
				.authenticate(username, password)
				.orElseGet(() -> {
					System.out.println("Invalid credentials. Try again.");
					return promptLogin();
				});
		}
		return null;
	}

	private void sessionLoop(User user) {
		boolean loggedIn = true;
		while (loggedIn && running) {
			printMenu(user);
			System.out.print("Select option: ");
			String choice = scanner.nextLine().trim();
			try {
				switch (choice) {
					case "1" -> viewRooms();
					case "2" -> viewBookings(user);
					case "3" -> createBooking(user);
					case "4" -> updateBooking(user);
					case "5" -> cancelBooking(user);
					case "6" -> manageRooms(user);
					case "7" -> manageUsers(user);
					case "0" -> loggedIn = false;
					case "9" -> {
						running = false;
						loggedIn = false;
					}
					default -> System.out.println("Unknown option.");
				}
			} catch (
				SecurityException
				| IllegalArgumentException
				| IllegalStateException ex
			) {
				System.out.println("Error: " + ex.getMessage());
			}
		}
	}

	private void printMenu(User user) {
		System.out.println();
		System.out.println("---- Menu (" + user.getRole() + ") ----");
		System.out.println("1. View rooms");
		System.out.println("2. View bookings");
		if (user.getRole() != Role.GUEST) {
			System.out.println("3. Create booking");
			System.out.println("4. Update booking");
			System.out.println("5. Cancel booking");
		} else {
			System.out.println("3. Create booking (not permitted)");
			System.out.println("4. Update booking (not permitted)");
			System.out.println("5. Cancel booking (not permitted)");
		}
		if (user.getRole().canManageRooms()) {
			System.out.println("6. Manage rooms");
		} else {
			System.out.println("6. Manage rooms (not permitted)");
		}
		if (user.getRole().canManageUsers()) {
			System.out.println("7. Manage users");
		} else {
			System.out.println("7. Manage users (not permitted)");
		}
		System.out.println("0. Logout");
		System.out.println("9. Exit application");
	}

	private void viewRooms() {
		List<Room> rooms = schedulerSystem.listRooms();
		if (rooms.isEmpty()) {
			System.out.println("No rooms configured.");
			return;
		}
		rooms.forEach(room ->
			System.out.println(
				"- " +
				room.getName() +
				" | capacity=" +
				room.getCapacity() +
				(room.getDescription().isBlank()
						? ""
						: " | " + room.getDescription())
			)
		);
	}

	private void viewBookings(User user) {
		List<Booking> bookings = schedulerSystem.listBookings(user);
		if (bookings.isEmpty()) {
			System.out.println("No bookings available.");
			return;
		}
		bookings.forEach(this::printBookingSummary);
	}

	private void createBooking(User user) {
		if (user.getRole() == Role.GUEST) {
			throw new SecurityException("Guests cannot create bookings");
		}
		System.out.print("Room name: ");
		String room = scanner.nextLine().trim();
		boolean recurring = promptYesNo("Make this booking recurring? (y/n): ");
		List<TimeSlot> slots;
		if (recurring) {
			slots = collectRecurringSlots();
		} else {
			LocalDateTime start = promptDateTime("Start (yyyy-MM-dd HH:mm): ");
			LocalDateTime end = promptDateTime("End (yyyy-MM-dd HH:mm): ");
			slots = List.of(new TimeSlot(start, end));
		}
		attemptCreateBookings(user, room, slots);
	}

	private void updateBooking(User user) {
		if (user.getRole() == Role.GUEST) {
			throw new SecurityException("Guests cannot update bookings");
		}
		System.out.print("Booking id: ");
		String idInput = scanner.nextLine().trim();
		UUID id = parseUuid(idInput);
		LocalDateTime start = promptDateTime("New start (yyyy-MM-dd HH:mm): ");
		LocalDateTime end = promptDateTime("New end (yyyy-MM-dd HH:mm): ");
		Booking target = requireAccessibleBooking(user, id);
		List<TimeSlot> requested = List.of(new TimeSlot(start, end));
		while (true) {
			List<Booking> conflicts = schedulerSystem.findConflicts(
				target.getRoom().getName(),
				requested,
				target.getId()
			);
			if (conflicts.isEmpty()) {
				schedulerSystem.updateBooking(user, id, start, end);
				System.out.println("Booking updated.");
				return;
			}
			if (!resolveConflicts(user, conflicts, requested, false)) {
				System.out.println("Update cancelled.");
				return;
			}
		}
	}

	private void cancelBooking(User user) {
		if (user.getRole() == Role.GUEST) {
			throw new SecurityException("Guests cannot cancel bookings");
		}
		System.out.print("Booking id: ");
		String idInput = scanner.nextLine().trim();
		UUID id = parseUuid(idInput);
		schedulerSystem.cancelBooking(user, id);
		System.out.println("Booking cancelled.");
	}

	private void manageRooms(User user) {
		if (!user.getRole().canManageRooms()) {
			throw new SecurityException("Insufficient permissions");
		}
		boolean managing = true;
		while (managing) {
			System.out.println();
			System.out.println("-- Room Management --");
			System.out.println("1. Create room");
			System.out.println("2. Update room");
			System.out.println("3. Delete room");
			System.out.println("0. Back");
			System.out.print("Choice: ");
			String choice = scanner.nextLine().trim();
			try {
				switch (choice) {
					case "1" -> createRoom(user);
					case "2" -> updateRoom(user);
					case "3" -> deleteRoom(user);
					case "0" -> managing = false;
					default -> System.out.println("Unknown option.");
				}
			} catch (RuntimeException ex) {
				System.out.println("Error: " + ex.getMessage());
			}
		}
	}

	private void manageUsers(User user) {
		if (!user.getRole().canManageUsers()) {
			throw new SecurityException("Insufficient permissions");
		}
		boolean managing = true;
		while (managing) {
			System.out.println();
			System.out.println("-- User Management --");
			System.out.println("1. List users");
			System.out.println("2. Create user");
			System.out.println("3. Delete user");
			System.out.println("0. Back");
			System.out.print("Choice: ");
			String choice = scanner.nextLine().trim();
			try {
				switch (choice) {
					case "1" -> listUsers(user);
					case "2" -> createUser(user);
					case "3" -> deleteUser(user);
					case "0" -> managing = false;
					default -> System.out.println("Unknown option.");
				}
			} catch (RuntimeException ex) {
				System.out.println("Error: " + ex.getMessage());
			}
		}
	}

	private void createRoom(User actingUser) {
		System.out.print("Name: ");
		String name = scanner.nextLine().trim();
		System.out.print("Capacity: ");
		int capacity = parseInteger(scanner.nextLine().trim());
		System.out.print("Description: ");
		String description = scanner.nextLine().trim();
		schedulerSystem.createRoom(actingUser, name, capacity, description);
		System.out.println("Room created.");
	}

	private void updateRoom(User actingUser) {
		System.out.print("Existing room name: ");
		String name = scanner.nextLine().trim();
		System.out.print("New capacity: ");
		int capacity = parseInteger(scanner.nextLine().trim());
		System.out.print("New description: ");
		String description = scanner.nextLine().trim();
		schedulerSystem.updateRoom(actingUser, name, capacity, description);
		System.out.println("Room updated.");
	}

	private void deleteRoom(User actingUser) {
		System.out.print("Room name: ");
		String name = scanner.nextLine().trim();
		schedulerSystem.deleteRoom(actingUser, name);
		System.out.println("Room deleted.");
	}

	private void listUsers(User actingUser) {
		schedulerSystem
			.listUsers(actingUser)
			.forEach(u ->
				System.out.println("- " + u.getRole() + " | " + u.getUsername())
			);
	}

	private void createUser(User actingUser) {
		System.out.print("Username: ");
		String username = scanner.nextLine().trim();
		System.out.print("Password: ");
		String password = scanner.nextLine().trim();
		Role role = promptRole();
		schedulerSystem.createUser(actingUser, username, password, role);
		System.out.println("User created.");
	}

	private void deleteUser(User actingUser) {
		System.out.print("Username to delete: ");
		String username = scanner.nextLine().trim();
		schedulerSystem.deleteUser(actingUser, username);
		System.out.println("User deleted.");
	}

	private void attemptCreateBookings(
		User user,
		String room,
		List<TimeSlot> slots
	) {
		while (true) {
			List<Booking> conflicts = schedulerSystem.findConflicts(
				room,
				slots,
				null
			);
			if (conflicts.isEmpty()) {
				List<Booking> created = schedulerSystem.createBookings(
					user,
					room,
					slots
				);
				if (created.size() == 1) {
					System.out.println(
						"Booking created with id: " + created.get(0).getId()
					);
				} else {
					System.out.println(
						"Created " + created.size() + " bookings:"
					);
					created.forEach(this::printBookingSummary);
				}
				return;
			}
			if (!resolveConflicts(user, conflicts, slots, true)) {
				System.out.println("Booking request cancelled.");
				return;
			}
		}
	}

	private boolean resolveConflicts(
		User user,
		List<Booking> conflicts,
		List<TimeSlot> requestedSlots,
		boolean allowDeleteNew
	) {
		System.out.println("Conflicts detected for the following requested slot(s):");
		requestedSlots.forEach(slot ->
			System.out.println("  - " + formatSlot(slot))
		);
		System.out.println("Conflicting bookings:");
		conflicts.forEach(conflict ->
			System.out.println(
				"  - " +
				conflict.getId() +
				" | " +
				conflict.getRoom().getName() +
				" | " +
				conflict.getStart().format(DATE_TIME_FORMAT) +
				" -> " +
				conflict.getEnd().format(DATE_TIME_FORMAT) +
				" | " +
				conflict.getBookedBy()
			)
		);
		while (true) {
			System.out.println("Choose an option:");
			System.out.println("1. Delete a conflicting booking");
			System.out.println("2. Delete all conflicting bookings");
			System.out.println("3. Ignore");
			System.out.print("Choice: ");
			String choice = scanner.nextLine().trim();
			switch (choice) {
				case "1" -> {
					if (allowDeleteNew) {
						System.out.print("Delete 'existing' booking or 'new' request? (existing/new): ");
						String target = scanner.nextLine().trim().toLowerCase();
						if (target.startsWith("n")) {
							System.out.println("New request discarded.");
							return false;
						}
						if (!target.startsWith("e")) {
							System.out.println("Unknown option.");
							continue;
						}
					}
					System.out.print("Enter booking id to delete: ");
					String idText = scanner.nextLine().trim();
					try {
						UUID toDelete = parseUuid(idText);
						boolean listed = conflicts
							.stream()
							.anyMatch(conflict -> conflict.getId().equals(toDelete));
						if (!listed) {
							System.out.println("Booking id not in conflict list.");
							continue;
						}
						schedulerSystem.cancelBooking(user, toDelete);
						System.out.println("Booking " + toDelete + " deleted.");
						return true;
					} catch (SecurityException | IllegalArgumentException ex) {
						System.out.println("Unable to delete booking: " + ex.getMessage());
					}
				}
				case "2" -> {
					boolean anyDeleted = false;
					for (Booking conflict : conflicts) {
						try {
							schedulerSystem.cancelBooking(user, conflict.getId());
							System.out.println(
								"Deleted booking " + conflict.getId()
							);
							anyDeleted = true;
						} catch (SecurityException | IllegalArgumentException ex) {
							System.out.println(
								"Could not delete booking " +
								conflict.getId() +
								": " +
								ex.getMessage()
							);
						}
					}
					if (!anyDeleted) {
						System.out.println("No bookings were deleted.");
						continue;
					}
					return true;
				}
				case "3" -> {
					System.out.println("Conflicts ignored.");
					return false;
				}
				default -> System.out.println("Unknown option.");
			}
		}
	}

	private List<TimeSlot> collectRecurringSlots() {
		LocalDate startDate = promptDate("First date (yyyy-MM-dd): ");
		LocalTime startTime = promptTime("Start time (HH:mm): ");
		LocalTime endTime = promptTime("End time (HH:mm): ");
		if (!startTime.isBefore(endTime)) {
			throw new IllegalArgumentException("Start time must be before end time");
		}
		Set<DayOfWeek> days = promptDaysOfWeek();
		LocalDate endDate = promptOptionalEndDate(startDate);
		boolean indefinite = endDate == null;
		LocalDate effectiveEnd = indefinite
			? startDate.plusWeeks(DEFAULT_RECURRING_WEEKS)
			: endDate;
		if (indefinite) {
			System.out.println(
				"Creating occurrences for the next " +
				DEFAULT_RECURRING_WEEKS +
				" week(s). Remove future bookings when you want to stop the series."
			);
		}
		return schedulerSystem.generateRecurringSlots(
			startDate,
			effectiveEnd,
			startTime,
			endTime,
			days
		);
	}

	private boolean promptYesNo(String prompt) {
		while (true) {
			System.out.print(prompt);
			String input = scanner.nextLine().trim().toLowerCase();
			if (input.isEmpty()) {
				System.out.println("Please answer with 'y' or 'n'.");
				continue;
			}
			if (input.startsWith("y")) {
				return true;
			}
			if (input.startsWith("n")) {
				return false;
			}
			System.out.println("Please answer with 'y' or 'n'.");
		}
	}

	private LocalDate promptDate(String prompt) {
		while (true) {
			System.out.print(prompt);
			String text = scanner.nextLine().trim();
			try {
				return LocalDate.parse(text);
			} catch (DateTimeParseException ex) {
				System.out.println("Invalid date. Expected format yyyy-MM-dd");
			}
		}
	}

	private LocalTime promptTime(String prompt) {
		while (true) {
			System.out.print(prompt);
			String text = scanner.nextLine().trim();
			try {
				return LocalTime.parse(text, TIME_FORMAT);
			} catch (DateTimeParseException ex) {
				System.out.println("Invalid time. Expected format HH:mm");
			}
		}
	}

	private Set<DayOfWeek> promptDaysOfWeek() {
		while (true) {
			System.out.print(
				"Days (choices: MON,TUE,WED,THU,FRI,SAT,SUN,WEEKDAYS,DAILY): "
			);
			String input = scanner.nextLine().trim();
			if (input.isEmpty()) {
				System.out.println("At least one day must be specified.");
				continue;
			}
			Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
			String[] tokens = input.toUpperCase().split(",");
			boolean dailySelected = false;
			boolean valid = true;
			for (String rawToken : tokens) {
				String token = rawToken.trim();
				if (token.isEmpty()) {
					continue;
				}
				switch (token) {
					case "MON" -> days.add(DayOfWeek.MONDAY);
					case "TUE" -> days.add(DayOfWeek.TUESDAY);
					case "WED" -> days.add(DayOfWeek.WEDNESDAY);
					case "THU" -> days.add(DayOfWeek.THURSDAY);
					case "FRI" -> days.add(DayOfWeek.FRIDAY);
					case "SAT" -> days.add(DayOfWeek.SATURDAY);
					case "SUN" -> days.add(DayOfWeek.SUNDAY);
					case "WEEKDAYS" -> {
						days.addAll(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
					}
					case "DAILY" -> {
						days = EnumSet.allOf(DayOfWeek.class);
						dailySelected = true;
					}
					default -> {
						System.out.println("Unknown day token: " + token);
						valid = false;
					}
				}
				if (!valid) {
					break;
				}
				if (dailySelected) {
					break;
				}
			}
			if (!valid) {
				continue;
			}
			if (days.isEmpty()) {
				System.out.println("At least one day must be specified.");
				continue;
			}
			return days;
		}
	}

	private LocalDate promptOptionalEndDate(LocalDate startDate) {
		while (true) {
			System.out.print(
				"End date (yyyy-MM-dd) or press Enter for indefinite: "
			);
			String input = scanner.nextLine().trim();
			if (input.isEmpty()) {
				return null;
			}
			try {
				LocalDate endDate = LocalDate.parse(input);
				if (endDate.isBefore(startDate)) {
					System.out.println("End date cannot be before start date.");
					continue;
				}
				return endDate;
			} catch (DateTimeParseException ex) {
				System.out.println("Invalid date. Expected format yyyy-MM-dd");
			}
		}
	}

	private Booking requireAccessibleBooking(User user, UUID id) {
		return schedulerSystem
			.listBookings(user)
			.stream()
			.filter(booking -> booking.getId().equals(id))
			.findFirst()
			.orElseThrow(() ->
				new IllegalArgumentException(
					"Booking not found or access denied"
				)
			);
	}

	private String formatSlot(TimeSlot slot) {
		return (
			slot.start().format(DATE_TIME_FORMAT) +
			" -> " +
			slot.end().format(DATE_TIME_FORMAT)
		);
	}

	private void printBookingSummary(Booking booking) {
		System.out.println(
			"- " +
			booking.getId() +
			" | " +
			booking.getRoom().getName() +
			" | " +
			booking.getStart().format(DATE_TIME_FORMAT) +
			" -> " +
			booking.getEnd().format(DATE_TIME_FORMAT) +
			" | " +
			booking.getBookedBy()
		);
	}

	private Role promptRole() {
		while (true) {
			System.out.print("Role (ADMIN/SCHEDULER/USER/GUEST): ");
			String value = scanner.nextLine().trim().toUpperCase();
			try {
				return Role.valueOf(value);
			} catch (IllegalArgumentException ex) {
				System.out.println("Unknown role. Try again.");
			}
		}
	}

	private LocalDateTime promptDateTime(String prompt) {
		while (true) {
			System.out.print(prompt);
			String text = scanner.nextLine().trim();
			try {
				return LocalDateTime.parse(text, DATE_TIME_FORMAT);
			} catch (DateTimeParseException ex) {
				System.out.println(
					"Invalid date/time. Expected format yyyy-MM-dd HH:mm"
				);
			}
		}
	}

	private UUID parseUuid(String input) {
		try {
			return UUID.fromString(input);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid UUID format");
		}
	}

	private int parseInteger(String input) {
		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid integer value");
		}
	}
}
