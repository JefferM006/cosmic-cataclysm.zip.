package com.cosmiccataclysm.client;

import com.cosmiccataclysm.CosmicMath;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Cataclismos activos vistos desde este cliente + funciones de temblor y destello. */
public final class ClientState {
    private ClientState() {}

    public static final class Active {
        public final Vec3d target;
        public final Vec3d sky;
        public final long seed;
        public int age = 0;

        public Active(Vec3d target, long seed) {
            this.target = target;
            this.sky = CosmicMath.skyCenter(target);
            this.seed = seed;
        }
    }

    public static final List<Active> ACTIVE = new ArrayList<>();

    /** Intensidad del temblor de cámara (grados) en la posición dada. */
    public static float shake(Vec3d pos) {
        float total = 0;
        for (Active a : ACTIVE) {
            double dist = Math.hypot(pos.x - a.target.x, pos.z - a.target.z);
            float prox = (float) CosmicMath.clamp01(1 - dist / 220.0);
            if (prox <= 0) continue;
            float s = 0;
            // el cielo se abre: retumbar suave
            if (a.age > 30 && a.age < CosmicMath.RIFT_END + 40) {
                s += 0.15f * (float) CosmicMath.smooth((a.age - 30) / 60.0);
            }
            // explosiones
            for (int b = 0; b <= CosmicMath.SUN_INDEX; b++) {
                int dt = a.age - CosmicMath.breakTick(b);
                if (dt >= 0 && dt < 40) {
                    float peak = b == CosmicMath.SUN_INDEX ? 2.2f : 0.7f;
                    s += peak * (1f - dt / 40f);
                }
            }
            // lluvia de meteoros: retumbar continuo
            if (a.age > CosmicMath.BREAK_START + 40 && a.age < CosmicMath.SUN_BREAK + 250) {
                s += 0.35f;
            }
            total += s * prox;
        }
        return Math.min(total, 2.5f);
    }

    /** Destello blanco de pantalla (0..1). */
    public static float flash(float partial) {
        float total = 0;
        for (Active a : ACTIVE) {
            float age = a.age + partial;
            for (int b = 0; b <= CosmicMath.SUN_INDEX; b++) {
                float dt = age - CosmicMath.breakTick(b);
                if (dt >= 0 && dt < 16) {
                    float k = 1f - dt / 16f;
                    total += (float) Math.pow(k, 1.5) * (b == CosmicMath.SUN_INDEX ? 0.95f : 0.22f);
                }
            }
        }
        return Math.min(total, 1f);
    }
}
