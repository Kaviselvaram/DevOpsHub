package com.devopshub.ui.component;

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.HashMap;
import java.util.Map;

/**
 * Strict SVG-Only Icon Component.
 * Renders scalable vector icon paths natively in JavaFX.
 * Completely compliant with the project's SVG-only icon policy.
 */
public class SvgIcon extends StackPane {

    public enum IconName {
        DASHBOARD,
        PROJECTS,
        PIPELINES,
        EXECUTIONS,
        LOGS,
        ANALYTICS,
        SETTINGS,
        PLAY,
        STOP,
        CHECK_CIRCLE,
        ALERT_TRIANGLE,
        X_CIRCLE,
        REFRESH,
        SEARCH,
        PLUS,
        EDIT,
        TRASH,
        EXTERNAL_LINK,
        TERMINAL,
        CPU,
        DATABASE,
        INFO,
        FILTER,
        CHEVRON_RIGHT,
        CHEVRON_DOWN,
        COPY,
        LIGHTBULB,
        GIT_BRANCH,
        CLOCK,
        HOME,
        BELL,
        GRID,
        LIST,
        MAP,
        USER,
        SHARE,
        CALENDAR
    }

    private static final Map<IconName, String> PATH_DATA = new HashMap<>();

    static {
        // Standard Lucide/Feather 24x24 SVG vector path definitions
        PATH_DATA.put(IconName.CALENDAR, "M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z M16 2v4 M8 2v4 M3 10h18");
        PATH_DATA.put(IconName.DASHBOARD, "M3 3h7v7H3zm11 0h7v7h-7zm0 11h7v7h-7zM3 14h7v7H3z");
        PATH_DATA.put(IconName.PROJECTS, "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z");
        PATH_DATA.put(IconName.PIPELINES, "M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83");
        PATH_DATA.put(IconName.EXECUTIONS, "M13 2L3 14h9l-1 8 10-12h-9l1-8z");
        PATH_DATA.put(IconName.LOGS, "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8");
        PATH_DATA.put(IconName.ANALYTICS, "M18 20V10 M12 20V4 M6 20v-6");
        PATH_DATA.put(IconName.SETTINGS, "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm7.4 1.4l1.6 2.8-2.8 1.6-1.6-2.8a7.9 7.9 0 0 1-2.6 1.1v3.3h-4v-3.3a7.9 7.9 0 0 1-2.6-1.1l-1.6 2.8-2.8-1.6 1.6-2.8A7.9 7.9 0 0 1 3.9 12H.6v-4h3.3c.2-.9.6-1.8 1.1-2.6L3.4 2.6 6.2 1l1.6 2.8c.8-.5 1.7-.9 2.6-1.1V-.6h4v3.3c.9.2 1.8.6 2.6 1.1l1.6-2.8 2.8 1.6-1.6 2.8c.5.8.9 1.7 1.1 2.6h3.3v4h-3.3c-.2.9-.6 1.8-1.1 2.6z");
        PATH_DATA.put(IconName.PLAY, "M5 3l14 9-14 9V3z");
        PATH_DATA.put(IconName.STOP, "M6 6h12v12H6z");
        PATH_DATA.put(IconName.CHECK_CIRCLE, "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3");
        PATH_DATA.put(IconName.ALERT_TRIANGLE, "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01");
        PATH_DATA.put(IconName.X_CIRCLE, "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm3.54 13.54L12 12l-3.54 3.54-1.42-1.42L10.58 12 7.04 8.46l1.42-1.42L12 10.58l3.54-3.54 1.42 1.42L13.42 12l3.54 3.54z");
        PATH_DATA.put(IconName.REFRESH, "M23 4v6h-6 M1 20v-6h6 M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15");
        PATH_DATA.put(IconName.SEARCH, "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zm10 2l-4.35-4.35");
        PATH_DATA.put(IconName.PLUS, "M12 5v14M5 12h14");
        PATH_DATA.put(IconName.EDIT, "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z");
        PATH_DATA.put(IconName.TRASH, "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2");
        PATH_DATA.put(IconName.EXTERNAL_LINK, "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6 M15 3h6v6 M10 14L21 3");
        PATH_DATA.put(IconName.TERMINAL, "M4 17l6-6-6-6 M12 19h8");
        PATH_DATA.put(IconName.CPU, "M4 4h16v16H4z M9 9h6v6H9z M9 1v3 M15 1v3 M9 20v3 M15 20v3 M20 9h3 M20 14h3 M1 9h3 M1 14h3");
        PATH_DATA.put(IconName.DATABASE, "M12 2C6.48 2 2 3.79 2 6v12c0 2.21 4.48 4 10 4s10-1.79 10-4V6c0-2.21-4.48-4-10-4zm0 6c-4.42 0-8-1.34-8-3s3.58-3 8-3 8 1.34 8 3-3.58 3-8 3z");
        PATH_DATA.put(IconName.INFO, "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 14v-4m0-4h.01");
        PATH_DATA.put(IconName.FILTER, "M22 3H2l8 9.46V19l4 2v-8.54L22 3z");
        PATH_DATA.put(IconName.CHEVRON_RIGHT, "M9 18l6-6-6-6");
        PATH_DATA.put(IconName.CHEVRON_DOWN, "M6 9l6 6 6-6");
        PATH_DATA.put(IconName.COPY, "M8 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4M16 4h4a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-4");
        PATH_DATA.put(IconName.LIGHTBULB, "M9 18h6 M10 22h4 M12 2a7 7 0 0 0-4.95 11.95c.78.78 1.25 1.83 1.25 2.95h7.4c0-1.12.47-2.17 1.25-2.95A7 7 0 0 0 12 2z");
        PATH_DATA.put(IconName.GIT_BRANCH, "M6 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6zm12 12a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM6 9v6a3 3 0 0 0 3 3h6");
        PATH_DATA.put(IconName.CLOCK, "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 5v5l4 2");
        PATH_DATA.put(IconName.HOME, "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10");
        PATH_DATA.put(IconName.BELL, "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0");
        PATH_DATA.put(IconName.GRID, "M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z");
        PATH_DATA.put(IconName.LIST, "M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01");
        PATH_DATA.put(IconName.MAP, "M1 6v16l7-4 8 4 7-4V2l-7 4-8-4-7 4z M8 2v16 M16 6v16");
        PATH_DATA.put(IconName.USER, "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z");
        PATH_DATA.put(IconName.SHARE, "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8 M16 6l-4-4-4 4 M12 2v13");
    }

    private final SVGPath svgPath;

    public SvgIcon(IconName iconName) {
        this(iconName, 18, Color.web("#94A3B8"));
    }

    public SvgIcon(IconName iconName, double size, Color color) {
        this.svgPath = new SVGPath();
        String path = PATH_DATA.getOrDefault(iconName, PATH_DATA.get(IconName.INFO));
        this.svgPath.setContent(path);

        // Normalize scaling
        double scale = size / 24.0;
        this.svgPath.setScaleX(scale);
        this.svgPath.setScaleY(scale);

        if (isFillIcon(iconName)) {
            this.svgPath.setFill(color);
            this.svgPath.setStroke(null);
        } else {
            this.svgPath.setFill(color);
            this.svgPath.setStroke(color);
            this.svgPath.setStrokeWidth(1.2);
        }

        this.setPrefSize(size, size);
        this.setMaxSize(size, size);
        this.getChildren().add(this.svgPath);
    }

    public void setColor(Color color) {
        this.svgPath.setFill(color);
        this.svgPath.setStroke(color);
    }

    private boolean isFillIcon(IconName name) {
        return name == IconName.PLAY || name == IconName.STOP || name == IconName.DASHBOARD ||
               name == IconName.PROJECTS || name == IconName.DATABASE || name == IconName.SETTINGS;
    }

    public static String getPath(IconName name) {
        return PATH_DATA.get(name);
    }
}
