# Cardboard Stereo — Minecraft 1.8.9 / Forge 11.15.1.2318

Client-side mod that splits the screen into a left/right stereoscopic pair
for Google Cardboard, intended to run under PojavLauncher on Android.

## Data flow (as requested)

```
Gyroscope -> PojavLauncher -> mouse movement -> Minecraft yaw/pitch -> this mod -> two cameras
```

The mod never touches Android sensors, the gyroscope, or any PojavLauncher
API. It only reads `EntityPlayer.rotationYaw` — the same field Pojav is
already driving via its built-in gyro-to-mouse translation, and the same
field a normal mouse/touchpad drives. Works identically either way.

## How the stereo pair is actually produced

Minecraft 1.8.9 has no public hook that lets a mod cleanly "render the
world twice with a different camera" — doing that at all without ASM/Mixins
means replacing `Minecraft.entityRenderer` with a subclass, which is the
approach this project uses (`StereoRenderer extends EntityRenderer`).

To avoid touching any *private* vanilla methods (whose exact names can
shift slightly between MCP mapping versions), the parallax offset is
produced a different way than "translate the GL camera": for each eye, the
player entity's `posX`/`posZ`/`prevPosX`/`prevPosZ` are nudged sideways by
half the configured eye separation, `super.updateCameraAndRender(...)` is
called into an off-screen `Framebuffer` for that eye, and the fields are
restored immediately after. `rotationYaw`/`rotationPitch` are **never**
read from anywhere but the player entity and **never** written — the
player keeps one single, permanent orientation, exactly as required.

```
for each eye:
    shift player.posX/posZ sideways by ±IPD/2 (temporarily)
    bind that eye's offscreen Framebuffer
    super.updateCameraAndRender(...)   <- normal vanilla render, into our FBO
    restore player.posX/posZ exactly

composite: draw left FBO texture into left half of the real screen
           draw right FBO texture into right half of the real screen
           (each optionally through the barrel-distortion fragment shader)
```

This is staged in the code exactly as you asked:

| Stage | Where |
|---|---|
| 1. World join/leave detection, F8 toggle, "Stereo Mode: ON/OFF" overlay | `ClientEventHandler.java`, `StereoRenderer#showOverlayMessage` |
| 2. Two camera passes with IPD offset | `StereoRenderer#renderStereoFrame` / `#renderEye` |
| 3. Per-eye Framebuffer Objects | `StereoRenderer#ensureFramebuffers`, `Framebuffer leftFB/rightFB` |
| 4. Barrel distortion shader | `ShaderProgram.java`, `assets/cardboardstereo/shaders/*.vsh/.fsh` |
| 5. Config (eye separation, fisheye on/off + strength, stereo default, eye swap) | `ConfigHandler.java` |

## Building

```
./gradlew setupDecompWorkspace   # first time only, slow
./gradlew build
```

The compiled jar lands in `build/libs/CardboardStereo-1.0.0.jar`. Drop it
into `.minecraft/mods` (or Pojav's equivalent mods folder) alongside Forge
11.15.1.2318 for 1.8.9.

## In-game

- Join any world (singleplayer or a server) → stereo mode auto-enables and
  briefly shows "Stereo Mode: ON".
- **F8** toggles it manually at any time.
- Config file (`config/cardboardstereo.cfg`) exposes:
  - `stereoEnabledByDefault`
  - `eyeSeparation` (default `0.065`)
  - `fisheyeEnabled`
  - `fisheyeStrength` (default `0.22`)
  - `swapEyes` (flip which half gets which camera, if your Cardboard shows
    it mirrored)

## Known caveats / things to verify in your dev environment

1. **`Minecraft#entityRenderer` field access.** The mod finds this field by
   reflection under the literal name `"entityRenderer"`, which has been
   stable across MCP mappings for 1.8.x, but if your chosen mapping ever
   differs, fix the lookup in `CardboardStereoMod#installEntityRenderer`
   (your IDE can jump straight into the decompiled `Minecraft.class` to
   confirm the real field name once ForgeGradle's decompiled workspace is
   set up).
2. **HUD/hand rendering per eye.** Because each eye is a full,
   independent call into vanilla's own `updateCameraAndRender`, the HUD
   (hotbar, crosshair, hand) currently gets drawn once per eye too — which
   is usually fine (and even desirable, so each eye's HUD sits in the
   right half of the screen), but it does mean it isn't parallax-corrected
   like the world geometry. If you'd rather suppress it on the second eye,
   there's a ready-made (currently commented out) cancellation in
   `StereoRenderer#onRenderOverlay`.
3. **Performance.** Rendering the full 3D scene twice per frame roughly
   doubles GPU cost. On phone-class GPUs via PojavLauncher, expect to need
   lower render distance / lower graphics settings than you'd normally run.
4. **FOV/aspect ratio.** Each eye's FBO is rendered at full display
   resolution before being scaled into its half of the screen — this
   keeps quality high but means the perceived FOV per eye is whatever
   your normal Minecraft FOV setting is, not automatically corrected for
   Cardboard's typical wider per-eye FOV. Adjust Minecraft's own FOV
   slider to taste.
5. **Barrel distortion strength has no universally correct value** — it
   depends on your specific Cardboard lens curvature. Tune
   `fisheyeStrength` empirically.

## Server compatibility

Everything above is purely client-side rendering: no packets are added,
no server-side class is touched, and a vanilla/Forge server has no idea
this mod exists.
