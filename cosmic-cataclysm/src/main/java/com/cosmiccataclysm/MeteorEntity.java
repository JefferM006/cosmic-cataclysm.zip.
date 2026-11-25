package com.cosmiccataclysm;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Meteoro gigante. Se mueve en línea recta (igual en cliente y servidor) y al tocar el suelo
 * detona un cráter enorme. El tamaño (radio de la roca en bloques) viaja en el DataTracker.
 */
public class MeteorEntity extends Entity {
    private static final TrackedData<Float> SIZE =
            DataTracker.registerData(MeteorEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public MeteorEntity(EntityType<? extends MeteorEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    public float getSize() {
        return this.dataTracker.get(SIZE);
    }

    public void setSize(float size) {
        this.dataTracker.set(SIZE, size);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SIZE, 3.0f);
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRender(double distance) {
        return true; // visible desde muy lejos
    }

    @Override
    public void tick() {
        super.tick();

        Vec3d vel = this.getVelocity();
        Vec3d from = this.getPos();
        Vec3d to = from.add(vel);

        if (!this.getWorld().isClient) {
            // Probamos un poco más adelante para que explote cuando la SUPERFICIE de la roca toca el suelo.
            Vec3d probe = to.add(vel.normalize().multiply(getSize() * 0.7));
            BlockHitResult hit = this.getWorld().raycast(new RaycastContext(
                    from, probe, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
            if (hit.getType() != HitResult.Type.MISS) {
                impact(hit.getPos());
                return;
            }
            if (this.age > 900 || this.getY() < this.getWorld().getBottomY() - 32) {
                this.discard();
                return;
            }
        }

        this.setPosition(to);

        if (this.getWorld().isClient) {
            spawnTrail(vel);
        }
    }

    private void spawnTrail(Vec3d vel) {
        World w = this.getWorld();
        float s = getSize();
        Vec3d back = vel.lengthSquared() > 1.0E-6 ? vel.normalize().multiply(-1) : new Vec3d(0, 1, 0);
        int n = 3 + (int) (s * 2);
        for (int i = 0; i < n; i++) {
            double along = this.random.nextDouble() * s * 4.0;
            double ox = (this.random.nextDouble() - 0.5) * s * 1.6;
            double oy = (this.random.nextDouble() - 0.5) * s * 1.6;
            double oz = (this.random.nextDouble() - 0.5) * s * 1.6;
            double px = this.getX() + back.x * along + ox;
            double py = this.getY() + back.y * along + oy;
            double pz = this.getZ() + back.z * along + oz;
            w.addParticle(ParticleTypes.FLAME, true, px, py, pz, back.x * 0.2, back.y * 0.2, back.z * 0.2);
            if (i % 2 == 0) {
                w.addParticle(ParticleTypes.LARGE_SMOKE, true, px, py, pz, back.x * 0.1, back.y * 0.1, back.z * 0.1);
            }
        }
    }

    private void impact(Vec3d pos) {
        ServerWorld w = (ServerWorld) this.getWorld();
        CataclysmManager.meteorImpact(w, pos, getSize());
        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Size")) {
            setSize(nbt.getFloat("Size"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("Size", getSize());
    }
}
