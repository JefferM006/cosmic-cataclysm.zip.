package com.cosmiccataclysm.client;

import com.cosmiccataclysm.CosmicCataclysm;
import com.cosmiccataclysm.CosmicMath;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Dibuja TODO el espectáculo del cielo (todo es geometría propia, sin shaders custom):
 *   1. domo de espacio estrellado que cubre el cielo
 *   2. grieta giratoria sobre el objetivo
 *   3. sol + planetas orbitando (con sombreado) y el anillo de Saturno
 *   4. explosiones: ondas de choque, fogonazos y fragmentos que salen volando
 */
public final class CosmicRenderer {
    private CosmicRenderer() {}

    private static Identifier tex(String name) {
        return Identifier.of(CosmicCataclysm.MOD_ID, "textures/cosmic/" + name + ".png");
    }

    private static final Identifier SPACE = tex("space");
    private static final Identifier RIFT = tex("rift");
    private static final Identifier SUN = tex("sun");
    private static final Identifier GLOW = tex("glow");
    private static final Identifier WHITE = tex("white");
    private static final Identifier ROCK = tex("rock");
    private static final Identifier RING = tex("ring");
    private static final Identifier[] PLANET_TEX = {
            tex("planet_0"), tex("planet_1"), tex("planet_2"), tex("planet_3"), tex("planet_4"), tex("planet_5")
    };

    private static final int FRAGS_PLANET = 36;
    private static final int FRAGS_SUN = 90;
    private static final float FRAG_LIFE = 95f;

    // ---------------------------------------------------------------------------------------------------
    public static void render(WorldRenderContext ctx) {
        if (ClientState.ACTIVE.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        float pt = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        Matrix4f m = ms.peek().getPositionMatrix();
        Vector3f right = new Vector3f(1, 0, 0).rotate(cam.getRotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(cam.getRotation());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        for (ClientState.Active a : ClientState.ACTIVE) {
            drawEvent(a, m, cp, pt, right, up);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ---------------------------------------------------------------------------------------------------
    private static void drawEvent(ClientState.Active a, Matrix4f m, Vec3d cp, float pt, Vector3f right, Vector3f up) {
        float age = a.age + pt;
        Vec3d sky = a.sky;
        float sx = (float) (sky.x - cp.x), sy = (float) (sky.y - cp.y), sz = (float) (sky.z - cp.z);

        double dxz = Math.hypot(cp.x - a.target.x, cp.z - a.target.z);
        float prox = (float) CosmicMath.clamp01(1 - (dxz - 250) / 350);
        float open = (float) CosmicMath.smooth(age / CosmicMath.RIFT_END);
        float close = 1f - (float) CosmicMath.smooth((age - CosmicMath.CLOSE_START) / (CosmicMath.TOTAL - CosmicMath.CLOSE_START - 20f));
        float vis = open * close;
        if (vis <= 0.001f) return;

        Vector3f sunRel = new Vector3f(sx, sy, sz);

        // =============================================================== 1) DOMO DE ESPACIO
        if (vis * prox > 0.01f) {
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, SPACE);
            BufferBuilder bb = begin();
            Gfx.sphere(bb, m, 0, 0, 0, 300f, 18, 36, age * 0.0006f, 1f, null, 0f, 1f, 1f, 1f, 0.93f * vis * prox);
            end(bb);
        }

        // =============================================================== 2) GRIETA
        {
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, RIFT);
            BufferBuilder bb = begin();
            Gfx.spinningDisc(bb, m, sx, sy - 18f, sz, 210f * vis, age * 0.012f, 1f, 1f, 1f, 0.95f * vis);
            end(bb);
        }

        // =============================================================== 3) CUERPOS SÓLIDOS (escriben profundidad)
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();

        // --- sol
        float sunBreak = CosmicMath.SUN_BREAK;
        float sunScale = (float) CosmicMath.smooth((age - CosmicMath.SYSTEM_IN) / 60.0);
        if (age < sunBreak && sunScale > 0.01f) {
            float swell = (float) CosmicMath.smooth((age - (sunBreak - 30)) / 30.0);
            float pulse = 1f + 0.015f * (float) Math.sin(age * 0.35f) + 0.07f * swell * (float) Math.sin(age * 2.6f);
            float rad = (float) CosmicMath.SUN_RADIUS * sunScale * pulse;
            RenderSystem.setShaderTexture(0, SUN);
            BufferBuilder bb = begin();
            Gfx.sphere(bb, m, sx, sy, sz, rad, 24, 40, age * 0.0015f, 1f, null, 0f, 1f, 1f, 1f, 1f);
            end(bb);
        }

        // --- planetas
        for (int i = 0; i < CosmicMath.PLANETS.length; i++) {
            CosmicMath.Body body = CosmicMath.PLANETS[i];
            int tb = CosmicMath.breakTick(i);
            float sc = (float) CosmicMath.smooth((age - CosmicMath.SYSTEM_IN - i * 8) / 50.0);
            if (age >= tb || sc <= 0.01f) continue;

            Vec3d wp = CosmicMath.planetPos(i, age, sky);
            float px = (float) (wp.x - cp.x), py = (float) (wp.y - cp.y), pz = (float) (wp.z - cp.z);

            // luz: desde el planeta hacia el sol
            Vector3f light = new Vector3f(sx - px, sy - py, sz - pz).normalize();

            // aviso antes de explotar: tiembla y se pone al rojo vivo
            float warn = (float) CosmicMath.smooth((age - (tb - 20)) / 20.0);
            float cr = 1f, cg = 1f - 0.55f * warn, cb = 1f - 0.85f * warn;
            if (warn > 0f) {
                float j = (float) body.radius() * 0.07f * warn;
                px += (float) Math.sin(age * 9.1f + i) * j;
                py += (float) Math.sin(age * 11.3f + i * 2) * j;
                pz += (float) Math.sin(age * 7.7f + i * 3) * j;
            }

            float rad = (float) body.radius() * sc;
            RenderSystem.setShaderTexture(0, PLANET_TEX[i]);
            BufferBuilder bb = begin();
            Gfx.sphere(bb, m, px, py, pz, rad, 22, 36, age * 0.004f + i * 0.3f, 1f, light, 0.14f, cr, cg, cb, 1f);
            end(bb);
        }

        // --- fragmentos rocosos (salen despedidos al explotar cada cuerpo)
        for (int i = 0; i <= CosmicMath.SUN_INDEX; i++) {
            float dt = age - CosmicMath.breakTick(i);
            if (dt <= 0 || dt >= FRAG_LIFE) continue;
            CosmicMath.Body planet = i < CosmicMath.PLANETS.length ? CosmicMath.PLANETS[i] : null;
            float cr = planet != null ? planet.r() : 1f;
            float cg = planet != null ? planet.g() : 0.75f;
            float cb = planet != null ? planet.b() : 0.4f;

            Vec3d base = CosmicMath.bodyPos(i, CosmicMath.breakTick(i), sky);
            int n = i == CosmicMath.SUN_INDEX ? FRAGS_SUN : FRAGS_PLANET;

            RenderSystem.setShaderTexture(0, ROCK);
            BufferBuilder bb = begin();
            for (int k = 0; k < n; k++) {
                float[] f = fragment(a.seed, i, k, dt, base, cp);
                if (f[3] <= 0.01f) continue;
                Gfx.sphere(bb, m, f[0], f[1], f[2], f[3], 5, 8, k * 0.1f, 1f, null, 0f, cr, cg, cb, 1f);
            }
            end(bb);
        }

        // =============================================================== 3b) ANILLO DE SATURNO (translúcido)
        {
            int si = CosmicMath.PLANETS.length - 1;
            CosmicMath.Body saturn = CosmicMath.PLANETS[si];
            float sc = (float) CosmicMath.smooth((age - CosmicMath.SYSTEM_IN - si * 8) / 50.0);
            if (saturn.ring() && age < CosmicMath.breakTick(si) && sc > 0.01f) {
                Vec3d wp = CosmicMath.planetPos(si, age, sky);
                float px = (float) (wp.x - cp.x), py = (float) (wp.y - cp.y), pz = (float) (wp.z - cp.z);
                float rad = (float) saturn.radius() * sc;
                RenderSystem.depthMask(false);
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderTexture(0, RING);
                BufferBuilder bb = begin();
                Gfx.ring(bb, m, px, py, pz, rad * 1.45f, rad * 2.5f, 0.45f, 0.18f, 1f, 1f, 1f, 1f);
                end(bb);
            }
        }

        // =============================================================== 4) BRILLOS ADITIVOS
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShaderTexture(0, GLOW);
        BufferBuilder glow = begin();

        // corona del sol
        if (age < sunBreak && sunScale > 0.01f) {
            float swell = (float) CosmicMath.smooth((age - (sunBreak - 40)) / 40.0);
            float r0 = (float) CosmicMath.SUN_RADIUS * sunScale;
            Gfx.billboard(glow, m, sx, sy, sz, r0 * (3.2f + 2.0f * swell), right, up, 1f, 0.65f, 0.15f, 0.85f * sunScale);
            Gfx.billboard(glow, m, sx, sy, sz, r0 * (6.0f + 4.0f * swell), right, up, 1f, 0.45f, 0.10f, 0.35f * sunScale);
        }

        // fogonazos de explosión + brillo de fragmentos
        for (int i = 0; i <= CosmicMath.SUN_INDEX; i++) {
            int tb = CosmicMath.breakTick(i);
            float dt = age - tb;
            if (dt <= 0 || dt >= FRAG_LIFE) continue;
            boolean sun = i == CosmicMath.SUN_INDEX;
            double radius = CosmicMath.bodyRadius(i);
            Vec3d base = CosmicMath.bodyPos(i, tb, sky);
            float bx = (float) (base.x - cp.x), by = (float) (base.y - cp.y), bz = (float) (base.z - cp.z);

            float flashLife = sun ? 40f : 18f;
            if (dt < flashLife) {
                float k = 1f - dt / flashLife;
                float size = (float) radius * ((sun ? 3f : 2.5f) + dt * (sun ? 0.4f : 0.5f));
                Gfx.billboard(glow, m, bx, by, bz, size, right, up, 1f, 0.85f, 0.6f, 0.9f * k);
            }

            int n = sun ? FRAGS_SUN : FRAGS_PLANET;
            for (int k = 0; k < n; k++) {
                float[] f = fragment(a.seed, i, k, dt, base, cp);
                if (f[3] <= 0.01f) continue;
                float fade = 1f - dt / FRAG_LIFE;
                Gfx.billboard(glow, m, f[0], f[1], f[2], f[3] * 5f, right, up, 1f, 0.5f, 0.15f, 0.65f * fade);
            }
        }
        end(glow);

        // ondas de choque (esferas aditivas que se expanden)
        RenderSystem.setShaderTexture(0, WHITE);
        for (int i = 0; i <= CosmicMath.SUN_INDEX; i++) {
            int tb = CosmicMath.breakTick(i);
            float dt = age - tb;
            boolean sun = i == CosmicMath.SUN_INDEX;
            float life = sun ? 50f : 28f;
            if (dt <= 0 || dt >= life) continue;
            Vec3d base = CosmicMath.bodyPos(i, tb, sky);
            float bx = (float) (base.x - cp.x), by = (float) (base.y - cp.y), bz = (float) (base.z - cp.z);
            float k = 1f - dt / life;
            float rad = (float) CosmicMath.bodyRadius(i) + dt * (sun ? 5.5f : 3.5f);
            float alpha = k * k * (sun ? 0.7f : 0.55f);
            BufferBuilder bb = begin();
            Gfx.sphere(bb, m, bx, by, bz, rad, 14, 24, 0f, 1f, null, 0f, 1f, 0.8f, 0.5f, alpha);
            end(bb);
        }

        RenderSystem.defaultBlendFunc();
    }

    /**
     * Fragmento k del cuerpo i, dt ticks después de la explosión. Determinista (usa la semilla del servidor).
     * Devuelve {x, y, z, tamaño} relativos a la cámara.
     */
    private static float[] fragment(long seed, int body, int k, float dt, Vec3d base, Vec3d cp) {
        boolean sun = body == CosmicMath.SUN_INDEX;
        double radius = CosmicMath.bodyRadius(body);

        double u = CosmicMath.hash(seed, body, k, 0) * 2 - 1;
        double th = CosmicMath.hash(seed, body, k, 1) * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        double dx = s * Math.cos(th), dy = u, dz = s * Math.sin(th);

        double speedMult = sun ? 2.0 : Math.max(0.6, radius / 7.0);
        double speed = (0.3 + CosmicMath.hash(seed, body, k, 2) * 1.0) * speedMult;
        double start = radius * 0.6;

        double x = base.x + dx * (start + speed * dt);
        double y = base.y + dy * (start + speed * dt) - 0.0035 * dt * dt;
        double z = base.z + dz * (start + speed * dt);

        double size = radius * (0.05 + CosmicMath.hash(seed, body, k, 3) * 0.14);
        size *= 1.0 - CosmicMath.smooth((dt - 50.0) / 45.0);

        return new float[]{(float) (x - cp.x), (float) (y - cp.y), (float) (z - cp.z), (float) size};
    }

    // ---------------------------------------------------------------------------------------------------
    private static BufferBuilder begin() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
    }

    private static void end(BufferBuilder bb) {
        BuiltBuffer built = bb.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }
}
