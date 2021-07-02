package com.sequentialread.socks5_proxy_server;

// original code: https://github.com/edveen/AndroidSocks5Proxy

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
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
        String connectionDescription = "error getting connection description";
        Socket outerSocket = null;
        try {
            InputStream innerInputStream = socket.getInputStream();
            OutputStream innerOutputStream = socket.getOutputStream();
            String clientAddress = socket.getRemoteSocketAddress().toString().substring(1);
            connectionDescription = clientAddress + "                     ";
            byte[] buff = new byte[BUFF_SIZE];
            int rc;
            ByteArrayOutputStream byteArrayOutputStream;

            // https://datatracker.ietf.org/doc/html/rfc1928 page 3
            // first byte the client sends is the socks version (must be 0x05)
            // second byte specifies the number of SOCKS5 methods that the client wants to use.
            byte[] clientHello = new byte[2];
            innerInputStream.read(clientHello, 0, 2);
            //Log.d(TAG, bytes2HexString( clientHello  ));
            // only support version 5
            if(clientHello[0] != (byte)0x05) {
                OnScreenLog.log(
                        "incoming connection "+connectionDescription
                        +" was closed because first byte '0x"+bytes2HexString(new byte[]{clientHello[0]})
                        +"' was != 0x05 (un-suppported SOCKS version)"
                );
                socket.close();
                return;
            }
            int numberOfMethods = byte2int(clientHello[1]);
            byte[] methods = new byte[numberOfMethods];
            innerInputStream.read(methods, 0, numberOfMethods);
            boolean foundNoAuthRequiredMethod = false;
            for (byte method : methods) {
                if(method == (byte)0x00) {
                    foundNoAuthRequiredMethod = true;
                }
            }
            if(!foundNoAuthRequiredMethod) {
                OnScreenLog.log(
                        "incoming connection "+connectionDescription
                        +" was closed because the client did not request the \"no authentication required\" method"
                );
                byte noAcceptableMethods = (byte)0xFF;
                innerOutputStream.write(new byte[]{noAcceptableMethods});
                innerOutputStream.flush();
                socket.close();
                return;
            }
            // the client requested the "no authentication required" method 0x00,
            // so we will respond saying that we, the server, are version 5 and we prefer that method.

            OnScreenLog.log(connectionDescription + " (accept)");
            innerOutputStream.write(new byte[]{(byte)0x05, (byte)0x00});
            innerOutputStream.flush();

            // now the "method dependent subnegotiation" is supposed to happen
            // but I think that for "no authentication required" nothing happens and we skip it.
            // so up next we listen for socks5 commands from the client.

            byte[] clientCommand = new byte[4];
            innerInputStream.read(clientCommand, 0, 4);

            // https://datatracker.ietf.org/doc/html/rfc1928 page 4
            if(clientCommand[0] != (byte)0x05) {
                OnScreenLog.log(
                        "incoming connection "+connectionDescription
                                + " was closed because socks version '0x"+bytes2HexString( new byte[]{clientCommand[0]}  )
                                +"' is not supported. expected 0x05."
                );
                socket.close();
                return;
            }
            if(clientCommand[1] != (byte)0x01) {
                OnScreenLog.log(
                        "incoming connection "+connectionDescription
                                + " was closed because socks command '0x"+bytes2HexString( new byte[]{clientCommand[1]}  )
                                +"' is not supported. expected 0x01 (CONNECT)."
                );
                innerOutputStream.write(new byte[]{0x05, 0x07}); //version 05, 07 "Command not supported"
                innerOutputStream.flush();
                socket.close();
                return;
            }
            if(clientCommand[3] != (byte)0x01 && clientCommand[3] != (byte)0x03) {
                OnScreenLog.log(
                        "incoming connection "+connectionDescription
                                + " was closed because socks address type '0x"+bytes2HexString( new byte[]{clientCommand[3]}  )
                                +"' is not supported. expected 0x01 (IP version 4) or 0x03 (Domain Name)."
                );
                innerOutputStream.write(new byte[]{0x05, 0x08}); //version 05, 08 "Address type not supported"
                innerOutputStream.flush();
                socket.close();
                return;
            }
            String ip = null;
            int port = 0;
            if(clientCommand[3] == (byte)0x01) {
                byte[] connectAddress = new byte[6];
                innerInputStream.read(connectAddress, 0, 6);

                ip = byte2int(connectAddress[0]) + "." + byte2int(connectAddress[1]) + "." + byte2int(connectAddress[2]) + "." + byte2int(connectAddress[3]);
                port = byte2int(connectAddress[4]) * 256 + byte2int(connectAddress[5]);
            }
            if(clientCommand[3] == (byte)0x03) {
                byte[] domainNameLength = new byte[1];
                innerInputStream.read(domainNameLength, 0, 1);

                byte[] domainNameBytes = new byte[byte2int(domainNameLength[0])];
                innerInputStream.read(domainNameBytes, 0, domainNameBytes.length);
                String domainName = new String(domainNameBytes);
                //String domainName = domainNameWithPadding.substring(0,domainNameWithPadding.length()-1);

                OnScreenLog.log(connectionDescription + " (dns lookup "+domainName+")");

                InetAddress inet = InetAddress.getByName(domainName.toLowerCase());
                ip = inet.getHostAddress();

                byte[] portBytes = new byte[2];
                innerInputStream.read(portBytes, 0, 2);
                //OnScreenLog.log(connectionDescription + " (portbytes "+bytes2HexString( portBytes  )+")");
                port = byte2int(portBytes[0]) * 256 + byte2int(portBytes[1]);
                //OnScreenLog.log(connectionDescription + " (port "+port+")");
            }


            connectionDescription = clientAddress + " --> " + ip+":"+port;
            OnScreenLog.log(connectionDescription + " (dailing)");


            InputStream outerInputStream = null;
            OutputStream outerOutputStream = null;
            try {
                outerSocket = new Socket(ip, port);
                outerInputStream = outerSocket.getInputStream();
                outerOutputStream = outerSocket.getOutputStream();
            } catch (Exception ex) {
                innerOutputStream.write(new byte[]{0x05, 0x01}); //version 05, 01 "general SOCKS server failure"
                innerOutputStream.flush();
                socket.close();
                return;
            }

            // respond to the client with the connection details once the connection has been established.
            byte[] remoteIp = outerSocket.getLocalAddress().getAddress();
            int remotePort = outerSocket.getLocalPort();

            innerOutputStream.write(new byte[]{
                    0x05, 0x00, 0x00, 0x01, // version 5, success 0x00, reserved (null), address  type 0x01 (ip version 4)
                    remoteIp[0],remoteIp[1],remoteIp[2],remoteIp[3],
                    (byte) (remotePort >> 8), (byte) (remotePort & 0xff)
            }, 0, 10);
            innerOutputStream.flush();

            OnScreenLog.log(connectionDescription + " (connected)");


            /**
             * New thread: continuously read data from the external network and send it to the client
             */
            SocksResponseThread responseThread = new SocksResponseThread(outerInputStream, innerOutputStream, connectionDescription);
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
            OnScreenLog.log(connectionDescription+ " threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (socket != null) socket.close();
                if (outerSocket != null) outerSocket.close();
                OnScreenLog.log(connectionDescription + " (disconnected)");
            } catch (IOException e) { }
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
