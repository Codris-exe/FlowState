package com.flowstate;

/**
 * Bootstrap Launcher for FlowState.
 * Bypasses the JVM JavaFX module-path check so the project can be run
 * directly in IntelliJ IDEA, Eclipse, or from a JAR with a single click.
 */
public class Main {
    public static void main(String[] args) {
        FlowStateApp.main(args);
    }
}
