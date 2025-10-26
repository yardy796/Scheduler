# Scheduler Copilot Instructions
## Architecture
- Console entry point `src/main/java/scheduler/Main.java` hosts the CLI loop, routing menu choices into service calls.
- Core business logic lives in `src/main/java/scheduler/service/SchedulerSystem.java`; treat it as the facade for users, rooms, and bookings.
- Domain models under `src/main/java/scheduler/model/` (`Room`, `Booking`) stay lightweight and serializable; prefer enriching logic in the service layer instead of the models.
## Persistence & Data
- Persistence is handled by `src/main/java/scheduler/persistence/FileManager.java`, which reads/writes serialized lists to `data/users.dat`, `data/rooms.dat`, and `data/bookings.dat`.
- Every mutating method in `SchedulerSystem` ends with `persist()`; call it (or reuse existing helpers) whenever you add new stateful operations.
- `FileManager` assumes the working directory root; keep relative paths stable or update the constructor in tandem with CLI changes.
## Roles & Permissions
- `src/main/java/scheduler/Role.java` defines permission flags (`canManageUsers`, `canManageRooms`, etc.) consumed by `SchedulerSystem.requirePermission` helpers.
- Enforce permissions via the existing predicates instead of manual role checks, so new roles only require flag updates.
- `SchedulerSystem.ensureDefaultAdmin()` guarantees at least one admin; keep that invariant when altering user deletion or seeding logic.
## Booking Logic
- Detect scheduling collisions with `scheduler.util.ConflictChecker.findConflict(...)`; it compares bookings for the same room and overlapping time windows.
- `scheduler.model.Booking` enforces `start.isBefore(end)` at construction; uphold the invariant when adding factories or deserializing custom data.
- Booking ownership is determined by `Booking.isOwnedBy(username)`; reuse it when authorizing updates or cancellations.
## Console Workflow
- `Main.sessionLoop` drives the menu; add new options by updating `printMenu` and the `switch` in `sessionLoop` (and any nested management menus).
- Input parsing already centralizes date handling in `promptDateTime` (expects `yyyy-MM-dd HH:mm`); reuse this or provide equivalent validation for new commands.
- Keep menu text aligned with role capabilities so the CLI reflects permission constraints (e.g., mirror `Role` flags when showing options).
## Build & Run
- Use the VS Code task `Compile Scheduler` to compile with `javac --release 21`; it populates the `out/` directory.
- Run the console app via `java -cp out scheduler.Main` from the repo root (PowerShell compatible).
- Reset persisted state by deleting the `data/` directory; the next launch reseeds the default `admin/admin` credentials.
## Development Practices
- After mutating `users`, `rooms`, or `bookings`, call the existing `persist()` hook before returning to ensure data consistency.
- Instantiate users through `SchedulerSystem.instantiateUser` so role wiring and subclasses (`Admin`, `Scheduler`, `RegularUser`, `Guest`) stay aligned.
- Preserve declared `serialVersionUID` values when modifying serializable classes to avoid breaking existing save files.
- Reuse `ConflictChecker` for any new booking validation flows to keep overlap rules consistent across the codebase.
- No automated tests exist; validate changes manually through the CLI, ideally using the seeded admin account to exercise privileged flows.