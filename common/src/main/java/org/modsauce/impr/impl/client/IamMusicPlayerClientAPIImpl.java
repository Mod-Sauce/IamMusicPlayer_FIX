package org.modsauce.impr.impl.client;

import org.modsauce.impr.api.client.IamMusicPlayerClientAPI;
import org.modsauce.impr.api.client.MusicEngineAccess;
import org.modsauce.impr.client.music.MusicEngine;

public class IamMusicPlayerClientAPIImpl implements IamMusicPlayerClientAPI {
    public static final IamMusicPlayerClientAPIImpl INSTANCE = new IamMusicPlayerClientAPIImpl();

    @Override
    public MusicEngineAccess getMusicEngine() {
        return MusicEngine.getInstance();
    }
}
