package com.cosmiccataclysm;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Lado servidor: dirige la cronología, lanza los meteoros y talla los cráteres. */
public final class CataclysmManager {
    private CataclysmManager() {}

    /** Un meteoro programado para aparecer en un tick concreto. */
    private record Pending(int spawnAge, Vec3d pos, float size, boolean direct) {}

    private static final class Event {
        final ServerWorld world;
        final Vec3d target;
        final Vec3d sky;
        final long seed;
        int age = 0;
        final List<Pending> pending = new ArrayList<>();

        Event(ServerWorld world, Vec3d target, long seed) {
            this.world = world;
            this.target = target;
            this.sky = CosmicMath.skyCenter(target);
            this.seed = seed;
        }
    }

    private static final List<Event> EVENTS = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(CataclysmManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EVENTS.clear());
    }

    // ================================================================== inicio
    public static void start(ServerWorld world, Vec3d target) {
        long seed = world.random.nextLong();
        Event e = new Event(world, target, seed);
        schedule(e, new java.util.Random(seed));
        EVENTS.add(e);

        CataclysmPayload payload = new CataclysmPayload(target.x, target.y, target.z, seed);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(target) < 900.0 * 900.0) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Decide de antemano cuántos meteoros suelta cada cuerpo y cuándo. */
    private static void schedule(Event e, java.util.Random rnd) {
        for (int body = 0; body <= CosmicMath.SUN_INDEX; body++) {
            boolean sun = body == CosmicMath.SUN_INDEX;
            int tb = CosmicMath.breakTick(body);
            double radius = CosmicMath.bodyRadius(body);
            Vec3d center = CosmicMath.bodyPos(body, tb, e.sky);

            int count = sun ? 50 : 6 + (int) (radius * 0.7);
            count = Math.max(1, (int) Math.round(count * CosmicMath.METEOR_COUNT_MULT));
            int window = sun ? 110 : 70;

            for (int j = 0; j < count; j++) {
                int spawnAge = tb + 6 + rnd.nextInt(window);
                // punto aleatorio dentro de la esfera del cuerpo
                Vec3d off = randomInSphere(rnd).multiply(radius * 0.8);
                double base = sun ? 3.0 + rnd.nextDouble() * 4.5
                        : 1.8 + rnd.nextDouble() * (1.2 + radius * 0.18);
                float size = (float) (base * CosmicMath.METEOR_SIZE_MULT);
                boolean direct = sun && j < 6; // los primeros del sol caen casi encima del objetivo
                e.pending.add(new Pending(spawnAge, center.add(off), size, direct));
            }
        }
    }

    private static Vec3d randomInSphere(java.util.Random rnd) {
        while (true) {
            double x = rnd.nextDouble() * 2 - 1, y = rnd.nextDouble() * 2 - 1, z = rnd.nextDouble() * 2 - 1;
            if (x * x + y * y + z * z <= 1) return new Vec3d(x, y, z);
        }
    }

    // ================================================================== tick
    private static void tick(ServerWorld world) {
        if (EVENTS.isEmpty()) return;
        Iterator<Event> it = EVENTS.iterator();
        while (it.hasNext()) {
            Event e = it.next();
            if (e.world != world) continue;
            e.age++;

            // --- sonidos de la fase "el cielo se abre"
            if (e.age == 5) sound(world, e.target, "entity.wither.spawn", 8f, 0.5f);
            if (e.age == 25 || e.age == 60 || e.age == 95) sound(world, e.target, "entity.lightning_bolt.thunder", 10f, 0.5f);
            if (e.age == 40) sound(world, e.target, "entity.ender_dragon.growl", 8f, 0.4f);

            // --- explosiones de planetas y sol
            for (int body = 0; body <= CosmicMath.SUN_INDEX; body++) {
                if (e.age == CosmicMath.breakTick(body)) {
                    Vec3d pos = CosmicMath.bodyPos(body, e.age, e.sky);
                    boolean sun = body == CosmicMath.SUN_INDEX;
                    sound(world, pos, "entity.generic.explode", sun ? 20f : 12f, sun ? 0.4f : 0.6f);
                    if (sun) sound(world, pos, "entity.lightning_bolt.thunder", 20f, 0.3f);
                }
            }

            // --- meteoros que toca soltar ahora
            for (Iterator<Pending> pi = e.pending.iterator(); pi.hasNext(); ) {
                Pending p = pi.next();
                if (p.spawnAge() <= e.age) {
                    spawnMeteor(e, p);
                    pi.remove();
                }
            }

            if (e.age >= CosmicMath.TOTAL) {
                it.remove();
            }
        }
    }

    private static void spawnMeteor(Event e, Pending p) {
        ServerWorld w = e.world;
        Random rnd = w.random;

        double ang = rnd.nextDouble() * Math.PI * 2;
        double rad = Math.sqrt(rnd.nextDouble()) * CosmicMath.TARGET_RADIUS * (p.direct() ? 0.2 : 1.0);
        double tx = e.target.x + Math.cos(ang) * rad;
        double tz = e.target.z + Math.sin(ang) * rad;

        double ty = e.target.y;
        if (w.getChunkManager().isChunkLoaded(((int) Math.floor(tx)) >> 4, ((int) Math.floor(tz)) >> 4)) {
            ty = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(tx), (int) Math.floor(tz));
        }

        Vec3d dir = new Vec3d(tx, ty, tz).subtract(p.pos()).normalize();
        double speed = 1.5 + rnd.nextDouble() * 0.9;

        MeteorEntity m = new MeteorEntity(CosmicCataclysm.METEOR, w);
        m.setPosition(p.pos());
        m.setSize(p.size());
        m.setVelocity(dir.multiply(speed));
        w.spawnEntity(m);
    }

    // ================================================================== impacto
    /** Llamado por el meteoro al tocar el suelo. */
    public static void meteorImpact(ServerWorld w, Vec3d pos, float size) {
        double radius = Math.min(18.0, size * CosmicMath.CRATER_MULT);

        if (CosmicMath.DESTROY_BLOCKS) {
            if (CosmicMath.EJECTA) {
                launchDebris(w, pos, radius);
            }
            carveCrater(w, pos, radius);
        }

        // Explosión "de mentira": sonido, ondas y daño a entidades, pero sin romper bloques (los rompe el cráter).
        w.createExplosion(null, pos.x, pos.y, pos.z, 3.0f + size * 1.6f, false, World.ExplosionSourceType.NONE);

        double spread = Math.max(2.0, radius * 0.45);
        burst(w, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1, pos.z, 2 + (int) size, spread, 0.0);
        burst(w, ParticleTypes.FLAME, pos.x, pos.y + 1, pos.z, 120 + (int) (radius * 10), spread, 0.25);
        burst(w, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2, pos.z, 80 + (int) (radius * 6), spread, 0.12);
        burst(w, ParticleTypes.LAVA, pos.x, pos.y + 1, pos.z, 30 + (int) (radius * 3), spread, 0.0);
    }

    /** Lanza bloques de la superficie por los aires antes de abrir el cráter. */
    private static void launchDebris(ServerWorld w, Vec3d impact, double radius) {
        Random rnd = w.random;
        int n = Math.min(8, 2 + (int) (radius / 3.0));
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int k = 0; k < n * 3 && n > 0; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double rad = Math.sqrt(rnd.nextDouble()) * radius * 0.8;
            int x = (int) Math.floor(impact.x + Math.cos(ang) * rad);
            int z = (int) Math.floor(impact.z + Math.sin(ang) * rad);
            if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            p.set(x, y, z);
            BlockState st = w.getBlockState(p);
            if (st.isAir() || st.getHardness(w, p) < 0 || !st.getFluidState().isEmpty() || st.hasBlockEntity()) continue;

            FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(w, p.toImmutable(), st);
            fb.dropItem = false;
            fb.setDestroyedOnLanding();
            fb.setHurtEntities(2.0f, 40);
            double vx = Math.cos(ang) * (0.3 + rnd.nextDouble() * 0.8);
            double vz = Math.sin(ang) * (0.3 + rnd.nextDouble() * 0.8);
            fb.setVelocity(vx, 0.7 + rnd.nextDouble() * 1.0, vz);
            if (--n <= 0) break;
        }
    }

    /**
     * Talla un cráter: elipsoide hundido en el suelo (también limpia lo que haya encima: árboles, casas...),
     * con un borde chamuscado de roca volcánica, magma y algo de fuego.
     */
    public static void carveCrater(ServerWorld w, Vec3d impact, double R) {
        Random rnd = w.random;
        double cy = impact.y - R * 0.25;
        double ry = R * 0.8;
        int cx = (int) Math.floor(impact.x);
        int cz = (int) Math.floor(impact.z);
        int cyi = (int) Math.floor(cy);
        int r = (int) Math.ceil(R * 1.5);
        int minY = w.getBottomY();
        int maxY = w.getTopY();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable p = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double horiz = (dx * dx + dz * dz) / (R * R);
                if (horiz > 2.1) continue;
                int x = cx + dx, z = cz + dz;
                if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

                for (int dy = -r; dy <= r; dy++) {
                    int y = cyi + dy;
                    if (y < minY || y >= maxY) continue;
                    double d = horiz + (dy * dy) / (ry * ry);
                    if (d > 2.1) continue;

                    p.set(x, y, z);
                    BlockState st = w.getBlockState(p);
                    if (st.isAir() || st.getHardness(w, p) < 0) continue;

                    if (d <= 1.0) {
                        w.setBlockState(p, air, Block.NOTIFY_LISTENERS);
                    } else if (d <= 1.45 && dy <= ry * 0.5 && rnd.nextFloat() < 0.7f) {
                        float c = rnd.nextFloat();
                        BlockState scorched = c < 0.30f ? Blocks.MAGMA_BLOCK.getDefaultState()
                                : c < 0.65f ? Blocks.BLACKSTONE.getDefaultState()
                                : c < 0.85f ? Blocks.BASALT.getDefaultState()
                                : Blocks.COAL_BLOCK.getDefaultState();
                        w.setBlockState(p, scorched, Block.NOTIFY_LISTENERS);

                        // fuego sobre el borde
                        if (rnd.nextFloat() < 0.12f) {
                            BlockPos above = p.up();
                            if (w.getBlockState(above).isAir()) {
                                BlockState fire = AbstractFireBlock.getState(w, above);
                                if (fire.canPlaceAt(w, above)) {
                                    w.setBlockState(above, fire, Block.NOTIFY_LISTENERS);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ================================================================== utilidades
    private static <T extends ParticleEffect> void burst(ServerWorld w, T effect, double x, double y, double z,
                                                         int count, double spread, double speed) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(x, y, z) < 300.0 * 300.0) {
                w.spawnParticles(p, effect, true, x, y, z, count, spread, spread, spread, speed);
            }
        }
    }

    private static void sound(ServerWorld w, Vec3d pos, String id, float volume, float pitch) {
        SoundEvent se = Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
        if (se != null) {
            w.playSound(null, pos.x, pos.y, pos.z, se, SoundCategory.AMBIENT, volume, pitch);
        }
    }
}
