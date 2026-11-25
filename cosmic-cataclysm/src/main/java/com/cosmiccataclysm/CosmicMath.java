package com.cosmiccataclysm;

import net.minecraft.util.math.Vec3d;

/**
 * Línea de tiempo y matemática orbital COMPARTIDA entre servidor y cliente.
 * Todo está en ticks (20 ticks = 1 segundo).
 *
 * Cronología del cataclismo:
 *   0 - 110   El cielo se abre (domo de espacio + grieta).
 *   70 - 280  Aparece el sistema solar y los planetas giran alrededor del sol.
 *   280 - 400 Los planetas explotan uno por uno y sueltan fragmentos.
 *   440       El sol explota (explosión mayor).
 *   ~290-600  Los fragmentos caen como meteoros gigantes sobre la tierra.
 *   620 - 840 El cielo se cierra.
 */
public final class CosmicMath {
    private CosmicMath() {}

    // ------------------------------------------------------------------ AJUSTES (cámbialos a tu gusto)
    /** Altura sobre el objetivo a la que está el sol. */
    public static final int SKY_HEIGHT = 130;
    /** Radio del sol en bloques. */
    public static final double SUN_RADIUS = 30;
    /** Radio (en bloques) de la zona donde caen los meteoros alrededor del objetivo. */
    public static final double TARGET_RADIUS = 75;
    /** Multiplicador del tamaño de los meteoros (1.0 = normal, 2.0 = el doble de grandes). */
    public static final double METEOR_SIZE_MULT = 1.0;
    /** Multiplicador del radio de los cráteres. */
    public static final double CRATER_MULT = 2.1;
    /** Multiplicador de la cantidad de meteoros (1.0 = normal). */
    public static final double METEOR_COUNT_MULT = 1.0;
    /** Si es false, los meteoros explotan pero no destruyen bloques. */
    public static final boolean DESTROY_BLOCKS = true;
    /** Si es true, los cráteres lanzan bloques volando (más espectacular, más lag). */
    public static final boolean EJECTA = true;

    // ------------------------------------------------------------------ TIEMPOS
    public static final int RIFT_END = 110;
    public static final int SYSTEM_IN = 70;
    public static final int BREAK_START = 280;
    public static final int BREAK_STEP = 24;
    public static final int SUN_BREAK = 440;
    public static final int TOTAL = 840;
    public static final int CLOSE_START = 620;

    /** Un planeta: radio, órbita, periodo (ticks por vuelta), inclinación (rad), fase inicial (rad). */
    public record Body(String name, double radius, double orbit, double period, double tilt, double phase,
                       boolean ring, float r, float g, float b) {}

    public static final Body[] PLANETS = {
            new Body("mercurio", 5.0, 52, 260, 0.35, 0.5, false, 0.75f, 0.72f, 0.70f),
            new Body("venus", 7.5, 70, 340, 0.30, 2.0, false, 1.00f, 0.85f, 0.55f),
            new Body("tierra", 8.0, 90, 430, 0.40, 3.6, false, 0.55f, 0.75f, 1.00f),
            new Body("marte", 6.0, 108, 520, 0.35, 5.0, false, 1.00f, 0.55f, 0.40f),
            new Body("jupiter", 17.0, 138, 700, 0.30, 1.2, false, 1.00f, 0.80f, 0.60f),
            new Body("saturno", 14.0, 166, 850, 0.38, 4.1, true, 0.95f, 0.88f, 0.65f),
    };

    /** Índice que representa al sol cuando hablamos de "cuerpos" (planetas + sol). */
    public static final int SUN_INDEX = PLANETS.length;

    public static Vec3d skyCenter(Vec3d target) {
        return target.add(0, SKY_HEIGHT, 0);
    }

    /** Posición del planeta i en el tick t, con el sol en el centro c. */
    public static Vec3d planetPos(int i, double t, Vec3d c) {
        Body b = PLANETS[i];
        double a = b.phase() + t * 2 * Math.PI / b.period();
        double x = b.orbit() * Math.cos(a);
        double z = b.orbit() * Math.sin(a);
        double y = z * Math.sin(b.tilt());
        z = z * Math.cos(b.tilt());
        return c.add(x, y, z);
    }

    /** Posición de un cuerpo (planeta o sol). */
    public static Vec3d bodyPos(int i, double t, Vec3d c) {
        return i == SUN_INDEX ? c : planetPos(i, t, c);
    }

    public static double bodyRadius(int i) {
        return i == SUN_INDEX ? SUN_RADIUS : PLANETS[i].radius();
    }

    /** Tick en el que explota el cuerpo i. */
    public static int breakTick(int i) {
        return i == SUN_INDEX ? SUN_BREAK : BREAK_START + i * BREAK_STEP;
    }

    // ------------------------------------------------------------------ utilidades
    public static double clamp01(double x) {
        return x < 0 ? 0 : Math.min(x, 1);
    }

    public static double smooth(double x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    /** Hash determinista -> [0,1). Sirve para que el cliente genere los mismos fragmentos siempre. */
    public static double hash(long seed, int a, int b, int c) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL + c * 0xD6E8FEB86659FD93L;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return (h >>> 11) * (1.0 / (1L << 53));
    }
}
