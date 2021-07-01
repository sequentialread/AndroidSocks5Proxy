package com.sequentialread.socks5_proxy_server;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * Created by JYM on 2016/7/14.
 */
public class SocksResponseThread extends Thread {

    private InputStream in;
    private OutputStream out;
    private final String connectionDescription;
    private int BUFF_SIZE = 1024 * 100;

    public SocksResponseThread(InputStream in, OutputStream out, String connectionDescription) {
        this.in = in;
        this.out = out;
        this.connectionDescription = connectionDescription;
    }

    @Override
    public void run() {
        int readbytes = 0;
        byte buf[] = new byte[BUFF_SIZE];
        while (true) {
            try {
                if (readbytes == -1) break;
                readbytes = in.read(buf, 0, BUFF_SIZE);
                if (readbytes > 0) {
                    out.write(buf, 0, readbytes);
                }
                out.flush();
            } catch (SocketException e) {
                if(!e.getMessage().equals("Socket closed")) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                OnScreenLog.log(connectionDescription+": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}
