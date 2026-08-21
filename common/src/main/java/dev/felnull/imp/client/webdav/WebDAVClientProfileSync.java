package dev.felnull.imp.client.webdav;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.IamMusicPlayer;
import dev.felnull.imp.networking.IMPPackets;

public final class WebDAVClientProfileSync {
    private static String lastFingerprint = "";

    private WebDAVClientProfileSync() {
    }

    public static void sync() {
        var config = IamMusicPlayer.getConfig().webDAVConfig;
        String fingerprint = config.baseUrl + "\n" + config.rootPath + "\n" + config.username + "\n" + config.password;
        if (fingerprint.equals(lastFingerprint)) return;
        NetworkManager.sendToServer(IMPPackets.WEBDAV_PROFILE_SYNC_CTS, new IMPPackets.WebDAVProfileSyncMessage(config.baseUrl, config.rootPath, config.username, config.password).toRFBB());
        lastFingerprint = fingerprint;
    }
}
