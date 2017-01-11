package com.droidsdoit.mp3player;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by Paul Keeling on 1/10/2017.
 */

public class MP3PlayerService extends Service implements MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private String TAG = "MP3PlayerService";

    public static String ACTION_NOTIFY_SERVICE_PLAY_MP3 = "com.droidsdoit.mp3player.ACTION_NOTIFY_SERVICE_PLAY_MP3";
    public static String ACTION_NOTIFY_SERVICE_UPDATE_TRACKS = "com.droidsdoit.mp3player.ACTION_NOTIFY_SERVICE_UPDATE_TRACKS";

    public static String ACTION_NOTIFY_ACTIVITY_UPDATE_PLAY_STATE = "com.droidsdoit.mp3player.ACTION_NOTIFY_ACTIVITY_UPDATE_PLAY_STATE";
    public static String ACTION_NOTIFY_ACTIVITY_PLAY_ERROR = "com.droidsdoit.mp3player.ACTION_NOTIFY_ACTIVITY_PLAY_ERROR";

    public static String SP_PLAYING = "playing";
    public static String SP_TRACK = "track";

    public static String ERROR_NO_INTERNET = "ERROR_NO_INTERNET";
    public static String ERROR_MP3_NOT_FOUND = "ERROR_URL_NOT_FOUND";

    private boolean m_Initialized = false;
    private Utils m_Utils = null;
    private Handler m_Handler = null;

    private MP3DatabaseHelper m_MP3DatabaseHelper = null;
    private Cursor m_MP3Cursor = null;
    private MediaPlayer m_MediaPlayer = null;
    private PlayMP3Task m_PlayMP3Task = null;

    public MP3PlayerService() {
    }

    //Foreground service methods
    private Notification getMyActivityNotification(String text){
        // The PendingIntent to launch our activity if the user selects
        // this notification
        CharSequence title = getText(R.string.app_name);
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, new Intent(this, MP3PlayerActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);

        return new Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.mp3)
                .setContentIntent(contentIntent).getNotification();
    }

    /**
     * This is the method that can be called to update the Notification
     */
    private void updateNotification(String text) {
        Notification notification = getMyActivityNotification(text);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, notification);
    }

    //initialization method
    private void init() {
        if (m_Initialized)
            return;

        //Make the Service a foreground service so that when the main activity is closed the OS will not shutdown the service and hence mp3s will continue to play
        startForeground(1, getMyActivityNotification(""));

        m_Handler = new Handler();

        m_Utils = new Utils(this);
        m_Utils.setPreference(SP_PLAYING, 0);
        m_MP3DatabaseHelper = new MP3DatabaseHelper(this);

        initMediaPlayer();

        m_MP3Cursor = m_MP3DatabaseHelper.queryMP3s();

        m_Initialized = true;
    }

    //initialize and reset the media player if it already exists, which is useful when prepareAsync takes a long time
    private void initMediaPlayer() {
        try {
            if (m_MediaPlayer != null) {
                m_MediaPlayer.release();
                m_MediaPlayer = null;
            }
        } catch (Exception ex) {}


        m_MediaPlayer = new MediaPlayer();
        m_MediaPlayer.setOnBufferingUpdateListener(this);
        m_MediaPlayer.setOnCompletionListener(this);
        m_MediaPlayer.setOnErrorListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        init();
        if (intent != null) {
            String action = intent.getAction();
            Log.i(TAG, "MP3PlayerService onStartCommand Action: " + action);

            if (action != null) {
                if (action.contentEquals(ACTION_NOTIFY_SERVICE_PLAY_MP3)) {
                    playMP3(intent.getIntExtra("track", -1));
                } else if (action.contentEquals(ACTION_NOTIFY_SERVICE_UPDATE_TRACKS)) {
                    m_MP3Cursor = m_MP3DatabaseHelper.queryMP3s();
                }
            }
        }

        return START_STICKY;
    }

    private final Runnable playNext = new Runnable(){
        public void run(){
            try {
                playMP3(m_Utils.getPreference(SP_TRACK, -1) + 1);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private synchronized void playMP3(int position) {
        try {
            int track = m_Utils.getPreference(SP_TRACK, -1);
            m_Handler.removeCallbacks(playNext);
            if (track == position && (m_PlayMP3Task != null)) {
                if (m_MediaPlayer.isPlaying()) {
                    m_MediaPlayer.pause();
                }
                else
                    m_MediaPlayer.start();

                notifyPlayState();
            }
            else {
                if (m_MP3Cursor.moveToPosition(position)) {
                    m_Utils.setPreference(SP_TRACK, position);
                    final String mp3URL = m_MP3Cursor.getString(1);

                    //Cancel a previous play request if pending
                    if (m_PlayMP3Task != null && m_PlayMP3Task.getStatus() != AsyncTask.Status.FINISHED) {
                        m_PlayMP3Task.cancelAndWait();
                    }

                    m_MediaPlayer.pause();
                    notifyPlayState();
                    m_PlayMP3Task = new PlayMP3Task();
                    m_PlayMP3Task.execute(mp3URL);
                } else {
                    Log.i(TAG, "playMP3 invalid URL index: " + position);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "playMP3 exception: " + ex.getMessage());
        }
    }

    /*
        Notify the activity that the playing state or track index has changed
    */
    private void notifyPlayState() {
        m_Utils.setPreference(SP_PLAYING, m_MediaPlayer.isPlaying() ? 1 : 0);

        Intent notifyPlayStateIntent = new Intent(MP3PlayerService.ACTION_NOTIFY_ACTIVITY_UPDATE_PLAY_STATE);
        sendBroadcast(notifyPlayStateIntent);
    }

    /*
        Notify the activity that an error occurred while attempting to play the track
    */
    private void notifyError(String error, String mp3URL) {
        Intent notifyErrorIntent = new Intent(MP3PlayerService.ACTION_NOTIFY_ACTIVITY_PLAY_ERROR);
        notifyErrorIntent.putExtra("error", error);
        notifyErrorIntent.putExtra("mp3URL", mp3URL);
        sendBroadcast(notifyErrorIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (m_MediaPlayer != null) {
            m_MediaPlayer.reset();
            m_MediaPlayer.release();
            m_MediaPlayer = null;
        }

        if (m_MP3Cursor != null) {
            m_MP3Cursor.close();
            m_MP3Cursor = null;
        }
    }

    //Media Player Listener Methods
    @Override
    public void onCompletion(MediaPlayer mp) {
        updateNotification("");
        notifyPlayState();

        //Play the next track if the current track was prepared and started successfully
        if (m_PlayMP3Task != null && m_PlayMP3Task.m_Prepared)
            playMP3(m_Utils.getPreference(SP_TRACK, -1) + 1);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        /** Method which updates the SeekBar secondary progress by current song loading from URL position*/
    }

    @Override
    public boolean onError(MediaPlayer player, int what, int extra) {
        Log.e(TAG, "onError what: " + what + " extra: " + extra);
        notifyPlayState();
        return false;
    };

    //PlayMP3 AsyncTask
    private class PlayMP3Task extends AsyncTask<String, Integer, Long> implements MediaPlayer.OnPreparedListener {
        public final long RESULT_UNKNWON = -1;
        public final long RESULT_SUCCESS = 0;
        public final long RESULT_INVALID_MP3_URL = 1;

        private final String GOOGLE_URL = "https://www.google.com";
        private final int PREPARE_TIMEOUT = 5000;
        private final int URL_TIMEOUT = 5000;
        private final int CANCEL_TIMEOUT = 10000;

        public long m_Result = RESULT_UNKNWON;
        private String m_MP3URL = "";

        public boolean m_WorkInProgress = false;
        public boolean m_Cancelled = false;
        public boolean m_Prepared = false;

        protected Long doInBackground(String... mp3URL) {
            Log.i(TAG, "PlayMP3Task doInBackground begin... mp3: " + mp3URL[0]);
            m_WorkInProgress = true;
            Long retVal = RESULT_UNKNWON;
            m_MP3URL = mp3URL[0];
            boolean reinitMediaPlayer = false;

            try {
                if (m_Cancelled)
                    return retVal;

                m_MediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                m_MediaPlayer.reset();
                m_MediaPlayer.setDataSource(m_MP3URL);
                m_MediaPlayer.setOnPreparedListener(this);
                m_MediaPlayer.prepareAsync();

                if (m_Cancelled)
                    return retVal;

                //Check the MP3 URL while preparing as an invalid URL can take over 30s to throw an error...
                if (!m_Utils.urlExists(m_MP3URL, URL_TIMEOUT)) {
                    if (m_Cancelled)
                        return retVal;

                    //If google can be accessed then assume the mp3 url is invalid
                    if (m_Utils.urlExists(GOOGLE_URL, URL_TIMEOUT)) {
                        reinitMediaPlayer = true;
                        retVal = RESULT_INVALID_MP3_URL;
                    }
                }
                else {
                    long startTime = System.currentTimeMillis();
                    while(!m_Prepared && !m_Cancelled && (System.currentTimeMillis() - startTime < PREPARE_TIMEOUT)){
                        try {
                            Thread.sleep(100);
                        } catch (Exception ex) {
                            break;
                        }
                    }
                    if (m_Prepared)
                        retVal = RESULT_SUCCESS;
                    else {
                        reinitMediaPlayer = true;
                        retVal = RESULT_INVALID_MP3_URL;
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "PlayMP3Task doInBackground exception: " + ex.getMessage());
            } finally {
                m_MediaPlayer.setOnPreparedListener(null);

                if (reinitMediaPlayer) {
                    //Cancel async prepare by creating a new media player
                    initMediaPlayer();
                }

                m_WorkInProgress = false;
            }

            Log.i(TAG, "PlayMP3Task doInBackground returning " + retVal);
            return retVal;
        }

        /** Called when MediaPlayer is ready */
        @Override
        public void onPrepared(MediaPlayer player) {
            Log.i(TAG, "PlayMP3Task onPrepared");
            m_Prepared = true;
            player.start();
        }

        protected void onPostExecute(Long result) {
            m_Result = result;
            Log.i(TAG, "PlayMP3Task onPostExecute Result: " + result + " mp3: " + m_MP3URL);

            if (m_Cancelled)
                return;

            if (m_Result != RESULT_SUCCESS) {
                if (m_Result == RESULT_INVALID_MP3_URL) {
                    notifyError(ERROR_MP3_NOT_FOUND, m_MP3URL);
                    m_Handler.postDelayed(playNext, 1000);
                }
                else {
                    notifyError(ERROR_NO_INTERNET, m_MP3URL);
                }
            }
            else {
                updateNotification(getString(R.string.notification_text).replace("__MP3URL__", m_MP3URL));
            }
            notifyPlayState();
        }

        @Override
        protected void onCancelled() {
            Log.i(TAG, "PlayMP3Task onCancelled");
            super.onCancelled();
        }

        public void cancelAndWait() {
            Log.i(TAG, "PlayMP3Task cancelAndWait...");
            m_Cancelled = true;

            long startTime = System.currentTimeMillis();
            while(m_WorkInProgress && (System.currentTimeMillis() - startTime < CANCEL_TIMEOUT)) {
                try {
                    Thread.sleep(100);
                } catch (Exception ex) {
                    break;
                }
            }
            Log.i(TAG, "PlayMP3Task cancelAndWait done");
        }
    }
}