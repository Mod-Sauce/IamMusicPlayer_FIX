package dev.felnull.imp.explatform;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class IMPCCTExpectPlatform {
    @ExpectPlatform
    public static void init(){throw new AssertionError();}
}
