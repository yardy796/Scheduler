# Scheduler

Console-based scheduling application with role-based access control and persistent storage.

## Features

-   Role-aware login with default `admin`/`admin` credentials
-   Room catalogue management with capacity tracking
-   Booking creation, update, and cancellation with conflict detection
-   Local persistence for users, rooms, and bookings (Java serialization in `data/`)

## Requirements

-   Java 21 or later

## Running the application

1. Compile the sources:
    ```pwsh
    javac -d out $(Get-ChildItem -Recurse src/main/java/*.java)
    ```
2. Launch the console UI:
   `pwsh
java -cp out scheduler.Main
`
   Alternatively, you may simply hit `F5` (or Run) in VSCode.

## Usage notes

-   The first run seeds a default admin account (`admin` / `admin`). Change or delete it once you create new admins.
-   Dates use the `yyyy-MM-dd HH:mm` format (24-hour clock).
-   Data files live under `data/`. Deleting them resets the application state.

## Roadmap

-   Optional JavaFX front-end
-   Automated tests for booking conflict scenarios
