package com.flowstate;

import javafx.application.Application;

/**
 * Standard launcher class to bypass JavaFX runtime module checks when executing from shaded or non-modular classpaths.
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(FlowStateApp.class, args);
    }
}
