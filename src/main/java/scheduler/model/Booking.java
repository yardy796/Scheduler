package scheduler.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a booking for a specific room and time range.
 */
public final class Booking implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final Room room;
    private LocalDateTime start;
    private LocalDateTime end;
    private final String bookedBy;

    public Booking(Room room, LocalDateTime start, LocalDateTime end, String bookedBy) {
        this(UUID.randomUUID(), room, start, end, bookedBy);
    }

    public Booking(UUID id, Room room, LocalDateTime start, LocalDateTime end, String bookedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.room = Objects.requireNonNull(room, "room");
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        this.bookedBy = Objects.requireNonNull(bookedBy, "bookedBy");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = Objects.requireNonNull(start, "start");
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = Objects.requireNonNull(end, "end");
    }

    public String getBookedBy() {
        return bookedBy;
    }

    public boolean isOwnedBy(String username) {
        return bookedBy.equalsIgnoreCase(username);
    }

    @Override
    public String toString() {
        return id + "|" + room.getName() + "|" + start + "->" + end + "|" + bookedBy;
    }
}
