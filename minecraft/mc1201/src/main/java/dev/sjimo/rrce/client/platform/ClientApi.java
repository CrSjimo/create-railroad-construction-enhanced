package dev.sjimo.rrce.client.platform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
public final class ClientApi {
    public static void vertex(PoseStack pose, VertexConsumer consumer, Vec3 point, float r, float g, float b, float a) {
        consumer.vertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
            .color(r, g, b, a).normal(0, 1, 0).endVertex();
    }
}
