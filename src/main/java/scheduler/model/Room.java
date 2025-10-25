package scheduler.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a schedulable room.
 */
public final class Room implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private int capacity;
    private String description;

    public Room(String name, int capacity, String description) {
        this.name = Objects.requireNonNull(name, "name").trim();
        this.capacity = capacity;
        this.description = description == null ? "" : description.trim();
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Room other)) {
            return false;
        }
        return name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + capacity + ")";
    }
}
