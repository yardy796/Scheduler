package scheduler.model;

import java.time.LocalDateTime;

/**
 * Defines supported recurrence patterns for bookings.
 */
public enum RecurrencePattern {
	NONE,
	DAILY,
	WEEKLY;

	/**
	 * Applies the recurrence offset to a base date.
	 *
	 * @param base the original date/time
	 * @param iteration zero-based iteration index
	 * @return the adjusted date/time for the iteration
	 */
	public LocalDateTime shift(LocalDateTime base, int iteration) {
		return switch (this) {
			case NONE -> base;
			case DAILY -> base.plusDays(iteration);
			case WEEKLY -> base.plusWeeks(iteration);
		};
	}

	/**
	 * Indicates whether the pattern produces more than one occurrence.
	 *
	 * @return true when the pattern repeats
	 */
	public boolean isRecurring() {
		return this != NONE;
	}
}
