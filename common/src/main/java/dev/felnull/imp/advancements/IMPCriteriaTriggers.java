package dev.felnull.imp.advancements;

import net.minecraft.advancements.CriteriaTriggers;

public class IMPCriteriaTriggers {
    public static final AddMusicTrigger ADD_MUSIC = new AddMusicTrigger();
    public static final WriteCassetteTapeTrigger WRITE_CASSETTE_TAPE = new WriteCassetteTapeTrigger();
    public static final ListenToMusicTrigger LISTEN_TO_MUSIC = new ListenToMusicTrigger();

    public static void init() {
        CriteriaTriggers.register("add_music", ADD_MUSIC);
        CriteriaTriggers.register("write_cassette_tape", WRITE_CASSETTE_TAPE);
        CriteriaTriggers.register("listen_to_music", LISTEN_TO_MUSIC);
    }
}
