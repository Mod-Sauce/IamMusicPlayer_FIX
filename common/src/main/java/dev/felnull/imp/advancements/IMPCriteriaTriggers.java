package dev.felnull.imp.advancements;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;

import java.lang.reflect.Method;

public class IMPCriteriaTriggers {
    public static final AddMusicTrigger ADD_MUSIC = new AddMusicTrigger();
    public static final WriteCassetteTapeTrigger WRITE_CASSETTE_TAPE = new WriteCassetteTapeTrigger();
    public static final ListenToMusicTrigger LISTEN_TO_MUSIC = new ListenToMusicTrigger();
/*
    public static void init() {
        CriteriaTriggers.register(ADD_MUSIC);
        CriteriaTriggers.register(WRITE_CASSETTE_TAPE);
        CriteriaTriggers.register(LISTEN_TO_MUSIC);
    }
    */
    public static void IMPInvolvementTrigger() {
        try {
            Method registerMethod = CriteriaTriggers.class.getDeclaredMethod("register", CriterionTrigger.class);
            registerMethod.setAccessible(true);
            registerMethod.invoke(null, ADD_MUSIC);
            registerMethod.invoke(null,WRITE_CASSETTE_TAPE);
            registerMethod.invoke(null,LISTEN_TO_MUSIC);
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exception
        }
    }
}
