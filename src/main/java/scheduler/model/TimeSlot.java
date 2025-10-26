package scheduler.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a candidate start/end window for bookings.
 */
public record TimeSlot(LocalDateTime start, LocalDateTime end) {

	public TimeSlot {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("Start must be before end");
		}
	}
}
