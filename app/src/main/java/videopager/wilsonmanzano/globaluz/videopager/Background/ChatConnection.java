package videopager.wilsonmanzano.globaluz.videopager.Background;

/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import videopager.wilsonmanzano.globaluz.videopager.Activitys.VideoActivity;
import videopager.wilsonmanzano.globaluz.videopager.R;

public class ChatConnection {

    private static final String TAG = "ChatConnection";
    private Handler mUpdateHandler;
    private ChatServer mChatServer;
    private ChatClient mChatClient;
    private String mIP;
    private Activity mActivity;
    private Socket mSocket;
    private int mPort;


    //Constructor when you will start a new connection listener
    public ChatConnection(Handler handler, int port, String IP, Activity activity) {
        this.mUpdateHandler = handler;
        //In the constructor the serversocket is created and wait a connection
        this.mChatServer = new ChatServer(handler);
        this.mPort = port;
        this.mIP = IP;
        this.mActivity = activity;



    }

    //Close the connection (It should be closed when connection is stabilized)
    public void tearDown() {

        mChatServer.tearDown();
        mChatClient.tearDown();

    }

    // Stabilize connection

    public void connectToServer(InetAddress address, int port) {
        mChatClient = new ChatClient(address, port);
    }

    public void sendMessage(String msg) {
        if (mChatClient != null) {
            mChatClient.sendMessage(msg);
        }
    }

    public int getLocalPort() {
        return mPort;
    }

    public void setLocalPort(int port) {
        mPort = port;
    }

    //This class send the message to the Handler that is in the Main Activity
    public synchronized void updateMessages(String msg) {
        Log.e(TAG, "Updating message: " + msg);


        Bundle messageBundle = new Bundle();
        messageBundle.putString("msg", msg);

        Message message = new Message();
        message.setData(messageBundle);
        mUpdateHandler.sendMessage(message);

    }

    private Socket getSocket() {
        return mSocket;
    }

    private synchronized void setSocket(Socket socket) {
        Log.d(TAG, "setSocket being called.");
        if (socket == null) {
            Log.d(TAG, "Setting a null socket.");
        }
        if (mSocket != null) {
            if (mSocket.isConnected()) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                    // TODO(alexlucas): Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        mSocket = socket;
    }

    private class ChatServer {
        ServerSocket mServerSocket = null;
        Thread mThread = null;


        //Create the server thread (Listener of the socket)
        public ChatServer(Handler handler) {
            mThread = new Thread(new ServerThread());
            mThread.start();
        }
        //Close the server socket that was created
        public void tearDown() {
            mThread.interrupt();
            try {
                mServerSocket.close();
            } catch (IOException ioe) {
                Log.e(TAG, "Error when closing server socket.");
            }
        }


        //The thread that create the server socket listener
        class ServerThread implements Runnable {

            @Override
            public void run() {

                try {


                    // Create a server socket that used the mPort extracted by the IP static number
                    mServerSocket = new ServerSocket();
                    mServerSocket.setReuseAddress(true);
                    mServerSocket.bind(new InetSocketAddress(mPort));
                    //Wait in a loop (listener) a connection by a client to initialize the socket
                    while (!Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "ServerSocket Created, awaiting connection");
                        //When a client request connection, the is accepted
                        setSocket(mServerSocket.accept());
                        Log.d(TAG, "Connected.");
                        if (mChatClient == null) {
                            int port = mSocket.getPort();
                            InetAddress address = mSocket.getInetAddress();
                            connectToServer(address, port);
                        }

                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error creating ServerSocket: ", e);
                    ((VideoActivity)mActivity).mSocketIsOpen = false;

                    e.printStackTrace();
                }
            }
        }
    }

    //When a connection by a client is request, the chat connection is open

    private class ChatClient {

        private final String CLIENT_TAG = "ChatClient";
        private InetAddress mAddress;
        private int PORT;
        private Thread mSendThread;
        private Thread mRecThread;

        public ChatClient(InetAddress address, int port) {

            //Create the sent thread and stabilize connection
            Log.d(CLIENT_TAG, "Creating chatClient");
            this.mAddress = address;
            this.PORT = port;

            mSendThread = new Thread(new SendingThread());
            mSendThread.start();
        }

        //Teardown the server socket
        public void tearDown() {
            try {
                getSocket().close();
            } catch (IOException ioe) {
                Log.e(CLIENT_TAG, "Error when closing server socket.");
            }
        }

        // Function that sent de message and can be access by any calls
        public void sendMessage(String msg) {
            try {
                Socket socket = getSocket();
                if (socket == null) {
                    Log.d(CLIENT_TAG, "Socket is null, wtf?");
                } else if (socket.getOutputStream() == null) {
                    Log.d(CLIENT_TAG, "Socket output stream is null, wtf?");
                }

                PrintWriter out = new PrintWriter(
                        new BufferedWriter(
                                new OutputStreamWriter(getSocket().getOutputStream())), true);
                out.println(msg);
                out.flush();
                updateMessages(msg);
            } catch (UnknownHostException e) {
                Log.d(CLIENT_TAG, "Unknown Host", e);

            } catch (IOException e) {
                Log.d(CLIENT_TAG, "I/O Exception", e);

            } catch (Exception e) {
                Log.d(CLIENT_TAG, "Error3", e);

            }
            Log.d(CLIENT_TAG, "Client sent message: " + msg);
        }

        // Only initialize the socket and there something message in the buffer, it will send

        class SendingThread implements Runnable {

            BlockingQueue<String> mMessageQueue;
            private int QUEUE_CAPACITY = 10;

            public SendingThread() {
                mMessageQueue = new ArrayBlockingQueue<String>(QUEUE_CAPACITY);
            }

            @Override
            public void run() {
                try {
                    if (getSocket() == null) {
                        setSocket(new Socket(mAddress, PORT));
                        Log.d(CLIENT_TAG, "Client-side socket initialized.");

                    } else {

                        updateMessages("NewOrderWaiting");
                        Log.d(CLIENT_TAG, "Socket already initialized. skipping!");

                    }

                    mRecThread = new Thread(new ReceivingThread());
                    mRecThread.start();

                } catch (UnknownHostException e) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, UHE", e);

                } catch (IOException e) {
                    Log.d(CLIENT_TAG, "Initializing socket failed, IOE.", e);

                }

                while (true) {
                    try {
                        String msg = mMessageQueue.take();
                        sendMessage(msg);
                    } catch (InterruptedException ie) {
                        Log.d(CLIENT_TAG, "Message sending loop interrupted, exiting");
                    }
                }
            }
        }

        class ReceivingThread implements Runnable {

            @Override
            public void run() {


                //Create a buffer reader
                BufferedReader input;

                try {
                    input = new BufferedReader(new InputStreamReader(
                            mSocket.getInputStream()));


                    //Wait some message from the client and do the logic
                    while (!Thread.currentThread().isInterrupted()) {

                        String messageStr = null;
                        messageStr = input.readLine();
                        if (messageStr != null) {

                            //Ordear ready when incomming message is Hola
                            Log.d(CLIENT_TAG, "Read from the stream: " + messageStr);
                            if ((messageStr.charAt(0) == 'H') && (messageStr.charAt(messageStr.length() - 1) == mIP.charAt(mIP.length() - 1))) {


                                ChatConnection.this.sendMessage("Hola mundo de " + mIP);
                                updateMessages("OrderReady");

                            }

                            //This happens when existed two buttons
                            if ((messageStr.charAt(0) == 'C') && (messageStr.charAt(messageStr.length() - 1) == mIP.charAt(mIP.length() - 1))) {

                                ChatConnection.this.sendMessage("Chao mundo de " + mIP);
                                updateMessages("NewOrderWaiting");


                            }
                            //When the first letter is a X this means that the pager has to download a video
                            if (messageStr.charAt(0) == 'X') {

                                messageStr = messageStr.substring(1, messageStr.length());
                                Log.d(CLIENT_TAG, "Read from the stream: " + messageStr);
                                ChatConnection.this.sendMessage("Se comenzará a descargar " + messageStr);
                                ((VideoActivity)mActivity).downloadVideoManager.
                                        processThisDownloadRequest(mActivity.getString(R.string.serverURL) + messageStr);

                            }

                            updateMessages(messageStr);
                        } else {
                            Log.d(CLIENT_TAG, "The nulls! The nulls!");
                            //When the socket is closed by the videoPC the pager will create a new connection
                            updateMessages("CreateNewConnection");
                            break;
                        }
                    }
                    input.close();

                    } catch (IOException e) {

                        updateMessages("SocketClose");
                        Log.e(CLIENT_TAG, "Server loop error: ", e);

                    }
            }
        }
    }
}
