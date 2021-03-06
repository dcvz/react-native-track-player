package guichaguri.trackplayer.logic.workers;

import android.content.Intent;
import android.os.Bundle;
import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import guichaguri.trackplayer.logic.Utils;
import javax.annotation.Nullable;

/**
 * @author Guilherme Chaguri
 */
public class PlayerTask extends HeadlessJsTaskService {

    public static final String EVENT_PLAYER = "track-player-event-player";
    public static final String EVENT_TYPE = "track-player-event-type";
    public static final String EVENT_DATA = "track-player-event-data";

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        if(intent == null) return null;

        String event = intent.getStringExtra(EVENT_TYPE);
        Bundle bundle = intent.getBundleExtra(EVENT_DATA);
        int player = intent.getIntExtra(EVENT_PLAYER, -1);

        if(event == null && bundle == null) {
            stopSelf();
            return null;
        }

        Utils.log("Sending event %s...", event);

        WritableMap map = bundle != null ? Arguments.fromBundle(bundle) : Arguments.createMap();
        if(event != null) map.putString("type", event);
        if(player != -1) map.putInt("player", player);

        return new HeadlessJsTaskConfig("TrackPlayer", map, 0, true);
    }

}
