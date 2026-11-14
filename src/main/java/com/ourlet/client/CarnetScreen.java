package com.ourlet.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.ourlet.Ourlet;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Le Carnet de couture : un tutoriel en scènes animées, dans l'esprit de « Ponder » (Create).
 *
 * Chaque chapitre est une suite d'étapes : une maquette brodée (image dessinée par
 * tools/carnet.py), une bulle de texte qui pointe un endroit de la maquette, et des objets
 * (leurs vraies icônes) qui suivent un chemin — un trou dans le chemin, c'est le passage par le pli.
 * ← → : étape, ↑ ↓ : chapitre, espace : pause, R : rejouer, Échap : fermer.
 */
public class CarnetScreen extends Screen {

    // ───────────────────────── données (chapters.json) ─────────────────────────

    public record Move(ItemStack stack, List<float[]> path) {}

    public record Step(ResourceLocation frame, String text, int[] point, int ticks, List<Move> moves) {}

    public record Chapter(String id, ItemStack icon, List<Step> steps) {}

    private static List<Chapter> chapters;
    private static int sceneW = 208, sceneH = 150;

    public static List<Chapter> chapters() {
        if (chapters != null) return chapters;
        chapters = new ArrayList<>();
        ResourceLocation file = Ourlet.id("carnet/chapters.json");
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(file);
            if (res.isEmpty()) throw new IllegalStateException("introuvable : " + file);
            try (Reader r = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                sceneW = root.get("width").getAsInt();
                sceneH = root.get("height").getAsInt();
                for (JsonElement ce : root.getAsJsonArray("chapters")) {
                    JsonObject c = ce.getAsJsonObject();
                    List<Step> steps = new ArrayList<>();
                    for (JsonElement se : c.getAsJsonArray("steps")) {
                        JsonObject s = se.getAsJsonObject();
                        int[] point = null;
                        if (s.has("point")) {
                            JsonArray p = s.getAsJsonArray("point");
                            point = new int[]{p.get(0).getAsInt(), p.get(1).getAsInt()};
                        }
                        List<Move> moves = new ArrayList<>();
                        if (s.has("items")) {
                            for (JsonElement me : s.getAsJsonArray("items")) {
                                JsonObject m = me.getAsJsonObject();
                                List<float[]> path = new ArrayList<>();
                                for (JsonElement pe : m.getAsJsonArray("path")) {
                                    if (pe.isJsonNull()) path.add(null);
                                    else path.add(new float[]{pe.getAsJsonArray().get(0).getAsFloat(), pe.getAsJsonArray().get(1).getAsFloat()});
                                }
                                moves.add(new Move(ClientCompat.item(m.get("item").getAsString()), path));
                            }
                        }
                        steps.add(new Step(Ourlet.id("textures/gui/carnet/" + s.get("frame").getAsString() + ".png"),
                            s.get("text").getAsString(), point, s.get("ticks").getAsInt(), moves));
                    }
                    chapters.add(new Chapter(c.get("id").getAsString(), ClientCompat.item(c.get("icon").getAsString()), steps));
                }
            }
        } catch (Exception e) {
            Ourlet.LOGGER.error("Carnet de couture illisible", e);
        }
        return chapters;
    }

    public static void open(String chapterId, Screen parent) {
        List<Chapter> list = chapters();
        int index = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(chapterId)) index = i;
        ClientCompat.open(new CarnetScreen(index, parent));
    }

    // ───────────────────────── état ─────────────────────────

    private static final int LINEN = 0xFFEDE3CF, LINEN_D = 0xFFD6C8AC, INK = 0xFF3A2826, ROSE = 0xFFE07A95,
        ROSE_D = 0xFF963E5C, PAPER_SHADOW = 0x66000000;

    private final Screen parent;
    private int chapter, step, tick;
    private boolean paused;

    // mise en page (calculée dans init) : la maquette à gauche, les notes dans la marge de droite
    private static final int NOTE_W = 150;
    private int px, py, pw, ph, scale, sx, sy, noteX;

    public CarnetScreen(int chapter, Screen parent) {
        super(Component.translatable("ourlet.carnet.title"));
        this.chapter = chapter;
        this.parent = parent;
    }

    /** Pour le test automatique : va directement à une étape, à un moment donné. */
    public void jump(int chapter, int step, int tick) {
        this.chapter = Math.floorMod(chapter, chapters().size());
        this.step = step;
        this.tick = tick;
        this.paused = true;
    }

    private Chapter current() {
        return chapters().get(chapter);
    }

    private Step currentStep() {
        Chapter c = current();
        return c.steps.get(Math.min(step, c.steps.size() - 1));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 600);
        ph = Math.min(height - 12, 360);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int roomW = pw - 24 - NOTE_W - 12, roomH = ph - 70;
        scale = Math.max(1, Math.min(roomW / sceneW, roomH / sceneH));
        sx = px + 12 + Math.max(0, (roomW - sceneW * scale) / 2);
        sy = py + 30 + Math.max(0, (roomH - sceneH * scale) / 2);
        noteX = px + pw - 12 - NOTE_W;
    }

    @Override
    public void tick() {
        if (chapters().isEmpty() || paused) return;
        tick++;
        if (tick >= currentStep().ticks) {
            if (step < current().steps.size() - 1) {
                step++;
                tick = 0;
            } else {
                tick = currentStep().ticks; // fin du chapitre : on reste sur la dernière étape
            }
        }
    }

    private void go(int newChapter, int newStep) {
        int n = chapters().size();
        chapter = Math.floorMod(newChapter, n);
        step = Math.max(0, Math.min(newStep, current().steps.size() - 1));
        tick = 0;
        paused = false;
        playClick();
    }

    private void playClick() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.WOOL_STEP, 0.4f, 1.6f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientCompat.open(parent);
    }

    // ───────────────────────── entrées ─────────────────────────

    private boolean key(int key) {
        if (chapters().isEmpty()) return false;
        if (key == InputConstants.KEY_RIGHT) { go(chapter, step + 1); return true; }
        if (key == InputConstants.KEY_LEFT) { go(chapter, step - 1); return true; }
        if (key == InputConstants.KEY_DOWN) { go(chapter + 1, 0); return true; }
        if (key == InputConstants.KEY_UP) { go(chapter - 1, 0); return true; }
        if (key == InputConstants.KEY_SPACE) { paused = !paused; return true; }
        if (key == InputConstants.KEY_R) { go(chapter, 0); return true; }
        return false;
    }

    private boolean click(double mx, double my) {
        if (chapters().isEmpty()) return false;
        int by = py + ph - 18;
        // boutons : ◀ chapitre précédent, ⟲ rejouer, ▶ chapitre suivant ; ✕ en haut à droite
        if (my >= by - 2 && my <= by + 12) {
            int cx = px + pw / 2;
            if (mx >= cx - 46 && mx < cx - 26) { go(chapter - 1, 0); return true; }
            if (mx >= cx - 10 && mx < cx + 10) { go(chapter, 0); return true; }
            if (mx >= cx + 26 && mx < cx + 46) { go(chapter + 1, 0); return true; }
        }
        if (mx >= px + pw - 16 && mx < px + pw - 4 && my >= py + 4 && my < py + 16) {
            onClose();
            return true;
        }
        // les nœuds de la frise : aller à une étape
        int n = current().steps.size();
        int ty = py + ph - 34;
        if (my >= ty - 5 && my <= ty + 5) {
            for (int i = 0; i < n; i++) {
                int kx = knotX(i, n);
                if (Math.abs(mx - kx) <= 4) { go(chapter, i); return true; }
            }
        }
        return false;
    }

    //#if mc26
    //$$ @Override
    //$$ public boolean keyPressed(net.minecraft.client.input.KeyEvent ev) {
    //$$     return key(ev.key()) || super.keyPressed(ev);
    //$$ }
    //$$
    //$$ @Override
    //$$ public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean dbl) {
    //$$     return click(ev.x(), ev.y()) || super.mouseClicked(ev, dbl);
    //$$ }
    //#else
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return key(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return click(mx, my) || super.mouseClicked(mx, my, button);
    }
    //#endif

    // ───────────────────────── dessin ─────────────────────────

    //#if mc26
    //$$ @Override
    //$$ public void extractBackground(GuiGraphics g, int mx, int my, float pt) {
    //$$     g.fill(0, 0, width, height, 0x90100C0A);
    //$$ }
    //$$
    //$$ @Override
    //$$ public void extractRenderState(GuiGraphics g, int mx, int my, float pt) {
    //$$     draw(g, mx, my, pt);
    //$$ }
    //#else
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x90100C0A);
        draw(g, mx, my, pt);
    }
    //#endif

    private int knotX(int i, int n) {
        int x0 = px + 40, x1 = px + pw - 40;
        return n <= 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * i / (n - 1);
    }

    /** Une ligne de points avant (le fil), horizontale ou verticale. */
    private static void running(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (y0 == y1) {
            for (int x = Math.min(x0, x1); x < Math.max(x0, x1); x += 4) g.fill(x, y0, Math.min(x + 2, Math.max(x0, x1)), y0 + 1, color);
        } else {
            for (int y = Math.min(y0, y1); y < Math.max(y0, y1); y += 4) g.fill(x0, y, x0 + 1, Math.min(y + 2, Math.max(y0, y1)), color);
        }
    }

    private void draw(GuiGraphics g, int mx, int my, float pt) {
        // la page du carnet : du lin, une ombre, une couture tout autour
        g.fill(px + 3, py + 4, px + pw + 3, py + ph + 4, PAPER_SHADOW);
        g.fill(px, py, px + pw, py + ph, LINEN);
        for (int y = py + 2; y < py + ph - 2; y += 2) {
            for (int x = px + 2 + (y / 2 % 2) * 2; x < px + pw - 2; x += 4) g.fill(x, y, x + 1, y + 1, LINEN_D);
        }
        running(g, px + 4, py + 4, px + pw - 4, py + 4, ROSE);
        running(g, px + 4, py + ph - 5, px + pw - 4, py + ph - 5, ROSE);
        running(g, px + 4, py + 4, px + 4, py + ph - 4, ROSE);
        running(g, px + pw - 5, py + 4, px + pw - 5, py + ph - 4, ROSE);

        if (chapters().isEmpty()) {
            ClientCompat.text(g, font, "Carnet de couture introuvable", px + 12, py + 12, INK, false);
            return;
        }
        Chapter c = current();
        Step s = currentStep();

        // en-tête : icône, titre du carnet, chapitre, numéro
        ClientCompat.item(g, c.icon, px + 10, py + 9);
        ClientCompat.text(g, font, Component.translatable("ourlet.carnet.title").withStyle(ChatFormatting.ITALIC).getVisualOrderText(),
            px + 30, py + 8, 0xFF8A7A66, false);
        ClientCompat.text(g, font, Component.translatable("ourlet.carnet.chapter." + c.id).withStyle(ChatFormatting.BOLD).getVisualOrderText(),
            px + 30, py + 18, INK, false);
        String num = (chapter + 1) + " / " + chapters().size();
        ClientCompat.text(g, font, num, px + pw - 22 - font.width(num), py + 8, 0xFF8A7A66, false);
        ClientCompat.text(g, font, "✕", px + pw - 14, py + 6, mx >= px + pw - 16 && my < py + 16 && my >= py + 4 ? ROSE_D : INK, false);

        // la maquette
        ClientCompat.image(g, s.frame, sx, sy, sceneW * scale, sceneH * scale, sceneW, sceneH);
        float t = Math.min(1f, (tick + (paused ? 0 : pt)) / Math.max(1, s.ticks));
        for (Move m : s.moves) drawMove(g, m, t);

        // la bulle
        drawCallout(g, s, t);

        // la frise : un fil, un nœud par étape
        int n = c.steps.size();
        int ty = py + ph - 34;
        running(g, knotX(0, n), ty, knotX(n - 1, n), ty, 0xFF8A7A66);
        if (n > 1) {
            int done = knotX(step, n) + (int) ((knotX(Math.min(step + 1, n - 1), n) - knotX(step, n)) * t);
            g.fill(knotX(0, n), ty, done, ty + 1, ROSE);
        }
        for (int i = 0; i < n; i++) {
            int kx = knotX(i, n);
            int col = i < step ? ROSE_D : (i == step ? ROSE : 0xFFB8A88E);
            g.fill(kx - 2, ty - 2, kx + 3, ty + 3, col);
            if (i == step) g.fill(kx - 1, ty - 1, kx + 2, ty + 2, LINEN);
        }

        // les boutons
        int by = py + ph - 18, cx = px + pw / 2;
        button(g, cx - 36, by, "◀", mx, my);
        button(g, cx, by, paused ? "▶" : "⟲", mx, my);
        button(g, cx + 36, by, "▶", mx, my);
        String hint = Component.translatable("ourlet.carnet.keys").getString();
        ClientCompat.text(g, font, hint, px + 10, by + 1, 0xFFA8987E, false);
    }

    private void button(GuiGraphics g, int cx, int y, String label, int mx, int my) {
        boolean over = Math.abs(mx - cx) <= 10 && my >= y - 2 && my <= y + 12;
        g.fill(cx - 10, y - 2, cx + 10, y + 12, over ? ROSE : LINEN_D);
        g.fill(cx - 9, y - 1, cx + 9, y + 11, over ? 0xFFF7D4DE : LINEN);
        ClientCompat.text(g, font, label, cx - font.width(label) / 2, y + 1, over ? ROSE_D : INK, false);
    }

    /** Un objet qui suit son chemin ; un trou (null) dans le chemin = il passe par le pli. */
    private void drawMove(GuiGraphics g, Move m, float t) {
        List<float[]> path = m.path;
        // longueur des segments (les sauts ne comptent pas)
        List<float[][]> segs = new ArrayList<>();
        float total = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            float[] a = path.get(i), b = path.get(i + 1);
            if (a == null || b == null) continue;
            float len = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            segs.add(new float[][]{a, b, {len}});
            total += len;
        }
        if (segs.isEmpty()) {
            float[] p = path.get(0);
            if (p != null) ClientCompat.item(g, m.stack, sx + (int) (p[0] * scale) - 8, sy + (int) (p[1] * scale) - 8);
            return;
        }
        float along = Math.min(1f, t / 0.75f) * total;
        float[] pos = null;
        for (float[][] seg : segs) {
            float len = seg[2][0];
            if (along <= len || seg == segs.get(segs.size() - 1)) {
                float k = len <= 0 ? 1 : Math.min(1f, along / len);
                pos = new float[]{seg[0][0] + (seg[1][0] - seg[0][0]) * k, seg[0][1] + (seg[1][1] - seg[0][1]) * k};
                break;
            }
            along -= len;
        }
        if (pos == null) return;
        ClientCompat.item(g, m.stack, sx + (int) (pos[0] * scale) - 8, sy + (int) (pos[1] * scale) - 8);
    }

    private void drawCallout(GuiGraphics g, Step s, float t) {
        Component text = Component.translatable(s.text);
        String full = text.getString();
        int shown = Math.min(full.length(), (int) (full.length() * Math.min(1f, t * 3f)) + 1);
        int boxW = NOTE_W - 6;
        List<FormattedCharSequence> lines = font.split(Component.literal(full.substring(0, shown)), boxW);
        List<FormattedCharSequence> allLines = font.split(text, boxW);
        int boxH = allLines.size() * 10 + 6;
        int bx = noteX, by;
        if (s.point != null) {
            int ax = sx + s.point[0] * scale, ay = sy + s.point[1] * scale;
            by = Math.max(py + 32, Math.min(ay - boxH / 2, py + ph - 46 - boxH));
            // le fil qui relie la note de marge au point visé
            int lx = bx - 5;
            running(g, ax, ay, lx, ay, ROSE_D);
            running(g, lx, Math.min(ay, by + boxH / 2), lx, Math.max(ay, by + boxH / 2) + 1, ROSE_D);
            g.fill(lx, by + boxH / 2, bx, by + boxH / 2 + 1, ROSE_D);
            int pulse = 2 + (int) (Math.sin((tick % 20) / 20.0 * Math.PI * 2) * 1.2);
            g.fill(ax - pulse, ay - pulse, ax + pulse + 1, ay + pulse + 1, ROSE_D);
            g.fill(ax - 1, ay - 1, ax + 2, ay + 2, 0xFFFFFFFF);
        } else {
            by = py + 32 + Math.max(0, (ph - 80 - boxH) / 2);
        }
        g.fill(bx - 1, by - 1, bx + boxW + 7, by + boxH + 1, ROSE_D);
        g.fill(bx, by, bx + boxW + 6, by + boxH, 0xF8FFFBF2);
        int y = by + 4;
        for (FormattedCharSequence line : lines) {
            ClientCompat.text(g, font, line, bx + 3, y, INK, false);
            y += 10;
        }
    }
}
