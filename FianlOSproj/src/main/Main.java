package main;

import gui.OSSimulatorGUI;
import javafx.application.Application;

/**
 * Entry point -- delegates directly to the JavaFX Application.
 * OSSimulatorGUI.start() handles algorithm selection, component
 * setup, and GUI construction all on the JavaFX Application Thread.
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(OSSimulatorGUI.class, args);
    }
}
