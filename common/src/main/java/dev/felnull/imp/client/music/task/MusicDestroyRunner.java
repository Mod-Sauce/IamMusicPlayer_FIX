package dev.felnull.imp.client.music.task;

import dev.felnull.imp.client.util.MusicUtils;

public interface MusicDestroyRunner {
    default void run(Runnable ifDestroy) {
        if (isDestroy()) {
            MusicUtils.runOnMusicTick(ifDestroy);
            // A **dedicated exception type** expresses "this slot was stopped" (cancelled) instead of matching on
            // message text: the consumer (MusicEntry) needs it to tell "normal cancellation" from "a real failure",
            // and matching on the message would silently break whenever the wording changes.
            throw new dev.felnull.imp.client.music.MusicEntry.MusicLoadCancelled();
        }
    }

    boolean isDestroy();
}
