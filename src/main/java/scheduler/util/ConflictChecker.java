package scheduler.util;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import scheduler.model.Booking;
import scheduler.model.Room;

/**
 * Utility for detecting time conflicts between bookings.
 */
public final class ConflictChecker {

	private ConflictChecker() {}

	public static Optional<Booking> findConflict(
		List<Booking> bookings,
		Room room,
		LocalDateTime start,
		LocalDateTime end,
		UUID excludeId
	) {
		return findConflicts(bookings, room, start, end, excludeId)
			.stream()
			.findFirst();
	}

	public static List<Booking> findConflicts(
		List<Booking> bookings,
		Room room,
		LocalDateTime start,
		LocalDateTime end,
		UUID excludeId
	) {
		Set<Booking> conflicts = new LinkedHashSet<>();
		for (Booking booking : bookings) {
			boolean sameRoom = booking
				.getRoom()
				.getName()
				.equalsIgnoreCase(room.getName());
			boolean differentId = excludeId == null || !booking.getId().equals(excludeId);
			if (sameRoom && differentId && overlap(booking.getStart(), booking.getEnd(), start, end)) {
				conflicts.add(booking);
			}
		}
		return List.copyOf(conflicts);
	}

	private static boolean overlap(
		LocalDateTime existingStart,
		LocalDateTime existingEnd,
		LocalDateTime requestedStart,
		LocalDateTime requestedEnd
	) {
		return (
			existingStart.isBefore(requestedEnd) &&
			requestedStart.isBefore(existingEnd)
		);
	}
}
