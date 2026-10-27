package com.devopshub.app;

/**
 * Standard launcher class to bypass JavaFX module path restrictions when launching as a standalone fat JAR.
 */
public class Launcher {
    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            String arg = args[0].trim();
            if (arg.equalsIgnoreCase("--version") || arg.equalsIgnoreCase("-v")) {
                System.out.println("DevOpsHub Desktop MVP 1.0.0 (Build: 2026-10-25)");
                return;
            } else if (arg.equalsIgnoreCase("--health")) {
                System.out.println("DevOpsHub System Health Check:");
                System.out.println("- Java Version: " + System.getProperty("java.version") + " (" + System.getProperty("os.name") + " " + System.getProperty("os.arch") + ")");
                boolean dockerOk = com.devopshub.execution.backend.ExecutionBackendFactory.getInstance().isContainerAvailable();
                System.out.println("- Execution Isolation Runtime: " + (dockerOk ? "Docker Container Available" : "Restricted Host Only (Fail-closed for untrusted repos)"));
                com.devopshub.config.DatabaseManager db = com.devopshub.config.DatabaseManager.getInstance();
                System.out.println("- Persistence Engine: " + db.getDatabaseType() + (db.isH2Fallback() ? " (H2 Fallback)" : " (Primary MySQL)"));
                System.out.println("- Database Healthy: " + db.testConnection());
                return;
            } else if (arg.equalsIgnoreCase("--help") || arg.equalsIgnoreCase("-h")) {
                System.out.println("DevOpsHub Desktop MVP 1.0.0");
                System.out.println("Usage:");
                System.out.println("  java -jar devopshub-desktop-1.0.0.jar            Launch the JavaFX GUI");
                System.out.println("  java -jar devopshub-desktop-1.0.0.jar --version  Print product version");
                System.out.println("  java -jar devopshub-desktop-1.0.0.jar --health   Run system health check");
                System.out.println("  java -jar devopshub-desktop-1.0.0.jar --help     Show this help");
                return;
            }
        }
        MainApp.main(args);
    }
}
