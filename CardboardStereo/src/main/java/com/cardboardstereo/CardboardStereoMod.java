package com.cardboardstereo;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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

        // Plain, direct field assignment - NOT reflection. FML's runtime
        // deobfuscation transformer rewrites this normal field access from
        // the MCP name ("entityRenderer") to whatever the field is really
        // called in the obfuscated production jar, exactly like it does
        // for every other field/method access in this file. A reflective
        // getDeclaredField("entityRenderer") lookup bypasses that
        // transformer entirely and fails at runtime, which is what caused
        // the NoSuchFieldException crash.
        mc.entityRenderer = stereoRenderer;

        MinecraftForge.EVENT_BUS.register(stereoRenderer);
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
    }
}
