package server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ESPClientHandler implements Runnable{

    public interface DisconnectCallback{
        void onDisconnect(byte id);
    }

    public static final byte READ_MASK = (byte) (0x1 << 7);
    public static final byte MESSAGE_PAUSE = (byte) 0xFF;
    public static final byte COMMAND_REFRESH = (byte) 0xFF;
    public static final byte COMMAND_DISCONNECT = (byte) 0xFE;

    private byte id;

    private Socket client;
    private OutputStream clientOutputStream;
    private InputStream clientInputStream;

    private HashMap<String, Byte> commands;
    private Queue<String> requests;
    private Stack<Map.Entry<String, String>> results;
    private DisconnectCallback disconnectCallback;

    private Lock requestLock, resultLock;
    public ESPClientHandler(Socket client, DisconnectCallback callback) {
        this.requestLock = new ReentrantLock();
        this.resultLock = new ReentrantLock();
        this.commands = new HashMap<>();
        this.requests = new LinkedList<>();
        this.disconnectCallback = callback;

        this.client = client;
        try {
            this.client.setKeepAlive(true);
            this.clientOutputStream = client.getOutputStream();
            this.clientInputStream = client.getInputStream();

            id = (byte) this.clientInputStream.read();

            this.refresh();

        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Client " + id + " connected!");
    }

    public byte getID(){
        return this.id;
    }
    public void disconnect() throws IOException{
        this.clientOutputStream.write(COMMAND_DISCONNECT);
        this.client.close();
        this.disconnectCallback.onDisconnect(this.id);
    }

    public void refresh() throws IOException {
        this.clientOutputStream.write(COMMAND_REFRESH);

        this.commands.clear();

        StringBuilder command = new StringBuilder();
        boolean stringReceived = false;
        while(this.clientInputStream.available() > 0){
            byte next = (byte) this.clientInputStream.read();

            if(!stringReceived){
                if(next != MESSAGE_PAUSE) {
                    command.append(new String(new byte[]{next}, Charset.defaultCharset()));
                } else if(next == MESSAGE_PAUSE) {
                    stringReceived = true;
                }
            } else {
                this.commands.put(command.toString(), next);
                command = new StringBuilder();
            }
        }

        this.commands.put("disconnect", COMMAND_DISCONNECT);
        this.commands.put("refresh", COMMAND_REFRESH);
    }

    public Map.Entry<String, String> pollResult() throws EmptyStackException {
        return results.pop();
    }
    public boolean checkReserved(String command) throws IOException {
        if(command.equals("disconnect")) {
            this.disconnect();
            return true;
        } else if(command.equals("refresh")){
            this.refresh();
            return true;
        }
        return false;
    }
    public void write(String command, byte... bytes) throws IOException {

        if(command.equals("disconnect")) {
            this.disconnect();
        } else if(command.equals("refresh")){
            this.refresh();
        }

        if(this.commands.containsKey(command)){

            byte indicator = this.commands.get(command);
            if((indicator & READ_MASK) == 0) {
                this.clientOutputStream.write(this.commands.get(command));
                if (bytes.length > 0) {
                    this.clientOutputStream.write(bytes);
                }
            } else {
                throw new IOException("Bad Instruction");
            }
        }
    }

    public String read(String command) throws IOException{
        if(this.commands.containsKey(command)){

            byte indicator = this.commands.get(command);

            if((indicator & READ_MASK) > 0) {
                this.clientOutputStream.write(indicator);
                BufferedReader clientInput = new BufferedReader(new InputStreamReader(this.clientInputStream));

                return clientInput.readLine();

            } else {
                throw new IOException("Bad Instruction");
            }
        }

        return null;
    }

    public void addRequest(String request){
        this.requestLock.lock();
        requests.add(request);
        this.requestLock.unlock();
    }

    public void run() {
        while(true){
            try{
                this.requestLock.lock();

                String command = null;

                if(!this.requests.isEmpty()){
                    command = this.requests.poll();
                }

                this.requestLock.unlock();

                if(command != null && !this.checkReserved(command)) {
                    String resPacket = this.read(command);
                    if(resPacket != null){

                        this.resultLock.lock();
                        this.results.push(new AbstractMap.SimpleEntry<String, String>(command, resPacket));
                        this.resultLock.unlock();
                    }
                    this.write(command);
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }





}
