package dev.felnull.imp.music.tracker;

import dev.felnull.imp.music.MusicSpeakerInfo;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;

/**
 * 音声の位置、情報などを追跡するため
 */
public interface MusicTracker extends TagSerializable {
    MusicSpeakerInfo getSpeakerInfo();
}
