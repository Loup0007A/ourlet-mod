# Textes de la page CurseForge

## Nom

Ourlet — The Spatial Needle

## Summary

Sew two block faces together anywhere in the world: what touches one comes out of the other, and you can mine and build through the seam.

## Description

![Ourlet](https://raw.githubusercontent.com/Loup0007A/ourlet-mod/main/curseforge/avatar.png)

*"Ourlet" is French for a **hem**: a fold of fabric, stitched down. Here, the fabric is the world.*

The **Spatial Needle** sews the space between two block faces. There's no portal block and no window texture: the seam is embroidered onto two blocks that already exist.

### How it works

1. **Tack.** Right-click a block face with the needle: a wall, a floor or a ceiling.
2. **Sew.** Right-click another face, 3 blocks away or 5,000. The two faces are now stitched together.
3. **Pass through.** Anything that touches one face comes out of the other: players, mobs, items, arrows, snowballs. **Velocity is preserved** and rotated with the face. Fall into a floor seam stitched to a wall and you get **launched sideways** at full speed.
4. **Pistons.** Push a block against a seam and it is pulled through the fold, coming out on the other side.
5. **Touch through the fold.** Your line of sight goes through the seam. Stitch your base wall to a diamond vein 5,000 blocks away, then:
   - **right-click the wall** to place a torch or flip a lever *next to the vein*;
   - **mine the wall** and *the ore on the other side* gives way, as fast as its hardness allows. The drops come back through the thread. The wall itself never breaks.
6. **Unpick.** Sneak + right-click a seam. If either block disappears, the fold tears.

7. **Across dimensions.** Tack the first stitch in the Overworld and the second in the Nether or the End. Everything still goes through.
8. **The Thimble.** Sneak + right-click a face to make it *home*. Later, anywhere you are (even in another dimension), right-click any face to baste a **pocket fold** back home. It lasts 20 seconds.
9. **Shears & redstone.** Snip a seam with shears to cycle it: open → **one-way** → **closed**. A redstone signal next to a sewn block also closes the fold, like a door, and the seams visibly pull tight.

10. **Gaze through.** Aim at a seam: the action bar tells you where it leads, whether it's open, one-way or closed, and which block you'd touch on the other side.
11. **Multiplayer-friendly.** Only a fold's owner (or an operator) can unpick or snip it; everyone can go through. Everything is configurable in `config/ourlet.properties` (limits, cross-dimension, chunk loading, relay, pistons, entity blacklist).

9 advancements in an embroidered "Ourlet" tab guide you through the mod.

### The Sewing Notebook (Ponder-style tutorial)

In any inventory, hover the Spatial Needle or the Thimble and **hold [W]**. The notebook opens: 7 chapters of embroidered isometric dioramas where real item icons travel through folds, with margin notes pointing at each part, a step timeline, pause and navigation.

### Details

- Needle: ender pearl (top-right), iron ingot (center), string (bottom-left). 48 seams.
- Thimble: 5 gold nuggets around an ender pearl (`NNN` / `NPN`). 64 pocket folds.
- 12 permanent folds per player, one pocket fold at a time.
- Both ends of a fold stay loaded, so whatever goes through arrives instantly.
- Matching thread colors (rose, indigo, saffron, sage) show which seams belong together.
- Fabric sounds and thread particles, all vanilla, so there's nothing extra to download.
- `/ourlet list` shows your folds.

### Versions

Forge 1.20.1 · Forge 1.21–1.21.1 · NeoForge 1.21–1.21.1 · NeoForge 26.3. **Required on both server and client.**

---

**Français** — L'Aiguille Spatiale coud deux faces de blocs. Ce qui touche l'une ressort par l'autre, vitesse comprise. Les pistons y font passer des blocs, et on peut miner, poser et actionner de l'autre côté en visant la couture. Les plis traversent les dimensions, le Dé à coudre ouvre un pli de poche vers la maison, les ciseaux et la redstone ferment ou orientent les plis, et le Carnet de couture (maintiens [W]) explique tout en scènes animées.
