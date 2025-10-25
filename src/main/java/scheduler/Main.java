package scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.persistence.FileManager;
import scheduler.service.SchedulerSystem;
import scheduler.user.User;

/**
 * Entry point for the Scheduler application with a basic console UI.
 */
public final class Main {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            return schedulerSystem.authenticate(username, password)
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
            } catch (SecurityException | IllegalArgumentException | IllegalStateException ex) {
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
        rooms.forEach(room -> System.out.println("- " + room.getName() + " | capacity=" + room.getCapacity()
                + (room.getDescription().isBlank() ? "" : " | " + room.getDescription())));
    }

    private void viewBookings(User user) {
        List<Booking> bookings = schedulerSystem.listBookings(user);
        if (bookings.isEmpty()) {
            System.out.println("No bookings available.");
            return;
        }
        bookings.forEach(booking -> System.out.println("- " + booking.getId() + " | "
                + booking.getRoom().getName() + " | "
                + booking.getStart().format(DATE_TIME_FORMAT) + " -> "
                + booking.getEnd().format(DATE_TIME_FORMAT) + " | "
                + booking.getBookedBy()));
    }

    private void createBooking(User user) {
        if (user.getRole() == Role.GUEST) {
            throw new SecurityException("Guests cannot create bookings");
        }
        System.out.print("Room name: ");
        String room = scanner.nextLine().trim();
        LocalDateTime start = promptDateTime("Start (yyyy-MM-dd HH:mm): ");
        LocalDateTime end = promptDateTime("End (yyyy-MM-dd HH:mm): ");
        Booking booking = schedulerSystem.createBooking(user, room, start, end);
        System.out.println("Booking created with id: " + booking.getId());
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
        schedulerSystem.updateBooking(user, id, start, end);
        System.out.println("Booking updated.");
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
        schedulerSystem.listUsers(actingUser)
                .forEach(u -> System.out.println("- " + u.getRole() + " | " + u.getUsername()));
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
                System.out.println("Invalid date/time. Expected format yyyy-MM-dd HH:mm");
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
