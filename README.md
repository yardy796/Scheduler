# Scheduler

Console-based scheduling application with role-based access control and persistent storage.

## Features

-   Role-aware login with default `admin`/`admin` credentials
-   Room catalogue management with capacity tracking
-   Booking creation, update, and cancellation with conflict detection
-   Embedded SQLite persistence stored in `data/scheduler.db`

## Requirements

-   Java 21 or later
-   [SQLite JDBC driver](https://github.com/xerial/sqlite-jdbc) (`sqlite-jdbc-x.y.z.jar`) placed on the runtime classpath (e.g. `lib/sqlite-jdbc.jar`; the VS Code launch config also checks `src/main/java/lib/sqlite-jdbc.jar`)

## Running the application

1. Compile the sources:
   ```pwsh
   javac --release 21 -d out $(Get-ChildItem -Recurse -Filter *.java -Path src/main/java | ForEach-Object FullName)
   ```
2. Launch the console UI (adjust the driver filename/path as needed):
   ```pwsh
   java -cp "out;lib/sqlite-jdbc.jar" scheduler.Main
   ```
   Alternatively, you may simply hit `F5` (or Run) in VSCode.

## Usage notes

-   The first run seeds a default admin account (`admin` / `admin`). Change or delete it once you create new admins.
-   Dates use the `yyyy-MM-dd HH:mm` format (24-hour clock).
-   Data now lives in `data/scheduler.db`. Deleting this file resets the application state.

## Roadmap

-   Optional JavaFX front-end
-   Automated tests for booking conflict scenarios
