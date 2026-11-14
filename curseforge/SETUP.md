# Mettre Ourlet sur CurseForge

La création du projet est **manuelle** (une seule fois), parce que CurseForge fait relire chaque nouveau projet par un modérateur. Ensuite, chaque nouvelle version est envoyée **automatiquement** par GitHub Actions, exactement comme pour Fate Flip.

## 1. Créer le projet (une fois)

1. <https://authors.curseforge.com/#/projects/create/choose-game> → **Minecraft** → **Mods**.
2. Textes à copier depuis [`description.md`](description.md) :
   - **Nom** : `Ourlet — The Spatial Needle`
   - **Résumé** : la ligne « Summary ».
   - **Description** : le bloc « Description » (mode Markdown).
   - **Avatar** : [`avatar.png`](avatar.png) (400×400).
   - **Catégories** conseillées : *Player Transport* (principale), *Utility & QoL*, *Technology*.
   - **Licence** : MIT. **Code source** : `https://github.com/Loup0007A/ourlet-mod`. **Issues** : `https://github.com/Loup0007A/ourlet-mod/issues`.
3. Premier fichier : `releases/ourlet-latest.jar` (ou celui de la Release GitHub v1.0.0), avec 1.21.1 · Forge · Client + Server · Java 21, type **Release**, notes = section 1.0.0 de `CHANGELOG.md`.
4. Galerie : les captures de [`screenshots/`](screenshots/).
5. Envoie en modération. L'**ID du projet** s'affiche dans l'encadré « About Project ».

## 2. Brancher l'envoi automatique (une fois)

Dans ce dépôt GitHub (**Settings → Secrets and variables → Actions**) :
- onglet **Secrets** : `CURSEFORGE_TOKEN` = ton jeton API CurseForge (<https://www.curseforge.com/account/api-tokens>, le même que pour fate-flip convient) ;
- onglet **Variables** : `CURSEFORGE_PROJECT_ID` = l'ID du projet Ourlet.

## 3. Publier une nouvelle version

Augmente `mod_version` dans `gradle.properties`, ajoute une section `## x.y.z` en haut de `CHANGELOG.md`, pousse sur `main`.
Le workflow compile les 4 cibles, les teste en jeu, crée la Release GitHub, copie les jars dans `releases/` et envoie un fichier par cible sur CurseForge.

Pour renvoyer la version actuelle : **Actions → Build Ourlet → Run workflow** sur `main`, case *publish_curseforge* cochée.
Sans le secret ou la variable, l'étape CurseForge est ignorée (avec un message dans le résumé du workflow).
