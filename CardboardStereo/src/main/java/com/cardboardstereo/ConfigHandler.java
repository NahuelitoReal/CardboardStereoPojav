package com.cardboardstereo;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class ConfigHandler {

    public static Configuration config;

    public static boolean stereoEnabledByDefault = true;
    public static double eyeSeparation = 0.065D;
    public static boolean fisheyeEnabled = true;
    public static float fisheyeStrength = 0.22F;
    public static boolean swapEyes = false;

    public static void init(File file) {
        if (config == null) {
            config = new Configuration(file);
            load();
        }
    }

    public static void load() {
        try {
            config.load();

            stereoEnabledByDefault = config.getBoolean(
                "stereoEnabledByDefault", "general", true,
                "Automatically enable stereo mode when joining a world.");

            eyeSeparation = config.get(
                "general", "eyeSeparation", 0.065D,
                "Distance between the two virtual cameras, in blocks. "
                + "Roughly matches human IPD scaled to Minecraft's world units.")
                .getDouble();

            fisheyeEnabled = config.getBoolean(
                "fisheyeEnabled", "general", true,
                "Apply barrel distortion correction for Cardboard lenses.");

            fisheyeStrength = (float) config.get(
                "general", "fisheyeStrength", 0.22D,
                "Strength of the barrel distortion. Tune to match your "
                + "specific Cardboard lenses (try 0.1 - 0.4).")
                .getDouble();

            swapEyes = config.getBoolean(
                "swapEyes", "general", false,
                "Flip which physical camera offset is used for which screen "
                + "half, in case left/right appear swapped for your headset.");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static void save() {
        if (config != null && config.hasChanged()) {
            config.save();
        }
    }
}
