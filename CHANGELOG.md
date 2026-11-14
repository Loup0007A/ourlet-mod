# Changelog

## 1.1.0

- **Carnet de couture** : un tutoriel en scènes animées, à la manière de « Ponder » (Create). Dans un inventaire, survole l'Aiguille Spatiale ou le Dé à coudre et **maintiens [W]**. 7 chapitres de maquettes brodées où les vrais objets traversent les plis, avec bulles, frise des étapes, pause et retour (← → ↑ ↓, espace).
- **Plis entre dimensions** : le premier point à la Surface, le second dans le Nether ou l'End. Tout passe (entités recréées de l'autre côté avec leur vitesse), et le relais (miner, poser, actionner) fonctionne aussi.
- **Dé à coudre** (5 pépites d'or + perle de l'Ender) : accroupi + clic droit = la maison ; clic droit n'importe où = un **pli de poche** faufilé vers la maison pendant 20 s (fil de bâti blanc), un seul à la fois. 64 utilisations.
- **Ciseaux et redstone** : clic droit sur une couture avec des ciseaux = ouvert → **sens unique** (on n'entre que par cette face) → **fermé** → ouvert. Un signal de redstone à côté d'un bloc cousu ferme le pli tant qu'il dure. La couture se resserre (animée) quand le pli se ferme.
- `/ourlet list` affiche la dimension et le mode de chaque pli.

Sewing Notebook (Ponder-style animated tutorial: hold [W] over the needle or thimble), cross-dimension folds, the Thimble (20-second pocket fold to home), and shears/redstone control (one-way and closed folds).

## 1.0.0

Première version d'Ourlet.

- **Aiguille Spatiale** : clic droit sur une face de bloc, puis sur une autre — les deux faces sont cousues. Accroupi + clic droit sur une couture pour la découdre. 48 utilisations, 12 plis par joueur.
- **Transfert** : joueurs, créatures, objets, flèches et autres projectiles qui touchent une face ressortent par l'autre, avec leur vitesse et leur regard tournés comme la face (tomber dans un sol cousu à un mur projette à l'horizontale).
- **Pistons** : un bloc poussé contre une couture passe par le pli et ressort devant l'autre face.
- **Manipulation à travers le pli** : le regard traverse la couture. Clic droit = utiliser l'objet tenu de l'autre côté (poser, actionner) ; casser le bloc cousu = c'est le bloc d'en face qui cède, au rythme de sa dureté, et ce qui tombe est ramené par le fil.
- Les deux bouts d'un pli restent chargés ; un pli se déchire si l'un de ses blocs disparaît.
- Style brodé : coutures animées en points de croix (rose, indigo, safran, sauge), sons de tissu.
- `/ourlet list` : vos plis.
- Forge 1.20.1, Forge 1.21–1.21.1, NeoForge 1.21–1.21.1, NeoForge 26.3.

First release of Ourlet: sew two block faces together with the Spatial Needle; whatever touches one comes out of the other, and you can mine, build and use things through the seam.
