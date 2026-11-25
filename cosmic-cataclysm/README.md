# Cosmic Cataclysm (Fabric 1.21.1)

Mod original inspirado en la idea de "destrucción cinematográfica": se abre el cielo, aparece un sistema solar
con planetas girando alrededor del sol, los planetas explotan y sus pedazos caen como meteoros gigantes.

## Cómo se usa
- Item **Control Cósmico** (pestaña Herramientas): clic derecho mirando a un punto del suelo (o al horizonte).
- Comando (OP): `/cataclysm` (sobre ti) o `/cataclysm <x> <y> <z>`.
- Dura ~42 segundos. Mejor verlo parado cerca del objetivo y mirando al cielo.

## Cómo compilarlo (necesitas el .jar)
**Opción A – sin instalar nada (GitHub):** crea un repositorio, sube esta carpeta, ve a *Actions → build → Run workflow*
y descarga el artefacto `cosmic-cataclysm-jar`. Usa el que NO termina en `-sources`.

**Opción B – local:** instala JDK 21 y Gradle 8.10+, y dentro de esta carpeta:
```
gradle wrapper --gradle-version 8.10.2
./gradlew build          (Windows: gradlew.bat build)
```
El jar queda en `build/libs/cosmic-cataclysm-1.0.0.jar`. Ponlo en `mods/` junto a **Fabric API** (1.21.1).

## Qué ajustar (todo en `CosmicMath.java`, arriba del archivo)
`METEOR_SIZE_MULT`, `CRATER_MULT`, `METEOR_COUNT_MULT`, `TARGET_RADIUS`, `SKY_HEIGHT`, `DESTROY_BLOCKS`, `EJECTA`.
Los tiempos de cada fase también están ahí (`BREAK_START`, `SUN_BREAK`, `TOTAL`...).
Las texturas se regeneran con `python3 tools/gen_textures.py` (necesita numpy y pillow).

## Notas
- Un cataclismo completo destruye una zona enorme (cientos de bloques de radio). Úsalo en un mundo de prueba.
- Sin soporte para shaders (Iris/Optifine) todavía: el cielo puede verse raro con ellos activados.
- Todo el código y las texturas son originales (generadas por script).
