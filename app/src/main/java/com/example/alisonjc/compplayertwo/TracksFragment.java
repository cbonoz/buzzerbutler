package com.example.alisonjc.compplayertwo;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.alisonjc.compplayertwo.spotify.SpotifyService;
import com.example.alisonjc.compplayertwo.spotify.model.tracklists.Item;
import com.example.alisonjc.compplayertwo.spotify.model.tracklists.PlaylistTracksList;
import com.google.inject.Inject;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.PlayerStateCallback;
import com.spotify.sdk.android.player.Spotify;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

public class TracksFragment extends RoboFragment {

    @Inject
    private SpotifyService mSpotifyService;

    @InjectView(R.id.tracksview)
    private ListView mListView;

    @InjectView(R.id.play)
    private ImageButton mPlayButton;

    @InjectView(R.id.pause)
    private ImageButton mPauseButton;

    @InjectView(R.id.seekerBarView)
    private SeekBar mSeekBar;

    @InjectView(R.id.musicCurrentLoc)
    private TextView mSongLocationView;

    @InjectView(R.id.musicDuration)
    private TextView mSongDurationView;

    @InjectView(R.id.radio_group)
    private RadioGroup mRadioGroup;

    @InjectView(R.id.one_minute_thirty)
    private RadioButton mOneThirtyMin;

    @InjectView(R.id.two_minutes)
    private RadioButton mTwoMin;

    private int mSongLocation;
    private Timer mTimer;
    private Handler seekHandler = new Handler();
    private Player mPlayer;
    private static TracksAdapter mTracksAdapter;
    private View rootView;
    private OnTracksInteractionListener mListener;
    private String playlistId;
    private int mItemPosition = 0;
    private int mPauseTimeAt = 90000;
    private boolean mBeepPlayed = false;

    public TracksFragment() {
    }

    public static TracksFragment newInstance(String playlistId) {
        TracksFragment fragment = new TracksFragment();

        Bundle args = new Bundle();
        args.putString("playlistId", playlistId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            playlistId = getArguments().getString("playlistId");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_tracks, container, false);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(savedInstanceState == null) {

            mSongLocationView.setText("0:00");
            mSongDurationView.setText(R.string.one_thirty_radio_button);

            mListView = (ListView) view.findViewById(R.id.tracksview);

            playerControlsSetup();
            //toolbarPlayerSetup();
            listViewSetup();
            startTimerTask();

            mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {

                    onRadioButtonClicked(checkedId);
                }
            });

            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (mPlayer != null && fromUser) {
                        mPlayer.seekToPosition(progress);
                        mSeekBar.setProgress(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }


    private void listViewSetup() {

        mTracksAdapter = new TracksAdapter(getActivity(), R.layout.item_track, new ArrayList<Item>());
        mListView.setAdapter(mTracksAdapter);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);

        mSpotifyService.getPlaylistTracks(playlistId).enqueue(new Callback<PlaylistTracksList>() {
            @Override
            public void onResponse(Call<PlaylistTracksList> call, Response<PlaylistTracksList> response) {
                if (response.isSuccess() && response.body() != null) {
                    updateListView(response.body().getItems());

                } else if (response.code() == 401) {
                    //add logout to interface
                    //userLogout();
                }
            }

            @Override
            public void onFailure(Call<PlaylistTracksList> call, Throwable t) {

            }
        });

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                setCurrentPlayingSong(position);
                playSong(position);
                showPauseButton();
            }
        });
    }

    private void playSong(int locationid) {

        mBeepPlayed = false;
        showPauseButton();
        setCurrentPlayingSong(locationid);
        getPlayer().play("spotify:track:" + mTracksAdapter.getItem(locationid).getTrack().getId());
        setSeekBar();
        getActivity().setTitle(mTracksAdapter.getItem(locationid).getTrack().getName());

    }

    private void startTimerTask() {

        TimerTask mTimerTask = new TimerTask() {
            @Override
            public void run() {
                getPlayer().getPlayerState(new PlayerStateCallback() {
                    @Override
                    public void onPlayerState(PlayerState playerState) {

                        if (mSongLocation >= mPauseTimeAt - 10000 && !mBeepPlayed) {
                            playBeep();
                            mBeepPlayed = true;
                        }
                        if (mSongLocation >= mPauseTimeAt) {
                            mPlayer.pause();
                            onSkipNextClicked();
                        }
                    }
                });
            }
        };
        mTimer = new Timer();
        mTimer.schedule(mTimerTask, 1000, 1000);
    }

    private void setSeekBar() {

        if (mPlayer != null) {
            mPlayer.getPlayerState(new PlayerStateCallback() {

                @Override
                public void onPlayerState(PlayerState playerState) {

                    mSongLocation = playerState.positionInMs;
                    mSeekBar.setMax(mPauseTimeAt);
                    mSeekBar.setProgress(mSongLocation);

                    int seconds = ((mSongLocation / 1000) % 60);
                    int minutes = ((mSongLocation / 1000) / 60);

                    mSongLocationView.setText(String.format("%2d:%02d", minutes, seconds, 0));
                }
            });
        }

        seekHandler.postDelayed(run, 1000);
    }

    Runnable run = new Runnable() {
        @Override
        public void run() {
            setSeekBar();
        }
    };

    private void playerControlsSetup() {

        View playerControls = rootView.findViewById(R.id.music_player);

        assert playerControls != null;
        playerControls.findViewById(R.id.skip_previous).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPreviousClicked();
            }
        });

        playerControls.findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayClicked();
            }
        });

        playerControls.findViewById(R.id.pause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPauseClicked();
            }
        });

        playerControls.findViewById(R.id.skip_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSkipNextClicked();
            }
        });
    }

    private Player getPlayer() {

        if (mPlayer != null) {
            return mPlayer;
        } else {

            final Config playerConfig = mSpotifyService.getPlayerConfig(getContext());
            playerConfig.useCache(false);

            mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {

                @Override
                public void onInitialized(Player player) {

                }

                @Override
                public void onError(Throwable throwable) {
                    Log.e("PlaylistActivity", "Could not initialize player: " + throwable.getMessage());
                }
            });
            mPlayer.isInitialized();
            return mPlayer;
        }
    }

    private void listviewSelector() {

        mListView.clearChoices();
        mListView.setItemChecked(mItemPosition, true);
        mListView.smoothScrollToPosition(mItemPosition);
        mListView.setSelected(true);
        mTracksAdapter.notifyDataSetChanged();
    }

    private void playBeep() {

        final MediaPlayer mediaPlayer = MediaPlayer.create(getContext(), R.raw.beep);
        mediaPlayer.start();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mediaPlayer.release();
            }
        });
    }

    private void setCurrentPlayingSong(int itemPosition) {

        this.mItemPosition = itemPosition;
        listviewSelector();
    }

    private void onPauseClicked() {

        if (mPlayer == null) {
            //Toast.makeText(this, "Please select a song", Toast.LENGTH_SHORT).show();
        } else {
            mPlayer.pause();
            showPlayButton();
        }
    }

    private void showPauseButton() {

        mPlayButton.setVisibility(View.GONE);
        mPauseButton.setVisibility(View.VISIBLE);
    }

    private void showPlayButton() {

        mPauseButton.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.VISIBLE);
    }

    private void onPlayClicked() {

        if (mPlayer == null) {
            //Toast.makeText(this, "Please select a song", Toast.LENGTH_SHORT).show();
        } else {
            mPlayer.resume();
            showPauseButton();
        }
    }

    private void onSkipNextClicked() {

        if (mTracksAdapter.getCount() <= mItemPosition + 1) {
            mItemPosition = 0;
            playSong(mItemPosition);
            mListView.setSelection(mItemPosition);
        } else {
            playSong(mItemPosition + 1);
        }
        if (mPlayer == null) {
            //Toast.makeText(this, "Please select a song", Toast.LENGTH_SHORT).show();
        }
    }

    private void onPreviousClicked() {

        if (mItemPosition < 1) {
            mItemPosition = 0;
            playSong(mItemPosition);
        } else {
            playSong(mItemPosition - 1);
        }
        if (mPlayer == null) {
            //Toast.makeText(this, "Please select a song", Toast.LENGTH_SHORT).show();
        }
    }

    public void onRadioButtonClicked(int id) {

        switch (id) {
            case R.id.one_minute_thirty:
                if (mOneThirtyMin.isChecked()) {
                    mSongDurationView.setText(R.string.one_thirty_radio_button);
                    mPauseTimeAt = 90000;
                }
                break;
            case R.id.two_minutes:
                if (mTwoMin.isChecked()) {
                    mSongDurationView.setText(R.string.two_minute_radio_button);
                    mPauseTimeAt = 120000;
                }
                break;
        }
    }

    private void updateListView(List<Item> items) {

        mTracksAdapter.clear();
        mTracksAdapter.addAll(items);
        mTracksAdapter.notifyDataSetChanged();
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onTrackSelected(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTracksInteractionListener) {
            mListener = (OnTracksInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnTracksInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Spotify.destroyPlayer(this);
        mListener = null;
    }

    public interface OnTracksInteractionListener {
        // TODO: Update argument type and name
        void onTrackSelected(Uri uri);
    }

    @Override
    public void onPause() {

        Spotify.destroyPlayer(this);
        mTimer.cancel();
        seekHandler.removeCallbacks(run);
        mSeekBar.setProgress(0);
        super.onPause();
    }


}
