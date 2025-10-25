package scheduler.util;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import scheduler.model.Booking;
import scheduler.model.Room;

/**
 * Utility for detecting time conflicts between bookings.
 */
public final class ConflictChecker {
    private ConflictChecker() {
    }

    public static Optional<Booking> findConflict(List<Booking> bookings, Room room, LocalDateTime start,
                                                 LocalDateTime end, UUID excludeId) {
        return bookings.stream()
                .filter(b -> b.getRoom().getName().equalsIgnoreCase(room.getName()))
                .filter(b -> excludeId == null || !b.getId().equals(excludeId))
                .filter(b -> overlap(b.getStart(), b.getEnd(), start, end))
                .findFirst();
    }

    private static boolean overlap(LocalDateTime existingStart, LocalDateTime existingEnd,
                                   LocalDateTime requestedStart, LocalDateTime requestedEnd) {
        return existingStart.isBefore(requestedEnd) && requestedStart.isBefore(existingEnd);
    }
}
