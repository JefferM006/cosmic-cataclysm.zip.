package com.cosmiccataclysm.client;

import com.cosmiccataclysm.CataclysmPayload;
import com.cosmiccataclysm.CosmicCataclysm;
import com.cosmiccataclysm.CosmicMath;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class CosmicCataclysmClient implements ClientModInitializer {
    private static float lastShakeYaw = 0;
    private static float lastShakePitch = 0;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CosmicCataclysm.METEOR, MeteorRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(CataclysmPayload.ID, (payload, context) ->
                ClientState.ACTIVE.add(new ClientState.Active(new Vec3d(payload.x(), payload.y(), payload.z()), payload.seed())));

        // Dibuja el cielo abierto, el sol, los planetas y los fragmentos.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CosmicRenderer::render);

        // Avanza la animación, temblor de cámara.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (client.world == null || player == null) {
                ClientState.ACTIVE.clear();
                lastShakeYaw = lastShakePitch = 0;
                return;
            }
            if (client.isPaused()) return;

            ClientState.ACTIVE.removeIf(a -> a.age >= CosmicMath.TOTAL);
            for (ClientState.Active a : ClientState.ACTIVE) a.age++;

            // deshacer el temblor del tick anterior y aplicar el nuevo
            player.setYaw(player.getYaw() - lastShakeYaw);
            player.setPitch(player.getPitch() - lastShakePitch);
            lastShakeYaw = lastShakePitch = 0;

            float amp = ClientState.shake(player.getPos());
            if (amp > 0.01f) {
                lastShakeYaw = (player.getRandom().nextFloat() - 0.5f) * 2f * amp;
                lastShakePitch = (player.getRandom().nextFloat() - 0.5f) * 2f * amp;
                player.setYaw(player.getYaw() + lastShakeYaw);
                player.setPitch(MathHelper.clamp(player.getPitch() + lastShakePitch, -90f, 90f));
            }
        });

        // Destello blanco cuando explota un planeta o el sol.
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (ClientState.ACTIVE.isEmpty()) return;
            float f = ClientState.flash(tickCounter.getTickDelta(false));
            if (f <= 0.01f) return;
            int alpha = (int) (MathHelper.clamp(f, 0f, 1f) * 255);
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                    (alpha << 24) | 0xFFF2DC);
        });
    }
}
