package com.droidsdoit.mp3player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import java.util.regex.Pattern;

public class MP3PlayerActivity extends AppCompatActivity {
    private final String TAG = "MP3Player";

    private Context m_Context = null;
    private Utils m_Utils = null;
    private MP3DatabaseHelper m_MP3DatabaseHelper = null;

    private final String m_RegExMP3URL = "^(https?|ftp)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]\\.mp3$";
    private final String m_RegExMP3TrackName = ".*/(.*)\\.mp3";

    private SimpleCursorAdapter m_DataAdapter = null;
    private Cursor m_MP3Cursor = null;
    private ListView m_MP3ListView = null;

    private View m_HighlightedView = null;

    //Controlas
    private ImageButton m_BackButton = null;
    private ImageButton m_PlayPauseButton = null;
    private ImageButton m_ForwardButton = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        m_Context = this;
        m_Utils = new Utils(this);
        m_MP3DatabaseHelper = new MP3DatabaseHelper(this);

        setContentView(R.layout.activity_mp3_player);

        final EditText editTextURL = (EditText) findViewById(R.id.url);
        final EditText editTextName = (EditText) findViewById(R.id.mp3_name);
        final Button buttonAdd = (Button) findViewById(R.id.add_mp3);
        m_MP3ListView = (ListView) findViewById(R.id.mp3_listview);

        buttonAdd.setEnabled(false);

        editTextURL.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String mp3Text = s.toString();
                if (m_Utils.findMatch(mp3Text, m_RegExMP3URL, Pattern.CASE_INSENSITIVE)) {
                    buttonAdd.setEnabled(true);
                    String mp3File = m_Utils.getMatch(mp3Text, m_RegExMP3TrackName, 1, Pattern.CASE_INSENSITIVE);
                    if (mp3File != null)
                        editTextName.setText(mp3File);
                }
                else
                    buttonAdd.setEnabled(false);
            }
        });

        buttonAdd.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                m_MP3DatabaseHelper.insert(editTextURL.getText().toString(), editTextName.getText().toString());
                editTextName.setText("");
                editTextURL.setText("");

                updateMP3ListView();
                updateControls();
                notifyUpdateTracks();
                highlightListItem(m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1));
            }
        });

        m_MP3ListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playMP3(position);
            }
        });

        //Initialize control buttons
        m_BackButton = (ImageButton) findViewById(R.id.back);
        m_BackButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                playMP3(m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1) -1);
            }
        });

        m_PlayPauseButton = (ImageButton) findViewById(R.id.playpause);
        m_PlayPauseButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int track = m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1);
                if (track == -1)
                    playMP3(0);
                else
                    playMP3(track);
            }
        });

        m_ForwardButton = (ImageButton) findViewById(R.id.forward);
        m_ForwardButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                playMP3(m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1)+1);
            }
        });
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "onReceive action: " + action);

            if (action.contentEquals(MP3PlayerService.ACTION_NOTIFY_ACTIVITY_UPDATE_PLAY_STATE)) {
                highlightListItem(m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1));
                updateControls();
            }
            else if (action.contentEquals(MP3PlayerService.ACTION_NOTIFY_ACTIVITY_PLAY_ERROR)) {
                String error = intent.getStringExtra("error");
                String mp3URL = intent.getStringExtra("mp3URL");

                String text = "";
                if (error.contentEquals(MP3PlayerService.ERROR_MP3_NOT_FOUND)) {
                    text = m_Context.getString(R.string.toast_error_general).replace("__MP3URL__", mp3URL);
                } else if (error.contentEquals(MP3PlayerService.ERROR_NO_INTERNET)) {
                    text = m_Context.getString(R.string.toast_error_no_internet);
                }

                if (text.length() > 0)
                    Toast.makeText(m_Context, text, Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        updateMP3ListView();
        updateControls();

        m_MP3ListView.requestFocus();
        highlightListItem(m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1));

        IntentFilter filter = new IntentFilter();
        filter.addAction(MP3PlayerService.ACTION_NOTIFY_ACTIVITY_UPDATE_PLAY_STATE);
        filter.addAction(MP3PlayerService.ACTION_NOTIFY_ACTIVITY_PLAY_ERROR);

        registerReceiver(receiver, filter);

        //launch the MP3PlayerService
        Intent startServiceIntent = new Intent(this, MP3PlayerService.class);
        startService(startServiceIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(receiver);
    }

    @Override
    protected void onStop() {
        super.onStop();

        m_MP3ListView.setAdapter(null);

        if (m_MP3Cursor != null) {
            m_MP3Cursor.close();
            m_MP3Cursor = null;
        }

        if (m_DataAdapter != null) {
            m_DataAdapter = null;
        }
    }

    private void updateMP3ListView() {
        try {

            if (m_MP3Cursor != null) {
                m_MP3Cursor.close();
            }
            m_MP3Cursor = m_MP3DatabaseHelper.queryMP3s();

            if (m_MP3Cursor == null) {
                m_MP3ListView.setAdapter(null);
                m_DataAdapter = null;
                return;
            }

            // the XML defined views which the data will be bound to
            int[] to = new int[]{
                    R.id.url_entry,
                    R.id.name_entry
            };

            String[] columns = new String[]{
                    "url",
                    "name"
            };

            // create the adapter using the cursor pointing to the desired data
            //as well as the layout information
            m_DataAdapter = new SimpleCursorAdapter(
                    this, R.layout.list_view_mp3_entry,
                    m_MP3Cursor,
                    columns,
                    to,
                    0);


            m_MP3ListView.setAdapter(m_DataAdapter);

        } catch(Exception ex) {
            Log.e(TAG, "updateMP3ListView Exception: " + ex.getMessage());
        }
    }

    /*
    updates the media control button states
     */
    private void updateControls() {
        boolean enabled = false;
        if (m_MP3Cursor != null && m_MP3Cursor.getCount() > 0) {
            enabled = true;
        }

        m_BackButton.setEnabled(enabled);
        m_PlayPauseButton.setEnabled(enabled);
        m_ForwardButton.setEnabled(enabled);

        int track = m_Utils.getPreference(MP3PlayerService.SP_TRACK, -1);
        boolean playing = m_Utils.getPreference(MP3PlayerService.SP_PLAYING, 0) == 1;

        m_BackButton.setImageResource((enabled && (track > 0)) ? R.drawable.back : R.drawable.back_disabled);
        m_PlayPauseButton.setImageResource(enabled ? (playing ? R.drawable.pause : R.drawable.play) : R.drawable.play_disabled);
        m_ForwardButton.setImageResource((enabled && (track < m_MP3Cursor.getCount() - 1)) ? R.drawable.forward : R.drawable.forward_disabled);
    }

    /*
    highlights the currently selected track
     */
    private void highlightListItem(int position) {
        try {
            m_MP3ListView.requestFocusFromTouch();
            m_MP3ListView.setSelection(position);
        } catch (Exception ex) {
            Log.e(TAG, "highlightListItem exception: " + ex.getMessage());
        }
    }

    private void playMP3(int trackIndex) {
        Intent playMP3Intent = new Intent(this, MP3PlayerService.class);
        playMP3Intent.setAction(MP3PlayerService.ACTION_NOTIFY_SERVICE_PLAY_MP3);
        playMP3Intent.putExtra("track", trackIndex);
        startService(playMP3Intent);

        highlightListItem(trackIndex);
    }

    private void notifyUpdateTracks() {
        Intent playMP3Intent = new Intent(this, MP3PlayerService.class);
        playMP3Intent.setAction(MP3PlayerService.ACTION_NOTIFY_SERVICE_UPDATE_TRACKS);
        startService(playMP3Intent);
    }
}