#!/usr/bin/env python3
"""Generate Android vector drawables for Recap's icon set (matches the approved mockups)."""
import os

OUT = os.path.expanduser("~/workspace/recap-android/app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

def circle(cx, cy, r):
    return (f"M{cx-r},{cy} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0")

STROKE = {
    "ic_phone": "M22,16.92 v3 a2,2 0 0 1 -2.18,2 19.79,19.79 0 0 1 -8.63,-3.07 19.5,19.5 0 0 1 -6,-6 A19.79,19.79 0 0 1 2.12,4.18 2,2 0 0 1 4.11,2 h3 a2,2 0 0 1 2,1.72 c0.13,0.96 0.36,1.9 0.7,2.81 a2,2 0 0 1 -0.45,2.11 L8.09,9.91 a16,16 0 0 0 6,6 l1.27,-1.27 a2,2 0 0 1 2.11,-0.45 c0.91,0.34 1.85,0.57 2.81,0.7 A2,2 0 0 1 22,16.92 z",
    "ic_clock": circle(12, 12, 9) + " M12,7 v5 l3.5,2",
    "ic_user": "M20,21 v-2 a4,4 0 0 0 -4,-4 H8 a4,4 0 0 0 -4,4 v2 " + circle(12, 7, 4),
    "ic_voicemail": circle(7, 10, 3.4) + " " + circle(17, 10, 3.4) + " M7,13.4 v2.8 h10 v-2.8",
    "ic_mic": "M12,1 a3,3 0 0 0 -3,3 v8 a3,3 0 0 0 6,0 V4 a3,3 0 0 0 -3,-3 z M19,10 v2 a7,7 0 0 1 -14,0 v-2 M12,19 v4 M8,23 h8",
    "ic_search": circle(11, 11, 7) + " M21,21 l-4.5,-4.5",
    "ic_moon": "M21,12.79 A9,9 0 1 1 11.21,3 7,7 0 0 0 21,12.79 z",
    "ic_volume": "M11,5 L6,9 H2 v6 h4 l5,4 V5 z M19.07,4.93 a10,10 0 0 1 0,14.14 M15.54,8.46 a5,5 0 0 1 0,7.07",
    "ic_headphones": "M3,18 v-6 a9,9 0 0 1 18,0 v6 M21,19 a2,2 0 0 1 -2,2 h-1 a2,2 0 0 1 -2,-2 v-3 a2,2 0 0 1 2,-2 h3 z M3,19 a2,2 0 0 0 2,2 h1 a2,2 0 0 0 2,-2 v-3 a2,2 0 0 0 -2,-2 H3 z",
    "ic_file": "M14,2 H6 a2,2 0 0 0 -2,2 v16 a2,2 0 0 0 2,2 h12 a2,2 0 0 0 2,-2 V8 z M14,2 v6 h6 M16,13 H8 M16,17 H8",
    "ic_globe": circle(12, 12, 10) + " M2,12 h20 M12,2 a15.3,15.3 0 0 1 4,10 15.3,15.3 0 0 1 -4,10 15.3,15.3 0 0 1 -4,-10 15.3,15.3 0 0 1 4,-10 z",
    "ic_folder": "M22,19 a2,2 0 0 1 -2,2 H4 a2,2 0 0 1 -2,-2 V5 a2,2 0 0 1 2,-2 h5 l2,3 h9 a2,2 0 0 1 2,2 z",
    "ic_trash": "M3,6 h18 M19,6 v14 a2,2 0 0 1 -2,2 H7 a2,2 0 0 1 -2,-2 V6 M8,6 V4 a2,2 0 0 1 2,-2 h4 a2,2 0 0 1 2,2 v2 M10,11 v6 M14,11 v6",
    "ic_plus": "M12,5 v14 M5,12 h14",
    "ic_pause": "M7,4 h3.5 v16 h-3.5 z M13.5,4 H17 v16 h-3.5 z",
    "ic_pencil": "M17,3 a2.83,2.83 0 1 1 4,4 L7.5,20.5 2,22 l1.5,-5.5 L17,3 z",
    "ic_calendar": "M5,4 h14 a2,2 0 0 1 2,2 v14 a2,2 0 0 1 -2,2 H5 a2,2 0 0 1 -2,-2 V6 a2,2 0 0 1 2,-2 z M16,2 v4 M8,2 v4 M3,10 h18",
    "ic_share": circle(18, 5, 3) + " " + circle(6, 12, 3) + " " + circle(18, 19, 3) + " M8.6,13.5 l6.8,4 M15.4,6.5 l-6.8,4",
    "ic_check": "M4,12.5 l5,5 L20,6.5",
    "ic_block": circle(12, 12, 9) + " M5.8,5.8 l12.4,12.4",
    "ic_chevron": "M9,5 l7,7 -7,7",
    "ic_back": "M19,12 H5 M11,6 l-6,6 6,6",
    "ic_close": "M6,6 l12,12 M18,6 L6,18",
}

FILL = {
    "ic_keypad": " ".join(circle(x, y, 2.35) for y in (5.2, 12, 18.8) for x in (5.2, 12, 18.8)),
    "ic_sparkle": "M12,2.5 c0.7,4.8 2.3,6.8 7,7.5 -4.7,0.7 -6.3,2.7 -7,7.5 -0.7,-4.8 -2.3,-6.8 -7,-7.5 4.7,-0.7 6.3,-2.7 7,-7.5 z",
    "ic_play": "M8,5.5 v13 l11,-6.5 z",
    "ic_dot": circle(12, 12, 6.5),
}

TPL_S = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="{p}" android:fillColor="#00000000"
        android:strokeColor="#FF000000" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round"/>
</vector>
"""
TPL_F = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="{p}" android:fillColor="#FF000000"/>
</vector>
"""

for name, p in STROKE.items():
    open(os.path.join(OUT, name + ".xml"), "w").write(TPL_S.format(p=p))
for name, p in FILL.items():
    open(os.path.join(OUT, name + ".xml"), "w").write(TPL_F.format(p=p))
print("icons:", len(STROKE) + len(FILL))
