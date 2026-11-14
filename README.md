<p align="center"><img src="curseforge/avatar.png" width="200" alt="Ourlet"></p>

# Ourlet

*Un ourlet, c'est un bord de tissu replié puis cousu. Ici, le tissu, c'est le monde.*

L'**Aiguille Spatiale** coud l'espace entre deux faces de blocs, n'importe où dans une dimension.
Il n'y a ni portail ni nouveau bloc : seulement une couture brodée sur deux blocs qui existent déjà.

## Comment ça marche

1. **Le bâti** — clic droit avec l'aiguille sur une face (un mur, un sol, un plafond).
2. **La couture** — clic droit sur une autre face, à 3 blocs ou à 5 000. Les deux faces sont cousues.
3. **Le passage** — tout ce qui touche une face ressort par l'autre : joueurs, créatures, objets, flèches, boules de neige… La vitesse est conservée et tournée comme la face. Si on tombe dans un sol cousu à un mur, on ressort à l'horizontale, lancé à pleine vitesse.
4. **Les pistons** — un bloc poussé contre une couture passe dans le pli et ressort de l'autre côté.
5. **Le pli tactile** — le regard traverse la couture. Si un mur de ta base est cousu à un filon de diamants à 5 000 blocs :
   - clic droit sur le mur : tu poses une torche ou tu actionnes un levier **près du filon** ;
   - tu casses le mur : c'est **le minerai d'en face** qui cède, au rythme de sa dureté, et les diamants reviennent par le fil. Le mur, lui, ne casse pas.
6. **Découdre** — accroupi + clic droit sur une couture. Si l'un des deux blocs disparaît, le pli se déchire.
7. **Entre les dimensions** — le premier point à la Surface, le second dans le Nether ou l'End : tout passe.
8. **Le dé à coudre** — accroupi + clic droit sur une face : c'est la maison. Ensuite, clic droit n'importe où
   (même dans une autre dimension) : un **pli de poche** faufilé vers la maison s'ouvre pendant 20 secondes.
9. **Ciseaux et redstone** — clic droit sur une couture avec des ciseaux : ouvert → **sens unique** → **fermé**.
   Un signal de redstone à côté d'un bloc cousu ferme le pli, comme une porte. La couture se resserre quand le pli se ferme.
10. **Le regard** — vise une couture : la barre d'action dit où elle mène, son état et le bloc que tu toucherais de l'autre côté.
11. **En multijoueur** — seuls le propriétaire d'un pli et les opérateurs peuvent le découdre, le couper, le teindre ou le nommer ; tout le monde peut le traverser.

### Haute couture (2.0)

12. **Grandes coutures 3×3** — une **Bobine** dans l'autre main : l'aiguille coud un patchwork de 3×3 faces,
    assez grand pour les chevaux, les bateaux et les wagonnets. Chaque carré mène à son jumeau.
13. **Teintures** — clic droit sur une couture avec l'une des 16 teintures : les deux bouts changent de couleur.
14. **Étiquettes** — une étiquette renommée donne un nom au pli, affiché quand on le vise et en titre à l'arrivée.
15. **Slime et miel** — une boule de slime rend le fil **élastique** (on ressort 1,5 fois plus vite),
    un rayon de miel le **cire** (on ressort au ralenti). Une couture cirée au plafond, cousue au sol : une chute sans fin.
16. **Entonnoirs** — un entonnoir tourné vers un bloc cousu envoie ses objets à travers le pli, directement dans
    le coffre qui touche l'autre couture. Des tuyaux d'objets de n'importe quelle longueur, même entre dimensions.
17. **Le fil d'Ariane** — dé à coudre en main, une ligne de points montre la direction de la maison.
18. **Du style** — la couture se coud point par point, gonfle quand on la traverse, aspire des lettres d'enchantement ;
    un ruban de fil suit ce qui vient de passer ; un titre annonce la destination des longs voyages.

### Le Carnet de couture

Dans un inventaire, survole l'Aiguille Spatiale ou le Dé à coudre et **maintiens [W]** : le carnet s'ouvre,
façon *Ponder* de Create. Onze chapitres de maquettes brodées où les vrais objets traversent les plis,
avec des notes dans la marge, une frise des étapes, pause et navigation (← → ↑ ↓, espace).

| | |
|---|---|
| Aiguille | perle de l'Ender (haut-droite), lingot de fer (centre), ficelle (bas-gauche) — 48 coutures |
| Dé à coudre | 5 pépites d'or autour d'une perle de l'Ender (`NNN` / `NPN`) — 64 plis de poche |
| Bobine | une ficelle entre deux planches (en colonne) — dans l'autre main pour les coutures 3×3 |
| Enchantements | Solidité et Raccommodage (à l'enclume) sur l'aiguille et le dé |
| Limite | 12 plis permanents par joueur ; un pli de poche à la fois |
| Chargement | les deux bouts d'un pli restent chargés : ce qui passe arrive tout de suite |
| Commande | `/ourlet list` |

Les deux bouts d'un pli portent la même couleur de fil. Quinze progrès, dans un onglet brodé,
accompagnent la découverte du mod, et tout est rangé dans l'onglet créatif **Ourlet**.

### Configuration du serveur

`config/ourlet.properties` est créé au premier démarrage :

| Clé | Défaut | Rôle |
|---|---|---|
| `max_folds_per_player` | 12 | plis permanents par joueur |
| `pocket_seconds` | 20 | durée d'un pli de poche |
| `cross_dimension` | true | plis entre deux dimensions |
| `force_load_chunks` | true | garder chargés les deux bouts d'un pli |
| `protect_folds` | true | seuls propriétaire et opérateurs décousent / coupent |
| `relay` | true | miner, poser, utiliser à travers une couture |
| `pistons` | true | les pistons poussent à travers les plis |
| `gaze` | true | viser une couture affiche sa destination |
| `blacklist` | ender_dragon, wither | entités qui ne passent jamais |

## Versions

| Minecraft | Chargeur | Java |
|---|---|---|
| 1.20.1 | Forge | 17 |
| 1.21 – 1.21.1 | Forge | 21 |
| 1.21 – 1.21.1 | NeoForge | 21 |
| 26.3 | NeoForge | 25 |

Le mod doit être installé sur le serveur **et** sur le client.

## Développement

Même organisation que [fate-flip](https://github.com/Loup0007A/fate-flip) : sources de référence dans `src/main`
(Forge 1.21.1), autres cibles fabriquées par `tools/port.py`, et un workflow GitHub qui compile chaque cible,
lance un vrai serveur et un vrai client de test, publie la release puis envoie les fichiers sur CurseForge
(voir [`curseforge/SETUP.md`](curseforge/SETUP.md)).

---

**English** — *Ourlet* is French for "hem". The Spatial Needle stitches two block faces together: anything that
touches one comes out of the other with its speed preserved, pistons push blocks through, and you can mine,
build and flip levers on the far side by aiming at the seam. Folds work across dimensions, the Thimble opens a
20-second pocket fold back home, shears and redstone make folds one-way or closed, and the Sewing Notebook
(hold [W] over the needle) is a Ponder-style animated tutorial. Version 2.0 adds 3×3 patchwork seams (Spool),
16 dyes, name tags, elastic (slime) and waxed (honey) threads for endless gentle falls, hoppers that pipe items
through folds, and a lot of embroidered flair.
