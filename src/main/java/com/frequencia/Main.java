package com.frequencia;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Frequência 87 — jogo de terror/tensão em JavaFX.
 *
 * Você é o único operador de rádio de uma estação retransmissora isolada
 * durante um apagão. Algo responde na frequência de emergência 87.0 MHz,
 * e não deveria. Encontre os 3 fusíveis, restaure a energia no quadro
 * elétrico e fuja antes que a Estática te alcance — mas cuidado: ela só
 * se move quando você não está com a lanterna apontada para ela.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        GameEngine engine = new GameEngine();

        StackPane root = new StackPane(engine.getCanvas());
        root.setStyle("-fx-background-color: black;");
        Scene scene = new Scene(root, GameMap.PIXEL_WIDTH, GameMap.PIXEL_HEIGHT + 70);

        engine.attachInput(scene);

        stage.setTitle("Frequência 87");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        engine.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
