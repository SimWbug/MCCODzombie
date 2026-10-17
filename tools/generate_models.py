#!/usr/bin/env python3
"""
Génère les modèles 3D des armes du pack CleanZombies (format modèle Minecraft / Blockbench),
la texture palette partagée et les icônes des bonus.

    python3 tools/generate_models.py            # écrit dans resourcepack/
    python3 tools/generate_models.py --preview  # + aperçus PNG dans build/model-previews/

Chaque arme est un assemblage de pavés (« elements »). Le canon pointe vers le nord (-Z) :
en vue à la première personne, le nord est la direction du regard.
Les fichiers .json produits s'ouvrent directement dans Blockbench pour retoucher la forme
ou les positions en main (section "display").

Dépendance : Pillow (pip install pillow).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "resourcepack", "assets", "cgzombies")
NS = "cgzombies"

# ── Palette partagée : 8×8 cases de 4 px dans une texture 32×32 ─────────────
PALETTE = {
    "gun_dark": (40, 42, 46), "gun_mid": (72, 76, 82), "gun_light": (120, 126, 134), "black": (16, 16, 18),
    "wood_dark": (78, 48, 28), "wood": (112, 72, 40), "wood_light": (148, 100, 58), "bakelite": (52, 34, 26),
    "brass": (178, 142, 62), "steel": (176, 182, 190), "steel_bright": (222, 228, 234), "red": (176, 34, 30),
    "red_dark": (108, 20, 18), "green_glow": (110, 255, 130), "green": (36, 150, 66), "silver": (198, 200, 210),
    "olive": (86, 92, 52), "orange": (226, 124, 34), "yellow": (242, 212, 64), "white": (238, 238, 238),
    "leather": (96, 60, 34), "gun_blue": (46, 52, 66),
}
NAMES = list(PALETTE.keys())
GRID = 8
CELL_UV = 16 / GRID  # taille d'une case en unités UV (0..16)


def build_palette_texture(path):
    rng = random.Random(7)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for i, name in enumerate(NAMES):
        cx, cy = i % GRID, i // GRID
        r, g, b = PALETTE[name]
        wood = name.startswith("wood")
        for x in range(4):
            for y in range(4):
                n = rng.randint(-7, 7)
                if wood and y % 2 == 0:
                    n -= 10  # veinage léger du bois
                px[cx * 4 + x, cy * 4 + y] = (clamp(r + n), clamp(g + n), clamp(b + n), 255)
    img.save(path)


def clamp(v):
    return max(0, min(255, v))


def uv(color):
    i = NAMES.index(color)
    cx, cy = i % GRID, i // GRID
    m = 0.2
    return [round(cx * CELL_UV + m, 3), round(cy * CELL_UV + m, 3),
            round((cx + 1) * CELL_UV - m, 3), round((cy + 1) * CELL_UV - m, 3)]


def box(frm, to, color, rot=None, name=None):
    faces = {f: {"uv": uv(color), "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")}
    el = {"name": name or color, "from": list(frm), "to": list(to), "faces": faces}
    if rot:
        el["rotation"] = rot
    return el


def grip_rot(origin, angle=-22.5):
    return {"angle": angle, "axis": "x", "origin": list(origin)}


# ── Armes ─────────────────────────────────────────────────────────────────

def pistol():
    g = grip_rot([8, 7, 10.5])
    return [
        box([7, 8, 2], [9, 10, 12.5], "gun_mid", name="glissiere"),
        box([7.25, 7, 3], [8.75, 8, 12], "gun_dark", name="carcasse"),
        box([7.5, 8.4, 1.4], [8.5, 9.6, 2], "black", name="bouche"),
        box([7.75, 10, 2.6], [8.25, 10.6, 3.4], "gun_dark", name="guidon"),
        box([7.4, 10, 11], [8.6, 10.6, 11.8], "gun_dark", name="hausse"),
        box([6.95, 8.3, 9.6], [9.05, 9.7, 9.9], "black", name="stries1"),
        box([6.95, 8.3, 10.4], [9.05, 9.7, 10.7], "black", name="stries2"),
        box([6.95, 8.3, 11.2], [9.05, 9.7, 11.5], "black", name="stries3"),
        box([7.6, 9, 12.5], [8.4, 10, 13.3], "gun_dark", name="chien"),
        box([7.1, 2.6, 9.2], [8.9, 7.2, 12.2], "gun_dark", rot=g, name="crosse"),
        box([6.85, 3.2, 9.6], [9.15, 6.6, 11.8], "wood", rot=g, name="plaquettes"),
        box([7.2, 2.2, 9.3], [8.8, 2.7, 12.1], "black", rot=g, name="talon_chargeur"),
        box([7.6, 5.6, 6.4], [8.4, 6.1, 9.4], "gun_dark", name="pontet_bas"),
        box([7.6, 5.6, 6.4], [8.4, 7.1, 6.9], "gun_dark", name="pontet_avant"),
        box([7.8, 6.1, 8.2], [8.2, 7.1, 8.6], "steel", name="detente"),
    ]


def mp40():
    g = grip_rot([8, 8.5, 10])
    return [
        box([7, 8.5, 1], [9, 10.6, 14], "gun_dark", name="boitier"),
        box([7.45, 9.05, -5], [8.55, 10.05, 1], "gun_mid", name="canon"),
        box([7.3, 8.5, -1.5], [8.7, 9.1, 1], "gun_dark", name="appui"),
        box([7.6, 10.05, -4.6], [8.4, 11.1, -3.8], "gun_dark", name="guidon"),
        box([7.4, 9.2, -5.5], [8.6, 9.9, -5], "black", name="bouche"),
        box([7.2, 7, 3.4], [8.8, 8.5, 6.6], "gun_mid", name="puits"),
        box([7.35, 0.5, 3.8], [8.65, 7, 6.2], "gun_dark", name="chargeur"),
        box([7.2, 3.6, 9], [8.8, 8.5, 11.2], "bakelite", rot=g, name="poignee"),
        box([7.6, 7, 7], [8.4, 7.4, 9.2], "gun_dark", name="pontet"),
        box([7.8, 7.4, 7.8], [8.2, 8.5, 8.2], "steel", name="detente"),
        box([9, 9.6, 6], [10, 10.2, 7], "steel", name="levier"),
        box([7.5, 10.6, 11], [8.5, 11.4, 12], "gun_dark", name="hausse"),
        box([6.9, 8.9, 14], [7.3, 9.3, 21], "gun_mid", name="crosse_haut_g"),
        box([8.7, 8.9, 14], [9.1, 9.3, 21], "gun_mid", name="crosse_haut_d"),
        box([6.9, 6.2, 14], [7.3, 6.6, 21], "gun_mid", name="crosse_bas_g"),
        box([8.7, 6.2, 14], [9.1, 6.6, 21], "gun_mid", name="crosse_bas_d"),
        box([6.8, 5.6, 20.6], [9.2, 9.6, 21.6], "gun_dark", name="plaque"),
        box([7.4, 6.4, 13.2], [8.6, 8.6, 14.4], "gun_dark", name="charniere"),
    ]


def garand():
    return [
        box([7.6, 9.1, -14], [8.4, 9.9, 6], "gun_mid", name="canon"),
        box([7.6, 8.2, -13.6], [8.4, 9.1, -7], "gun_dark", name="cylindre_gaz"),
        box([7.7, 9.9, -13.4], [8.3, 10.9, -12.6], "gun_dark", name="guidon"),
        box([7.2, 7, -6], [8.8, 9.1, 9], "wood", name="garde_main_bas"),
        box([7.35, 9.1, -4], [8.65, 10, 5], "wood_light", name="garde_main_haut"),
        box([7.2, 9, 5], [8.8, 10.6, 13.5], "gun_dark", name="boitier"),
        box([7.5, 10.6, 11.8], [8.5, 11.6, 13.4], "gun_dark", name="hausse"),
        box([8.8, 9.8, 6], [9.4, 10.3, 7.2], "steel", name="levier"),
        box([7.15, 6.2, 9], [8.85, 9.1, 17.5], "wood", name="poignet"),
        box([7.6, 5.5, 9.6], [8.4, 6.2, 12], "gun_dark", name="pontet"),
        box([7.8, 6.2, 10.6], [8.2, 7.2, 11], "steel", name="detente"),
        box([7.1, 6.2, 17], [8.9, 9.1, 28], "wood", name="crosse_haut"),
        box([7.15, 4.8, 20], [8.85, 6.2, 28], "wood", name="crosse_milieu"),
        box([7.2, 3.4, 23.5], [8.8, 4.8, 28], "wood_dark", name="crosse_bas"),
        box([7.05, 3.3, 28], [8.95, 9.2, 28.6], "gun_dark", name="plaque"),
        box([7.5, 6.6, -1], [8.5, 7, 0.4], "steel", name="anneau_bretelle"),
    ]


def bowie():
    return [
        box([7.4, 7, 9.2], [8.6, 8.7, 14.4], "leather", name="manche"),
        box([7.35, 7.3, 10], [8.65, 7.6, 13.6], "wood_dark", name="bague1"),
        box([6.9, 6.4, 8.6], [9.1, 9.3, 9.2], "brass", name="garde"),
        box([7.3, 6.8, 14.4], [8.7, 8.9, 15.4], "brass", name="pommeau"),
        box([7.82, 7.2, -1.5], [8.18, 9, 8.6], "steel", name="lame"),
        box([7.78, 8.75, 0], [8.22, 9.15, 8.6], "steel_bright", name="dos"),
        box([7.84, 7.5, -3.6], [8.16, 8.75, -1.5], "steel", name="pointe"),
        box([7.86, 7.9, -4.6], [8.14, 8.45, -3.6], "steel_bright", name="pointe_fine"),
        box([7.8, 7.2, 4], [8.2, 7.5, 8.6], "steel_bright", name="tranchant"),
    ]


def raygun():
    g = grip_rot([8, 7.5, 10.5])
    return [
        box([7, 7.6, 4], [9, 10.6, 12], "red", name="corps"),
        box([6.6, 8, 5], [9.4, 10.2, 11], "red_dark", name="corps_flancs"),
        box([6.4, 7.2, 3], [9.6, 11, 3.8], "silver", name="anneau1"),
        box([6.7, 7.5, 1.6], [9.3, 10.7, 2.4], "silver", name="anneau2"),
        box([7, 7.8, 0.2], [9, 10.4, 1], "silver", name="anneau3"),
        box([7.4, 8.5, -1.6], [8.6, 9.7, 4], "gun_dark", name="emetteur"),
        box([7.55, 8.65, -2.2], [8.45, 9.55, -1.6], "green_glow", name="bout_lumineux"),
        box([7.4, 10.6, 5.6], [8.6, 11.6, 10], "green_glow", name="chambre"),
        box([7.25, 10.5, 5.2], [8.75, 10.8, 10.4], "silver", name="chambre_base"),
        box([6, 8.6, 7], [6.6, 10.2, 10.4], "silver", name="aileron_g"),
        box([9.4, 8.6, 7], [10, 10.2, 10.4], "silver", name="aileron_d"),
        box([7.2, 3.2, 9.4], [8.8, 7.8, 11.8], "red_dark", rot=g, name="poignee"),
        box([7.6, 5.6, 7], [8.4, 6.1, 9.6], "silver", name="pontet"),
        box([7.8, 6.1, 8.2], [8.2, 7.6, 8.6], "yellow", name="detente"),
        box([7.6, 9.6, 12], [8.4, 10.4, 13], "silver", name="arriere"),
    ]


WEAPONS = {
    # nom: (fonction, réglages d'affichage)
    "pistol": (pistol, {"fp": 0.75, "tp": 0.6, "gui": 1.0, "fixed": 1.2}),
    "mp40": (mp40, {"fp": 0.6, "tp": 0.5, "gui": 0.62, "fixed": 0.8}),
    "garand": (garand, {"fp": 0.55, "tp": 0.45, "gui": 0.4, "fixed": 0.55}),
    "bowie": (bowie, {"fp": 0.8, "tp": 0.65, "gui": 0.85, "fixed": 1.0}),
    "raygun": (raygun, {"fp": 0.75, "tp": 0.6, "gui": 0.95, "fixed": 1.2}),
}


def display(s):
    def sc(v):
        return [v, v, v]
    return {
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1.5, 1.5, -2], "scale": sc(s["fp"])},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1.5, 1.5, -2], "scale": sc(s["fp"])},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": sc(s["tp"])},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1.5], "scale": sc(s["tp"])},
        "gui": {"rotation": [20, -90, 0], "translation": [0, 0, 0], "scale": sc(s["gui"])},
        "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": sc(s["tp"])},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": sc(s["fixed"])},
        "head": {"rotation": [0, 90, 0], "translation": [0, 6, 0], "scale": sc(s["tp"])},
    }


def write_weapon(name, elements, s):
    model = {
        "credit": "CleanZombies — modèle original généré par tools/generate_models.py",
        "texture_size": [32, 32],
        "textures": {"0": f"{NS}:item/weapon_palette", "particle": f"{NS}:item/weapon_palette"},
        "elements": elements,
        "display": display(s),
    }
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), model)
    write_json(os.path.join(ASSETS, "items", name + ".json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


# ── Icônes des bonus (objets 2D épais, vus de face) ────────────────────────

def icon_max_ammo(path):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([4, 14, 27, 28], fill=(70, 86, 44), outline=(28, 34, 18))     # caisse
    d.rectangle([4, 14, 27, 17], fill=(92, 110, 58), outline=(28, 34, 18))
    d.rectangle([13, 20, 18, 24], fill=(200, 170, 70))                        # plaque
    for i, x in enumerate((7, 11, 15, 19, 23)):                                # balles
        top = 3 + (i % 2) * 2
        d.rectangle([x, top + 3, x + 2, 13], fill=(196, 150, 60), outline=(120, 86, 30))
        d.polygon([(x, top + 3), (x + 1, top), (x + 2, top + 3)], fill=(150, 120, 90))
    img.save(path)


def icon_nuke(path):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([5, 9, 27, 30], fill=(34, 34, 38), outline=(10, 10, 12))
    d.ellipse([9, 13, 14, 18], fill=(90, 90, 100))                            # reflet
    d.rectangle([13, 6, 19, 10], fill=(70, 70, 76), outline=(20, 20, 22))
    d.line([(16, 6), (19, 3), (22, 4)], fill=(150, 110, 60), width=1)          # mèche
    d.ellipse([20, 0, 26, 6], fill=(255, 190, 40))
    d.ellipse([22, 2, 24, 4], fill=(255, 250, 200))
    for start in (-120, 0, 120):  # trèfle de radioactivité
        d.pieslice([10, 14, 22, 26], start - 30, start + 30, fill=(236, 204, 40))
    d.ellipse([14, 18, 18, 22], fill=(34, 34, 38))
    d.ellipse([15, 19, 17, 21], fill=(236, 204, 40))
    img.save(path)


def icon_double(path):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold, dark = (246, 200, 40), (120, 80, 10)
    # x
    for o in range(3):
        d.line([(2 + o, 10), (11 + o, 22)], fill=gold, width=1)
        d.line([(11 + o, 10), (2 + o, 22)], fill=gold, width=1)
    # 2
    d.arc([14, 5, 29, 18], 180, 30, fill=gold, width=4)
    d.line([(27, 15), (15, 26)], fill=gold, width=4)
    d.rectangle([14, 24, 29, 27], fill=gold)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (32, 32), dark + (255,)), (1, 1), img)         # ombre
    out.alpha_composite(img)
    out.save(path)


def write_icon(name, painter):
    painter(os.path.join(ASSETS, "textures", "item", name + ".png"))
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"},
                "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]}}})
    write_json(os.path.join(ASSETS, "items", name + ".json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)


# ── Aperçu : rendu logiciel des pavés (vue de profil + vue 3/4) ─────────────

def rotate_point(p, rot):
    if not rot:
        return p
    ox, oy, oz = rot["origin"]
    a = math.radians(rot["angle"])
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if rot["axis"] == "x":
        y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    elif rot["axis"] == "y":
        x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    else:
        x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
    return (x + ox, y + oy, z + oz)


def render(elements, path, yaw, pitch, size=360):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    polys = []
    light = (0.35, 0.8, -0.5)
    for el in elements:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        c = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
        c = [view(rotate_point(p, el.get("rotation"))) for p in c]
        faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
        color = PALETTE[next(n for n in NAMES if uv(n) == el["faces"]["north"]["uv"])]
        for f in faces:
            pts = [c[i] for i in f]
            ax, ay, az = (pts[1][j] - pts[0][j] for j in range(3))
            bx, by, bz = (pts[2][j] - pts[0][j] for j in range(3))
            nx, ny, nz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
            ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
            nx, ny, nz = nx / ln, ny / ln, nz / ln
            depth = sum(p[2] for p in pts) / 4
            shade = 0.55 + 0.45 * abs(nx * light[0] + ny * light[1] + nz * light[2])
            polys.append((depth, pts, tuple(int(v * shade) for v in color)))
    polys.sort(key=lambda t: -t[0])
    img = Image.new("RGB", (size, size), (34, 36, 40))
    d = ImageDraw.Draw(img)
    k = size / 40
    for _, pts, col in polys:
        d.polygon([(size / 2 + p[0] * k, size / 2 - p[1] * k) for p in pts], fill=col, outline=(0, 0, 0))
    img.save(path)


def main():
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    build_palette_texture(os.path.join(ASSETS, "textures", "item", "weapon_palette.png"))
    for name, (fn, s) in WEAPONS.items():
        write_weapon(name, fn(), s)
    write_icon("powerup_max_ammo", icon_max_ammo)
    write_icon("powerup_nuke", icon_nuke)
    write_icon("powerup_double_points", icon_double)
    if "--preview" in sys.argv:
        out = os.path.join(ROOT, "build", "model-previews")
        os.makedirs(out, exist_ok=True)
        for name, (fn, _) in WEAPONS.items():
            render(fn(), os.path.join(out, name + "_profil.png"), 90, 0)
            render(fn(), os.path.join(out, name + "_3-4.png"), 125, 25)
        print("Aperçus :", out)
    print("Modèles écrits dans", ASSETS)


if __name__ == "__main__":
    main()
