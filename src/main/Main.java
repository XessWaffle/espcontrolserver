package main;

import server.ESPControlServer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Main {

    static final int MAX_BUFFER = 300;
    static byte currentId;
    static ESPControlServer server;

    static Lock serverLock;

    public static void main(String[] args) {
        // Run GUI codes in the Event-Dispatching thread for thread safety

        Scanner scan = new Scanner(System.in);
        serverLock = new ReentrantLock();

        try{
            server = new ESPControlServer();

            Thread service = new Thread(server);
            service.start();

            Thread readService = new Thread(() -> {
                System.out.println("Starting Read Service");
                while(true) {
                    serverLock.lock();
                    String result = server.readResult(currentId);
                    serverLock.unlock();
                    if(result != null)
                        System.out.println(result);
                }
            });
            readService.start();
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        while(true){
            if(scan.hasNextLine()) {
                String next = scan.nextLine();

                if (next.contains("local")) {
                    if (next.contains("switch")) {
                        handleLocalSwitch(next);
                    } else if (next.contains("disconnect") || next.contains("quit") || next.contains("shutdown")) {
                        handleLocalDisconnect();
                    }
                } else {

                    String[] check = next.split(" ");

                    ByteBuffer buffer = ByteBuffer.allocate(MAX_BUFFER);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);

                    for (int i = 1; i < check.length; i++) {
                        buffer.putInt(Integer.parseInt(check[i]));
                    }

                    ByteBuffer trueBuffer = ByteBuffer.allocate(buffer.position());

                    trueBuffer.put(0, buffer, 0, buffer.position());

                    serverLock.lock();
                    server.addRequest(check[0], currentId, trueBuffer);
                    serverLock.unlock();
                }
            }
        }
    }

    private static void handleLocalSwitch(String next){
        try {
            byte toVerify = Byte.parseByte(next.split(" ")[2]);

            if(server.hasHandler(toVerify)){
                currentId  = toVerify;
                System.out.println("Now talking to Client " + currentId);
            } else {
                System.out.println("No client with ID " + toVerify + " found");
            }

        } catch(Exception e){
            System.out.println("Incorrect client id provided, could not switch context!");
        }
    }

    private static void handleLocalDisconnect(){
        try {
            server.shutdown();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}
