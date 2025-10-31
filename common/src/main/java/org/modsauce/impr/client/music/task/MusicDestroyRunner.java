package org.modsauce.impr.client.music.task;

import org.modsauce.impr.client.util.MusicUtils;

public interface MusicDestroyRunner {
    default void run(Runnable ifDestroy) {
        if (isDestroy()) {
            MusicUtils.runOnMusicTick(ifDestroy);
            throw new RuntimeException("Stopped!");
        }
    }

    boolean isDestroy();
}
