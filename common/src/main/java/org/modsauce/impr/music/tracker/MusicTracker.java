package org.modsauce.impr.music.tracker;

import org.modsauce.impr.music.MusicSpeakerInfo;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;

/**
 * 音声の位置、情報などを追跡するため
 */
public interface MusicTracker extends TagSerializable {
    MusicSpeakerInfo getSpeakerInfo();
}
