package dev.felnull.imp.client.music.playlist;

import dev.felnull.otyacraftengine.util.FlagThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class IPlaylistResolver extends FlagThread {
    private static final Logger LOGGER = LogManager.getLogger("PlaylistResolver");
    private final String id;
    private boolean failure = false;
    private volatile PlaylistResult result;
    public IPlaylistResolver(String id){
        this.id = id;
        setName("Playlist Resolver");
    }

    public String getInputId() {
        return id;
    }

    protected void setFailure(String reason) {
        LOGGER.warn("Resolve Failed:{}", reason);
        this.failure = true;
    }

    protected void setFailure(Exception e){
        setFailure(e.getLocalizedMessage());
    }

    protected void setResult(PlaylistResult result) {
        this.result = result;
    }

    public boolean isFailure() {
        return failure;
    }

    public PlaylistResult getResult() {
        return result;
    }
}
