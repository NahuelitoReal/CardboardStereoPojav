package com.cardboardstereo;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public class KeyBindings {

    public static KeyBinding toggleStereo;

    public static void init() {
        toggleStereo = new KeyBinding(
            "key.cardboardstereo.toggle",
            Keyboard.KEY_F8,
            "key.categories.cardboardstereo");
        ClientRegistry.registerKeyBinding(toggleStereo);
    }
}
