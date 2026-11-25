package com.cosmiccataclysm.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Utilidades para dibujar esferas, carteles (billboards) y anillos a mano. */
public final class Gfx {
    private Gfx() {}

    private static final float PI = (float) Math.PI;

    // =====================================================================================
    //  Formato POSITION_TEXTURE_COLOR (dibujo directo con RenderSystem)
    // =====================================================================================

    /**
     * Esfera con textura equirectangular.
     *
     * @param light dirección HACIA la luz (normalizada) o null para esfera sin sombreado
     * @param amb   luz ambiente mínima (0..1) cuando hay sombreado
     */
    public static void sphere(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz, float rad,
                              int lat, int lon, float uOff, float uScale,
                              Vector3f light, float amb,
                              float r, float g, float b, float a) {
        for (int i = 0; i < lat; i++) {
            float t0 = PI * i / lat;
            float t1 = PI * (i + 1) / lat;
            float v0 = (float) i / lat;
            float v1 = (float) (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = 2 * PI * j / lon;
                float p1 = 2 * PI * (j + 1) / lon;
                float u0 = (float) j / lon * uScale + uOff;
                float u1 = (float) (j + 1) / lon * uScale + uOff;
                vert(bb, m, cx, cy, cz, rad, t0, p0, u0, v0, light, amb, r, g, b, a);
                vert(bb, m, cx, cy, cz, rad, t1, p0, u0, v1, light, amb, r, g, b, a);
                vert(bb, m, cx, cy, cz, rad, t1, p1, u1, v1, light, amb, r, g, b, a);
                vert(bb, m, cx, cy, cz, rad, t0, p1, u1, v0, light, amb, r, g, b, a);
            }
        }
    }

    private static void vert(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz, float rad,
                             float theta, float phi, float u, float v,
                             Vector3f light, float amb, float r, float g, float b, float a) {
        float st = (float) Math.sin(theta);
        float sx = st * (float) Math.cos(phi);
        float sy = (float) Math.cos(theta);
        float sz = st * (float) Math.sin(phi);
        float k = 1f;
        if (light != null) {
            float dot = sx * light.x + sy * light.y + sz * light.z;
            k = amb + (1f - amb) * Math.max(0f, Math.min(1f, dot * 1.15f + 0.08f));
        }
        bb.vertex(m, cx + sx * rad, cy + sy * rad, cz + sz * rad).texture(u, v).color(r * k, g * k, b * k, a);
    }

    /** Cartel que siempre mira a la cámara. right/up son los ejes de la cámara en el mundo. */
    public static void billboard(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz, float size,
                                 Vector3f right, Vector3f up, float r, float g, float b, float a) {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        bb.vertex(m, cx - rx - ux, cy - ry - uy, cz - rz - uz).texture(0, 1).color(r, g, b, a);
        bb.vertex(m, cx + rx - ux, cy + ry - uy, cz + rz - uz).texture(1, 1).color(r, g, b, a);
        bb.vertex(m, cx + rx + ux, cy + ry + uy, cz + rz + uz).texture(1, 0).color(r, g, b, a);
        bb.vertex(m, cx - rx + ux, cy - ry + uy, cz - rz + uz).texture(0, 0).color(r, g, b, a);
    }

    /** Disco horizontal (cuadrado texturizado) girado alrededor del eje Y. */
    public static void spinningDisc(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz, float radius,
                                    float angle, float r, float g, float b, float a) {
        float c = (float) Math.cos(angle) * radius;
        float s = (float) Math.sin(angle) * radius;
        // esquinas (-1,-1) (1,-1) (1,1) (-1,1) rotadas
        bb.vertex(m, cx + (-c + s), cy, cz + (-s - c)).texture(0, 0).color(r, g, b, a);
        bb.vertex(m, cx + (c + s), cy, cz + (s - c)).texture(1, 0).color(r, g, b, a);
        bb.vertex(m, cx + (c - s), cy, cz + (s + c)).texture(1, 1).color(r, g, b, a);
        bb.vertex(m, cx + (-c - s), cy, cz + (-s + c)).texture(0, 1).color(r, g, b, a);
    }

    /** Anillo plano inclinado (tipo Saturno). La textura va de v=0 (interior) a v=1 (exterior). */
    public static void ring(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz,
                            float inner, float outer, float tiltX, float tiltZ,
                            float r, float g, float b, float a) {
        int seg = 56;
        float cxT = (float) Math.cos(tiltX), sxT = (float) Math.sin(tiltX);
        float czT = (float) Math.cos(tiltZ), szT = (float) Math.sin(tiltZ);
        for (int i = 0; i < seg; i++) {
            float p0 = 2 * PI * i / seg;
            float p1 = 2 * PI * (i + 1) / seg;
            float u0 = (float) i / seg * 4f;
            float u1 = (float) (i + 1) / seg * 4f;
            float[] a0 = ringPoint(inner, p0, cxT, sxT, czT, szT);
            float[] b0 = ringPoint(outer, p0, cxT, sxT, czT, szT);
            float[] b1 = ringPoint(outer, p1, cxT, sxT, czT, szT);
            float[] a1 = ringPoint(inner, p1, cxT, sxT, czT, szT);
            bb.vertex(m, cx + a0[0], cy + a0[1], cz + a0[2]).texture(u0, 0).color(r, g, b, a);
            bb.vertex(m, cx + b0[0], cy + b0[1], cz + b0[2]).texture(u0, 1).color(r, g, b, a);
            bb.vertex(m, cx + b1[0], cy + b1[1], cz + b1[2]).texture(u1, 1).color(r, g, b, a);
            bb.vertex(m, cx + a1[0], cy + a1[1], cz + a1[2]).texture(u1, 0).color(r, g, b, a);
        }
    }

    private static float[] ringPoint(float rad, float phi, float cxT, float sxT, float czT, float szT) {
        float x = rad * (float) Math.cos(phi);
        float y = 0;
        float z = rad * (float) Math.sin(phi);
        // inclinar alrededor de X
        float y1 = y * cxT - z * sxT;
        float z1 = y * sxT + z * cxT;
        // inclinar alrededor de Z
        float x2 = x * czT - y1 * szT;
        float y2 = x * szT + y1 * czT;
        return new float[]{x2, y2, z1};
    }

    // =====================================================================================
    //  Formato de ENTIDAD (para el renderizador del meteoro)
    // =====================================================================================

    public static void entitySphere(VertexConsumer vc, MatrixStack.Entry entry, float rad, int lat, int lon,
                                    float uOff, int cr, int cg, int cb, int ca, int light) {
        Matrix4f m = entry.getPositionMatrix();
        for (int i = 0; i < lat; i++) {
            float t0 = PI * i / lat;
            float t1 = PI * (i + 1) / lat;
            float v0 = (float) i / lat;
            float v1 = (float) (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = 2 * PI * j / lon;
                float p1 = 2 * PI * (j + 1) / lon;
                float u0 = (float) j / lon + uOff;
                float u1 = (float) (j + 1) / lon + uOff;
                evert(vc, entry, m, rad, t0, p0, u0, v0, cr, cg, cb, ca, light);
                evert(vc, entry, m, rad, t1, p0, u0, v1, cr, cg, cb, ca, light);
                evert(vc, entry, m, rad, t1, p1, u1, v1, cr, cg, cb, ca, light);
                evert(vc, entry, m, rad, t0, p1, u1, v0, cr, cg, cb, ca, light);
            }
        }
    }

    private static void evert(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f m, float rad,
                              float theta, float phi, float u, float v,
                              int cr, int cg, int cb, int ca, int light) {
        float st = (float) Math.sin(theta);
        float nx = st * (float) Math.cos(phi);
        float ny = (float) Math.cos(theta);
        float nz = st * (float) Math.sin(phi);
        vc.vertex(m, nx * rad, ny * rad, nz * rad)
                .color(cr, cg, cb, ca)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }
}
