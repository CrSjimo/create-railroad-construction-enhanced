package dev.sjimo.rrce.client.platform;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class AlphaVertexConsumer implements VertexConsumer {
    private final VertexConsumer target;
    private final float opacity;
    public AlphaVertexConsumer(VertexConsumer target, float opacity) { this.target = target; this.opacity = opacity; }
    public VertexConsumer addVertex(float x, float y, float z) { target.addVertex(x, y, z); return this; }
    public VertexConsumer setColor(int r, int g, int b, int a) { target.setColor(r, g, b, Math.round(a * opacity)); return this; }
    public VertexConsumer setUv(float u, float v) { target.setUv(u, v); return this; }
    public VertexConsumer setUv1(int u, int v) { target.setUv1(u, v); return this; }
    public VertexConsumer setUv2(int u, int v) { target.setUv2(u, v); return this; }
    public VertexConsumer setNormal(float x, float y, float z) { target.setNormal(x, y, z); return this; }
}
