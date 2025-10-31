package org.modsauce.impr.api.client;

import org.modsauce.impr.api.MusicSpeakerInfoAccess;

public interface MusicSpeakerAccess {
    /**
     * スピーカーの再生情報の取得
     *
     * @return 再生情報
     */
    MusicSpeakerInfoAccess getInfo();
}
