package com.devopshub.app;

/**
 * Standard launcher class to bypass JavaFX module path restrictions when launching as a standalone fat JAR.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
