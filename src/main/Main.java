package main;

import server.ESPControlServer;

import java.io.IOException;

public class Main {

    public static void main(String[] args){
        try {
            ESPControlServer server = new ESPControlServer();

            Thread service = new Thread(server);
            service.start();
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
