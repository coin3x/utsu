package com.utsusynth.utsu;

import java.io.InputStream;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.utsusynth.utsu.controller.UtsuController;
import com.utsusynth.utsu.model.ModelModule;
import com.utsusynth.utsu.view.ViewModule;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * UTAU-ish Thingy with Some Updates (UTSU)
 */
public class UtsuApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Set up Guice.
        Injector injector =
                Guice.createInjector(new UtsuModule(), new ModelModule(), new ViewModule());
        FXMLLoader loader = injector.getInstance(FXMLLoader.class);

        // Construct scene.
        InputStream fxml = getClass().getResourceAsStream("/fxml/UtsuScene.fxml");
        BorderPane pane = loader.load(fxml);
        Scene scene = new Scene(pane);
        scene.getStylesheets().add("/css/piano_roll.css");

        // Set the stage.
        primaryStage.setScene(scene);
        primaryStage.setTitle("Utsu");
        primaryStage.show();

        // Set up an event that runs when the program is closed.
        primaryStage.setOnCloseRequest((windowEvent) -> {
            UtsuController controller = (UtsuController) loader.getController();
            if (!controller.onCloseWindow()) {
                windowEvent.consume();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
