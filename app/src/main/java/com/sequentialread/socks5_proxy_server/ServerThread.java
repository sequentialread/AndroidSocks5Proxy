package com.sequentialread.socks5_proxy_server;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by JYM on 2016/7/12.
 */
public class ServerThread implements Runnable {

    private Socket socket;
    private String TAG = this.getClass().getName();
    private int BUFF_SIZE = 1024 * 100;

    public ServerThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            InputStream innerInputStream = socket.getInputStream();
            OutputStream innerOutputStream = socket.getOutputStream();
            byte[] buff = new byte[BUFF_SIZE];
            int rc;
            ByteArrayOutputStream byteArrayOutputStream;

            /**
             * The client will send 510 to the proxy, so the result of execution here is buff={5,1,0}
             * Caution: This cannot be combined with the following innerInputStream.read(buff, 0, 10); into innerInputStream.read(buff, 0, 13);
             *          I have tried, and most of the cases have no effect, but occasionally there will be major bugs (cannot read the external network ip), as for the reason is not known yet
             *          It seems that the operation of this type of input and output is still a little more prudent, don't be too impatient
             */
            innerInputStream.read(buff, 0, 3);

            /**
             *  The proxy sends a response to the client {5,0}
             */
            byte[] firstAckMessage = new byte[]{5, 0};
            byte[] secondAckMessage = new byte[10];
            innerOutputStream.write(firstAckMessage);
            innerOutputStream.flush();

            /**
             * The client sends the command 5101 + destination address (4Bytes) + destination port (2Bytes)
             * That is {5,1,0,1,IPx1,IPx2,IPx3,IPx4,PORTx1,PORTx2} a total of 10 bits
             * For example, sent to port 80 of the 52.88.216.252 server, then the buff here is {5,1,0,1,52,88,-40,-4,0,80} (where each bit is byte, so in- Between 128~127, you can convert it to 0~255 by yourself)
             */
            innerInputStream.read(buff, 0, 10);

            String IP = byte2int(buff[4]) + "." + byte2int(buff[5]) + "." + byte2int(buff[6]) + "." + byte2int(buff[7]);
            int port = byte2int(buff[8]) * 256 + byte2int(buff[9]);

            Log.e("ServerThread", "Connected to " + IP + ":" + port);
            Socket outerSocket = new Socket(IP, port);
            InputStream outerInputStream = outerSocket.getInputStream();
            OutputStream outerOutputStream = outerSocket.getOutputStream();

            /**
             * The proxy returns a response 5+0+0+1+Internet socket bound IP address (4-byte hexadecimal representation) + Internet socket bound port number (2-byte hexadecimal System representation)
             */
            byte ip1[] = new byte[4];
            int port1 = 0;
            ip1 = outerSocket.getLocalAddress().getAddress();
            port1 = outerSocket.getLocalPort();

            secondAckMessage[0] = 5;
            secondAckMessage[1] = 0;
            secondAckMessage[2] = 0;
            secondAckMessage[3] = 1;
            secondAckMessage[4] = ip1[0];
            secondAckMessage[5] = ip1[1];
            secondAckMessage[6] = ip1[2];
            secondAckMessage[7] = ip1[3];
            secondAckMessage[8] = (byte) (port1 >> 8);
            secondAckMessage[9] = (byte) (port1 & 0xff);
            innerOutputStream.write(secondAckMessage, 0, 10);
            innerOutputStream.flush();

            /**
             * New thread: continuously read data from the external network and send it to the client
             */
            SocksResponseThread responseThread = new SocksResponseThread(outerInputStream, innerOutputStream);
            responseThread.start();

            /**
             * This thread: continuously read data from the client and send it to the external networkk
             */
            byteArrayOutputStream = new ByteArrayOutputStream();
            while ((rc = innerInputStream.read(buff, 0, BUFF_SIZE)) > 0) {
                outerOutputStream.write(buff, 0, rc);
                byteArrayOutputStream.write(buff, 0, rc);
                outerOutputStream.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public int byte2int(byte b) {
        return b & 0xff;
    }

}