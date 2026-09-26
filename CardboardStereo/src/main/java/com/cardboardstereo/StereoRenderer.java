package com.cardboardstereo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Replaces Minecraft's normal EntityRenderer. When stereo mode is active
 * and a world is loaded, updateCameraAndRender() renders the scene TWICE
 * - once per eye - into two full-resolution offscreen Framebuffers, using
 * a temporary sideways shift of the player's interpolated render position
 * to produce the parallax offset (see renderEye()). The two eye textures
 * are then composited side-by-side into the real screen, optionally
 * passing each through a barrel-distortion fragment shader.
 *
 * IMPORTANT: this never reads rotationYaw/rotationPitch from anywhere but
 * the player entity's own fields, and never writes to them. Only posX/Z
 * and prevPosX/Z are nudged, and only for the duration of a single eye's
 * render call, then restored exactly. The player's actual simulated
 * position and orientation are therefore untouched.
 */
public class StereoRenderer extends EntityRenderer {

    private final Minecraft mc;

    private boolean enabled = false;
    private boolean secondaryEyePass = false;

    private Framebuffer leftFB;
    private Framebuffer rightFB;
    private int fbWidth = -1;
    private int fbHeight = -1;

    private ShaderProgram fisheyeShader;
    private boolean shaderLoaded = false;

    private int overlayTicksRemaining = 0;
    private String overlayText = "";

    public StereoRenderer(Minecraft mcIn, IResourceManager resourceManager) {
        super(mcIn, resourceManager);
        this.mc = mcIn;
    }

    // ------------------------------------------------------------------
    // Public control API, used by ClientEventHandler / the keybinding.
    // ------------------------------------------------------------------

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        showOverlayMessage();
    }

    public void showOverlayMessage() {
        this.overlayText = "Stereo Mode: " + (enabled ? "ON" : "OFF");
        this.overlayTicksRemaining = 60; // ~3 seconds at 20 ticks/sec
    }

    public void onWorldUnload() {
        // Free GPU resources tied to the old world's render context.
        if (leftFB != null) {
            leftFB.deleteFramebuffer();
            leftFB = null;
        }
        if (rightFB != null) {
            rightFB.deleteFramebuffer();
            rightFB = null;
        }
        fbWidth = -1;
        fbHeight = -1;
    }

    // ------------------------------------------------------------------
    // Main render entry point (called every frame by Minecraft.runGameLoop)
    // ------------------------------------------------------------------

    @Override
    public void updateCameraAndRender(float partialTicks, long finishTimeNano) {
        if (!enabled || mc.theWorld == null || mc.thePlayer == null) {
            super.updateCameraAndRender(partialTicks, finishTimeNano);
            return;
        }

        try {
            renderStereoFrame(partialTicks, finishTimeNano);
        } catch (Exception e) {
            // Never let a stereo-render failure crash the whole game -
            // fall back to a normal single-eye frame and disable stereo.
            System.err.println("CardboardStereo: stereo render failed, "
                + "disabling stereo mode. Cause: " + e);
            e.printStackTrace();
            enabled = false;
            super.updateCameraAndRender(partialTicks, finishTimeNano);
        }
    }

    private void renderStereoFrame(float partialTicks, long finishTimeNano) {
        ensureFramebuffers();
        if (!shaderLoaded) {
            fisheyeShader = new ShaderProgram();
            fisheyeShader.load(
                "/assets/cardboardstereo/shaders/vertex.vsh",
                "/assets/cardboardstereo/shaders/fisheye.fsh");
            shaderLoaded = true;
        }

        EntityPlayer player = mc.thePlayer;
        double half = ConfigHandler.eyeSeparation / 2.0D;

        // Right-vector (perpendicular to look direction) on the horizontal
        // plane, derived purely from the player's current yaw - the same
        // yaw Pojav has already been driving via its gyro-to-mouse layer.
        float yawRad = player.rotationYaw * ((float) Math.PI / 180F);
        double rightX = MathHelper.cos(yawRad);
        double rightZ = MathHelper.sin(yawRad);

        boolean swap = ConfigHandler.swapEyes;
        double leftSign = swap ? 1.0D : -1.0D;
        double rightSign = swap ? -1.0D : 1.0D;

        // Left eye
        secondaryEyePass = false;
        renderEye(leftFB, player, rightX * half * leftSign, rightZ * half * leftSign,
            partialTicks, finishTimeNano);

        // Right eye
        secondaryEyePass = true;
        renderEye(rightFB, player, rightX * half * rightSign, rightZ * half * rightSign,
            partialTicks, finishTimeNano);

        compositeToScreen();
    }

    /**
     * Renders one full frame into the given offscreen framebuffer, with the
     * player's render position temporarily nudged sideways by (offsetX,
     * offsetZ) to produce that eye's parallax view. The player's real
     * simulation state (posX/posZ/prevPosX/prevPosZ) is restored exactly
     * afterwards, so nothing about the player's actual position changes.
     */
    private void renderEye(Framebuffer target, EntityPlayer player,
                            double offsetX, double offsetZ,
                            float partialTicks, long finishTimeNano) {

        double origPosX = player.posX;
        double origPosZ = player.posZ;
        double origPrevPosX = player.prevPosX;
        double origPrevPosZ = player.prevPosZ;

        try {
            player.posX = origPosX + offsetX;
            player.posZ = origPosZ + offsetZ;
            // Also shift the interpolation source position so the camera
            // doesn't briefly "swim" toward the offset over the next tick's
            // partial-tick interpolation.
            player.prevPosX = origPrevPosX + offsetX;
            player.prevPosZ = origPrevPosZ + offsetZ;

            target.bindFramebuffer(true);
            super.updateCameraAndRender(partialTicks, finishTimeNano);
        } finally {
            player.posX = origPosX;
            player.posZ = origPosZ;
            player.prevPosX = origPrevPosX;
            player.prevPosZ = origPrevPosZ;
        }
    }

    // ------------------------------------------------------------------
    // Framebuffer management
    // ------------------------------------------------------------------

    private void ensureFramebuffers() {
        int w = mc.displayWidth;
        int h = mc.displayHeight;

        if (w <= 0 || h <= 0) {
            return; // window minimized / not ready yet
        }

        if (leftFB == null || rightFB == null || w != fbWidth || h != fbHeight) {
            if (leftFB != null) leftFB.deleteFramebuffer();
            if (rightFB != null) rightFB.deleteFramebuffer();

            leftFB = new Framebuffer(w, h, true);
            rightFB = new Framebuffer(w, h, true);
            leftFB.setFramebufferFilter(GL11.GL_LINEAR);
            rightFB.setFramebufferFilter(GL11.GL_LINEAR);

            fbWidth = w;
            fbHeight = h;
        }
    }

    // ------------------------------------------------------------------
    // Final composite: LEFT | RIGHT onto the real screen, each optionally
    // passed through the barrel-distortion shader.
    // ------------------------------------------------------------------

    private void compositeToScreen() {
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        int halfWidth = width / 2;

        // Make sure we're drawing to the real, on-screen framebuffer -
        // whichever one Minecraft's own game loop bound before calling us.
        mc.getFramebuffer().bindFramebuffer(true);

        GlStateManager.viewport(0, 0, width, height);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        boolean useShader = ConfigHandler.fisheyeEnabled && fisheyeShader != null && fisheyeShader.isValid();

        if (useShader) {
            fisheyeShader.use();
            fisheyeShader.setUniform1i("u_texture", 0);
            fisheyeShader.setUniform1f("u_strength", ConfigHandler.fisheyeStrength);
        }

        drawEyeQuad(leftFB, 0, 0, halfWidth, height);
        drawEyeQuad(rightFB, halfWidth, 0, width - halfWidth, height);

        if (useShader) {
            ShaderProgram.unuse();
        }

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();

        drawOverlay(width, height);

        // Restore a plain 3D-friendly state for whatever renders next
        // (Minecraft's own post-frame bookkeeping, other mods, etc).
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
    }

    private void drawEyeQuad(Framebuffer source, int x, int y, int w, int h) {
        GlStateManager.bindTexture(source.framebufferTexture);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0F, 1F); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1F, 1F); GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1F, 0F); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0F, 0F); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private void drawOverlay(int width, int height) {
        if (overlayTicksRemaining <= 0) {
            return;
        }
        overlayTicksRemaining--;

        FontRenderer font = mc.fontRendererObj;
        String text = overlayText;
        int textWidth = font.getStringWidth(text);

        // Draw the label once, centered over the LEFT half, so it's
        // legible without doubling it across both eyes.
        int halfWidth = width / 2;
        int x = (halfWidth - textWidth) / 2;
        int y = 20;

        GlStateManager.enableAlpha();
        font.drawStringWithShadow(text, x, y, 0xFFFFFF);
        font.drawStringWithShadow(text, x + halfWidth, y, 0xFFFFFF);
    }

    // ------------------------------------------------------------------
    // Suppress the HUD's own second draw during the right-eye pass so the
    // hotbar/crosshair/etc aren't rendered with an unwanted parallax
    // offset - drawn once per eye is fine to keep, but if you'd rather
    // suppress it entirely on the right eye, uncomment the cancellation
    // below.
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        // if (secondaryEyePass) {
        //     event.setCanceled(true);
        // }
    }
}
