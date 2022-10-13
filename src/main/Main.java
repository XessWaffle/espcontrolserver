package main;

import server.ESPControlServer;
import java.util.Scanner;


public class Main {

    static byte currentId;
    static ESPControlServer server;

    public static void main(String[] args) {
        // Run GUI codes in the Event-Dispatching thread for thread safety

        Scanner scan = new Scanner(System.in);

        try{
            server = new ESPControlServer();

            Thread service = new Thread(server);
            service.start();
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        while(true){
            String next = scan.nextLine();

            if(next.contains("local")){
                if(next.contains("switch")){
                    handleLocalSwitch(next);
                } else if(next.contains("disconnect")){
                    handleLocalDisconnect(next);
                }

            } else {

            }
        }
    }

    public static void handleLocalSwitch(String next){
        try {
            byte toVerify = Byte.valueOf(next.split(" ")[1]);

            if(server.hasHandler(toVerify)){
                currentId  = toVerify;
                System.out.println("Now talking to Client " + currentId);
            }

        } catch(Exception e){
            System.out.println("Incorrect client id provided, could not switch context!");
        }
    }

    public static void handleLocalDisconnect(String next){

    }

}
