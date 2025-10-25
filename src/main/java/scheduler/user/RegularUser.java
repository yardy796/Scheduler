package scheduler.user;

import scheduler.Role;

/**
 * User with the USER role.
 */
public final class RegularUser extends User {

	public RegularUser(String username, String password) {
		super(username, password, Role.USER);
	}
}
