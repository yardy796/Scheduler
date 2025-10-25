package scheduler.user;

import java.io.Serial;
import java.io.Serializable;
import scheduler.Role;

/**
 * Base class for all users of the scheduler.
 */
public abstract class User implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String username;
	private final String password;
	private final Role role;

	protected User(String username, String password, Role role) {
		this.username = username;
		this.password = password;
		this.role = role;
	}

	public String getUsername() {
		return username;
	}

	public Role getRole() {
		return role;
	}

	public boolean verifyPassword(String candidate) {
		return password.equals(candidate);
	}

	public String getMaskedPassword() {
		return "*".repeat(Math.max(1, password.length()));
	}

	@Override
	public String toString() {
		return role + ":" + username;
	}
}
