#!/usr/bin/env python3
"""Les progrès d'Ourlet, au format de chaque version (icône, dossier, fond de l'onglet).

Usage : python3 tools/advancements.py   (titres et descriptions : advancements.ourlet.<id>.* dans les langues)
Ils sont attribués en jeu par /advancement (fold/Progress.java), sauf la racine (trigger minecraft:tick).
"""
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADV = [  # id, parent, icône, cadre
    ("root", None, "ourlet:spatial_needle", "task"),
    ("sew", "root", "ourlet:spatial_needle", "task"),
    ("pass", "sew", "minecraft:feather", "task"),
    ("fling", "pass", "minecraft:firework_rocket", "goal"),
    ("far", "sew", "minecraft:compass", "challenge"),
    ("dimensions", "sew", "minecraft:obsidian", "goal"),
    ("relay", "sew", "minecraft:diamond_pickaxe", "task"),
    ("snip", "sew", "minecraft:shears", "task"),
    ("pocket", "sew", "ourlet:thimble", "task"),
]


def make(fmt):
    out = {}
    for aid, parent, icon, frame in ADV:
        d = {"display": {
            "icon": {"item": icon} if fmt == "1201" else {"id": icon},
            "title": {"translate": f"advancements.ourlet.{aid}.title"},
            "description": {"translate": f"advancements.ourlet.{aid}.description"},
            "frame": frame, "show_toast": aid != "root", "announce_to_chat": aid != "root", "hidden": False}}
        if aid == "root":
            d["display"]["background"] = ("ourlet:gui/advancements/linen" if fmt == "26"
                                          else "ourlet:textures/gui/advancements/linen.png")
            d["criteria"] = {"tick": {"trigger": "minecraft:tick"}}
        else:
            d["parent"] = f"ourlet:{parent}"
            d["criteria"] = {"done": {"trigger": "minecraft:impossible"}}
        out[aid] = d
    return out


def dump(folder, objs):
    folder = os.path.join(ROOT, folder)
    if os.path.isdir(folder):
        shutil.rmtree(folder)
    os.makedirs(folder)
    for k, v in objs.items():
        with open(os.path.join(folder, k + '.json'), 'w') as f:
            json.dump(v, f, indent=2, ensure_ascii=False)


if __name__ == '__main__':
    dump('src/main/resources/data/ourlet/advancement', make("121"))
    dump('targets/forge-1.20.1/overrides/resources/data/ourlet/advancements', make("1201"))
    dump('targets/neoforge-26.3/overrides/resources/data/ourlet/advancement', make("26"))
    print(len(ADV), 'progrès × 3 formats')
