package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ESPControlServer implements Runnable{

    public static final int DEFAULT_PORT = 4444;
    public static final int DEFAULT_POOL_SIZE = 20;

    private final ServerSocket espSocket;
    private final ExecutorService pool;
    private HashMap<Byte, ESPClientHandler> activeHandlers;
    private Lock handlerLock;

    public ESPControlServer() throws IOException{

        this.handlerLock = new ReentrantLock();
        this.activeHandlers = new HashMap<>();

        System.out.println("Creating Server");
        espSocket = new ServerSocket(DEFAULT_PORT);
        pool = Executors.newFixedThreadPool(DEFAULT_POOL_SIZE);
    }

    public ESPControlServer(int port, int poolSize) throws IOException{

        this.handlerLock = new ReentrantLock();
        this.activeHandlers = new HashMap<>();

        System.out.println("Creating Server");
        espSocket = new ServerSocket(port);
        pool = Executors.newFixedThreadPool(poolSize);
    }

    public void addRequest(String command, byte id){
        this.handlerLock.lock();
        ESPClientHandler clientHandler = this.activeHandlers.get(id);

        if(clientHandler != null){
            clientHandler.addRequest(command);
        }

        this.handlerLock.unlock();
    }
    public void removeHandler(byte id){
        this.handlerLock.lock();
        this.activeHandlers.remove(id);
        this.handlerLock.unlock();
    }



    public void run() {
        try{
            while(true){
                ESPClientHandler clientHandler = new ESPClientHandler(espSocket.accept(), this::removeHandler);
                this.handlerLock.lock();
                activeHandlers.put(clientHandler.getID(), clientHandler);
                this.handlerLock.unlock();
                pool.execute(clientHandler);
            }
        } catch (IOException e){
            pool.shutdown();
        }
    }

}