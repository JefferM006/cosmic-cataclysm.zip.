package com.cosmiccataclysm.client;

import com.cosmiccataclysm.CosmicCataclysm;
import com.cosmiccataclysm.MeteorEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Meteoro: esfera rocosa con grietas de lava, siempre a máxima luz, rodeada de un halo naranja. */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final Identifier ROCK = Identifier.of(CosmicCataclysm.MOD_ID, "textures/cosmic/rock.png");
    private static final Identifier WHITE = Identifier.of(CosmicCataclysm.MOD_ID, "textures/cosmic/white.png");

    public MeteorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(MeteorEntity entity) {
        return ROCK;
    }

    @Override
    public boolean shouldRender(MeteorEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // la roca es mucho más grande que su caja de colisión
    }

    @Override
    public void render(MeteorEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float size = entity.getSize();
        float t = entity.age + tickDelta;
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 5f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 3f));

        // roca
        VertexConsumer rock = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ROCK));
        Gfx.entitySphere(rock, matrices.peek(), size, 12, 18, 0f, 255, 255, 255, 255, full);

        // halo de fuego (translúcido y brillante)
        VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Gfx.entitySphere(glow, matrices.peek(), size * 1.22f, 10, 14, 0f, 255, 110, 20, 105, full);
        Gfx.entitySphere(glow, matrices.peek(), size * 1.5f, 8, 12, 0f, 255, 70, 10, 45, full);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
