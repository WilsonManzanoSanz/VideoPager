package videopager.wilsonmanzano.globaluz.videopager.Activitys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import videopager.wilsonmanzano.globaluz.videopager.Background.ChatConnection;
import videopager.wilsonmanzano.globaluz.videopager.Background.DownloadVideoManager;
import videopager.wilsonmanzano.globaluz.videopager.R;

public class VideoActivity extends AppCompatActivity implements View.OnClickListener {

    //Classes
    public Handler mUpdateHandler;
    //Views
    private VideoView mVideoView;
    private TextView mImageView;
    private RelativeLayout mTextsViews;
    private TextView mBatteryView;
    private TextView mPagerView;
    private Button mButtonVolume;
    //Primitives
    private String mIP;
    private int mPORT;
    private int mPagerNumber;
    private int mBatteryLevel;
    private boolean firstChatConnection = true;
    public boolean mSocketIsOpen = false;
    public boolean mWifiOFF = false;
    private boolean mSocketWaitingConnection = false;

    //My classes
    private ChatConnection mConnection;
    private AudioManager mAudioManager;
    public DownloadVideoManager downloadVideoManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        //initialize the vies
        mVideoView = (VideoView) findViewById(R.id.video_view);
        mImageView = (TextView) findViewById(R.id.image_view);
        mTextsViews = (RelativeLayout) findViewById(R.id.text_view);
        mBatteryView = (TextView) findViewById(R.id.text_view_battery);
        mPagerView = (TextView) findViewById(R.id.text_view_pager);
        mButtonVolume = (Button) findViewById(R.id.button_volume);
        // Listener and visibility from the invisible volume button
        mButtonVolume.setBackgroundColor(Color.TRANSPARENT);
        mButtonVolume.setOnClickListener(this);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //All views become full screen
        ApplyFullScreenAPP(mImageView);
        ApplyFullScreenAPP(mVideoView);
        ApplyFullScreenAPP(mTextsViews);


        // Handler is a thread that allow change communication via string betwen different classes
        //It's used because in a thread isn´t possible modify a view
        mUpdateHandler = new Handler() {


            @Override
            public void handleMessage(Message msg) {
                //Capture message from the Serversocket receive
                String message = msg.getData().getString("msg");
                //If a message from VideoCoderPC is coming and said that the order is ready, video will stop and notify in the pager
                if (message == "OrderReady") {

                    OrderReady();

                } else if (message == "NewOrderWaiting") {
                    // If a new connection via socket is stabilized, volumen become max


                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                            mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),0);

                    //Play de video in memory

                    NewOrderWaiting(Environment.getExternalStorageDirectory().getPath() + "/VideosDescargados/video.mp4");

                    mSocketWaitingConnection = false;
                    mSocketIsOpen = true;


                } else if (message == "CreateNewConnection") {

                    // Create new Connection mean that the socket is closed by the PC and it should
                    //create a new server socket and wait a new connection

                    //Only will tear down if a connection was stabilized
                    if (mSocketIsOpen) {
                        mConnection.tearDown();
                    }
                    createNewChatConnection();

                } else if (message == "SocketClose") {

                    // Create new Connection mean that the socket is closed unexpectedly and it should
                    //create a new server socket and wait a new connection

                    //Only will tear down if a connection was stabilized
                    Handler delay = new Handler();
                    //Hanlder is a delay that wait if socket is closed by WIFI connection or by a error in the connection
                    delay.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            //If isn't closed by the WIFI means that there is a problem with the connection
                            //and shloud be closed and reopen a new connection listener
                            if (!mWifiOFF) {

                                mConnection.tearDown();
                                createNewChatConnection();
                                NewOrderWaiting(Environment.getExternalStorageDirectory().getPath() + "/VideosDescargados/video.mp4");
                            }

                        }
                    }, 1000);

                    mConnection.tearDown();
                    mSocketIsOpen = false;

                }


            }
        };

        //Get the static IP and show it in the screen
        getCurrentIP();

        //


    }

    @Override
    protected void onResume() {
        super.onResume();
        //A broadcast receiver from the system operative that listen if occrus a change in level battery or in the WIFI
        this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        this.registerReceiver(this.mWiFiListenerReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        downloadVideoManager = new DownloadVideoManager(this);
        downloadVideoManager.registerDownloadReceiver();

    }






    @Override
    protected void onDestroy() {
        super.onDestroy();
        //If the problem is closed unexpectedly the socket connection shloud be close
        if (mSocketIsOpen) {
            mConnection.tearDown();
            mSocketIsOpen = false;

        }
        this.unregisterReceiver(this.mBatInfoReceiver);
        this.unregisterReceiver(this.mWiFiListenerReceiver);
        downloadVideoManager.unRegisterDownloadReceiver();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){

            case R.id.button_volume:
                // Button that allow change the volume of the videopager
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        mAudioManager.getStreamMaxVolume(AudioManager.ADJUST_RAISE),AudioManager.FLAG_SHOW_UI);

                break;

        }
    }



    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Get a battery level
            mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            setBatteryAndIPText();


        }
    };

    private BroadcastReceiver mWiFiListenerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            //Get the WIFI state
            ConnectivityManager conMan = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = conMan.getActiveNetworkInfo();

            if (netInfo != null && netInfo.isConnected()) {
                Log.i("WifiBroadcast", "Connect");
                Toast.makeText(VideoActivity.this, "Conectado a la RED", Toast.LENGTH_SHORT).show();
                // If occurs a change in the WIFI getCurrent IP won't show the number of the pager,
                //It will show that there aren't connection
                getCurrentIP();
                setBatteryAndIPText();
                if (firstChatConnection) {
                    //Wait a connection from the PC
                    createFirstChatConnection();

                } else {
                        if (!mSocketWaitingConnection && !mSocketIsOpen) {
                            //Only will create a new connection if the previous conenction is closed
                            createNewChatConnection();
                        }

                }
                mWifiOFF = false;


            } else {

                //If the WiFi connection is closed

                Log.i("WifiBroadcast", "Disconnect");

                // And there was a Socket connection, it will be disconnect
                if (mSocketIsOpen) {
                    mConnection.tearDown();
                    mSocketIsOpen = false;
                }

                mWifiOFF = true;
                Toast.makeText(VideoActivity.this, "Por favor acercate mas al restaurante, estas muy lejos" +
                                "\n" + "     ¡Gracias!",
                        Toast.LENGTH_LONG).show();
                Handler delay = new Handler();
                //Hanlder is a delay that will show a Toast for more time
                delay.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        Toast.makeText(VideoActivity.this, "Por favor acercate mas al restaurante, estas muy lejos" +
                                        "\n" + "     ¡Gracias!",
                                Toast.LENGTH_LONG).show();

                    }
                }, 3000);

            }


        }


    };

    //Function that change the color of battery view and show the IP in the screen

    private void setBatteryAndIPText() {


        String concatenate = String.valueOf(mBatteryLevel) + "%";

        mBatteryView.setText(concatenate);

        if (mBatteryLevel > 69) {

            mBatteryView.setTextColor(Color.GREEN);

        } else if (mBatteryLevel > 29 && mBatteryLevel < 70) {

            mBatteryView.setTextColor(Color.YELLOW);
        } else if (mBatteryLevel < 30) {

            mBatteryView.setTextColor(Color.RED);

        }

        if (mPagerNumber == 0) {
            mPagerView.setText(getString(R.string.no_hay_internet));
            mPagerView.setTextSize(40);
        } else {

            mPagerView.setText(String.valueOf(mPagerNumber));
        }

        /* String mTextToChanged = "Pager " + String.valueOf(mPagerNumber)
                + "\n" + "Carga: " + String.valueOf(mBatteryLevel) + "%";
        mTextsViews.setText(mTextToChanged);
        */
    }

    //Get Static IP from the system and abstract it in the PORT and Number of the pager

    private void getCurrentIP() {


        WifiManager mWifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        mIP = Formatter.formatIpAddress(mWifiManager.getConnectionInfo().getIpAddress());

        try {
            String lastIP = (mIP.substring(mIP.length() - 2, mIP.length()));
            if (lastIP.charAt(0) != '.') {
                mPORT = Integer.parseInt(lastIP);
            } else {
                lastIP = (mIP.substring(mIP.length() - 1, mIP.length()));
                mPagerNumber = Integer.parseInt(lastIP);
            }

        } catch (Exception e) {
            Log.e("Cast Port from mIP", e.toString());

        }
        mPORT = mPagerNumber + Integer.parseInt(getString(R.string.PORT));


    }


    // New Order waiting means that a socket connection is stabilized and the video should be playing

    public void NewOrderWaiting(final String mVideoPath) {


        mImageView.setVisibility(View.GONE);
        mTextsViews.setVisibility(View.GONE);
        mVideoView.setVisibility(View.VISIBLE);

        Uri uri = Uri.parse(mVideoPath);
        mVideoView.setVideoURI(uri);
        mVideoView.requestFocus();
        mVideoView.start();
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                NewOrderWaiting(mVideoPath);
            }
        });


    }

    //Create a new connection listener that respect conditionals

    public void createNewChatConnection() {

        mConnection = new ChatConnection(mUpdateHandler, mPORT, mIP, VideoActivity.this);

        if (!mWifiOFF) {

            mImageView.setVisibility(View.GONE);
            mTextsViews.setVisibility(View.VISIBLE);
            mVideoView.setVisibility(View.GONE);

        }

        mSocketIsOpen = false;
        mSocketWaitingConnection = true;


    }


    //Create a new chat connection listener without conditionals

    private void createFirstChatConnection() {


        mConnection = new ChatConnection(mUpdateHandler, mPORT, mIP, VideoActivity.this);


        mImageView.setVisibility(View.GONE);
        mTextsViews.setVisibility(View.VISIBLE);
        mVideoView.setVisibility(View.GONE);

        firstChatConnection = false;

    }

    /*

    public void OrderReady() {

        mVideoView.stopPlayback();

        mImageView.setVisibility(View.VISIBLE);
        mTextsViews.setVisibility(View.GONE);
        mVideoView.setVisibility(View.GONE);


    }

    */

    public void OrderReady() {

        mImageView.setVisibility(View.VISIBLE);
        mTextsViews.setVisibility(View.GONE);
        final String mVideoAlarmaPath = Environment.getExternalStorageDirectory().getPath() + "/VideosDescargados/videoalarma.mp4";
        Uri uri = Uri.parse(mVideoAlarmaPath);
        mVideoView.setVideoURI(uri);
        mVideoView.requestFocus();
        mVideoView.start();
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                NewOrderWaiting(mVideoAlarmaPath);
            }
        });


    }

    //Only show the app in full Screen
    private void ApplyFullScreenAPP(View view) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }



}
