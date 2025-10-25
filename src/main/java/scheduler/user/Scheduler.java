package scheduler.user;

import scheduler.Role;

/**
 * User with the SCHEDULER role.
 */
public final class Scheduler extends User {

	public Scheduler(String username, String password) {
		super(username, password, Role.SCHEDULER);
	}
}
