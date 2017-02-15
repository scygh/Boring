package com.susion.boring.music.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.susion.boring.music.model.Song;
import com.susion.boring.music.presenter.IMediaPlayPresenter;
import com.susion.boring.music.presenter.MediaPlayPresenter;
import com.susion.boring.music.view.IMediaPlayView;
import com.susion.boring.utils.SPUtils;

/**
 * Created by susion on 17/2/13.
 */
public class MusicPlayerService extends Service implements IMediaPlayView{

    private ServiceMusicReceiver mReceiver;
    private IMediaPlayPresenter mPresenter;
    public static final String SERVICE_ACTION = "MUSIC_SERVICE";
    private Song mSong;
    private boolean mAutoPlay;

    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    private void init() {
        mPresenter = new MediaPlayPresenter(this, this);
        mReceiver = new ServiceMusicReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, mReceiver.getIntentFilter());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadMusicInfo(Song song, boolean autoPlay) {
        mAutoPlay = autoPlay;
        if (song != null) {
            mSong = song;
            try {
                mPresenter.initMediaPlayer(mSong.audio);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void tryToChangeMusic(Song song) {
        mAutoPlay = true;
        if (song.id.equals(mSong.id) && mPresenter.isPrepared()) {
            notifyMediaDuration();
            return;
        }

        mSong = song;
        try {
            mPresenter.initMediaPlayer(mSong.audio);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyMediaDuration() {
        Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_SET_DURATION);
        intent.putExtra(MusicInstruction.CLIENT_PARAM_MEDIA_DURATION, mPresenter.getDuration());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void updateBufferedProgress(int percent) {
        Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_UPDATE_BUFFERED_PROGRESS);
        intent.putExtra(MusicInstruction.CLIENT_PARAM_BUFFERED_PROGRESS, percent);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void updatePlayProgress(int curPos, int duration) {
        Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_UPDATE_PLAY_PROGRESS);
        intent.putExtra(MusicInstruction.CLIENT_PARAM_PLAY_PROGRESS_CUR_POS, curPos);
        intent.putExtra(MusicInstruction.CLIENT_PARAM_PLAY_PROGRESS_DURATION, duration);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void preparedPlay(int duration) {
        if (mAutoPlay) {
            mPresenter.startPlay();
        }

        Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_PLAYER_PREPARED);
        intent.putExtra(MusicInstruction.CLIENT_PARAM_PREPARED_TOTAL_DURATION, duration);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void informCurrentPlayMusic() {
        Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_CURRENT_PLAY_MUSIC);
        Song song;
        if (mSong == null) {
            song = SPUtils.getGson().fromJson(SPUtils.getStringFromMusicConfig(SPUtils.MUSIC_CONFIG_LAST_PLAY_MUSIC, this),
                    Song.class);
        } else {
            song = mSong;
        }
        intent.putExtra(MusicInstruction.CLIENT_PARAM_CURRENT_PLAY_MUSIC, song);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void informCurrentIfPlaying() {
        Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_CURRENT_IS_PALING);
        if (mPresenter.isPrepared()) {
            if (mPresenter.isPlaying()) {
                intent.putExtra(MusicInstruction.CLIENT_PARAM_IS_PLAYING, true);
            } else {
                intent.putExtra(MusicInstruction.CLIENT_PARAM_IS_PLAYING, false);
            }
            intent.putExtra(MusicInstruction.CLIENT_PARAM_NEED_LOAD_MUSIC, false);
        } else {
            intent.putExtra(MusicInstruction.CLIENT_PARAM_NEED_LOAD_MUSIC, true);
            intent.putExtra(MusicInstruction.CLIENT_PARAM_IS_PLAYING, false);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void informCurrentIfPlayProgress() {
        if (mPresenter.isPrepared()) {
            Intent intent = new Intent(MusicInstruction.CLIENT_RECEIVER_CURRENT_PLAY_PROGRESS);
            intent.putExtra(MusicInstruction.CLIENT_PARAM_CURRENT_PLAY_PROGRESS, mPresenter.getCurrentProgress());
            intent.putExtra(MusicInstruction.CLIENT_PARAM_MEDIA_DURATION, mPresenter.getDuration());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private void saveLastPlayMusic() {
        SPUtils.writeStringToMusicConfig(SPUtils.MUSIC_CONFIG_LAST_PLAY_MUSIC, SPUtils.getGson().toJson(mSong),this);
    }

    @Override
    public void completionPlay() {

    }

    //用长按home调出最近运行历史，在这里面清除软件,可能会调用
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        clear();
    }

    //手动停止, 会被调用
    @Override
    public void onDestroy() {
        super.onDestroy();
        clear();
    }

    private void clear() {
        saveLastPlayMusic();
        mPresenter.releaseResource();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }


    class ServiceMusicReceiver extends BroadcastReceiver{

        IntentFilter getIntentFilter(){
            IntentFilter filter = new IntentFilter();
            filter.addAction(MusicInstruction.SERVICE_RECEIVER_PLAY_MUSIC);
            filter.addAction(MusicInstruction.SERVICE_RECEIVER_PAUSE_MUSIC);
            filter.addAction(MusicInstruction.SERVICE_RECEIVER_SEEK_TO);
            filter.addAction(MusicInstruction.SERVICE_SAVE_LAST_PLAY_MUSIC);
            filter.addAction(MusicInstruction.SERVICE_CURRENT_PLAY_MUSIC);
            filter.addAction(MusicInstruction.SERVICE_LOAD_MUSIC_INFO);
            filter.addAction(MusicInstruction.SERVER_RECEIVER_CHANGE_MUSIC);
            filter.addAction(MusicInstruction.SERVICE_RECEIVER_QUERY_CURRENT_STATE);
            filter.addAction(MusicInstruction.SERVICE_RECEIVER_QUERY_IS_PLAYING);
            filter.addAction(MusicInstruction.SERVICE_RECEIVER_GET_PLAY_PROGRESS);
            return filter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action){
                case MusicInstruction.SERVICE_LOAD_MUSIC_INFO:
                    loadMusicInfo((Song) intent.getSerializableExtra(MusicInstruction.SERVICE_PARAM_PLAY_SONG),
                            intent.getBooleanExtra(MusicInstruction.SERVICE_PARAM_PLAY_SONG_AUTO_PLAY, false));
                    break;
                case MusicInstruction.SERVICE_RECEIVER_PLAY_MUSIC:
                    if (!mPresenter.isPrepared()) {
                        mAutoPlay = true;
                        return;
                    }
                    mPresenter.startPlay();
                    break;
                case MusicInstruction.SERVICE_RECEIVER_PAUSE_MUSIC:
                    mPresenter.pausePlay();
                    break;
                case MusicInstruction.SERVICE_RECEIVER_SEEK_TO:
                    mPresenter.seekTo(intent.getIntExtra(MusicInstruction.SERVICE_PARAM_SEEK_TO_POS, 0));
                    break;
                case MusicInstruction.SERVICE_SAVE_LAST_PLAY_MUSIC:
                    saveLastPlayMusic();
                    break;
                case MusicInstruction.SERVICE_CURRENT_PLAY_MUSIC:
                    informCurrentPlayMusic();
                    break;
                case MusicInstruction.SERVER_RECEIVER_CHANGE_MUSIC:
                    tryToChangeMusic((Song) intent.getSerializableExtra(MusicInstruction.SERVICE_PARAM_CHANGE_MUSIC));
                    break;
                case MusicInstruction.SERVICE_RECEIVER_QUERY_IS_PLAYING:
                    informCurrentIfPlaying();
                    break;
                case MusicInstruction.SERVICE_RECEIVER_GET_PLAY_PROGRESS:
                    informCurrentIfPlayProgress();
                    break;
            }
        }
    }

}