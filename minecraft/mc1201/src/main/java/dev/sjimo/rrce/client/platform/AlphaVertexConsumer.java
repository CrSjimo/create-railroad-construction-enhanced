package dev.sjimo.rrce.client.platform;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class AlphaVertexConsumer implements VertexConsumer {
    private final VertexConsumer target;
    private final float opacity;
    public AlphaVertexConsumer(VertexConsumer target, float opacity) { this.target = target; this.opacity = opacity; }
    public VertexConsumer vertex(double x, double y, double z) { target.vertex(x, y, z); return this; }
    public VertexConsumer color(int r, int g, int b, int a) { target.color(r, g, b, Math.round(a * opacity)); return this; }
    public VertexConsumer uv(float u, float v) { target.uv(u, v); return this; }
    public VertexConsumer overlayCoords(int u, int v) { target.overlayCoords(u, v); return this; }
    public VertexConsumer uv2(int u, int v) { target.uv2(u, v); return this; }
    public VertexConsumer normal(float x, float y, float z) { target.normal(x, y, z); return this; }
    public void endVertex() { target.endVertex(); }
    public void defaultColor(int r, int g, int b, int a) { target.defaultColor(r, g, b, Math.round(a * opacity)); }
    public void unsetDefaultColor() { target.unsetDefaultColor(); }
}
