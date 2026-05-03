package com.weaversworkshop.playerdisguise;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class PlayerDisguise {
    public static final String MODID = "playerdisguise";
    public static final Logger LOGGER = LogUtils.getLogger();

    private PlayerDisguise() {}

    public static void init() {
        LOGGER.info("PlayerDisguise common init");
    }
}
