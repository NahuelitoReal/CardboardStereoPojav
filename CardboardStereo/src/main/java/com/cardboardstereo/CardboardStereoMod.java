package com.cardboardstereo;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;

/**
 * Client-only stereoscopic rendering mod for Google Cardboard, built to run
 * under PojavLauncher on Android. The mod never touches Android sensors or
 * the gyroscope: it only reads the player's normal yaw/pitch, which Pojav
 * has already driven via its own gyro-to-mouse translation. See README.md
 * for the full data-flow explanation and known caveats.
 */
@Mod(modid = CardboardStereoMod.MODID,
     name = "Cardboard Stereo",
     version = "@VERSION@",
     clientSideOnly = true,
     acceptableRemoteVersions = "*")
public class CardboardStereoMod {

    public static final String MODID = "cardboardstereo";

    @Mod.Instance(MODID)
    public static CardboardStereoMod instance;

    public static StereoRenderer stereoRenderer;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHandler.init(event.getSuggestedConfigurationFile());
        KeyBindings.init();
    }

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        Minecraft mc = Minecraft.getMinecraft();

        stereoRenderer = new StereoRenderer(mc, mc.getResourceManager());
        installEntityRenderer(mc, stereoRenderer);

        MinecraftForge.EVENT_BUS.register(stereoRenderer);
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
    }

    /**
     * Swaps Minecraft's active EntityRenderer for our subclass.
     *
     * Uses reflection (setAccessible) rather than a direct field write so
     * this keeps compiling even if the exact visibility of
     * Minecraft#entityRenderer differs slightly between MCP mapping
     * versions. The field name "entityRenderer" itself has been stable
     * across MCP mappings for 1.8.x.
     */
    private static void installEntityRenderer(Minecraft mc, StereoRenderer renderer) {
        try {
            Field field = Minecraft.class.getDeclaredField("entityRenderer");
            field.setAccessible(true);
            field.set(mc, renderer);
        } catch (Exception e) {
            throw new RuntimeException(
                "CardboardStereo: could not install the stereo EntityRenderer. "
                + "If Minecraft.class no longer has a field literally named "
                + "'entityRenderer' under your chosen MCP mapping, rename the "
                + "field lookup in CardboardStereoMod#installEntityRenderer to "
                + "match (check via your IDE's decompiled Minecraft.class).", e);
        }
    }
}
