package scheduler.user;

import scheduler.Role;

/**
 * User with the ADMIN role.
 */
public final class Admin extends User {
    public Admin(String username, String password) {
        super(username, password, Role.ADMIN);
    }
}
