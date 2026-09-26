package com.cardboardstereo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Stage 1 of the mod: notices when the player enters/leaves a world
 * (singleplayer, a server, or re-entering after leaving) and drives the
 * automatic Stereo Mode activation, plus the manual F8 toggle.
 *
 * We deliberately poll `Minecraft.theWorld` on the client tick rather than
 * relying only on WorldEvent.Load/Unload, because those fire on the
 * server/logical-side thread for singleplayer's internal integrated server
 * too, which is a needless complication for a purely client-side visual
 * feature. Watching the client's world reference is simpler and equally
 * correct for both singleplayer and multiplayer.
 */
public class ClientEventHandler {

    private World lastWorld = null;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World currentWorld = mc.theWorld;

        if (currentWorld != lastWorld) {
            if (currentWorld != null) {
                // Joined a world: singleplayer, a server, or a rejoin.
                if (ConfigHandler.stereoEnabledByDefault) {
                    CardboardStereoMod.stereoRenderer.setEnabled(true);
                    CardboardStereoMod.stereoRenderer.showOverlayMessage();
                }
            } else {
                // Left the world entirely (back to main menu / disconnected).
                CardboardStereoMod.stereoRenderer.setEnabled(false);
                CardboardStereoMod.stereoRenderer.onWorldUnload();
            }
            lastWorld = currentWorld;
        }

        if (mc.theWorld != null && KeyBindings.toggleStereo.isPressed()) {
            CardboardStereoMod.stereoRenderer.toggle();
        }
    }
}
