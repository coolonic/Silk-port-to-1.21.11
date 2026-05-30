package cc.silk.utils.render.font.util;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;

/**
 * Vertex-buffer draw utilities.
 *
 * <p><b>1.21.11 migration status:</b>
 * <ul>
 *   <li>{@code net.minecraft.client.render.BufferRenderer} was <b>removed</b> as part of the
 *       Blaze3D rendering-pipeline overhaul that landed in 1.21.5.</li>
 *   <li>{@code net.minecraft.client.gl.VertexBuffer} (the VBO wrapper) was similarly removed.
 *       VBO operations now go through {@code com.mojang.blaze3d.buffers.GpuBuffer} via
 *       {@code RenderSystem.getDevice().createBuffer(...)}.</li>
 * </ul>
 *
 * <p><b>Batch 2 TODO:</b> Replace {@link #draw} with a proper
 * {@code RenderPipeline / RenderPass / GpuDevice} implementation, and rewrite
 * {@code uploadToVbo} in terms of {@code GpuBuffer}.
 */
public class BufferUtils {

    /**
     * Draws the contents of the buffer using the currently active shader pipeline.
     *
     * <p><b>⚠ Stub — 1.21.11:</b> {@code BufferRenderer.drawWithGlobalProgram()} was removed.
     * This method closes the built buffer (preventing a memory leak) but does <em>not</em>
     * actually draw anything.  Fix in Batch 2 by routing through
     * {@code RenderSystem.getDevice()} / {@code CommandEncoder} / {@code RenderPass}.
     *
     * @param builder The buffer whose contents should be drawn.
     */
    public static void draw(BufferBuilder builder) {
        // Close the BuiltBuffer to free native memory; actual draw is a Batch-2 task.
        try (BuiltBuffer built = builder.end()) {
            // TODO (Batch 2): upload built.getBuffer() to a GpuBuffer, obtain a RenderPass
            //   from the current frame's CommandEncoder, bind the pipeline & uniforms, then call
            //   renderPass.draw(built.getDrawParameters().vertexCount(), 0).
        }
    }
}
