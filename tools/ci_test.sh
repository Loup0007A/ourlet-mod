#!/bin/bash
# Test en jeu d'un jar : vrai serveur (mise en scène) + vrai client (portablemc, Xvfb, rendu logiciel)
# qui regarde une couture, casse à travers un pli puis le traverse.
# Usage : tools/ci_test.sh <jar> <loader: forge|neoforge> <version du loader> <nom du test>
#   ex.   tools/ci_test.sh out/ourlet-2.3.0-forge-1.21.1.jar forge 1.21.1-52.0.7 forge-1.21.1
set -u
JAR="$(realpath "$1")"; LOADER="$2"; LV="$3"; NAME="$4"; ROOT="$PWD"
OUT="$PWD/ci-out/$NAME"; mkdir -p "$OUT/screenshots"
W="$PWD/ci-work/$NAME"; rm -rf "$W"; mkdir -p "$W"; cd "$W"

case "$LOADER" in
  forge)    INSTALLER="https://maven.minecraftforge.net/net/minecraftforge/forge/${LV}/forge-${LV}-installer.jar" ;;
  neoforge) INSTALLER="https://maven.neoforged.net/releases/net/neoforged/neoforge/${LV}/neoforge-${LV}-installer.jar" ;;
  *) echo "loader inconnu : $LOADER"; exit 2 ;;
esac

# ---- Serveur ----
mkdir -p srv && cd srv
curl -sSfL -o installer.jar "$INSTALLER" || { echo "installeur introuvable : $INSTALLER"; exit 1; }
java -jar installer.jar --installServer . > installer.log 2>&1 || (tail -30 installer.log; exit 1)
echo "eula=true" > eula.txt
printf 'online-mode=false\nwhite-list=false\nenforce-whitelist=false\nspawn-protection=0\nlevel-seed=ourlet\ngamemode=creative\nspawn-monsters=false\n' > server.properties
mkdir -p mods && cp "$JAR" mods/
printf -- '\n-Dourlet.selftest=true\n' >> user_jvm_args.txt
chmod +x run.sh
(timeout 1700 ./run.sh nogui < /dev/null > "$OUT/selftest.log" 2>&1; echo "server exit $?" >> "$OUT/selftest.log") &
cd ..
for i in $(seq 1 150); do grep -q "SELFTEST\] READY" "$OUT/selftest.log" && break; sleep 4; done
grep -E "\[SELFTEST\]|Exception|ERROR" "$OUT/selftest.log" | head -50

# ---- Client ----
mkdir -p client/mods && cp "$JAR" client/mods/
printf 'onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\nsoundCategory_master:0.0\nnarrator:0\nguiScale:3\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:8\n' > client/options.txt
# Minecraft 26.x : choix du moteur de rendu (opengl / vulkan), BACKEND vide = valeur par défaut du jeu
[ -n "${BACKEND:-}" ] && echo "preferredGraphicsBackend:${BACKEND}" >> client/options.txt
# Sous Xvfb, GLX ne propose pas de visuel « sRGB » demandé par 26.x : on passe SDL par EGL
[ "${BACKEND:-}" = "opengl" ] && export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
VERSION="${LOADER}::${LV}"; LAUNCH=()
if [[ "$LV" =~ ^2[6-9]\. ]]; then
  # Minecraft 26.x : installeur officiel du loader (client), puis lancement de la version installée
  mkdir -p mcmain && echo '{"profiles":{}}' > mcmain/launcher_profiles.json
  java -jar srv/installer.jar --installClient mcmain > "$OUT/client-installer.log" 2>&1 || tail -30 "$OUT/client-installer.log"
  VERSION=$(ls mcmain/versions | grep -i "$LOADER" | head -1)
  echo "version installée : $VERSION"
  LAUNCH=(--jvm "$(command -v java)")
fi
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" timeout 1700 \
  portablemc --main-dir "$PWD/mcmain" start --mc-dir "$PWD/client" "${LAUNCH[@]}" "$VERSION" \
  -u Tester --join-server 127.0.0.1 --resolution 1280x720 \
  --jvm-arg="-Xmx3G,-Dourlet.clienttest=true${GIF:+,-Dourlet.gif=true}" > "$OUT/clienttest.log" 2>&1
echo "client exit $?"
grep -E "CLIENTTEST|SELFTEST|Ourlet|Exception|ERROR" "$OUT/clienttest.log" | head -150
grep -q "CLIENTTEST\] RESULT" "$OUT/clienttest.log" || tail -80 "$OUT/clienttest.log"
for f in client/screenshots/*.png; do
  case "$(basename "$f")" in gif_*) ;; *) cp "$f" "$OUT/screenshots/" ;; esac
done
# rafales pour l'animation de la description : réduites en JPEG (l'animation est montée ensuite par tools/make_gif.py)
if ls client/screenshots/gif_*.png > /dev/null 2>&1; then
  pip install -q pillow > /dev/null 2>&1 || true
  python3 "$ROOT/tools/gif_frames.py" client/screenshots "$OUT/gif" || true
fi
cp client/crash-reports/*.txt "$OUT/" 2>/dev/null || true
ls "$OUT/screenshots" | wc -l
pkill -f "nogui" || true
wait
grep -E "\[SELFTEST\]" "$OUT/selftest.log" | tail -20
grep -q "\[SELFTEST\] RESULT: OK" "$OUT/selftest.log" && grep -q "\[SELFTEST\] RELAY: OK" "$OUT/selftest.log" \
  && grep -q "\[SELFTEST\] POCKET: OK" "$OUT/selftest.log" \
  && grep -q "\[CLIENTTEST\] RESULT: OK" "$OUT/clienttest.log"
