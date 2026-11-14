# Changelog

## 2.0.0 — Haute couture

- **Grandes coutures 3×3** : une **Bobine** (2 planches + 1 ficelle) dans l'autre main, et l'aiguille coud un patchwork de 3×3 faces. Assez grand pour les chevaux, les bateaux, les wagonnets ; chaque carré mène à son jumeau.
- **Teintures** : clic droit sur une couture avec l'une des 16 teintures, les deux bouts changent de couleur.
- **Étiquettes** : une étiquette renommée donne un nom au pli. Il s'affiche quand on vise la couture, dans `/ourlet list`, et en titre à l'arrivée d'un long voyage.
- **Slime et miel** : une boule de slime rend le fil élastique (on ressort 1,5 fois plus vite), un rayon de miel le cire (on ressort au ralenti). Une couture cirée au plafond, cousue au sol : une **chute sans fin**, tout en douceur.
- **Entonnoirs** : un entonnoir tourné vers un bloc cousu envoie ses objets à travers le pli, directement dans le coffre (ou tonneau, entonnoir…) qui touche l'autre couture. Des tuyaux d'objets de n'importe quelle longueur, même entre deux dimensions.
- **Le fil d'Ariane** : dé à coudre en main, une ligne de points montre la direction de la maison ; aiguille en main, celle du premier point en attente.
- **Plus de style** : la couture se coud point par point (comme à la machine), gonfle quand quelque chose la traverse, aspire des lettres de la table d'enchantement ; un ruban de fil suit ce qui vient de passer ; un titre annonce la destination des longs voyages ; le froissement de tissu monte avec la vitesse.
- **Boucles** : on peut maintenant retomber dans un pli dès qu'on en sort par l'autre bout (seule la face de sortie reste fermée un instant).
- **Solidité et Raccommodage** s'appliquent à l'aiguille et au dé (à l'enclume).
- Un onglet **Ourlet** dans le mode créatif.
- **6 nouveaux progrès** (Patchwork, Haute couture, Cousu main, Ressort, Livraison express, Boucle infinie) — 15 en tout.
- **Carnet de couture** : 4 nouveaux chapitres (grandes coutures, teintures et étiquettes, slime et miel, entonnoirs) — 11 en tout.
- Les tronçons gardés chargés sont maintenant suivis un par un (un pli 3×3 à cheval sur deux tronçons les garde tous les deux).

Haute couture: 3×3 patchwork seams (Spool), 16 dyes, name tags, slime (elastic) and honey (waxed, endless gentle falls), hoppers through folds, Ariadne's thread, sewing/ripple/glyph/trail animations, arrival titles, creative tab, 6 new advancements and 4 new notebook chapters.

## 1.2.0

- **Regard à travers la couture** : vise une couture, la barre d'action dit où mène le pli (dimension, position, distance), son état (ouvert, sens unique, fermé, fermé par la redstone, temps restant d'un pli de poche) et le bloc que tu toucherais de l'autre côté.
- **Plis protégés** : sur un serveur, seuls le propriétaire d'un pli et les opérateurs peuvent le découdre ou le couper aux ciseaux. Tout le monde peut toujours le traverser.
- **Fichier de configuration** `config/ourlet.properties` (créé au premier démarrage) : plis par joueur, durée des plis de poche, plis entre dimensions, chargement des deux bouts, protection, relais, pistons, regard, liste noire d'entités (Ender Dragon et Wither par défaut).
- **Progrès** : un onglet « Ourlet » brodé, 9 progrès (Premier ourlet, Entre les mailles, Tomber à pic, Grand écart, D'un monde à l'autre, Doigts de fée, Coupe franche, Retour au bercail).
- **Carnet de couture** : deux nouvelles pages (le regard à travers la couture, la protection des plis).

Gaze through seams (action bar shows destination, state and the block on the other side), protected folds on servers (owner/ops only), a server config file, 9 advancements in an embroidered tab, and two new notebook pages.

## 1.1.1

- Carnet de couture : les boutons (« chapitre précédent », « rejouer », « chapitre suivant ») passent à gauche, l'aide des touches à droite ; elles ne se chevauchent plus.

Sewing Notebook: buttons moved to the left and the key hint to the right, so they no longer overlap.

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
