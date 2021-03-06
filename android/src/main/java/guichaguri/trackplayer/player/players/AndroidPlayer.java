package guichaguri.trackplayer.player.players;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.support.v4.media.session.PlaybackStateCompat;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import guichaguri.trackplayer.logic.LibHelper;
import guichaguri.trackplayer.logic.MediaManager;
import guichaguri.trackplayer.logic.Utils;
import guichaguri.trackplayer.logic.track.Track;
import guichaguri.trackplayer.player.LocalPlayer;
import guichaguri.trackplayer.player.components.PlayerView;
import guichaguri.trackplayer.player.components.ProxyCache;
import java.io.IOException;

/**
 * Basic player using Android's {@link MediaPlayer}
 *
 * @author Guilherme Chaguri
 */
public class AndroidPlayer extends LocalPlayer<Track> implements OnInfoListener, OnCompletionListener,
        OnSeekCompleteListener, OnPreparedListener, OnBufferingUpdateListener, OnErrorListener {

    private final MediaPlayer player;
    private ProxyCache cache;

    private Promise loadCallback;

    private boolean loaded = false;
    private boolean buffering = false;
    private boolean ended = false;
    private boolean started = false;

    private float buffered = 0;

    public AndroidPlayer(Context context, MediaManager manager) {
        super(context, manager);

        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        player.setOnInfoListener(this);
        player.setOnCompletionListener(this);
        player.setOnSeekCompleteListener(this);
        player.setOnPreparedListener(this);
        player.setOnBufferingUpdateListener(this);
        player.setOnErrorListener(this);
    }

    @Override
    protected Track createTrack(ReadableMap data) {
        return new Track(manager, data);
    }

    @Override
    protected Track createTrack(Track track) {
        return new Track(track);
    }

    @Override
    public void load(Track track, Promise callback) {
        String url = track.url.url;
        boolean local = track.url.local;
        int cacheMaxFiles = track.cache.maxFiles;
        long cacheMaxSize = track.cache.maxSize;

        // Resets the player to update its state to idle
        player.reset();
        if(cache != null) cache.destroy();

        // Prepares the caching
        if(LibHelper.isProxyCacheAvailable() && !local && (cacheMaxFiles > 0 || cacheMaxSize > 0)) {
            cache = new ProxyCache(context, cacheMaxFiles, cacheMaxSize);
            url = cache.getURL(url, track.id);
        } else {
            cache = null;
        }

        // Updates the state
        buffering = true;
        ended = false;
        loaded = false;

        try {
            // Loads the uri
            loadCallback = callback;
            player.setDataSource(context, Utils.toUri(context, url, local));
            player.prepareAsync();
        } catch(IOException ex) {
            loadCallback = null;
            Utils.rejectCallback(callback, ex);
            manager.onError(this, ex);
        }

        updateState();
    }

    @Override
    public void reset() {
        super.reset();

        // Release the playback resources
        player.reset();

        // Stops the caching server
        if(cache != null) {
            cache.destroy();
            cache = null;
        }

        // Update the state
        buffering = false;
        ended = false;
        loaded = false;
        updateState();
    }

    @Override
    public void play() {
        started = true;

        if(!loaded) return;

        player.start();

        buffering = false;
        ended = false;
        updateState();
    }

    @Override
    public void pause() {
        started = false;

        if(!loaded) return;

        player.pause();

        updateState();
    }

    @Override
    public void stop() {
        started = false;

        if(!loaded) return;

        player.stop();

        ended = true;
        updateState();
    }

    @Override
    public int getState() {
        if(ended) return PlaybackStateCompat.STATE_STOPPED;
        if(buffering) return PlaybackStateCompat.STATE_BUFFERING;
        if(!loaded) return PlaybackStateCompat.STATE_NONE;
        if(!player.isPlaying()) return PlaybackStateCompat.STATE_PAUSED;
        return PlaybackStateCompat.STATE_PLAYING;
    }

    @Override
    public long getPosition() {
        return loaded ? player.getCurrentPosition() : 0;
    }

    @Override
    public long getBufferedPosition() {
        return (long)(buffered * getDuration());
    }

    @Override
    public long getDuration() {
        return loaded ? player.getDuration() : 0;
    }

    @Override
    public void seekTo(long ms) {
        buffering = true;
        player.seekTo((int)ms);
        updateState();
    }

    @Override
    public float getSpeed() {
        return 1; // player.getPlaybackParams().getSpeed();
    }

    @Override
    public void setVolume(float volume) {
        player.setVolume(volume, volume);
        updateMetadata();
    }

    @Override
    public void bindView(PlayerView view) {
        player.setDisplay(view != null ? view.getHolder() : null);
    }

    @Override
    public void destroy() {
        player.release();

        if(cache != null) {
            cache.destroy();
            cache = null;
        }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if(what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            buffering = true;
            updateState();
        } else if(what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            buffering = false;
            updateState();
        }
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        ended = true;
        updateState();

        manager.onEnd(this);

        skipToNext(null);
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        buffering = false;
        updateState();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if(started) player.start();

        Utils.resolveCallback(loadCallback);
        loadCallback = null;

        loaded = true;
        buffering = false;
        updateState();

        manager.onLoad(this, getCurrentTrack());
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        buffered = percent / 100F;
        updateMetadata();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Exception ex;
        if(what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            ex = new IOException("Server died");
        } else {
            ex = new RuntimeException("Unknown error");
        }

        Utils.rejectCallback(loadCallback, ex);
        loadCallback = null;

        manager.onError(this, ex);
        return true;
    }

    private void updateState() {
        updateState(getState());
    }
}
