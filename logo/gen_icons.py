#!/usr/bin/env python3
"""
Generate Android vector drawable icon files from check.svg.

Output:
  ../app/src/main/res/drawable/ic_launcher_foreground.xml
  ../app/src/main/res/drawable/ic_launcher_background.xml
  ../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  ../app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
  ../app/src/main/res/drawable/ic_notification.xml
"""

import re, os, math

# ── Affine transform helpers ─────────────────────────────────────────────────

def pt(x, y, m):
    """Apply matrix m=(a,b,c,d,e,f) to point (x,y)."""
    a,b,c,d,e,f = m
    return (a*x + c*y + e, b*x + d*y + f)

# Outer group translate
TX, TY = -164.987574, -300.255362

# Combined transforms (inner matrix + outer translate)
# Group 1: matrix(1.150673,0.664341,-1.726534,2.990444,327.653508,-1183.099367)
M1 = (1.150673, 0.664341, -1.726534, 2.990444,
      327.653508 + TX, -1183.099367 + TY)
# Group 2: matrix(1.150673,0.664341,-1.726534,2.990444,507.653508,-1183.099367)
M2 = (1.150673, 0.664341, -1.726534, 2.990444,
      507.653508 + TX, -1183.099367 + TY)

# ── SVG path parser ──────────────────────────────────────────────────────────

NUM = r'[-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?'

def parse_path(d):
    """Return list of ('CMD', [coords...]) where coords are (x,y) tuples."""
    tokens = re.findall(r'[MLCZmlcz]|' + NUM, d)
    cmds = []
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if not re.match(r'[MLCZmlcz]', t):
            i += 1
            continue
        cmd = t.upper()
        i += 1
        if cmd == 'Z':
            cmds.append(('Z', []))
            continue
        # Collect all numbers belonging to this command (implicit repetition)
        nums = []
        while i < len(tokens) and not re.match(r'[MLCZmlcz]', tokens[i]):
            nums.append(float(tokens[i]))
            i += 1
        if cmd == 'M':
            for j in range(0, len(nums), 2):
                cmds.append(('M', [(nums[j], nums[j+1])]))
        elif cmd == 'L':
            for j in range(0, len(nums), 2):
                cmds.append(('L', [(nums[j], nums[j+1])]))
        elif cmd == 'C':
            for j in range(0, len(nums), 6):
                cmds.append(('C', [(nums[j],nums[j+1]),
                                   (nums[j+2],nums[j+3]),
                                   (nums[j+4],nums[j+5])]))
    return cmds

def transform_cmds(cmds, m):
    return [(cmd, [pt(x,y,m) for x,y in coords]) for cmd,coords in cmds]

def bbox(cmds):
    xs = [x for _,coords in cmds for x,_ in coords]
    ys = [y for _,coords in cmds for _,y in coords]
    return min(xs), min(ys), max(xs), max(ys)

def path_str(cmds, dp=3):
    parts = []
    for cmd, coords in cmds:
        if cmd == 'Z':
            parts.append('Z')
        elif cmd == 'M':
            parts.append(f'M{coords[0][0]:.{dp}f},{coords[0][1]:.{dp}f}')
        elif cmd == 'L':
            parts.append(f'L{coords[0][0]:.{dp}f},{coords[0][1]:.{dp}f}')
        elif cmd == 'C':
            body = ' '.join(f'{x:.{dp}f},{y:.{dp}f}' for x,y in coords)
            parts.append(f'C{body}')
    return ' '.join(parts)

def scale_cmds(cmds, sx, sy, dx, dy):
    return [(cmd, [(x*sx+dx, y*sy+dy) for x,y in coords]) for cmd,coords in cmds]

# ── Raw path data (from check.svg) ──────────────────────────────────────────

# Group 1 – leftmost leg of N
G1P1_D = (
    "M691.818,462.141 L691.818,489.987 "
    "C691.818,494.36 682.592,497.91 671.228,497.91 "
    "L630.048,497.91 "
    "C618.684,497.91 609.458,494.36 609.458,489.987 "
    "L609.458,453.233 "
    "C611.254,453.689 613.267,454.043 615.457,454.269 "
    "L691.818,462.141 Z"
)
# G1P2 outer+inner (evenodd shadow/depth on leg – same hue, skip for notif icon)
G1P2_D = (
    "M691.818,462.141 L691.818,489.987 "
    "C691.818,494.36 682.592,497.91 671.228,497.91 "
    "L630.048,497.91 "
    "C618.684,497.91 609.458,494.36 609.458,489.987 "
    "L609.458,453.233 "
    "C611.254,453.689 613.267,454.043 615.457,454.269 "
    "L691.818,462.141 Z"
    "M618.49,458.179 L618.49,489.987 "
    "C618.49,492.442 623.669,494.435 630.048,494.435 "
    "L671.228,494.435 "
    "C677.607,494.435 682.786,492.442 682.786,489.987 "
    "L682.786,464.808 L618.49,458.179 Z"
)
# Group 2 – N body + checkmark (dark green fill)
G2P1_D = (
    "M492.136,479.297 "
    "C484.931,477.47 481.221,474.01 483.575,470.629 "
    "L494.233,455.324 "
    "C497.175,451.1 508.474,448.59 519.451,449.722 "
    "L604.477,458.488 "
    "C598.788,423.342 604.457,373.418 604.457,373.418 "
    "L672.382,358.328 "
    "C672.382,358.328 675.311,424.234 685.311,466.822 "
    "C687.024,474.116 688.628,481.245 689.976,488.024 "
    "C690.94,492.872 683.499,497.455 670.519,497.154 "
    "C666.736,497.067 663.062,497.01 659.565,496.976 "
    "L498.135,480.332 "
    "C495.944,480.106 493.932,479.753 492.136,479.297 Z"
)
# Group 2 – black outline (outer = same as G2P1, inner = inset version)
# We use the inner subpath as a "gap mask" for the notification icon
G2P2_INNER_D = (
    "M497.105,476.396 "
    "C498.113,476.651 499.243,476.849 500.472,476.976 "
    "L660.872,493.513 "
    "C664.143,493.549 667.556,493.605 671.061,493.686 "
    "C678.325,493.853 681.51,491.002 680.971,488.289 "
    "C679.626,481.525 678.025,474.412 676.316,467.135 "
    "C668.095,432.125 664.639,381.395 663.661,364.278 "
    "L613.278,375.471 "
    "C612.322,384.89 608.483,427.332 613.491,458.272 "
    "C613.671,459.388 612.445,460.47 610.195,461.178 "
    "C607.945,461.886 604.947,462.134 602.139,461.845 "
    "L517.113,453.078 "
    "C510.951,452.443 504.608,453.852 502.957,456.223 "
    "L492.299,471.529 "
    "C490.977,473.427 493.06,475.37 497.105,476.396 Z"
)
G2P2_OUTER_D = G2P1_D  # same outer boundary as G2P1

# ── Transform to canvas space ────────────────────────────────────────────────

g1p1 = transform_cmds(parse_path(G1P1_D), M1)
g1p2 = transform_cmds(parse_path(G1P2_D), M1)
g2p1 = transform_cmds(parse_path(G2P1_D), M2)
g2p2_outer = transform_cmds(parse_path(G2P2_OUTER_D), M2)
g2p2_inner = transform_cmds(parse_path(G2P2_INNER_D), M2)

# Overall bounding box
all4 = g1p1 + g1p2 + g2p1
x0, y0, x1, y1 = bbox(all4)
W, H = x1 - x0, y1 - y0
print(f"Canvas bbox: ({x0:.2f},{y0:.2f}) to ({x1:.2f},{y1:.2f})  W={W:.2f} H={H:.2f}")

# ── Adaptive icon foreground (108dp, safe zone 72dp, margin 18dp) ────────────

ICON = 108.0
SAFE = 72.0
MARGIN = (ICON - SAFE) / 2   # 18

scale_fg = min(SAFE / W, SAFE / H)
sw, sh = W * scale_fg, H * scale_fg
dx_fg = MARGIN + (SAFE - sw) / 2 - x0 * scale_fg
dy_fg = MARGIN + (SAFE - sh) / 2 - y0 * scale_fg

def fg(cmds):
    return path_str(scale_cmds(cmds, scale_fg, scale_fg, dx_fg, dy_fg))

FG = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Leftmost leg of N -->
    <path
        android:fillColor="#195839"
        android:pathData="{fg(g1p1)}" />
    <path
        android:fillColor="#185839"
        android:fillType="evenOdd"
        android:pathData="{fg(g1p2)}" />

    <!-- N body + checkmark -->
    <path
        android:fillColor="#195839"
        android:pathData="{fg(g2p1)}" />

    <!-- Black outline (evenOdd: outer fills, inner cuts back to bg) -->
    <path
        android:fillColor="#000000"
        android:fillType="evenOdd"
        android:pathData="{fg(g2p2_outer)} {fg(g2p2_inner)}" />

</vector>"""

# ── Background ────────────────────────────────────────────────────────────────

BG = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#7BD7FF"
        android:pathData="M0,0 H108 V108 H0 Z" />
</vector>"""

# ── Adaptive icon descriptor ──────────────────────────────────────────────────

LAUNCHER = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>"""

# ── Notification icon ─────────────────────────────────────────────────────────
# White silhouette: green paths → white, black outline → transparent.
# The black outline in the original artwork provided the gap between the
# left leg (G1) and the N body (G2).  We recreate it by rendering G2 with the
# evenOdd compound path (outer shell minus the inset inner shell) so the
# border zone stays transparent — that border is what separates the leg from
# the body.

NOTIF_VP = 24.0
scale_n = min(NOTIF_VP / W, NOTIF_VP / H)
sw_n, sh_n = W * scale_n, H * scale_n
dx_n = (NOTIF_VP - sw_n) / 2 - x0 * scale_n
dy_n = (NOTIF_VP - sh_n) / 2 - y0 * scale_n

def nf(cmds):
    return path_str(scale_cmds(cmds, scale_n, scale_n, dx_n, dy_n), dp=4)

# For the notification icon the gap strategy:
# - G1P1: the left leg, rendered white.
# - G2 (N body + checkmark): use the evenOdd compound path of
#   [G2P1 outer] + [G2P2 inner] — the inner path "punches out" the border
#   zone just as the black outline did, leaving a transparent strip between
#   the leg and the body.
# Combined data: outer subpath first, then inner subpath (evenOdd toggles fill)
g2_notif_compound = f"{nf(g2p2_outer)} {nf(g2p2_inner)}"

NOTIF = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">

    <!-- Left leg of N (white) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="{nf(g1p1)}" />

    <!-- N body + checkmark: evenOdd compound punches out the border zone,
         recreating the gap that the black outline provided in the colour icon -->
    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="{g2_notif_compound}" />

</vector>"""

# ── Write files ───────────────────────────────────────────────────────────────

BASE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

files = {
    os.path.join(BASE, "drawable", "ic_launcher_foreground.xml"): FG,
    os.path.join(BASE, "drawable", "ic_launcher_background.xml"): BG,
    os.path.join(BASE, "mipmap-anydpi-v26", "ic_launcher.xml"): LAUNCHER,
    os.path.join(BASE, "mipmap-anydpi-v26", "ic_launcher_round.xml"): LAUNCHER,
    os.path.join(BASE, "drawable", "ic_notification.xml"): NOTIF,
}

for path, content in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Wrote {os.path.relpath(path)}")

print("Done.")
