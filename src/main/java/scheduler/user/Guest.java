package scheduler.user;

import scheduler.Role;

/**
 * User with the GUEST role.
 */
public final class Guest extends User {
    public Guest(String username, String password) {
        super(username, password, Role.GUEST);
    }
}
