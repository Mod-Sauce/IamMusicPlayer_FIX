package org.modsauce.impr.client.music.media;

import org.modsauce.impr.music.resource.ImageInfo;
import org.modsauce.impr.music.resource.MusicSource;

public record MusicMediaResult(MusicSource source, ImageInfo imageInfo, String name, String author) {
}
