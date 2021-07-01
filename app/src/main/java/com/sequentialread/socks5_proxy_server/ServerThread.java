package com.sequentialread.socks5_proxy_server;

// original code: https://github.com/edveen/AndroidSocks5Proxy

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * Created by JYM on 2016/7/12.
 */
public class ServerThread implements Runnable {

    private Socket socket;
    private String TAG = "socks5_proxy_server";
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

            // https://datatracker.ietf.org/doc/html/rfc1928 page 3
            // first byte the client sends is the socks version (must be 0x05)
            // second byte specifies the number of SOCKS5 methods that the client wants to use.
            byte[] clientHello = new byte[2];
            innerInputStream.read(clientHello, 0, 2);
            Log.d(TAG, bytes2HexString( clientHello  ));
            // only support version 5
            if(clientHello[0] != (byte)0x05) {
                socket.close();
                return;
            }
            int numberOfMethods = byte2int(clientHello[1]);
            byte[] methods = new byte[numberOfMethods];
            innerInputStream.read(methods, 0, numberOfMethods);
            Log.d(TAG, bytes2HexString( methods  ));
            boolean foundNoAuthRequiredMethod = false;
            for (byte method : methods) {
                if(method == (byte)0x00) {
                    foundNoAuthRequiredMethod = true;
                }
            }
            if(!foundNoAuthRequiredMethod) {
                // we only accept the "no authentication required" method.
                // if it is not requested, then we respond with  NO ACCEPTABLE METHODS (0xFF)
                byte noAcceptableMethods = (byte)0xFF;
                innerOutputStream.write(new byte[]{noAcceptableMethods});
                innerOutputStream.flush();
                socket.close();
                return;
            }
            // the client requested the "no authentication required" method 0x00,
            // so we will respond saying that we, the server, are version 5 and we prefer that method.

            Log.d(TAG, "ok we got the NoAuthRequiredMethod, sending 0500");
            innerOutputStream.write(new byte[]{(byte)0x05, (byte)0x00});
            innerOutputStream.flush();

            // now the "method dependent subnegotiation" is supposed to happen
            // but I think that for "no authentication required" nothing happens and we skip it.
            // so up next we listen for socks5 commands from the client.

            byte[] clientCommand = new byte[4];

            Log.d(TAG, "trying to read clientCommand");
            innerInputStream.read(clientCommand, 0, 4);
            Log.d(TAG, bytes2HexString( clientCommand  ));

            // https://datatracker.ietf.org/doc/html/rfc1928 page 4
            if(clientCommand[0] != (byte)0x05) {
                Log.e(TAG, "closing connection because socks version '"+bytes2HexString( new byte[]{clientCommand[0]}  )+"' is not supported. expected 05.");
                socket.close();
                return;
            }
            if(clientCommand[1] != (byte)0x01) {
                Log.e(TAG, "closing connection because socks command '"+bytes2HexString( new byte[]{clientCommand[1]}  )+"' is not supported. expected 01 (CONNECT).");
                innerOutputStream.write(new byte[]{0x05, 0x07}); //version 05, 07 "Command not supported"
                innerOutputStream.flush();
                socket.close();
                return;
            }
            if(clientCommand[3] != (byte)0x01) {
                Log.e(TAG, "closing connection because socks address type '"+bytes2HexString( new byte[]{clientCommand[3]}  )+"' is not supported. expected 01 (IP version 4).");
                innerOutputStream.write(new byte[]{0x05, 0x08}); //version 05, 08 "Address type not supported"
                innerOutputStream.flush();
                socket.close();
                return;
            }

            byte[] connectAddress = new byte[6];
            Log.d(TAG, "trying to read connectAddress");
            innerInputStream.read(connectAddress, 0, 6);
            Log.d(TAG, bytes2HexString( connectAddress  ));

            String ip = byte2int(connectAddress[0]) + "." + byte2int(connectAddress[1]) + "." + byte2int(connectAddress[2]) + "." + byte2int(connectAddress[3]);
            int port = byte2int(connectAddress[4]) * 256 + byte2int(connectAddress[5]);
            Log.d(TAG, ip+":"+port);

            Socket outerSocket = null;
            InputStream outerInputStream = null;
            OutputStream outerOutputStream = null;
            try {
                outerSocket = new Socket(ip, port);
                outerInputStream = outerSocket.getInputStream();
                outerOutputStream = outerSocket.getOutputStream();
            } catch (Exception ex) {
                innerOutputStream.write(new byte[]{0x05, 0x01}); //version 05, 01 "general SOCKS server failure"
                innerOutputStream.flush();
                return;
            }
            Log.e(TAG, "Connected to " + ip + ":" + port);

            // respond to the client with the connection details once the connection has been established.
            byte[] remoteIp = outerSocket.getLocalAddress().getAddress();
            int remotePort = outerSocket.getLocalPort();

            innerOutputStream.write(new byte[]{
                    0x05, 0x00, 0x00, 0x01, // version 5, success 0x00, reserved (null), address  type 0x01 (ip version 4)
                    remoteIp[0],remoteIp[1],remoteIp[2],remoteIp[3],
                    (byte) (remotePort >> 8), (byte) (remotePort & 0xff)
            }, 0, 10);
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

    private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f' };

    public static String bytes2HexString(byte[] bytes) {
        final int nBytes = bytes.length;
        char[] result = new char[2 * nBytes];

        int j = 0;
        for (byte aByte : bytes) {
            result[j++] = HEX[(0xF0 & aByte) >>> 4];
            result[j++] = HEX[(0x0F & aByte)];
        }

        return new String(result);
    }
}
