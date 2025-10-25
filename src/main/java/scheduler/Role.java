package scheduler;

/**
 * Enumerates the roles recognized by the scheduler system and captures their permissions.
 */
public enum Role {
	ADMIN(true, true, true, true),
	SCHEDULER(false, true, true, true),
	USER(false, false, true, true),
	GUEST(false, false, false, true);

	private final boolean canManageUsers;
	private final boolean canManageRooms;
	private final boolean canManageAllBookings;
	private final boolean canViewSchedules;

	Role(
		boolean canManageUsers,
		boolean canManageRooms,
		boolean canManageAllBookings,
		boolean canViewSchedules
	) {
		this.canManageUsers = canManageUsers;
		this.canManageRooms = canManageRooms;
		this.canManageAllBookings = canManageAllBookings;
		this.canViewSchedules = canViewSchedules;
	}

	public boolean canManageUsers() {
		return canManageUsers;
	}

	public boolean canManageRooms() {
		return canManageRooms;
	}

	public boolean canManageAllBookings() {
		return canManageAllBookings;
	}

	public boolean canViewSchedules() {
		return canViewSchedules;
	}
}
