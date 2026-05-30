package dev.felnull.imp.client.music.playlist;

import dev.felnull.imp.client.gui.screen.monitor.music_manager.ImportPlayListBaseMMMonitor;
import dev.felnull.imp.music.resource.ImageInfo;
import dev.felnull.imp.music.resource.Music;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record PlaylistResult(String id, int count, String name, String author, @Nullable ImageInfo ico, List<ImportPlayListBaseMMMonitor.PlayListEntry> musicEntries, List<Music> musics) { }
