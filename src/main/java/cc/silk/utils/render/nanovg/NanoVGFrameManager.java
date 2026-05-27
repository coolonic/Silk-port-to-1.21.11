package cc.silk.utils.render.nanovg;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Manages NanoVG frame lifecycle for MC 1.21.11.
 *
 * MC 1.21.11 dropped beginWrite() and switched to a deferred GPU abstraction:
 *   - Screen.render() runs with GL FBO 0 (window surface) bound — not MC's texture
 *   - GuiRenderer.render() creates a RenderPass (LOAD mode) targeting MC's color texture AFTER Screen.render()
 *   - blitToScreen() / presentTexture() copies MC's texture to the window
 *
 * Problem 1 – wrong render target:
 *   NanoVG was rendering to FBO 0, which MC never presents. blitToScreen() copies
 *   MC's own texture (without NanoVG content) on top, making the GUI invisible.
 *
 * Problem 2 – missing stencil buffer:
 *   WindowFramebuffer uses DEPTH32 (no stencil). NanoVG's fill algorithm writes
 *   path winding numbers to the stencil buffer; without one all fills are invisible.
 *
 * Fix:
 *   We create a dedicated GL FBO that attaches:
 *     • MC's main color GlTexture as the color target (so NanoVG draws straight into it)
 *     • Our own DEPTH24_STENCIL8 renderbuffer (so stencil fills work)
 *   GuiRenderer's LOAD-mode RenderPass then preserves our pixels, and blitToScreen
 *   presents them to the user.
 */
public class NanoVGFrameManager {
    private static boolean inFrame = false;

    // Saved GL state (restored in endFrame)
    private static int savedVAO = 0;
    private static int savedArrayBuffer = 0;
    private static int savedElementBuffer = 0;
    private static int savedProgram = 0;
    private static int savedTexture = 0;
    private static int savedFBO = 0;
    // MC 1.21.11 binds GL 3.3 sampler objects to texture units. These override the
    // texture's own filtering parameters (e.g. force GL_LINEAR_MIPMAP_LINEAR).
    // NanoVG's glyph atlas has no mipmaps, so a mipmap-filter sampler makes text
    // invisible. We save the sampler on unit 0 and unbind it while NanoVG renders.
    private static int savedSampler0 = 0;

    // Our dedicated FBO – shared color texture with MC + private DEPTH24_STENCIL8 RBO
    private static int nanoVGFBO = 0;
    private static int nanoVGStencilRBO = 0;
    private static int lastColorTexGlId = -1;   // detect MC texture recreation (window resize)
    private static int lastFBOWidth = 0;
    private static int lastFBOHeight = 0;

    public static void beginFrame() {
        RenderSystem.assertOnRenderThread();

        if (!NanoVGContext.isInitialized() || !NanoVGContext.isValid()) {
            NanoVGContext.init();
            NanoVGFontManager.loadFonts();
        }

        if (inFrame) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int fbWidth  = mc.getWindow().getFramebufferWidth();
        int fbHeight = mc.getWindow().getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;

        try {
            // ── Step 1: ensure our FBO targets the current MC color texture ──────────
            int mcColorGlId = getMcColorTexGlId(mc);
            if (mcColorGlId != -1
                    && (nanoVGFBO == 0
                    || mcColorGlId != lastColorTexGlId
                    || fbWidth     != lastFBOWidth
                    || fbHeight    != lastFBOHeight)) {
                createNanoVGFBO(mcColorGlId, fbWidth, fbHeight);
                lastColorTexGlId = mcColorGlId;
                lastFBOWidth     = fbWidth;
                lastFBOHeight    = fbHeight;
            }

            // ── Step 2: save current GL state ────────────────────────────────────────
            savedVAO           = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            savedArrayBuffer   = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            savedElementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            savedProgram       = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            savedTexture       = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            savedFBO           = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            // MC 1.21.11 binds GL sampler objects with mipmap filters to texture units.
            // NanoVG's glyph atlas has no mipmaps — sampling it through a mipmap-filter
            // sampler returns black, making ALL text invisible. Unbind any sampler on
            // unit 0 so NanoVG's own texture parameters (GL_LINEAR / GL_NEAREST) apply.
            savedSampler0 = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, 0);
            GL33.glBindSampler(0, 0);

            // ── Step 3: bind our NanoVG FBO ──────────────────────────────────────────
            // NanoVG now writes into MC's color texture directly.
            // GuiRenderer's subsequent LOAD-mode RenderPass will preserve those pixels.
            if (nanoVGFBO != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, nanoVGFBO);
                // Ensure viewport covers the full framebuffer
                GL11.glViewport(0, 0, fbWidth, fbHeight);
            }

            // ── Step 4: set up GL state for NanoVG ──────────────────────────────────
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // Clear stencil so NanoVG's winding-number fill algorithm starts clean.
            // Our DEPTH24_STENCIL8 RBO provides the stencil buffer MC's FBO lacks.
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

            // ── Step 5: begin NanoVG frame ───────────────────────────────────────────
            NanoVGContext.assertValid();
            nvgBeginFrame(NanoVGContext.getHandle(), fbWidth, fbHeight, 1f);

            // Convert MC's GUI coordinate space → framebuffer pixels
            int scaledW = mc.getWindow().getScaledWidth();
            int scaledH = mc.getWindow().getScaledHeight();
            nvgScale(NanoVGContext.getHandle(),
                    (float) fbWidth  / (float) scaledW,
                    (float) fbHeight / (float) scaledH);

            inFrame = true;
        } catch (Exception e) {
            inFrame = false;
            throw e;
        }
    }

    public static void endFrame() {
        if (!inFrame) return;

        RenderSystem.assertOnRenderThread();

        try {
            NanoVGContext.assertValid();
            nvgEndFrame(NanoVGContext.getHandle());
        } catch (Exception e) {
            inFrame = false;
            throw e;
        }

        inFrame = false;

        // ── Restore FBO ──────────────────────────────────────────────────────────────
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFBO);

        // ── Restore GL state ─────────────────────────────────────────────────────────
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0);   // prevent accidental stencil writes by MC passes

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL30.glBindVertexArray(savedVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedArrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, savedElementBuffer);
        GL20.glUseProgram(savedProgram);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        // Restore MC's sampler object on unit 0 so subsequent MC rendering is unaffected.
        GL33.glBindSampler(0, savedSampler0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────

    /**
     * Returns the raw GL texture ID of MC's main framebuffer color attachment, or -1.
     * MC 1.21.11 uses GlTexture (extends GpuTexture) with a public getGlId() method.
     */
    private static int getMcColorTexGlId(MinecraftClient mc) {
        try {
            if (mc.getFramebuffer() == null) return -1;
            var colorAttachment = mc.getFramebuffer().getColorAttachment();
            if (colorAttachment instanceof GlTexture glTex) {
                return glTex.getGlId();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * (Re)creates our NanoVG FBO.
     *
     * Color attachment  → MC's existing color GlTexture (same GL texture ID).
     *   NanoVG draws into it; GuiRenderer's LOAD-mode RenderPass preserves it;
     *   blitToScreen presents it to the window.
     *
     * Depth+Stencil → our own DEPTH24_STENCIL8 renderbuffer.
     *   Gives NanoVG the stencil it needs for fills/strokes without touching
     *   MC's DEPTH32-only framebuffer.
     *
     * This method saves and restores the caller's FBO binding.
     */
    private static void createNanoVGFBO(int colorTexGlId, int width, int height) {
        int prevFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        // Delete old resources
        if (nanoVGFBO != 0) {
            GL30.glDeleteFramebuffers(nanoVGFBO);
            nanoVGFBO = 0;
        }
        if (nanoVGStencilRBO != 0) {
            GL30.glDeleteRenderbuffers(nanoVGStencilRBO);
            nanoVGStencilRBO = 0;
        }

        // Create FBO
        nanoVGFBO = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, nanoVGFBO);

        // Attach MC's color texture as the color target
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorTexGlId, 0);

        // Create DEPTH24_STENCIL8 renderbuffer and attach it
        nanoVGStencilRBO = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, nanoVGStencilRBO);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, nanoVGStencilRBO);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            // FBO incomplete – clean up and fall back to rendering without our FBO
            GL30.glDeleteFramebuffers(nanoVGFBO);
            GL30.glDeleteRenderbuffers(nanoVGStencilRBO);
            nanoVGFBO = 0;
            nanoVGStencilRBO = 0;
        }

        // Restore caller's FBO binding
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO);
    }

    public static boolean isInFrame() {
        return inFrame;
    }

    public static void resetInFrame() {
        inFrame = false;
    }
}
