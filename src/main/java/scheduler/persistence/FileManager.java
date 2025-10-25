package scheduler.persistence;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.user.User;

/**
 * Persists users, rooms, and bookings using Java serialization.
 */
public final class FileManager {
    private final Path dataDirectory;
    private final Path usersFile;
    private final Path roomsFile;
    private final Path bookingsFile;

    public FileManager() {
        this(Paths.get("data"));
    }

    public FileManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.usersFile = dataDirectory.resolve("users.dat");
        this.roomsFile = dataDirectory.resolve("rooms.dat");
        this.bookingsFile = dataDirectory.resolve("bookings.dat");
        ensureDirectory();
    }

    public List<User> loadUsers() {
        return readList(usersFile);
    }

    public List<Room> loadRooms() {
        return readList(roomsFile);
    }

    public List<Booking> loadBookings() {
        return readList(bookingsFile);
    }

    public void saveUsers(List<User> users) {
        writeList(usersFile, users);
    }

    public void saveRooms(List<Room> rooms) {
        writeList(roomsFile, rooms);
    }

    public void saveBookings(List<Booking> bookings) {
        writeList(bookingsFile, bookings);
    }

    public void persistAll(List<User> users, List<Room> rooms, List<Booking> bookings) {
        saveUsers(users);
        saveRooms(rooms);
        saveBookings(bookings);
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create data directory " + dataDirectory, e);
        }
    }

    private <T> List<T> readList(Path file) {
        if (Files.notExists(file)) {
            return new ArrayList<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file))) {
            Object data = ois.readObject();
            if (data instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<T> casted = (List<T>) new ArrayList<>(list);
                return casted;
            }
            return new ArrayList<>();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Warning: could not load " + file + " (" + e.getMessage() + ")");
            return new ArrayList<>();
        }
    }

    private <T> void writeList(Path file, List<T> data) {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file))) {
            oos.writeObject(new ArrayList<>(data));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write data to " + file, e);
        }
    }
}
