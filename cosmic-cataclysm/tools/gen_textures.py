"""Genera todas las texturas procedurales del mod (originales, sin assets de terceros)."""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "src", "main", "resources", "assets", "cosmic_cataclysm", "textures")
COS = os.path.join(OUT, "cosmic")
ITEM = os.path.join(OUT, "item")
os.makedirs(COS, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)


def make_table(seed, n=32):
    return np.random.default_rng(seed).random((n, n, n)).astype(np.float32)


def noise3(tab, x, y, z):
    n = tab.shape[0]
    xi, yi, zi = np.floor(x).astype(int), np.floor(y).astype(int), np.floor(z).astype(int)
    xf, yf, zf = x - xi, y - yi, z - zi
    xf, yf, zf = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf), zf * zf * (3 - 2 * zf)

    def g(a, b, c):
        return tab[a % n, b % n, c % n]

    x00 = g(xi, yi, zi) * (1 - xf) + g(xi + 1, yi, zi) * xf
    x10 = g(xi, yi + 1, zi) * (1 - xf) + g(xi + 1, yi + 1, zi) * xf
    x01 = g(xi, yi, zi + 1) * (1 - xf) + g(xi + 1, yi, zi + 1) * xf
    x11 = g(xi, yi + 1, zi + 1) * (1 - xf) + g(xi + 1, yi + 1, zi + 1) * xf
    y0 = x00 * (1 - yf) + x10 * yf
    y1 = x01 * (1 - yf) + x11 * yf
    return y0 * (1 - zf) + y1 * zf


def fbm(tab, x, y, z, octaves=5, lac=2.0, gain=0.5):
    total, amp, norm = 0, 1.0, 0
    for _ in range(octaves):
        total = total + amp * noise3(tab, x, y, z)
        norm += amp
        amp *= gain
        x, y, z = x * lac, y * lac, z * lac
    return total / norm


def sphere_pts(w, h):
    """Puntos de una esfera para una textura equirectangular (sin costura)."""
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    phi, theta = U * 2 * np.pi, V * np.pi
    return np.sin(theta) * np.cos(phi), np.cos(theta), np.sin(theta) * np.sin(phi)


def ramp(t, stops):
    t = np.clip(t, 0, 1)
    pos = [s[0] for s in stops]
    out = np.zeros(t.shape + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(t, pos, [s[1][c] for s in stops])
    return out


def save(arr, name, alpha=None, folder=COS):
    a = np.clip(arr, 0, 255).astype(np.uint8)
    if alpha is not None:
        a = np.dstack([a, np.clip(alpha * 255, 0, 255).astype(np.uint8)])
        img = Image.fromarray(a, "RGBA")
    else:
        img = Image.fromarray(a, "RGB")
    img.save(os.path.join(folder, name))
    print("ok", name, img.size)


T1, T2, T3 = make_table(1), make_table(2), make_table(3)
W, H = 512, 256
x, y, z = sphere_pts(W, H)

# ---------- SOL ----------
n = fbm(T1, x * 3 + 10, y * 3, z * 3, 6)
n2 = fbm(T2, x * 9, y * 9, z * 9 + 5, 4)
v = np.clip(n * 1.1 + (n2 - 0.5) * 0.5, 0, 1)
save(ramp(v, [(0, (200, 60, 0)), (0.4, (255, 140, 10)), (0.7, (255, 210, 60)), (1, (255, 255, 220))]),
     "sun.png")

# ---------- MERCURIO (planet_0) ----------
n = fbm(T1, x * 4, y * 4, z * 4, 6)
r = 1 - np.abs(fbm(T3, x * 10, y * 10, z * 10, 3) * 2 - 1)
v = np.clip(n * 0.8 + r * 0.35, 0, 1)
gray = 70 + v * 140
save(np.dstack([gray, gray * 0.97, gray * 0.93]), "planet_0.png")

# ---------- VENUS (planet_1) ----------
warp = fbm(T2, x * 2, y * 2, z * 2, 4)
band = np.sin(y * 9 + warp * 7) * 0.5 + 0.5
n = fbm(T3, x * 5, y * 5, z * 5, 5)
v = band * 0.55 + n * 0.45
save(ramp(v, [(0, (170, 110, 50)), (0.5, (225, 180, 100)), (1, (250, 230, 170))]), "planet_1.png")

# ---------- TIERRA (planet_2) ----------
land = fbm(T1, x * 2.2 + 3, y * 2.2, z * 2.2, 6)
ocean = ramp(land / 0.5, [(0, (5, 20, 90)), (0.8, (20, 70, 170)), (1, (40, 120, 200))])
lv = np.clip((land - 0.5) / 0.25, 0, 1)
landc = ramp(lv, [(0, (50, 130, 50)), (0.5, (110, 140, 60)), (0.8, (140, 110, 70)), (1, (200, 200, 200))])
col = np.where((land > 0.5)[..., None], landc, ocean)
ice = np.clip((np.abs(y) - 0.82) / 0.08, 0, 1)[..., None]
col = col * (1 - ice) + 245 * ice
cl = np.clip((fbm(T2, x * 3.5, y * 3.5, z * 3.5, 5) - 0.5) * 3.2, 0, 1)[..., None] * 0.85
col = col * (1 - cl) + 255 * cl
save(col, "planet_2.png")

# ---------- MARTE (planet_3) ----------
n = fbm(T3, x * 3, y * 3, z * 3, 6)
m = fbm(T1, x * 7 + 2, y * 7, z * 7, 4)
v = np.clip(n * 0.8 + m * 0.3, 0, 1)
col = ramp(v, [(0, (90, 30, 15)), (0.5, (180, 80, 40)), (1, (225, 140, 80))])
ice = np.clip((np.abs(y) - 0.9) / 0.05, 0, 1)[..., None]
save(col * (1 - ice) + 245 * ice, "planet_3.png")

# ---------- JUPITER (planet_4) ----------
warp = fbm(T2, x * 3, y * 1.2, z * 3, 5)
band = np.sin(y * 16 + warp * 4.5) * 0.5 + 0.5
fine = fbm(T3, x * 6, y * 14, z * 6, 4)
v = band * 0.7 + fine * 0.3
col = ramp(v, [(0, (110, 60, 35)), (0.35, (190, 130, 85)), (0.7, (235, 205, 165)), (1, (250, 240, 220))])
lon = np.arctan2(z, x)
lat = np.arcsin(np.clip(y, -1, 1))
d = ((lon - 1.0) / 0.35) ** 2 + ((lat + 0.35) / 0.14) ** 2
spot = np.clip(1 - d, 0, 1)[..., None]
save(col * (1 - spot) + np.array([200, 70, 40]) * spot, "planet_4.png")

# ---------- SATURNO (planet_5) ----------
warp = fbm(T1, x * 2, y * 1, z * 2, 4)
band = np.sin(y * 22 + warp * 3) * 0.5 + 0.5
fine = fbm(T2, x * 4, y * 10, z * 4, 3)
v = band * 0.6 + fine * 0.4
save(ramp(v, [(0, (160, 130, 80)), (0.5, (215, 190, 130)), (1, (245, 230, 185))]), "planet_5.png")

# ---------- ANILLO (u = angulo, v = radio interior->exterior) ----------
rv = np.linspace(0, 1, 128)[:, None] * np.ones((1, 8))
dens = 0.55 + 0.45 * np.sin(rv * 80) * np.sin(rv * 37 + 1)
alpha = np.clip(dens, 0, 1) * np.clip(rv * 8, 0, 1) * np.clip((1 - rv) * 8, 0, 1)
alpha = alpha * np.where((rv > 0.55) & (rv < 0.63), 0.05, 1.0) * 0.85
base = ramp(rv * 0.9 + 0.05, [(0, (150, 125, 95)), (0.5, (220, 200, 160)), (1, (190, 170, 135))])
save(base, "ring.png", alpha)

# ---------- ESPACIO (domo) 2048x1024 ----------
SW, SH = 2048, 1024
sx, sy, sz = sphere_pts(SW, SH)
neb = fbm(T1, sx * 1.6 + 7, sy * 1.6, sz * 1.6, 6)
neb2 = fbm(T2, sx * 2.4, sy * 2.4 + 3, sz * 2.4, 5)
dust = np.clip((neb - 0.45) * 2.2, 0, 1) ** 1.6
img = (np.array([90, 30, 170])[None, None, :] * dust[..., None] * 0.55
       + np.array([30, 90, 200])[None, None, :] * (np.clip((neb2 - 0.5) * 2, 0, 1) ** 1.5)[..., None] * 0.45
       + np.array([230, 90, 40])[None, None, :] * (np.clip((neb * neb2 - 0.28) * 4, 0, 1) ** 2)[..., None] * 0.35
       + np.array([3, 3, 10]))
rng = np.random.default_rng(7)
tints = [[255, 255, 255], [255, 220, 170], [170, 200, 255]]
for _ in range(9000):
    px, py = int(rng.integers(0, SW)), int(rng.integers(0, SH))
    b = rng.random() ** 3 * 255
    c = np.array(tints[int(rng.integers(0, 3))]) * (b / 255)
    img[py, px] = np.maximum(img[py, px], c)
    if b > 150:
        for dx, dy in ((1, 0), (0, 1), (1, 1)):
            q = ((py + dy) % SH, (px + dx) % SW)
            img[q] = np.maximum(img[q], c * 0.6)
save(img, "space.png")

# ---------- GRIETA ----------
R = 512
yy, xx = np.mgrid[0:R, 0:R]
xx = (xx + 0.5) / R * 2 - 1
yy = (yy + 0.5) / R * 2 - 1
r = np.sqrt(xx ** 2 + yy ** 2)
ang = np.arctan2(yy, xx)
wz = fbm(T3, np.cos(ang) * 2 + r * 3, np.sin(ang) * 2 + r * 3, r * 4, 4)
arms = 0.5 + 0.5 * np.sin(ang * 3 + r * 14 + wz * 5)
alpha = np.clip((1 - r) * 1.8, 0, 1) * (0.5 + 0.5 * arms)
rim = np.exp(-((r - 0.86) / 0.07) ** 2)
alpha = np.clip(alpha * 0.85 + rim * 0.9, 0, 1) * np.clip((1 - r) * 14, 0, 1)
glow = np.clip(np.clip((r - 0.5) / 0.45, 0, 1) * arms + rim * 1.2, 0, 1)
save(ramp(glow, [(0, (6, 0, 18)), (0.5, (90, 25, 160)), (0.85, (230, 90, 220)), (1, (255, 230, 255))]),
     "rift.png", alpha)

# ---------- BRILLO ----------
G = 128
yy, xx = np.mgrid[0:G, 0:G]
xx = (xx + 0.5) / G * 2 - 1
yy = (yy + 0.5) / G * 2 - 1
rr = np.sqrt(xx ** 2 + yy ** 2)
a = np.clip(np.exp(-rr ** 2 * 5) - 0.02, 0, 1) * np.clip((1 - rr) * 4, 0, 1)
save(np.full((G, G, 3), 255, np.float32), "glow.png", a)

# ---------- BLANCO ----------
save(np.full((4, 4, 3), 255, np.float32), "white.png")

# ---------- ROCA CON LAVA ----------
RW, RH = 256, 128
rx, ry, rz = sphere_pts(RW, RH)
n = fbm(T1, rx * 3, ry * 3, rz * 3, 6)
rock = ramp(n, [(0, (22, 18, 16)), (0.5, (60, 50, 44)), (1, (105, 90, 80))])
crack = 1 - np.abs(fbm(T2, rx * 4 + 1, ry * 4, rz * 4, 4) * 2 - 1)
lava = np.clip((crack - 0.88) / 0.1, 0, 1)[..., None]
lcol = ramp(fbm(T3, rx * 8, ry * 8, rz * 8, 3), [(0, (220, 60, 0)), (0.6, (255, 150, 20)), (1, (255, 240, 120))])
save(rock * (1 - lava) + lcol * lava, "rock.png")

# ---------- ICONO DEL ITEM 16x16 ----------
S = 16
pix = np.zeros((S, S, 4), np.uint8)


def put(px, py, c):
    pix[py, px] = (*c, 255)


for i in range(2, 13):  # vara diagonal
    put(i, 15 - i, (70, 70, 85))
    put(min(i + 1, 15), 15 - i, (40, 40, 52))
cx, cy = 10, 5
for j in range(S):  # sol
    for i in range(S):
        d = ((i - cx) ** 2 + (j - cy) ** 2) ** 0.5
        if d < 3.3:
            t = d / 3.3
            put(i, j, (255, int(235 - 110 * t), int(120 - 100 * t)))
for k in range(0, 360, 10):  # anillo orbital
    a_ = np.radians(k)
    px, py = int(round(cx + 5.4 * np.cos(a_))), int(round(cy + 1.9 * np.sin(a_)))
    if 0 <= px < S and 0 <= py < S and pix[py, px, 3] == 0:
        put(px, py, (190, 120, 255))
put(2, 3, (255, 255, 255))
put(13, 12, (255, 255, 255))
put(4, 9, (200, 220, 255))
Image.fromarray(pix, "RGBA").save(os.path.join(ITEM, "cosmic_remote.png"))
print("ok item")
