package scheduler.ui;

import javafx.application.Application;

/**
 * Launches the JavaFX application.
 */
public final class GuiLauncher {

	private GuiLauncher() {}

	public static void main(String[] args) {
		Application.launch(SchedulerApp.class, args);
	}
}
