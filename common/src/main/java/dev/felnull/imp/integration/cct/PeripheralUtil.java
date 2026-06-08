package dev.felnull.imp.integration.cct;

import dan200.computercraft.api.lua.LuaException;

import java.util.UUID;

public class PeripheralUtil {
    public static UUID getUUID(String uuid) throws LuaException {
        try{
            return UUID.fromString(uuid);
        } catch (Exception e) {
            throw new LuaException("Invalid UUID format.");
        }
    }
}
