package holefinder;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Draws filled boxes through walls. Based on the Fabric docs custom-pipeline example for 1.21.11. */
public class HoleRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("holefinder", "pipeline/hole_box"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

    public static void render(WorldRenderContext context) {
        Config c = Config.I;
        Minecraft mc = Minecraft.getInstance();
        if (!c.enabled || mc.player == null) return;

        List<HoleScanner.Hole> holes = HoleScanner.nearest(mc.player.getX(), mc.player.getY(), mc.player.getZ(), c);
        if (holes.isEmpty()) return;

        PoseStack matrices = context.matrices();
        Vec3 cam = context.worldState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        float r = c.r / 255f, g = c.g / 255f, b = c.b / 255f, a = c.alpha / 255f;
        float e = 0.002f;
        Matrix4fc mat = matrices.last().pose();
        for (HoleScanner.Hole h : holes) {
            float x0 = Math.min(h.a().getX(), h.b().getX()) - e;
            float y0 = Math.min(h.a().getY(), h.b().getY()) - e;
            float z0 = Math.min(h.a().getZ(), h.b().getZ()) - e;
            float x1 = Math.max(h.a().getX(), h.b().getX()) + 1 + e;
            float y1 = Math.max(h.a().getY(), h.b().getY()) + 1 + e;
            float z1 = Math.max(h.a().getZ(), h.b().getZ()) + 1 + e;
            // filled (breakable) pockets get channel-rotated colour so you can tell them apart
            if (h.filled()) filledBox(mat, buffer, x0, y0, z0, x1, y1, z1, g, b, r, a);
            else filledBox(mat, buffer, x0, y0, z0, x1, y1, z1, r, g, b, a);
        }
        matrices.popPose();

        drawBuffer(mc, buffer);
    }

    private static void filledBox(Matrix4fc m, BufferBuilder v, float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
        v.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a);

        v.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a);
        v.addVertex(m, minX, minY, minZ).setColor(r, g, b, a);
        v.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a);

        v.addVertex(m, minX, minY, minZ).setColor(r, g, b, a);
        v.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a);

        v.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a);

        v.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a);
        v.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a);

        v.addVertex(m, minX, minY, minZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a);
        v.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a);
        v.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a);
    }

    private static void drawBuffer(Minecraft client, BufferBuilder buffer) {
        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState ds = built.drawState();
        VertexFormat format = ds.format();

        int size = ds.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "holefinder vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        GpuBuffer vertices = vertexBuffer.currentBuffer();

        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (PIPELINE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
            indexType = built.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer sib = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
            indices = sib.getBuffer(ds.indexCount());
            indexType = sib.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "holefinder render",
                client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, ds.indexCount(), 1);
        }

        built.close();
        vertexBuffer.rotate();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
