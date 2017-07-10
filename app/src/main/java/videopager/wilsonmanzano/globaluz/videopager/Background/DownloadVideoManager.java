package videopager.wilsonmanzano.globaluz.videopager.Background;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;

import videopager.wilsonmanzano.globaluz.videopager.Activitys.VideoActivity;


/**
 * Created by ${User} on 8/06/2017.
 */

public class DownloadVideoManager extends BroadcastReceiver {

    private DownloadManager downloadManager;
    private Context mContext;
    private Activity mActivity;


    //Constructor of the class only request the activiy
    public DownloadVideoManager(Activity activity) {
        this.mContext = activity.getApplicationContext();
        downloadManager = (DownloadManager)
                mContext.getSystemService(mContext.DOWNLOAD_SERVICE);
        this.mActivity = activity;
    }

    //All receiver have to register
    public void registerDownloadReceiver() {
        try {
            mContext.registerReceiver(this, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Exception error) {
            Log.e("DownloadManagerBridge", "registerDownloadReceiver: " + error.toString());
        }
    }
    //And will be unregister in onDestroy
    public void unRegisterDownloadReceiver(){
        try{
            mContext.unregisterReceiver(this);
        }catch (Exception error){
            Log.e("DownloadManagerBridge","registerDownloadReceiver: "+error.toString());
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        try {
            String action = intent.getAction();
            //If complete download is complete save the file and play
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = this.downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                        int fileUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String fileUri = cursor.getString(fileUriIdx);
                        if (fileUri != null) {
                            File file = new File(Uri.parse(fileUri).getPath());
                            Log.i("Download", file.getAbsolutePath());
                            ((VideoActivity) mActivity).NewOrderWaiting(file.getAbsolutePath());

                        }


                    } else {
                        Log.w("Download", "Error code: " + cursor.getInt(columnIndex));
                    }
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }

    }


    public long processThisDownloadRequest(String downloadRequest) {
        long enqueueId = -1;
        try {

            //The video will download from WiFi and here we set the path of download
            DownloadManager.Request request = new
                    DownloadManager.Request(Uri.parse(downloadRequest));

            request.setDestinationInExternalPublicDir("VideosDescargados/", "video.mp4");

            request.setAllowedNetworkTypes(request.NETWORK_WIFI);

            enqueueId = this.downloadManager.enqueue(request);

            return enqueueId;
        } catch (Exception error) {

            Log.e("DownloadManagerBridge", "processThisDownloadRequest: " + error.toString());
            return enqueueId;
        }

    }
}
