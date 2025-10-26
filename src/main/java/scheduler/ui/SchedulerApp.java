package scheduler.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import scheduler.Role;
import scheduler.model.Booking;
import scheduler.model.Room;
import scheduler.persistence.FileManager;
import scheduler.service.SchedulerSystem;
import scheduler.user.User;

/**
 * JavaFX front-end for the scheduler system.
 */
public final class SchedulerApp extends Application {

	private static final DateTimeFormatter DATE_TIME_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter TIME_FORMAT =
		DateTimeFormatter.ofPattern("HH:mm");

	private SchedulerSystem schedulerSystem;
	private Stage primaryStage;
	private User currentUser;

	private TableView<Room> roomTable;
	private TableView<Booking> bookingTable;
	private TableView<User> userTable;

	private final ObservableList<Room> roomItems = FXCollections.observableArrayList();
	private final ObservableList<Booking> bookingItems = FXCollections.observableArrayList();
	private final ObservableList<User> userItems = FXCollections.observableArrayList();

	private Label statusLabel;

	@Override
	public void start(Stage stage) {
		schedulerSystem = new SchedulerSystem(new FileManager());
		primaryStage = stage;
		primaryStage.setTitle("Scheduler System");
		showLoginScene();
		primaryStage.show();
	}

	private void showLoginScene() {
		VBox container = new VBox(12);
		container.setPadding(new Insets(24));

		Label heading = new Label("Sign in");
		heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

		TextField usernameField = new TextField();
		usernameField.setPromptText("Username");
		PasswordField passwordField = new PasswordField();
		passwordField.setPromptText("Password");

		Label feedback = new Label();
		feedback.setStyle("-fx-text-fill: red;");

		Button loginButton = new Button("Login");
		loginButton.setDefaultButton(true);
		loginButton.setOnAction(event -> {
			String username = usernameField.getText().trim();
			String password = passwordField.getText();
			Optional<User> authenticated = schedulerSystem.authenticate(username, password);
			if (authenticated.isPresent()) {
				currentUser = authenticated.get();
				showDashboard();
				setStatus("Logged in as " + currentUser.getUsername());
			} else {
				feedback.setText("Invalid credentials. Try again.");
			}
		});

		container.getChildren().addAll(heading, usernameField, passwordField, loginButton, feedback);

		Scene scene = new Scene(container, 360, 240);
		primaryStage.setScene(scene);
	}

	private void showDashboard() {
		BorderPane root = new BorderPane();
		root.setPadding(new Insets(12));

		root.setTop(buildHeader());

		TabPane tabPane = new TabPane();
		tabPane.getTabs().add(buildRoomsTab());
		tabPane.getTabs().add(buildBookingsTab());
		Tab usersTab = buildUsersTab();
		if (usersTab != null) {
			tabPane.getTabs().add(usersTab);
		}
		root.setCenter(tabPane);

		statusLabel = new Label("Ready");
		statusLabel.setPadding(new Insets(6, 0, 0, 4));
		root.setBottom(statusLabel);

		refreshRooms();
		refreshBookings();
		refreshUsers();

		Scene scene = new Scene(root, 960, 600);
		primaryStage.setScene(scene);
	}

	private HBox buildHeader() {
		HBox header = new HBox(12);
		header.setPadding(new Insets(0, 0, 12, 0));

		Label userLabel = new Label(
			"Logged in as " +
			currentUser.getUsername() +
			" (" +
			currentUser.getRole() +
			")"
		);

		Button logout = new Button("Logout");
		logout.setOnAction(event -> {
			currentUser = null;
			showLoginScene();
		});

		Button exit = new Button("Exit");
		exit.setOnAction(event -> Platform.exit());

		header.getChildren().addAll(userLabel, logout, exit);
		return header;
	}

	private Tab buildRoomsTab() {
		roomTable = new TableView<>();
		roomTable.setItems(roomItems);
		roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<Room, String> nameCol = new TableColumn<>("Name");
		nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));

		TableColumn<Room, Integer> capacityCol = new TableColumn<>("Capacity");
		capacityCol.setCellValueFactory(new PropertyValueFactory<>("capacity"));

		TableColumn<Room, String> descCol = new TableColumn<>("Description");
		descCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));

		roomTable.getColumns().addAll(nameCol, capacityCol, descCol);

		Button refresh = new Button("Refresh");
		refresh.setOnAction(event -> refreshRooms());

		Button add = new Button("Add Room");
		add.setOnAction(event -> showRoomDialog(null));

		Button edit = new Button("Edit Room");
		edit.setOnAction(event -> {
			Room selected = roomTable.getSelectionModel().getSelectedItem();
			if (selected != null) {
				showRoomDialog(selected);
			}
		});

		Button delete = new Button("Delete Room");
		delete.setOnAction(event -> {
			Room selected = roomTable.getSelectionModel().getSelectedItem();
			if (selected != null) {
				confirmDeleteRoom(selected);
			}
		});

		boolean canManageRooms = currentUser.getRole().canManageRooms();
		add.setDisable(!canManageRooms);
		if (canManageRooms) {
			edit.disableProperty().bind(
				roomTable.getSelectionModel().selectedItemProperty().isNull()
			);
			delete.disableProperty().bind(
				roomTable.getSelectionModel().selectedItemProperty().isNull()
			);
		} else {
			edit.setDisable(true);
			delete.setDisable(true);
		}

		ToolBar actions = new ToolBar(refresh, add, edit, delete);

		VBox content = new VBox(12, roomTable, actions);
		VBox.setVgrow(roomTable, Priority.ALWAYS);

		Tab tab = new Tab("Rooms", content);
		tab.setClosable(false);
		return tab;
	}

	private Tab buildBookingsTab() {
		bookingTable = new TableView<>();
		bookingTable.setItems(bookingItems);
		bookingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<Booking, String> idCol = new TableColumn<>("ID");
		idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getId().toString()));

		TableColumn<Booking, String> roomCol = new TableColumn<>("Room");
		roomCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRoom().getName()));

		TableColumn<Booking, String> startCol = new TableColumn<>("Start");
		startCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStart().format(DATE_TIME_FORMAT)));

		TableColumn<Booking, String> endCol = new TableColumn<>("End");
		endCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getEnd().format(DATE_TIME_FORMAT)));

		TableColumn<Booking, String> ownerCol = new TableColumn<>("Booked By");
		ownerCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getBookedBy()));

		bookingTable.getColumns().addAll(idCol, roomCol, startCol, endCol, ownerCol);

		Button refresh = new Button("Refresh");
		refresh.setOnAction(event -> refreshBookings());

		Button add = new Button("Create Booking");
		add.setOnAction(event -> showBookingDialog(null));

		Button edit = new Button("Update Booking");
		edit.setOnAction(event -> {
			Booking selected = bookingTable.getSelectionModel().getSelectedItem();
			if (selected != null) {
				showBookingDialog(selected);
			}
		});

		Button delete = new Button("Cancel Booking");
		delete.setOnAction(event -> {
			Booking selected = bookingTable.getSelectionModel().getSelectedItem();
			if (selected != null) {
				confirmDeleteBooking(selected);
			}
		});

		boolean canCreate = currentUser.getRole() != Role.GUEST;
		add.setDisable(!canCreate);

		boolean canModify = currentUser.getRole() != Role.GUEST;
		if (canModify) {
			edit.disableProperty().bind(
				bookingTable.getSelectionModel().selectedItemProperty().isNull()
			);
			delete.disableProperty().bind(
				bookingTable.getSelectionModel().selectedItemProperty().isNull()
			);
		} else {
			edit.setDisable(true);
			delete.setDisable(true);
		}

		ToolBar actions = new ToolBar(refresh, add, edit, delete);

		VBox content = new VBox(12, bookingTable, actions);
		VBox.setVgrow(bookingTable, Priority.ALWAYS);

		Tab tab = new Tab("Bookings", content);
		tab.setClosable(false);
		return tab;
	}

	private Tab buildUsersTab() {
		boolean canView = currentUser.getRole().canManageUsers() || currentUser.getRole() == Role.SCHEDULER;
		if (!canView) {
			return null;
		}

		userTable = new TableView<>();
		userTable.setItems(userItems);
		userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<User, String> roleCol = new TableColumn<>("Role");
		roleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRole().name()));

		TableColumn<User, String> usernameCol = new TableColumn<>("Username");
		usernameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUsername()));

		userTable.getColumns().addAll(roleCol, usernameCol);

		Button refresh = new Button("Refresh");
		refresh.setOnAction(event -> refreshUsers());

		Button add = new Button("Create User");
		add.setOnAction(event -> showUserDialog());

		Button delete = new Button("Delete User");
		delete.setOnAction(event -> {
			User selected = userTable.getSelectionModel().getSelectedItem();
			if (selected != null) {
				confirmDeleteUser(selected);
			}
		});

		boolean canManageUsers = currentUser.getRole().canManageUsers();
		add.setDisable(!canManageUsers);
		if (canManageUsers) {
			delete.disableProperty().bind(
				userTable.getSelectionModel().selectedItemProperty().isNull()
			);
		} else {
			delete.setDisable(true);
		}

		ToolBar actions = new ToolBar(refresh, add, delete);

		VBox content = new VBox(12, userTable, actions);
		VBox.setVgrow(userTable, Priority.ALWAYS);

		Tab tab = new Tab("Users", content);
		tab.setClosable(false);
		return tab;
	}

	private void refreshRooms() {
		roomItems.setAll(schedulerSystem.listRooms());
	}

	private void refreshBookings() {
		bookingItems.setAll(schedulerSystem.listBookings(currentUser));
	}

	private void refreshUsers() {
		if (userTable != null) {
			try {
				userItems.setAll(schedulerSystem.listUsers(currentUser));
			} catch (SecurityException ignored) {
				userItems.clear();
			}
		}
	}

	private void showRoomDialog(Room existing) {
		boolean creating = existing == null;
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle(creating ? "Add Room" : "Edit Room");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField nameField = new TextField(creating ? "" : existing.getName());
		nameField.setPromptText("Name");
		nameField.setDisable(!creating);

		TextField capacityField = new TextField(creating ? "" : String.valueOf(existing.getCapacity()));
		capacityField.setPromptText("Capacity");

		TextField descriptionField = new TextField(creating ? "" : existing.getDescription());
		descriptionField.setPromptText("Description");

		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: red;");

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.addRow(0, new Label("Name"), nameField);
		grid.addRow(1, new Label("Capacity"), capacityField);
		grid.addRow(2, new Label("Description"), descriptionField);
		grid.add(errorLabel, 0, 3, 2, 1);
		dialog.getDialogPane().setContent(grid);

		Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(ActionEvent.ACTION, event -> {
			try {
				String name = nameField.getText().trim();
				if (name.isEmpty()) {
					throw new IllegalArgumentException("Name is required");
				}
				int capacity = Integer.parseInt(capacityField.getText().trim());
				String description = descriptionField.getText().trim();
				if (creating) {
					schedulerSystem.createRoom(currentUser, name, capacity, description);
					setStatus("Created room " + name);
				} else {
					schedulerSystem.updateRoom(currentUser, name, capacity, description);
					setStatus("Updated room " + name);
				}
				refreshRooms();
				errorLabel.setText("");
			} catch (NumberFormatException ex) {
				errorLabel.setText("Capacity must be a number");
				event.consume();
			} catch (RuntimeException ex) {
				errorLabel.setText(ex.getMessage());
				event.consume();
			}
		});

		dialog.showAndWait();
	}

	private void confirmDeleteRoom(Room room) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Delete Room");
		alert.setHeaderText("Delete room " + room.getName() + "?");
		alert.setContentText("This action cannot be undone.");
		alert.showAndWait().ifPresent(response -> {
			if (response == ButtonType.OK) {
				try {
					schedulerSystem.deleteRoom(currentUser, room.getName());
					setStatus("Deleted room " + room.getName());
					refreshRooms();
				} catch (RuntimeException ex) {
					showError("Unable to delete room", ex.getMessage());
				}
			}
		});
	}

	private void showBookingDialog(Booking existing) {
		boolean creating = existing == null;
		Dialog<BookingForm> dialog = new Dialog<>();
		dialog.setTitle(creating ? "Create Booking" : "Update Booking");
		ButtonType okType = new ButtonType(creating ? "Create" : "Update", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

		ComboBox<Room> roomChoice = new ComboBox<>(FXCollections.observableArrayList(schedulerSystem.listRooms()));
		roomChoice.setDisable(!creating);
		if (creating && !roomChoice.getItems().isEmpty()) {
			roomChoice.getSelectionModel().selectFirst();
		}

		DatePicker startDate = new DatePicker();
		TextField startTime = new TextField();
		DatePicker endDate = new DatePicker();
		TextField endTime = new TextField();

		if (creating) {
			LocalDate today = LocalDate.now();
			startDate.setValue(today);
			endDate.setValue(today);
			startTime.setText("09:00");
			endTime.setText("10:00");
		} else {
			if (!roomChoice.getItems().contains(existing.getRoom())) {
				roomChoice.getItems().add(existing.getRoom());
			}
			roomChoice.getSelectionModel().select(existing.getRoom());
			startDate.setValue(existing.getStart().toLocalDate());
			startTime.setText(existing.getStart().toLocalTime().format(TIME_FORMAT));
			endDate.setValue(existing.getEnd().toLocalDate());
			endTime.setText(existing.getEnd().toLocalTime().format(TIME_FORMAT));
		}

		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: red;");

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.addRow(0, new Label("Room"), roomChoice);
		grid.addRow(1, new Label("Start Date"), startDate);
		grid.addRow(2, new Label("Start Time"), startTime);
		grid.addRow(3, new Label("End Date"), endDate);
		grid.addRow(4, new Label("End Time"), endTime);
		grid.add(errorLabel, 0, 5, 2, 1);
		dialog.getDialogPane().setContent(grid);

		final BookingForm[] resultHolder = new BookingForm[1];
		Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
		okButton.addEventFilter(ActionEvent.ACTION, event -> {
			try {
				Room room = creating ? roomChoice.getValue() : existing.getRoom();
				if (room == null) {
					throw new IllegalArgumentException("Select a room");
				}
				LocalDate startD = Optional.ofNullable(startDate.getValue()).orElseThrow(() -> new IllegalArgumentException("Start date required"));
				LocalDate endD = Optional.ofNullable(endDate.getValue()).orElseThrow(() -> new IllegalArgumentException("End date required"));
				LocalTime startT = parseTime(startTime.getText());
				LocalTime endT = parseTime(endTime.getText());
				LocalDateTime start = LocalDateTime.of(startD, startT);
				LocalDateTime end = LocalDateTime.of(endD, endT);
				if (!start.isBefore(end)) {
					throw new IllegalArgumentException("Start must be before end");
				}
				resultHolder[0] = new BookingForm(room, start, end);
				errorLabel.setText("");
			} catch (RuntimeException ex) {
				errorLabel.setText(ex.getMessage());
				event.consume();
			}
		});

		dialog.setResultConverter(button -> button == okType ? resultHolder[0] : null);

		dialog.showAndWait().ifPresent(form -> {
			try {
				if (creating) {
					Booking created = schedulerSystem.createBooking(
						currentUser,
						form.room().getName(),
						form.start(),
						form.end()
					);
					setStatus("Created booking " + created.getId());
				} else {
					schedulerSystem.updateBooking(
						currentUser,
						existing.getId(),
						form.start(),
						form.end()
					);
					setStatus("Updated booking " + existing.getId());
				}
				refreshBookings();
			} catch (RuntimeException ex) {
				showError("Booking error", ex.getMessage());
			}
		});
	}

	private void confirmDeleteBooking(Booking booking) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Cancel Booking");
		alert.setHeaderText("Cancel booking " + booking.getId() + "?");
		alert.setContentText(
			booking.getRoom().getName() +
			"\n" +
			booking.getStart().format(DATE_TIME_FORMAT) +
			" -> " +
			booking.getEnd().format(DATE_TIME_FORMAT)
		);
		alert.showAndWait().ifPresent(response -> {
			if (response == ButtonType.OK) {
				try {
					schedulerSystem.cancelBooking(currentUser, booking.getId());
					setStatus("Cancelled booking " + booking.getId());
					refreshBookings();
				} catch (RuntimeException ex) {
					showError("Unable to cancel booking", ex.getMessage());
				}
			}
		});
	}

	private void showUserDialog() {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Create User");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField usernameField = new TextField();
		usernameField.setPromptText("Username");

		PasswordField passwordField = new PasswordField();
		passwordField.setPromptText("Password");

		ComboBox<Role> roleChoice = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
		roleChoice.getSelectionModel().select(Role.USER);

		Label errorLabel = new Label();
		errorLabel.setStyle("-fx-text-fill: red;");

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.addRow(0, new Label("Username"), usernameField);
		grid.addRow(1, new Label("Password"), passwordField);
		grid.addRow(2, new Label("Role"), roleChoice);
		grid.add(errorLabel, 0, 3, 2, 1);
		dialog.getDialogPane().setContent(grid);

		Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(ActionEvent.ACTION, event -> {
			try {
				String username = usernameField.getText().trim();
				String password = passwordField.getText();
				Role role = roleChoice.getValue();
				if (username.isEmpty()) {
					throw new IllegalArgumentException("Username is required");
				}
				if (password.isEmpty()) {
					throw new IllegalArgumentException("Password is required");
				}
				schedulerSystem.createUser(currentUser, username, password, role);
				setStatus("Created user " + username);
				refreshUsers();
				errorLabel.setText("");
			} catch (RuntimeException ex) {
				errorLabel.setText(ex.getMessage());
				event.consume();
			}
		});

		dialog.showAndWait();
	}

	private void confirmDeleteUser(User user) {
		if (!currentUser.getRole().canManageUsers()) {
			return;
		}
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Delete User");
		alert.setHeaderText("Delete user " + user.getUsername() + "?");
		alert.setContentText("This action cannot be undone.");
		alert.showAndWait().ifPresent(response -> {
			if (response == ButtonType.OK) {
				try {
					schedulerSystem.deleteUser(currentUser, user.getUsername());
					setStatus("Deleted user " + user.getUsername());
					refreshUsers();
				} catch (RuntimeException ex) {
					showError("Unable to delete user", ex.getMessage());
				}
			}
		});
	}

	private LocalTime parseTime(String text) {
		try {
			return LocalTime.parse(text.trim(), TIME_FORMAT);
		} catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("Time must match HH:mm");
		}
	}

	private void setStatus(String message) {
		if (statusLabel != null) {
			statusLabel.setText(message);
		}
	}

	private void showError(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle(title);
		alert.setHeaderText(title);
		alert.setContentText(message);
		alert.showAndWait();
	}

	private record BookingForm(Room room, LocalDateTime start, LocalDateTime end) {}
}
