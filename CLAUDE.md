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
- Test en jeu (CI) : `debug/ServerSelfTest` (objet, flèche, piston, relais de minage) et `debug/ClientSelfTest` (captures, traversée).
