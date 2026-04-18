package dev.felnull.imp.explatform.neoforge;

import dan200.computercraft.impl.Peripherals;
import dev.felnull.imp.integration.CCTIntegration;

public class IMPCCTExpectPlatformImpl {
    public static void init() {
        Peripherals.addGenericLookup(CCTIntegration::lookup);
    }
}
