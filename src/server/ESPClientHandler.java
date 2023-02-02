package server;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;
import java.util.logging.Formatter;

public class ESPClientHandler implements Runnable{

    public interface DisconnectCallback{
        void onDisconnect(byte id);
    }

    public class LogFormatter extends Formatter {
        @Override
        public String format(LogRecord message) {
            StringBuffer sb = new StringBuffer();
            sb.append(message.getMessage() + "\n");
            return sb.toString();
        }
    }

    public static final byte READ_MASK = (byte) (0x1 << 7);
    public static final byte STREAM_MASK = (byte) (0x1 << 7);
    public static final byte MESSAGE_PAUSE = (byte) 0xFF;
    public static final byte COMMAND_REFRESH = (byte) 0xFF;
    public static final byte COMMAND_DISCONNECT = (byte) 0xFE;

    private byte id;

    private Socket client;
    private OutputStream clientOutputStream;
    private InputStream clientInputStream;

    private HashMap<String, Byte> commands;
    private Queue<Map.Entry<String, ByteBuffer>> requests;
    private Stack<Map.Entry<String, String>> results;
    private DisconnectCallback disconnectCallback;

    private Lock requestLock, resultLock, finishLock;

    private Logger logger;

    private boolean finished = false, streaming = false;
    public ESPClientHandler(Socket client, DisconnectCallback callback) {
        this.requestLock = new ReentrantLock();
        this.resultLock = new ReentrantLock();
        this.finishLock = new ReentrantLock();
        this.commands = new HashMap<>();
        this.requests = new LinkedList<>();
        this.results = new Stack<>();
        this.disconnectCallback = callback;

        this.client = client;
        try {
            this.client.setKeepAlive(true);
            this.clientOutputStream = client.getOutputStream();
            this.clientInputStream = client.getInputStream();

            id = (byte) this.clientInputStream.read();
            this.streaming = (this.id & STREAM_MASK) < 0;

            if(!this.streaming)
                this.refresh();
            this.logger = Logger.getLogger("Client#" + id);
            this.logger.setUseParentHandlers(false);
            FileHandler logFileHandler = new FileHandler("./Client_" + id + ".txt");
            this.logger.addHandler(logFileHandler);
            logFileHandler.setFormatter(new LogFormatter());

        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Client " + id + " connected!");
    }

    public byte getID(){
        return this.id;
    }
    public void disconnect() throws IOException{

        System.out.println("Exiting service for unused or older version of Client " + this.getID() + "");

        this.clientOutputStream.write(COMMAND_DISCONNECT);
        this.client.close();
        this.disconnectCallback.onDisconnect(this.id);
        this.finishLock.lock();
        finished = true;
        this.finishLock.unlock();
    }

    public void refresh() throws IOException {
        this.clientOutputStream.write(COMMAND_REFRESH);

        this.commands.clear();

        StringBuilder command = new StringBuilder();
        boolean stringReceived = false;
        int commands = this.clientInputStream.read();

        while(commands > 0){
            byte next = (byte) this.clientInputStream.read();
            if(!stringReceived){
                if(next != MESSAGE_PAUSE) {
                    command.append(new String(new byte[]{next}, Charset.defaultCharset()));
                    //System.out.println("Waiting for info..." + command.toString() + ":" + this.clientInputStream.available());
                } else if(next == MESSAGE_PAUSE) {
                    stringReceived = true;
                }
            } else {
                System.out.println("Received Command " + command + ":" + next);
                this.commands.put(command.toString(), next);
                command = new StringBuilder();
                stringReceived = false;
                commands--;
            }
        }

        this.commands.put("disconnect", COMMAND_DISCONNECT);
        this.commands.put("refresh", COMMAND_REFRESH);
    }

    public Map.Entry<String, String> pollResult() {

        if(results.size() > 0){
            return results.pop();
        }
        return null;
    }
    public boolean checkReserved(String command) throws IOException {
        if(command.equals("disconnect")) {
            this.disconnect();
            return true;
        } else if(command.equals("refresh")){
            this.refresh();
            return true;
        } else if(command.equals("stream")){
            this.streaming = !this.streaming;
        }
        return false;
    }

    public void write(String command, byte... bytes) throws IOException {
        if(this.commands.containsKey(command)){
            byte indicator = this.commands.get(command);
            if((indicator & READ_MASK) == 0) {
                this.clientOutputStream.write(this.commands.get(command));
                if (bytes.length > 0) {
                    System.out.println(Arrays.toString(bytes));
                    this.clientOutputStream.write(bytes);
                }
            }
        }
    }

    public String read(String command) throws IOException{
        if(this.commands.containsKey(command)){

            byte indicator = this.commands.get(command);

            if((indicator & READ_MASK) < 0) {
                this.clientOutputStream.write(indicator);
                BufferedReader clientInput = new BufferedReader(new InputStreamReader(this.clientInputStream));

                return clientInput.readLine();

            }
        }

        return null;
    }

    public String readStream() throws IOException{
        if(this.clientInputStream.available() > 0){
            BufferedReader clientInput = new BufferedReader(new InputStreamReader(this.clientInputStream));
            return clientInput.readLine();
        }
        return null;
    }


    public void addRequest(String request, ByteBuffer buffer){
        this.requestLock.lock();
        requests.add(new AbstractMap.SimpleEntry<String, ByteBuffer>(request, buffer));
        this.requestLock.unlock();
    }

    public void run() {
        while(true){
            try{
                String resPacket = null;
                String command = null;

                if(!this.streaming) {
                    this.requestLock.lock();

                    Map.Entry<String, ByteBuffer> commandPair;
                    ByteBuffer buffer = null;

                    if (!this.requests.isEmpty()) {
                        commandPair = this.requests.poll();
                        command = commandPair.getKey();
                        buffer = commandPair.getValue();

                        this.logger.log(Level.INFO,command + " " + buffer.toString());
                    }

                    this.requestLock.unlock();

                    if (command != null && !this.checkReserved(command)) {
                        resPacket = this.read(command);
                        this.write(command, buffer.array());
                    }

                } else {
                    resPacket = this.readStream();
                }

                if (resPacket != null) {
                    this.resultLock.lock();
                    this.logger.log(Level.INFO, resPacket);
                    if(!streaming)
                        this.results.push(new AbstractMap.SimpleEntry<String, String>(command, resPacket));
                    this.resultLock.unlock();
                }

                this.finishLock.lock();
                if(this.finished){
                    this.finishLock.unlock();
                    break;
                }
                this.finishLock.unlock();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }





}
