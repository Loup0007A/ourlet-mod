# Ourlet — consignes pour Claude

- **Toujours pousser directement sur `main`** (pas de branche, pas de PR), à la demande de Loup0007A.
- Avant de pousser : le code doit compiler pour **chaque cible**. Ensuite, vérifier le résultat de la CI : sur la branche `ci-logs`, `main/status.txt` puis, par test, `main/<test>/selftest.log`, `main/<test>/clienttest.log` et `main/<test>/screenshots/`.
- Après chaque push sur `main`, la CI ajoute un commit « Jars compilés … » : faire `git fetch` + rebase avant le push suivant.
- Nouvelle version = augmenter `mod_version` dans `gradle.properties` + ajouter une section `## x.y.z` en haut de `CHANGELOG.md` (notes de la release GitHub et de CurseForge, envoi automatique).

## Multi-versions (même système que fate-flip)
- `src/main` = la variante de référence **Forge 1.21–1.21.1** (compile telle quelle avec le `build.gradle` racine).
- Les autres cibles sont dans `targets/<cible>/` (`forge-1.20.1`, `neoforge-1.21.1`, `neoforge-26.3`) : build Gradle propre + `target.json` + `overrides/`. Leurs sources sont fabriquées par `python3 tools/port.py <cible>` (blocs `//#if neoforge` / `//#elif` / `//#else` / `//#endif`, lignes inactives préfixées `//$$ `, puis remplacements de `target.json`, puis `overrides/`). `python3 tools/port.py --check` vérifie `src/main`.
- Tout ce qui change d'une version de Minecraft à l'autre passe par `platform/Compat.java`.
- Bibliothèques pour compiler hors ligne : workflow « Export libs » → branches `mc-libs-<cible>` (forge-1.21.1 est sur ce dépôt ; les autres peuvent venir de fate-flip).

## Le mod
- Tout est côté serveur (`fold/`) ; le client n'a que les objets, textures et traductions. Pas de paquets réseau : la couture visible est un `item_display` vanilla créé par `/summon` (`fold/Markers`), les effets sont des particules et sons vanilla.
- Les plis sont sauvegardés dans `<monde>/data/ourlet_folds.txt` (texte, identique pour toutes les versions).
- Géométrie : `fold/Fold.mapVector` / `mapPoint` (rotation propre : normale entrante → normale sortante, haut → haut, droite → −droite).
- Textures, logo et avatar : `python3 tools/textures.py` (dessinés en code, style broderie).
- Plis entre dimensions : chaque `Anchor` porte sa dimension ; `Folds.level(anchor)` ; passage d'une entité vers un autre monde par `Compat.moveAcross`.
- Modes (`Fold.OPEN/ONLY_A/ONLY_B/CLOSED`, ciseaux) et `powered` (redstone) : `Fold.canEnter` ; la taille de la couture (`Fold.scaleAt`) est mise à jour par `Markers.refresh` (`/data merge`).
- Dé à coudre (`item/ThimbleItem`) : maison par joueur (lignes `H;` du fichier), plis de poche temporaires (`Fold.expires`, couleur `BASTING`, jamais sauvegardés).
- Carnet de couture (tuto façon Ponder) : scènes générées par `python3 tools/carnet.py` (images + `assets/ourlet/carnet/chapters.json`), écran `client/CarnetScreen`, ouverture « maintenir [W] » dans `client/ClientHooks`. Tout texte de scène = clé `ourlet.carnet.*` dans les deux fichiers de langue. Le client ne doit rien charger côté serveur dédié : tout le code client est inscrit dans `FMLClientSetupEvent`.
- Réglages serveur : `OurletConfig` (`config/ourlet.properties`, simple fichier Properties, relu à chaque démarrage). Protection : `Folds.mayEdit`. Regard : `fold/Gaze` (barre d'action ; `Compat.actionBar` fait taire le regard 50 ticks). Progrès : `fold/Progress` (commande /advancement) ; JSON générés par `python3 tools/advancements.py` (3 formats).
- 2.0 « Haute couture » : `Fold.size` (1 ou 3 : `Anchor.cells/covers`, `Fold.mapCell/mapPoint`), `Fold.color` (0–3 au hasard, 4 bâti, 5–20 = teintures, objets `seam_<nom>`), `Fold.name` (étiquette), `Fold.tension` (slime/miel, `Transit.tensioned`). Entonnoirs : `fold/Hoppers`. Fil d'Ariane : `fold/Ariadne`. Animations : `Folds.later`, `Markers.ripple`, `Effects.sewingAnimation/idle/trail`. Tronçons chargés : `Folds.refreshForcing` (lignes `C;` du fichier).
- Outils sur une couture (ciseaux, teinture, étiquette, slime, miel) : `ServerEvents.handleTool`.
- Test en jeu (CI) : `debug/ServerSelfTest` (objet, flèche, piston, Nether, redstone, sens unique, 3×3, élastique, chute sans fin, entonnoir, teinture, relais de minage, dé à coudre, outils) et `debug/ClientSelfTest` (captures, traversée, vitrine, carnet).
- Animation de la description : le test `forge-1.21.1` (option `gif` de la matrice) prend des rafales `gif_*` (réduites par `tools/gif_frames.py`, dossier `gif/` sur `ci-logs`) : a = on marche dans le mur cousu, b = face à la couture dans la mine (le client attend que la mine soit dessinée puis `/tp`), c = la tête tourne vers la vitrine (couture cirée 3×3, mouton), d = le carnet. `python3 tools/make_gif.py <dossier gif>` monte `curseforge/ourlet.gif` (plans et légendes : `SCENES`, palette octree 256).
