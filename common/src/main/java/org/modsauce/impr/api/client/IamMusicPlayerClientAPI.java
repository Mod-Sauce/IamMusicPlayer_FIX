package org.modsauce.impr.api.client;

import org.modsauce.impr.impl.client.IamMusicPlayerClientAPIImpl;

/**
 * IamMusicPlayerAPIと同じで互換性を維持させてください
 *
 * @author MORIMROI0317
 * @since 3.19
 */
public interface IamMusicPlayerClientAPI {
    static IamMusicPlayerClientAPI getInstance() {
        return IamMusicPlayerClientAPIImpl.INSTANCE;
    }

    /**
     * 音楽エンジンを取得
     *
     * @return 音楽エンジン
     */
    MusicEngineAccess getMusicEngine();
}
