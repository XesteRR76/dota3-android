#!/usr/bin/env python3
"""
Procedural dark-fantasy Dota 3 3D Asset Generator.
Generates smooth UV-mapped Wavefront .obj models and painted .png textures
into app/src/main/assets/dota/models/ using only Python standard library.
"""
import math
import os
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "dota" / "models"


def clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


def lerp(a, b, t):
    return a + (b - a) * t


def v_add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def v_sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def v_scale(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def v_cross(a, b):
    return (
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )


def v_norm(a):
    l = math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
    if l < 1e-8:
        return (0.0, 1.0, 0.0)
    return (a[0] / l, a[1] / l, a[2] / l)


def rot_euler(p, rx_deg=0.0, ry_deg=0.0, rz_deg=0.0):
    x, y, z = p
    if rx_deg != 0.0:
        r = math.radians(rx_deg)
        c, s = math.cos(r), math.sin(r)
        y, z = y * c - z * s, y * s + z * c
    if ry_deg != 0.0:
        r = math.radians(ry_deg)
        c, s = math.cos(r), math.sin(r)
        x, z = x * c + z * s, -x * s + z * c
    if rz_deg != 0.0:
        r = math.radians(rz_deg)
        c, s = math.cos(r), math.sin(r)
        x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


class ObjBuilder:
    def __init__(self):
        self.positions = []
        self.uvs = []
        self.normals = []
        self.faces = []  # list of ((vi, ti, ni), (vi, ti, ni), (vi, ti, ni))

    def _transform_pts(self, pts, center=(0, 0, 0), rot=(0, 0, 0)):
        out = []
        for p in pts:
            rp = rot_euler(p, rot[0], rot[1], rot[2])
            out.append((rp[0] + center[0], rp[1] + center[1], rp[2] + center[2]))
        return out

    def add_smooth_grid(self, grid_pts, grid_uvs, grid_norms=None, closed_u=False, flip_winding=False):
        """
        grid_pts: rows x cols of (x, y, z)
        grid_uvs: rows x cols of (u, v)
        Produces smooth averaged vertex normals across the surface.
        """
        rows = len(grid_pts)
        cols = len(grid_pts[0])

        # Compute smooth normals from quads if not supplied
        if grid_norms is None:
            acc = [[(0.0, 0.0, 0.0) for _ in range(cols)] for _ in range(rows)]
            for r in range(rows - 1):
                for c in range(cols - 1):
                    p00 = grid_pts[r][c]
                    p10 = grid_pts[r + 1][c]
                    p11 = grid_pts[r + 1][c + 1]
                    p01 = grid_pts[r][c + 1]
                    if flip_winding:
                        fn1 = v_cross(v_sub(p11, p00), v_sub(p10, p00))
                        fn2 = v_cross(v_sub(p01, p00), v_sub(p11, p00))
                    else:
                        fn1 = v_cross(v_sub(p10, p00), v_sub(p11, p00))
                        fn2 = v_cross(v_sub(p11, p00), v_sub(p01, p00))
                    fn = v_add(fn1, fn2)
                    acc[r][c] = v_add(acc[r][c], fn)
                    acc[r + 1][c] = v_add(acc[r + 1][c], fn)
                    acc[r + 1][c + 1] = v_add(acc[r + 1][c + 1], fn)
                    acc[r][c + 1] = v_add(acc[r][c + 1], fn)
            if closed_u:
                for r in range(rows):
                    merged = v_add(acc[r][0], acc[r][cols - 1])
                    acc[r][0] = merged
                    acc[r][cols - 1] = merged
            grid_norms = [[v_norm(acc[r][c]) for c in range(cols)] for r in range(rows)]

        v_idx = [[0] * cols for _ in range(rows)]
        t_idx = [[0] * cols for _ in range(rows)]
        n_idx = [[0] * cols for _ in range(rows)]
        for r in range(rows):
            for c in range(cols):
                self.positions.append(grid_pts[r][c])
                v_idx[r][c] = len(self.positions)
                self.uvs.append(grid_uvs[r][c])
                t_idx[r][c] = len(self.uvs)
                self.normals.append(grid_norms[r][c])
                n_idx[r][c] = len(self.normals)

        for r in range(rows - 1):
            for c in range(cols - 1):
                a = (v_idx[r][c], t_idx[r][c], n_idx[r][c])
                b = (v_idx[r + 1][c], t_idx[r + 1][c], n_idx[r + 1][c])
                d = (v_idx[r + 1][c + 1], t_idx[r + 1][c + 1], n_idx[r + 1][c + 1])
                e = (v_idx[r][c + 1], t_idx[r][c + 1], n_idx[r][c + 1])
                if v_idx[r][c] != v_idx[r][c + 1]:
                    if flip_winding:
                        self.faces.append((a, d, b))
                        self.faces.append((a, e, d))
                    else:
                        self.faces.append((a, b, d))
                        self.faces.append((a, d, e))

    def add_flat_tri(self, p0, p1, p2, uv0=(0.1, 0.1), uv1=(0.9, 0.1), uv2=(0.5, 0.9)):
        fn = v_norm(v_cross(v_sub(p1, p0), v_sub(p2, p0)))
        self.normals.append(fn)
        ni = len(self.normals)
        self.positions.extend([p0, p1, p2])
        v0 = len(self.positions) - 2
        self.uvs.extend([uv0, uv1, uv2])
        t0 = len(self.uvs) - 2
        self.faces.append(((v0, t0, ni), (v0 + 1, t0 + 1, ni), (v0 + 2, t0 + 2, ni)))

    def add_flat_quad(self, p0, p1, p2, p3, uv_rect=(0.0, 0.0, 1.0, 1.0)):
        u0, v0, u1, v1 = uv_rect
        self.add_flat_tri(p0, p1, p2, (u0, v0), (u1, v0), (u1, v1))
        self.add_flat_tri(p0, p2, p3, (u0, v0), (u1, v1), (u0, v1))

    def add_lathe(self, profile, segs=16, center=(0, 0, 0), rot=(0, 0, 0),
                  uv_rect=(0.0, 0.0, 1.0, 1.0), oval_xz=(1.0, 1.0), cap_ends=True):
        """
        profile: list of (radius, y) from bottom to top.
        """
        u0, v0, u1, v1 = uv_rect
        rows = len(profile)
        cols = segs + 1
        pts = []
        uvs = []
        for ri, (rad, py) in enumerate(profile):
            v_t = ri / max(1, rows - 1)
            row_p = []
            row_u = []
            for ci in range(cols):
                u_t = ci / segs
                ang = u_t * math.tau
                px = math.sin(ang) * rad * oval_xz[0]
                pz = math.cos(ang) * rad * oval_xz[1]
                rp = rot_euler((px, py, pz), rot[0], rot[1], rot[2])
                row_p.append((rp[0] + center[0], rp[1] + center[1], rp[2] + center[2]))
                row_u.append((lerp(u0, u1, u_t), lerp(v0, v1, v_t)))
            pts.append(row_p)
            uvs.append(row_u)
        self.add_smooth_grid(pts, uvs, closed_u=True, flip_winding=True)

        if cap_ends:
            if profile[0][0] > 1e-4:
                c_bot = self._transform_pts([(0.0, profile[0][1], 0.0)], center, rot)[0]
                for ci in range(segs):
                    self.add_flat_tri(c_bot, pts[0][ci + 1], pts[0][ci],
                                      ((u0 + u1) * 0.5, v0), uvs[0][ci + 1], uvs[0][ci])
            if profile[-1][0] > 1e-4:
                c_top = self._transform_pts([(0.0, profile[-1][1], 0.0)], center, rot)[0]
                for ci in range(segs):
                    self.add_flat_tri(c_top, pts[-1][ci], pts[-1][ci + 1],
                                      ((u0 + u1) * 0.5, v1), uvs[-1][ci], uvs[-1][ci + 1])

    def add_superellipsoid(self, rx, ry, rz, u_segs=16, v_segs=10, e=0.85, n=0.85,
                           center=(0, 0, 0), rot=(0, 0, 0), uv_rect=(0.0, 0.0, 1.0, 1.0)):
        def spow(val, p):
            return math.copysign(abs(val) ** p, val) if abs(val) > 1e-9 else 0.0

        u0, v0, u1, v1 = uv_rect
        pts = []
        uvs = []
        for vi in range(v_segs + 1):
            vt = vi / v_segs
            phi = -math.pi * 0.5 + vt * math.pi
            cp, sp = math.cos(phi), math.sin(phi)
            row_p = []
            row_u = []
            for ui in range(u_segs + 1):
                ut = ui / u_segs
                theta = ut * math.tau
                ct, st = math.cos(theta), math.sin(theta)
                px = rx * spow(cp, n) * spow(st, e)
                py = ry * spow(sp, n)
                pz = rz * spow(cp, n) * spow(ct, e)
                rp = rot_euler((px, py, pz), rot[0], rot[1], rot[2])
                row_p.append((rp[0] + center[0], rp[1] + center[1], rp[2] + center[2]))
                row_u.append((lerp(u0, u1, ut), lerp(v0, v1, vt)))
            pts.append(row_p)
            uvs.append(row_u)
        self.add_smooth_grid(pts, uvs, closed_u=True, flip_winding=True)

    def add_swept_profile(self, path_pts, radii_xy, segs=8, uv_rect=(0.0, 0.0, 1.0, 1.0),
                          cap_start=True, cap_end=True):
        """
        Sweeps an elliptical/diamond cross-section along 3D path_pts.
        """
        u0, v0, u1, v1 = uv_rect
        n_pts = len(path_pts)
        pts = []
        uvs = []
        for i, p in enumerate(path_pts):
            if i == 0:
                tang = v_norm(v_sub(path_pts[1], path_pts[0]))
            elif i == n_pts - 1:
                tang = v_norm(v_sub(path_pts[-1], path_pts[-2]))
            else:
                tang = v_norm(v_sub(path_pts[i + 1], path_pts[i - 1]))
            up_ref = (0.0, 1.0, 0.0) if abs(tang[1]) < 0.92 else (1.0, 0.0, 0.0)
            right = v_norm(v_cross(up_ref, tang))
            up = v_norm(v_cross(tang, right))
            rx, ry = radii_xy[i]
            vt = i / max(1, n_pts - 1)
            row_p = []
            row_u = []
            for s in range(segs + 1):
                ut = s / segs
                ang = ut * math.tau
                off = v_add(v_scale(right, math.cos(ang) * rx), v_scale(up, math.sin(ang) * ry))
                row_p.append(v_add(p, off))
                row_u.append((lerp(u0, u1, ut), lerp(v0, v1, vt)))
            pts.append(row_p)
            uvs.append(row_u)
        self.add_smooth_grid(pts, uvs, closed_u=True, flip_winding=True)
        if cap_start and max(radii_xy[0]) > 1e-4:
            for s in range(segs):
                self.add_flat_tri(path_pts[0], pts[0][s + 1], pts[0][s],
                                  ((u0 + u1) * 0.5, v0), uvs[0][s + 1], uvs[0][s])
        if cap_end and max(radii_xy[-1]) > 1e-4:
            for s in range(segs):
                self.add_flat_tri(path_pts[-1], pts[-1][s], pts[-1][s + 1],
                                  ((u0 + u1) * 0.5, v1), uvs[-1][s], uvs[-1][s + 1])

    def add_box(self, sx, sy, sz, center=(0, 0, 0), rot=(0, 0, 0), uv_rect=(0.0, 0.0, 1.0, 1.0)):
        hx, hy, hz = sx * 0.5, sy * 0.5, sz * 0.5
        raw = [
            (-hx, -hy, hz), (hx, -hy, hz), (hx, hy, hz), (-hx, hy, hz),
            (-hx, -hy, -hz), (hx, -hy, -hz), (hx, hy, -hz), (-hx, hy, -hz),
        ]
        p = self._transform_pts(raw, center, rot)
        self.add_flat_quad(p[0], p[1], p[2], p[3], uv_rect)  # +Z
        self.add_flat_quad(p[5], p[4], p[7], p[6], uv_rect)  # -Z
        self.add_flat_quad(p[3], p[2], p[6], p[7], uv_rect)  # +Y
        self.add_flat_quad(p[4], p[5], p[1], p[0], uv_rect)  # -Y
        self.add_flat_quad(p[1], p[5], p[6], p[2], uv_rect)  # +X
        self.add_flat_quad(p[4], p[0], p[3], p[7], uv_rect)  # -X

    def add_crystal(self, rx, rz, h_top, h_bot, segs=6, center=(0, 0, 0), rot=(0, 0, 0),
                    uv_rect=(0.0, 0.0, 1.0, 1.0)):
        u0, v0, u1, v1 = uv_rect
        ring = []
        for i in range(segs):
            a = (i / segs) * math.tau
            ring.append((math.sin(a) * rx, 0.0, math.cos(a) * rz))
        ring_t = self._transform_pts(ring, center, rot)
        top_t = self._transform_pts([(0.0, h_top, 0.0)], center, rot)[0]
        bot_t = self._transform_pts([(0.0, -h_bot, 0.0)], center, rot)[0]
        um = (u0 + u1) * 0.5
        vm = (v0 + v1) * 0.5
        for i in range(segs):
            j = (i + 1) % segs
            self.add_flat_tri(top_t, ring_t[i], ring_t[j], (um, v1), (u0, vm), (u1, vm))
            self.add_flat_tri(bot_t, ring_t[j], ring_t[i], (um, v0), (u1, vm), (u0, vm))

    def add_torus(self, major_r, minor_r, maj_segs=20, min_segs=8, center=(0, 0, 0),
                  rot=(0, 0, 0), uv_rect=(0.0, 0.0, 1.0, 1.0)):
        u0, v0, u1, v1 = uv_rect
        pts = []
        uvs = []
        for i in range(maj_segs + 1):
            ut = i / maj_segs
            u = ut * math.tau
            cu, su = math.cos(u), math.sin(u)
            row_p = []
            row_u = []
            for j in range(min_segs + 1):
                vt = j / min_segs
                v = vt * math.tau
                cv, sv = math.cos(v), math.sin(v)
                px = (major_r + minor_r * cv) * su
                py = minor_r * sv
                pz = (major_r + minor_r * cv) * cu
                rp = rot_euler((px, py, pz), rot[0], rot[1], rot[2])
                row_p.append((rp[0] + center[0], rp[1] + center[1], rp[2] + center[2]))
                row_u.append((lerp(u0, u1, ut), lerp(v0, v1, vt)))
            pts.append(row_p)
            uvs.append(row_u)
        self.add_smooth_grid(pts, uvs, closed_u=True)

    def write(self, path: Path):
        lines = [f"# Generated by Dota 3 asset generator ({len(self.faces)} tris)\n"]
        for x, y, z in self.positions:
            lines.append(f"v {x:.4f} {y:.4f} {z:.4f}\n")
        for u, v in self.uvs:
            lines.append(f"vt {u:.4f} {v:.4f}\n")
        for nx, ny, nz in self.normals:
            lines.append(f"vn {nx:.4f} {ny:.4f} {nz:.4f}\n")
        for (a, b, c) in self.faces:
            lines.append(f"f {a[0]}/{a[1]}/{a[2]} {b[0]}/{b[1]}/{b[2]} {c[0]}/{c[1]}/{c[2]}\n")
        path.write_text("".join(lines), encoding="utf-8")
        return len(self.positions), len(self.faces)


# ---------------- PNG Writer (Pure Stdlib) ----------------

def write_png(path: Path, width: int, height: int, pixel_fn):
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0
        v = y / max(1, height - 1)
        for x in range(width):
            u = x / max(1, width - 1)
            r, g, b, a = pixel_fn(u, v, x, y)
            raw.append(int(clamp(r, 0.0, 1.0) * 255))
            raw.append(int(clamp(g, 0.0, 1.0) * 255))
            raw.append(int(clamp(b, 0.0, 1.0) * 255))
            raw.append(int(clamp(a, 0.0, 1.0) * 255))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), level=6)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
    path.write_bytes(png)


def hash_noise(x, y):
    n = (int(x * 73856093) ^ int(y * 19349663)) & 0xFFFF
    return n / 65535.0


def generate_textures():
    # 1. Hero neutral armor & trim texture
    # Note: flipV=True maps OBJ v_obj=0..0.55 -> PNG v=0.45..1.00 (armor), v_obj=0.68..0.96 -> PNG v=0.04..0.32 (trim)
    def hero_armor_px(u, v, x, y):
        grain = math.sin(u * 48.0 + v * 12.0) * 0.04 + (hash_noise(x, y) - 0.5) * 0.05
        if v < 0.36:
            # Trim / pauldrons / crest / cape with gold-ivory edge highlight
            band = math.sin(u * math.tau * 4.0) * 0.05
            trim = 0.14 if (v < 0.10 or v > 0.28) else 0.0
            base = 0.88 + grain + band + trim
            return (base, base * 0.94, base * 0.82, 1.0)
        # Sculpted brushed steel plate with panel seams
        seam = -0.18 if (abs((u * 4.0) % 1.0 - 0.5) > 0.46 or abs((v * 3.0) % 1.0 - 0.5) > 0.45) else 0.0
        sheen = math.sin(v * math.pi * 2.0) * 0.08
        val = 0.82 + grain + sheen + seam
        return (val, val, val * 1.02, 1.0)

    write_png(OUT_DIR / "tex_hero_armor.png", 256, 256, hero_armor_px)

    # 2. Radiant marble/emerald stone (bottom 0.68 = weathered stone blocks, top 0.32 = glowing cyan-emerald rune)
    def rad_stone_px(u, v, x, y):
        n = math.sin(u * 30.0) * math.cos(v * 28.0) * 0.06 + (hash_noise(x, y) - 0.5) * 0.05
        if v < 0.32:
            # Glowing rune crystal region (V flipped -> top of OBJ maps near v=0 or v=1; we keep safe band)
            pulse = 0.85 + 0.15 * math.sin(u * math.tau * 6.0)
            return (0.18 * pulse, 0.92 * pulse, 0.78 * pulse, 1.0)
        if v > 0.82:
            # Gold trim
            return (0.85 + n, 0.68 + n, 0.24, 1.0)
        brick_u = (u * 6.0 + (0.5 if int(v * 8.0) % 2 else 0.0)) % 1.0
        brick_v = (v * 8.0) % 1.0
        mortar = -0.14 if (brick_u < 0.07 or brick_v < 0.09) else 0.0
        moss = 0.07 if v > 0.68 else 0.0
        return (0.48 + n + mortar, 0.56 + n + mortar + moss, 0.50 + n + mortar, 1.0)

    write_png(OUT_DIR / "tex_radiant_stone.png", 256, 256, rad_stone_px)

    # 3. Dire volcanic obsidian & ember cracks
    def dire_obs_px(u, v, x, y):
        n = math.sin(u * 34.0 + v * 19.0) * 0.06 + (hash_noise(x, y) - 0.5) * 0.05
        if v < 0.32:
            glow = 0.82 + 0.18 * math.sin(u * math.tau * 5.0)
            return (1.0 * glow, 0.26 * glow, 0.10 * glow, 1.0)
        crack = math.exp(-abs(math.sin(u * 14.0 + math.sin(v * 10.0) * 1.4)) * 18.0)
        r = 0.22 + n + crack * 0.68
        g = 0.17 + n * 0.7 + crack * 0.18
        b = 0.20 + n * 0.7 + crack * 0.04
        return (r, g, b, 1.0)

    write_png(OUT_DIR / "tex_dire_obsidian.png", 256, 256, dire_obs_px)

    # 4. Radiant & Dire Trees (v < 0.50 = canopy, v >= 0.50 = bark with flipV=True!)
    # Remember with flipV=True: OBJ v=0 maps to bottom of PNG (y=height-1, script v=1.0); OBJ v=1 maps to top of PNG (script v=0.0)!
    def tree_rad_px(u, v, x, y):
        n = (hash_noise(x, y) - 0.5) * 0.08 + math.sin(u * 26.0) * math.cos(v * 26.0) * 0.06
        if v < 0.52:  # OBJ v > 0.48 -> Canopy leaves
            leaf = math.sin(u * 45.0 + v * 35.0) * 0.07
            return (0.14 + n + leaf * 0.5, 0.52 + n + leaf, 0.18 + n * 0.6, 1.0)
        # Bark
        streak = math.sin(u * 60.0 + math.sin(v * 18.0)) * 0.06
        return (0.36 + n + streak, 0.23 + n * 0.7 + streak * 0.6, 0.12 + n * 0.4, 1.0)

    def tree_dire_px(u, v, x, y):
        n = (hash_noise(x, y) - 0.5) * 0.08 + math.sin(u * 28.0) * math.cos(v * 24.0) * 0.05
        if v < 0.52:  # Dire blight pine needles
            needle = math.sin(u * 55.0 + v * 40.0) * 0.06
            return (0.38 + n + needle, 0.14 + n * 0.5, 0.18 + n * 0.5, 1.0)
        streak = math.sin(u * 56.0 + math.cos(v * 16.0)) * 0.05
        return (0.20 + n + streak, 0.14 + n + streak, 0.15 + n + streak, 1.0)

    write_png(OUT_DIR / "tex_tree_radiant.png", 256, 256, tree_rad_px)
    write_png(OUT_DIR / "tex_tree_dire.png", 256, 256, tree_dire_px)

    # 5. Roshan Demon Boss texture
    def roshan_px(u, v, x, y):
        n = (hash_noise(x, y) - 0.5) * 0.07
        scale = math.sin(u * 38.0) * math.sin(v * 38.0) * 0.05
        if v < 0.30:  # Horns & spikes (bone ivory -> dark base)
            return (0.90 + n, 0.80 + n, 0.62 + n, 1.0)
        if v < 0.52:  # Fiery magma chest core & eyes
            glow = 0.7 + 0.3 * math.sin(u * math.tau * 3.0)
            return (0.98 * glow, 0.38 * glow, 0.08, 1.0)
        return (0.44 + n + scale, 0.14 + n * 0.6 + scale, 0.15 + n * 0.6, 1.0)

    write_png(OUT_DIR / "tex_roshan.png", 256, 256, roshan_px)

    # 6. Weapons & Projectiles (flipV=True: v_obj 0.72..0.95 -> PNG v 0.05..0.28 hilt, v_obj 0.05..0.32 -> PNG v 0.68..0.95 glowing tip)
    def weapon_px(u, v, x, y):
        n = (hash_noise(x, y) - 0.5) * 0.04
        if v > 0.66:  # Glowing energy / arcane tip (v_obj 0.05..0.34)
            return (0.97 + n, 0.95 + n, 0.92 + n, 1.0)
        if v < 0.30:  # Dark wrapped leather hilt / wood stock (v_obj 0.70..0.95)
            return (0.38 + n, 0.25 + n, 0.16 + n, 1.0)
        # Gleaming Damascus blade steel (v_obj 0.34..0.70)
        wave = math.sin(u * 40.0 + v * 25.0) * 0.05
        return (0.88 + wave + n, 0.90 + wave + n, 0.94 + wave + n, 1.0)

    write_png(OUT_DIR / "tex_weapons.png", 256, 256, weapon_px)


# ---------------- 3D Model Definitions ----------------

def build_hero_models():
    models = {}

    # 1. Hero Torso Agile (Juggernaut, PA, Anti-Mage, Sniper)
    m = ObjBuilder()
    # Sculpted breastplate + tapered waist + armored tassets
    m.add_lathe(
        [(11.5, -18.0), (13.0, -10.0), (10.5, -2.0), (13.8, 7.0), (14.2, 13.0), (10.0, 17.5), (5.5, 19.5)],
        segs=18, oval_xz=(1.12, 0.84), uv_rect=(0.05, 0.05, 0.95, 0.55)
    )
    # Shoulder pauldrons
    m.add_superellipsoid(8.5, 6.2, 9.2, 14, 10, 0.75, 0.85, center=(-14.5, 11.5, 0.0), rot=(0, 0, 18), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_superellipsoid(8.5, 6.2, 9.2, 14, 10, 0.75, 0.85, center=(14.5, 11.5, 0.0), rot=(0, 0, -18), uv_rect=(0.05, 0.68, 0.95, 0.96))
    # Chest sash / emblem
    m.add_superellipsoid(7.5, 7.0, 3.2, 12, 8, 0.7, 0.7, center=(0.0, 7.5, 10.2), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_torso_agile"] = m

    # 2. Hero Torso Brute (Axe, Pudge, Earthshaker)
    m = ObjBuilder()
    m.add_lathe(
        [(15.5, -19.0), (17.5, -10.0), (16.0, -1.0), (19.5, 8.0), (18.8, 15.0), (12.0, 19.5), (7.0, 21.0)],
        segs=18, oval_xz=(1.16, 0.94), uv_rect=(0.05, 0.05, 0.95, 0.55)
    )
    m.add_superellipsoid(11.0, 8.0, 11.5, 14, 10, 0.80, 0.80, center=(-19.5, 12.5, 0.0), rot=(0, 0, 22), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_superellipsoid(11.0, 8.0, 11.5, 14, 10, 0.80, 0.80, center=(19.5, 12.5, 0.0), rot=(0, 0, -22), uv_rect=(0.05, 0.68, 0.95, 0.96))
    # Belt buckle
    m.add_box(16.0, 6.0, 21.0, center=(0.0, -7.0, 0.0), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_torso_brute"] = m

    # 3. Hero Torso Mage (Invoker, CM, Zeus, Void Spirit, Chronos)
    m = ObjBuilder()
    # Flared battle robe skirt + sculpted chestplate + arcane high collar
    m.add_lathe(
        [(17.0, -24.0), (14.5, -13.0), (10.2, -3.0), (13.2, 8.0), (13.6, 14.0), (9.5, 18.0), (6.0, 20.0)],
        segs=18, oval_xz=(1.08, 0.90), uv_rect=(0.05, 0.05, 0.95, 0.55)
    )
    # High arcane collar behind neck
    m.add_lathe(
        [(10.0, 14.0), (13.5, 22.0), (14.5, 27.0)],
        segs=14, oval_xz=(1.1, 0.95), uv_rect=(0.05, 0.68, 0.95, 0.96), cap_ends=False
    )
    m.add_superellipsoid(8.2, 5.5, 8.8, 12, 8, 0.7, 0.8, center=(-13.8, 11.8, 0.0), rot=(0, 0, 14), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_superellipsoid(8.2, 5.5, 8.8, 12, 8, 0.7, 0.8, center=(13.8, 11.8, 0.0), rot=(0, 0, -14), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_torso_mage"] = m

    # 4. Articulated Limb (pivot at y=0, extends down to y=-1.0)
    m = ObjBuilder()
    m.add_lathe(
        [(0.42, -1.0), (0.48, -0.82), (0.38, -0.52), (0.44, -0.25), (0.46, 0.0)],
        segs=12, uv_rect=(0.05, 0.05, 0.95, 0.55)
    )
    models["hero_limb"] = m

    # 5. Flowing Cape (pivot at y=0 at shoulders, hangs down to y=-36 along -Z tilt)
    m = ObjBuilder()
    for seg_i in range(5):
        t0 = seg_i / 5.0
        t1 = (seg_i + 1) / 5.0
        y0, y1 = -t0 * 35.0, -t1 * 35.0
        w0, w1 = lerp(10.0, 17.5, t0), lerp(10.0, 17.5, t1)
        z0, z1 = -t0 * 10.0, -t1 * 10.0
        m.add_flat_quad((-w0, y0, z0), (w0, y0, z0), (w1, y1, z1), (-w1, y1, z1), uv_rect=(0.05, 0.68, 0.95, 0.96))
        m.add_flat_quad((w0, y0, z0 - 1.5), (-w0, y0, z0 - 1.5), (-w1, y1, z1 - 1.5), (w1, y1, z1 - 1.5), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_cape"] = m

    # 6. Hero Heads (distinct Dota silhouettes!)
    # Samurai mask + headband crest
    m = ObjBuilder()
    m.add_superellipsoid(9.2, 10.4, 9.8, 16, 10, 0.95, 0.95, center=(0, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_superellipsoid(8.5, 9.2, 4.2, 12, 8, 0.65, 0.75, center=(0, -0.5, 7.6), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_torus(9.6, 2.2, 16, 6, center=(0, 5.5, 0), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_head_samurai"] = m

    # Horned War Helm (Axe / Earthshaker)
    m = ObjBuilder()
    m.add_superellipsoid(10.2, 10.8, 10.6, 16, 10, 0.9, 0.9, center=(0, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    for side in (-1.0, 1.0):
        m.add_swept_profile(
            [(side * 7.0, 6.0, 1.0), (side * 14.0, 12.0, 2.5), (side * 17.5, 20.5, 5.5), (side * 15.0, 26.0, 9.0)],
            [(3.6, 3.6), (2.9, 2.9), (1.8, 1.8), (0.3, 0.3)], segs=8, uv_rect=(0.05, 0.68, 0.95, 0.96)
        )
    models["hero_head_horned"] = m

    # Assassin / Marksman Hood (PA / Sniper)
    m = ObjBuilder()
    m.add_superellipsoid(9.0, 10.2, 9.5, 14, 10, 0.95, 0.95, center=(0, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_lathe([(12.5, -2.0), (12.0, 7.0), (6.0, 14.0), (0.4, 17.5)], segs=14, center=(0, 2, -1.5), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_head_hood"] = m

    # Mage / Celestial Crown (Invoker / CM / Zeus / Void Spirit / Chronos)
    m = ObjBuilder()
    m.add_superellipsoid(9.2, 10.4, 9.6, 16, 10, 0.95, 0.95, center=(0, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_crystal(10.5, 10.5, 14.0, 2.0, segs=6, center=(0, 9.0, 0), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_torus(11.0, 1.6, 16, 6, center=(0, 6.5, 0), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_head_mage"] = m

    # Butcher Brute Head (Pudge)
    m = ObjBuilder()
    m.add_superellipsoid(11.5, 11.2, 11.2, 16, 10, 0.85, 0.9, center=(0, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_box(13.0, 5.0, 6.0, center=(0, -5.0, 7.5), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["hero_head_butcher"] = m

    return models


def build_weapon_models():
    models = {}

    # Curved Samurai Katana (origin at grip, tip extends along +Z to 54)
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -10), (0, 0, 0)], [(1.8, 1.8), (2.0, 2.0)], segs=8, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_box(9.0, 9.0, 2.2, center=(0, 0, 1.0), uv_rect=(0.05, 0.05, 0.95, 0.30))
    m.add_swept_profile(
        [(0, 0, 2), (0.8, 0, 16), (2.6, 0, 32), (5.2, 0, 46), (7.6, 0, 55)],
        [(1.2, 3.6), (1.1, 3.5), (0.95, 3.1), (0.65, 2.2), (0.15, 0.3)],
        segs=6, uv_rect=(0.05, 0.36, 0.95, 0.66)
    )
    models["weapon_katana"] = m

    # Red Mist Twin-Crescent Battleaxe
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -10), (0, 0, 48)], [(2.2, 2.2), (2.4, 2.4)], segs=8, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_box(28.0, 4.0, 18.0, center=(0, 0, 36), uv_rect=(0.05, 0.36, 0.95, 0.66))
    m.add_crystal(18.0, 13.0, 3.0, 3.0, segs=4, center=(0, 0, 36), rot=(0, 45, 0), uv_rect=(0.05, 0.05, 0.95, 0.30))
    models["weapon_axe"] = m

    # Curved Warglaive (PA / AM)
    m = ObjBuilder()
    m.add_swept_profile(
        [(-4, 0, -24), (-1, 0, -10), (2, 0, 0), (1, 0, 12), (-4, 0, 26)],
        [(0.2, 0.4), (1.4, 4.0), (1.8, 4.5), (1.4, 4.0), (0.2, 0.4)],
        segs=6, uv_rect=(0.05, 0.05, 0.95, 0.32)
    )
    models["weapon_glaive"] = m

    # Butcher Cleaver (Pudge)
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -8), (0, 0, 10)], [(2.1, 2.1), (2.2, 2.2)], segs=8, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_box(3.5, 15.0, 30.0, center=(0, 4.5, 24.0), uv_rect=(0.05, 0.36, 0.95, 0.66))
    models["weapon_cleaver"] = m

    # Earthshaker Stone Totem
    m = ObjBuilder()
    m.add_lathe(
        [(4.5, -10), (6.5, 10), (8.2, 32), (8.8, 48), (6.5, 54)],
        segs=10, rot=(90, 0, 0), uv_rect=(0.05, 0.36, 0.95, 0.95)
    )
    m.add_torus(8.8, 1.5, 14, 6, center=(0, 0, 38), rot=(90, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.30))
    models["weapon_totem"] = m

    # Ornate Arcane Staff (Invoker / CM)
    m = ObjBuilder()
    m.add_swept_profile([(0, -24, 0), (0, 0, 0), (0, 28, 0), (2, 38, 4)],
                        [(2.0, 2.0), (2.2, 2.2), (2.5, 2.5), (1.8, 1.8)], segs=8, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_crystal(6.5, 6.5, 11.0, 9.0, segs=6, center=(2, 46, 4), uv_rect=(0.05, 0.05, 0.95, 0.30))
    m.add_torus(8.5, 1.2, 14, 6, center=(2, 45, 4), rot=(25, 0, 25), uv_rect=(0.05, 0.05, 0.95, 0.30))
    models["weapon_staff"] = m

    # Sniper Precision Rifle + Scope
    m = ObjBuilder()
    m.add_box(5.0, 8.0, 22.0, center=(0, 0, 6.0), uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_swept_profile([(0, 2.5, 14), (0, 2.5, 56)], [(1.7, 1.7), (1.3, 1.3)], segs=10, uv_rect=(0.05, 0.36, 0.95, 0.66))
    m.add_swept_profile([(0, 7.2, 8), (0, 7.2, 28)], [(2.2, 2.2), (2.4, 2.4)], segs=10, uv_rect=(0.05, 0.05, 0.95, 0.30))
    models["weapon_rifle"] = m

    # Zeus Thunderbolt
    m = ObjBuilder()
    m.add_swept_profile(
        [(0, 0, 0), (4, 2, 14), (-4, -1, 28), (3, 1, 42), (0, 0, 54)],
        [(2.6, 2.6), (2.4, 2.4), (2.0, 2.0), (1.4, 1.4), (0.2, 0.2)], segs=6, uv_rect=(0.05, 0.05, 0.95, 0.32)
    )
    models["weapon_bolt"] = m

    # Void Spirit / Chronos Halberd
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -14), (0, 0, 44)], [(1.9, 1.9), (2.1, 2.1)], segs=8, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_swept_profile(
        [(0, 0, 32), (4.5, 0, 42), (3.0, 0, 52), (0, 0, 60)],
        [(1.5, 4.5), (1.8, 6.5), (1.2, 5.0), (0.2, 0.4)], segs=6, uv_rect=(0.05, 0.05, 0.95, 0.32)
    )
    models["weapon_halberd"] = m

    # 3D Crescent Weapon Slash Ribbon Trail (flat/tapered sweeping crescent around Y-axis, radius ~ 1.0)
    m = ObjBuilder()
    segs = 18
    span = math.radians(150.0)
    pts = []
    uvs = []
    for r_i, (r_off, y_off) in enumerate([(0.56, 0.0), (0.88, 1.2), (1.0, 0.0), (0.88, -1.2), (0.56, 0.0)]):
        row_p = []
        row_u = []
        for s in range(segs + 1):
            t = s / segs
            a = -span * 0.5 + t * span
            taper = math.sin(t * math.pi)
            rr = lerp(0.95, r_off, taper)
            row_p.append((math.sin(a) * rr, y_off * taper * 0.04, math.cos(a) * rr))
            row_u.append((t, r_i / 4.0))
        pts.append(row_p)
        uvs.append(row_u)
    m.add_smooth_grid(pts, uvs, closed_u=False)
    models["weapon_slash_ribbon"] = m

    return models


def build_creep_and_boss_models():
    models = {}

    # Melee Creep (armored soldier with shield & helm)
    m = ObjBuilder()
    m.add_lathe([(7, 0), (9, 12), (11, 24), (10, 33), (6, 36)], segs=12, oval_xz=(1.1, 0.9), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_superellipsoid(7.5, 8.0, 7.5, 12, 8, 0.9, 0.9, center=(0, 42, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_crystal(8.5, 8.5, 10.0, 1.0, segs=6, center=(0, 48, 0), uv_rect=(0.05, 0.68, 0.95, 0.96))
    # Shield on left side (-X) & Spear/Blade on right (+X)
    m.add_box(3.5, 18.0, 14.0, center=(-12.5, 25.0, 4.0), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_swept_profile([(12, 22, -4), (12, 28, 22)], [(1.5, 3.0), (0.3, 0.5)], segs=6, uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["creep_melee"] = m

    # Ranged Creep (robed acolyte + arcane staff)
    m = ObjBuilder()
    m.add_lathe([(12.5, 0), (10.5, 14), (8.2, 28), (9.5, 35), (6.0, 38)], segs=12, uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_superellipsoid(7.0, 7.5, 7.5, 12, 8, 0.95, 0.95, center=(0, 43, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_crystal(9.0, 9.0, 12.0, 1.0, segs=6, center=(0, 48, -1), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_swept_profile([(11, 10, 4), (12, 46, 8)], [(1.8, 1.8), (1.6, 1.6)], segs=8, uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_crystal(5.5, 5.5, 7.5, 6.0, segs=6, center=(12, 52, 8), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["creep_ranged"] = m

    # Siege Catapult
    m = ObjBuilder()
    m.add_box(24.0, 10.0, 34.0, center=(0, 13, 0), uv_rect=(0.05, 0.05, 0.95, 0.55))
    for wx in (-14.5, 14.5):
        for wz in (-11.0, 11.0):
            m.add_lathe([(7.5, -2.5), (7.5, 2.5)], segs=12, center=(wx, 8.0, wz), rot=(0, 0, 90), uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_swept_profile([(0, 18, -6), (0, 38, -14)], [(2.6, 2.6), (2.2, 2.2)], segs=6, uv_rect=(0.05, 0.68, 0.95, 0.96))
    m.add_superellipsoid(6.0, 6.0, 6.0, 10, 8, 1.0, 1.0, center=(0, 40, -14), uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["creep_siege"] = m

    # Neutral Jungle Prowler Beast
    m = ObjBuilder()
    m.add_lathe([(9, 0), (14, 14), (16, 26), (13, 36), (8, 40)], segs=12, oval_xz=(1.2, 0.95), uv_rect=(0.05, 0.05, 0.95, 0.55))
    m.add_superellipsoid(11, 10, 11, 12, 8, 0.85, 0.85, center=(0, 46, 3), uv_rect=(0.05, 0.05, 0.95, 0.55))
    for side in (-1, 1):
        m.add_swept_profile([(side * 6, 50, 4), (side * 12, 58, 8)], [(3.0, 3.0), (0.3, 0.3)], segs=6, uv_rect=(0.05, 0.68, 0.95, 0.96))
    models["creep_neutral"] = m

    # Roshan the Immortal Behemoth
    m = ObjBuilder()
    # Heavy demon legs & broad torso
    for sx in (-17, 17):
        m.add_lathe([(10, 0), (12, 16), (11, 34)], segs=10, center=(sx, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.45))
    m.add_lathe(
        [(22, 26), (28, 44), (33, 64), (28, 80), (16, 88)],
        segs=16, oval_xz=(1.22, 0.88), uv_rect=(0.05, 0.05, 0.95, 0.45)
    )
    # Molten ember chest core (maps to fiery UV band v in 0.48..0.68)
    m.add_crystal(13, 10, 16, 14, segs=6, center=(0, 62, 24), uv_rect=(0.05, 0.48, 0.95, 0.68))
    # Armored arms
    for sx in (-1, 1):
        m.add_swept_profile(
            [(sx * 32, 74, 0), (sx * 42, 52, 8), (sx * 38, 32, 16)],
            [(10, 10), (8.5, 8.5), (9.5, 9.5)], segs=8, uv_rect=(0.05, 0.05, 0.95, 0.45)
        )
    # Horned skull
    m.add_superellipsoid(18, 17, 19, 14, 10, 0.85, 0.85, center=(0, 96, 6), uv_rect=(0.05, 0.05, 0.95, 0.45))
    for sx in (-1, 1):
        m.add_swept_profile(
            [(sx * 11, 102, 4), (sx * 22, 114, 6), (sx * 28, 128, 14), (sx * 22, 138, 22)],
            [(6.0, 6.0), (4.5, 4.5), (2.6, 2.6), (0.4, 0.4)], segs=8, uv_rect=(0.05, 0.72, 0.95, 0.96)
        )
        # Spread demon wings
        m.add_flat_quad(
            (sx * 14, 78, -14), (sx * 64, 106, -34), (sx * 54, 54, -28), (sx * 14, 46, -12),
            uv_rect=(0.05, 0.05, 0.95, 0.45)
        )
    models["roshan"] = m

    return models


def build_structure_and_nature_models():
    models = {}

    # Radiant Tower (tiered marble spire + buttresses + floating crystal)
    m = ObjBuilder()
    m.add_lathe(
        [(44, 0), (44, 12), (34, 18), (32, 26), (25, 34), (23, 78), (28, 88), (20, 96)],
        segs=16, uv_rect=(0.05, 0.05, 0.95, 0.65)
    )
    for k in range(4):
        a = k * 90 + 45
        rad = math.radians(a)
        m.add_box(13, 52, 15, center=(math.sin(rad) * 30, 34, math.cos(rad) * 30), rot=(0, a, 0), uv_rect=(0.05, 0.05, 0.95, 0.65))
    m.add_torus(22, 4.0, 18, 8, center=(0, 96, 0), uv_rect=(0.05, 0.05, 0.95, 0.20))
    m.add_crystal(18, 18, 28, 24, segs=6, center=(0, 126, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
    models["tower_radiant"] = m

    # Dire Tower (spiked obsidian citadel spire + glowing crimson crystal)
    m = ObjBuilder()
    m.add_lathe(
        [(44, 0), (42, 12), (32, 20), (24, 40), (22, 82), (28, 92), (18, 98)],
        segs=12, uv_rect=(0.05, 0.05, 0.95, 0.65)
    )
    for k in range(4):
        a = k * 90 + 45
        rad = math.radians(a)
        bx, bz = math.sin(rad) * 31, math.cos(rad) * 31
        m.add_box(14, 54, 14, center=(bx, 34, bz), rot=(0, a, 0), uv_rect=(0.05, 0.05, 0.95, 0.65))
        m.add_crystal(7, 7, 22, 2, segs=4, center=(bx * 1.1, 66, bz * 1.1), rot=(0, a, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
    m.add_crystal(18, 18, 30, 22, segs=6, center=(0, 128, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
    models["tower_dire"] = m

    # Radiant & Dire Barracks
    for key in ("barracks_radiant", "barracks_dire"):
        m = ObjBuilder()
        m.add_box(70, 44, 64, center=(0, 22, 0), uv_rect=(0.05, 0.05, 0.95, 0.65))
        m.add_crystal(52, 48, 30, 2, segs=4, center=(0, 46, 0), rot=(0, 45, 0), uv_rect=(0.05, 0.05, 0.95, 0.65))
        m.add_crystal(14, 14, 18, 14, segs=6, center=(0, 74, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
        models[key] = m

    # Radiant Ancient
    m = ObjBuilder()
    m.add_lathe([(74, 0), (74, 18), (58, 28), (54, 42), (40, 52)], segs=16, uv_rect=(0.05, 0.05, 0.95, 0.65))
    m.add_crystal(36, 36, 62, 52, segs=6, center=(0, 112, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
    m.add_torus(46, 4.5, 22, 8, center=(0, 108, 0), rot=(25, 0, 15), uv_rect=(0.05, 0.05, 0.95, 0.20))
    for k in range(4):
        a = k * 90 + 45
        rad = math.radians(a)
        m.add_crystal(12, 12, 34, 6, segs=5, center=(math.sin(rad) * 56, 56, math.cos(rad) * 56), uv_rect=(0.05, 0.70, 0.95, 0.98))
    models["ancient_radiant"] = m

    # Dire Ancient
    m = ObjBuilder()
    m.add_lathe([(74, 0), (72, 18), (56, 32), (48, 48)], segs=12, uv_rect=(0.05, 0.05, 0.95, 0.65))
    m.add_crystal(38, 38, 64, 52, segs=6, center=(0, 114, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
    for k in range(5):
        a = k * 72
        rad = math.radians(a)
        m.add_swept_profile(
            [(math.sin(rad) * 52, 24, math.cos(rad) * 52),
             (math.sin(rad) * 60, 74, math.cos(rad) * 60),
             (math.sin(rad) * 44, 118, math.cos(rad) * 44)],
            [(9, 9), (6.5, 6.5), (1.2, 1.2)], segs=6, uv_rect=(0.05, 0.05, 0.95, 0.65)
        )
    models["ancient_dire"] = m

    # Radiant Tree (trunk v in 0.02..0.44, canopy v in 0.56..0.96 -> OBJ v=0..0.44 is bark!)
    m = ObjBuilder()
    m.add_lathe([(12, 0), (7.5, 14), (6.0, 42), (4.5, 58)], segs=10, uv_rect=(0.05, 0.02, 0.95, 0.44))
    m.add_superellipsoid(27, 20, 27, 12, 8, 0.95, 0.95, center=(0, 62, 0), uv_rect=(0.05, 0.56, 0.95, 0.96))
    m.add_superellipsoid(19, 16, 19, 10, 7, 0.95, 0.95, center=(-11, 70, 8), uv_rect=(0.05, 0.56, 0.95, 0.96))
    m.add_superellipsoid(18, 16, 18, 10, 7, 0.95, 0.95, center=(10, 73, -7), uv_rect=(0.05, 0.56, 0.95, 0.96))
    m.add_superellipsoid(16, 15, 16, 10, 7, 0.95, 0.95, center=(0, 84, 0), uv_rect=(0.05, 0.56, 0.95, 0.96))
    models["tree_radiant"] = m

    # Dire Blight Pine
    m = ObjBuilder()
    m.add_lathe([(11, 0), (7.5, 14), (5.5, 44), (3.5, 64)], segs=10, uv_rect=(0.05, 0.02, 0.95, 0.44))
    m.add_lathe([(27, 38), (22, 54), (6, 66)], segs=10, uv_rect=(0.05, 0.56, 0.95, 0.96))
    m.add_lathe([(21, 58), (16, 74), (4, 86)], segs=10, uv_rect=(0.05, 0.56, 0.95, 0.96))
    m.add_lathe([(14, 78), (9, 92), (0.5, 106)], segs=10, uv_rect=(0.05, 0.56, 0.95, 0.96))
    models["tree_dire"] = m

    # Ward / Totem
    m = ObjBuilder()
    m.add_lathe([(6, 0), (5, 28), (7, 32)], segs=10, uv_rect=(0.05, 0.05, 0.95, 0.65))
    m.add_crystal(9, 9, 14, 10, segs=6, center=(0, 42, 0), uv_rect=(0.05, 0.70, 0.95, 0.98))
    models["ward_sentinel"] = m

    return models


def build_projectile_models():
    models = {}

    # 3D Fletched Arrow / Bolt (points +Z)
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -14), (0, 0, 10)], [(1.4, 1.4), (1.4, 1.4)], segs=6, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_crystal(4.2, 4.2, 12.0, 2.0, segs=4, center=(0, 0, 10), rot=(90, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.30))
    m.add_box(8.0, 0.9, 9.0, center=(0, 0, -10), uv_rect=(0.05, 0.36, 0.95, 0.66))
    m.add_box(0.9, 8.0, 9.0, center=(0, 0, -10), uv_rect=(0.05, 0.36, 0.95, 0.66))
    models["proj_arrow"] = m

    # Energy Orb / Meteor
    m = ObjBuilder()
    m.add_superellipsoid(1.0, 1.0, 1.0, 12, 8, 1.0, 1.0, uv_rect=(0.05, 0.05, 0.95, 0.32))
    models["proj_orb"] = m

    # Pudge Meat Hook
    m = ObjBuilder()
    m.add_swept_profile(
        [(0, 0, -14), (0, 0, 2), (7, 0, 14), (12, 0, 6), (8, 0, -2)],
        [(2.6, 2.6), (3.0, 3.0), (2.8, 2.8), (2.2, 2.2), (0.4, 0.4)], segs=8, uv_rect=(0.05, 0.36, 0.95, 0.66)
    )
    models["proj_hook"] = m

    # PA Stifling Dagger
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -12), (0, 0, -3)], [(1.5, 1.5), (1.6, 1.6)], segs=6, uv_rect=(0.05, 0.72, 0.95, 0.95))
    m.add_crystal(4.0, 1.6, 16.0, 3.0, segs=4, center=(0, 0, 0), rot=(90, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.32))
    models["proj_dagger"] = m

    # Chronos Phase Spear / Wave Lance
    m = ObjBuilder()
    m.add_swept_profile([(0, 0, -18), (0, 0, 12)], [(1.6, 1.6), (2.0, 2.0)], segs=8, uv_rect=(0.05, 0.36, 0.95, 0.66))
    m.add_crystal(5.2, 5.2, 16.0, 3.0, segs=6, center=(0, 0, 12), rot=(90, 0, 0), uv_rect=(0.05, 0.05, 0.95, 0.32))
    models["proj_spear"] = m

    return models


def verify_outward_winding():
    def signed_vol(b: ObjBuilder):
        vol = 0.0
        for (a, bb, c) in b.faces:
            p0 = b.positions[a[0] - 1]
            p1 = b.positions[bb[0] - 1]
            p2 = b.positions[c[0] - 1]
            vol += (
                p0[0] * (p1[1] * p2[2] - p1[2] * p2[1])
                + p0[1] * (p1[2] * p2[0] - p1[0] * p2[2])
                + p0[2] * (p1[0] * p2[1] - p1[1] * p2[0])
            ) / 6.0
        return vol

    t_box = ObjBuilder(); t_box.add_box(10, 10, 10)
    t_lat = ObjBuilder(); t_lat.add_lathe([(5, -5), (5, 5)], segs=12)
    t_sup = ObjBuilder(); t_sup.add_superellipsoid(5, 5, 5, 12, 8)
    t_tor = ObjBuilder(); t_tor.add_torus(10, 2, 16, 8)
    t_cry = ObjBuilder(); t_cry.add_crystal(5, 5, 5, 5, segs=6)
    t_swp = ObjBuilder(); t_swp.add_swept_profile([(0, 0, -5), (0, 0, 5)], [(3, 3), (3, 3)], segs=8)

    for label, bld in [("box", t_box), ("lathe", t_lat), ("superellipsoid", t_sup),
                       ("torus", t_tor), ("crystal", t_cry), ("swept", t_swp)]:
        sv = signed_vol(bld)
        assert sv > 0.0, f"Primitive {label} has inverted winding (signed_vol={sv})"
    print("Winding verification PASSED (all primitives outward CCW)!")


def main():
    verify_outward_winding()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    generate_textures()

    all_models = {}
    all_models.update(build_hero_models())
    all_models.update(build_weapon_models())
    all_models.update(build_creep_and_boss_models())
    all_models.update(build_structure_and_nature_models())
    all_models.update(build_projectile_models())

    total_tris = 0
    for name, builder in sorted(all_models.items()):
        verts, tris = builder.write(OUT_DIR / f"{name}.obj")
        total_tris += tris
        assert verts < 32767, f"Model {name} exceeds 16-bit vertex index cap: {verts}"
        print(f"Generated {name}.obj: {verts} verts, {tris} tris")
    print(f"Done! {len(all_models)} OBJ models ({total_tris} total tris) + 7 UV PNG textures in {OUT_DIR}")


if __name__ == "__main__":
    main()
