package cz.hudecekpetr.snowride;

import javafx.stage.Stage;

public class SnowConstants {
    public static final SnowConstants INSTANCE = new SnowConstants();
	public static Stage primaryStage;

    public static void setStageTitle(String suffix) {
        if (suffix == null) {
            suffix = "";
        }
        primaryStage.setTitle("Snowride " + suffix);
    }
}