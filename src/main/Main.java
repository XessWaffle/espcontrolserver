package main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import server.ESPControlServer;

import java.io.IOException;

public class Main extends Application {

    public static void main(String[] args) {
        try{
            ESPControlServer server = new ESPControlServer();

            Thread service = new Thread(server);
            service.start();
        } catch(Exception e){
            e.printStackTrace();
        }


        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }
}
