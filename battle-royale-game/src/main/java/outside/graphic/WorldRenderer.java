package outside.graphic;

import static outside.graphic.GraphicUtilities.*;

import java.util.HashMap;
import java.util.List;

import inside.BoardSnapshot;
import inside.BoardSnapshot.BulletState;
import inside.BoardSnapshot.PlayerState;
import inside.Direction;
import inside.IConfig;
import inside.GameMap;
import inside.Obstacle;
import inside.SafeZone;
import inside.geometry.Rectangle;

/**
 * Dessin du monde : sol, obstacles, lave, zone sûre, joueurs, projectiles et coups d'épée.
 * Les positions sont interpolées entre les deux dernières images de la simulation.
 * @author mourtaza
 */
public class WorldRenderer implements IConfig {
	/**
	 * Taille d'affichage d'un emoji de joueur
	 */
	private static final float AVATAR_SIZE = 26;
	/**
	 * Durée du clignotement après des dégâts, en pas de simulation
	 */
	private static final int HIT_FLASH_TICKS = 10;

	private final TextureManager TEXTURES;
	private final Font LABEL_FONT;
	private final java.util.Map<Integer, PlayerState> PREVIOUS_PLAYERS = new HashMap<>();
	private final java.util.Map<Integer, BulletState> PREVIOUS_BULLETS = new HashMap<>();


	/**
	 * Construit le dessinateur du monde
	 * @param textures Textures
	 * @param labelFont Police des pseudos
	 */
	public WorldRenderer(TextureManager textures, Font labelFont) {
		TEXTURES = textures;
		LABEL_FONT = labelFont;
	}

	/**
	 * Dessine le monde
	 * @param map Carte (obstacles immuables)
	 * @param previous Image précédente (peut être null)
	 * @param current Image courante
	 * @param alpha Avancement entre les deux images (0 à 1)
	 * @param time Instant courant (secondes, pour les animations)
	 * @param localId Identifiant du joueur local (-1 si aucun)
	 * @param effects Effets à dessiner au-dessus des joueurs
	 * @param numberFont Police des nombres de dégâts
	 */
	public void render(GameMap map, BoardSnapshot previous, BoardSnapshot current, float alpha, double time, int localId,
			Effects effects, Font numberFont) {
		PREVIOUS_PLAYERS.clear();
		PREVIOUS_BULLETS.clear();
		if (previous != null) {
			for (PlayerState p : previous.players()) PREVIOUS_PLAYERS.put(p.id(), p);
			for (BulletState b : previous.bullets()) PREVIOUS_BULLETS.put(b.id(), b);
		}

		renderGround();
		if (map != null) renderObstacles(map.getObstacles(), time);

		Rectangle zone = lerp(previous != null ? previous.zone() : current.zone(), current.zone(), alpha);
		renderLava(zone, time, current);
		renderZone(zone, current, time);

		for (PlayerState p : current.players())
			if (!p.alive()) renderDeadPlayer(p);

		renderBullets(current.bullets(), alpha);

		for (PlayerState p : current.players())
			if (p.alive()) renderSwing(p, interpolated(p, alpha));

		for (PlayerState p : current.players())
			if (p.alive()) renderPlayer(p, interpolated(p, alpha), current.tick(), time, p.id() == localId, effects);

		effects.render(TEXTURES.getGlow(), numberFont);
	}

	/**
	 * Dessine le sol
	 */
	private void renderGround() {
		tiled(TEXTURES.getGround(), 0, 0, MAP_WIDTH, MAP_HEIGHT, 150, 0, 0, Color.WHITE);
		// Vignettage léger vers les bords
		Color edge = new Color(0, 0, 0, 0.18f), none = new Color(0, 0, 0, 0);
		gradientRect(0, 0, MAP_WIDTH, 60, edge, none);
		gradientRect(0, MAP_HEIGHT - 60, MAP_WIDTH, MAP_HEIGHT, none, edge);
		gradientLine(0, MAP_HEIGHT / 2, 60, MAP_HEIGHT / 2, MAP_HEIGHT, edge, none);
		gradientLine(MAP_WIDTH, MAP_HEIGHT / 2, MAP_WIDTH - 60, MAP_HEIGHT / 2, MAP_HEIGHT, edge, none);
	}

	/**
	 * Dessine les obstacles en répétant leur tuile
	 * @param obstacles Obstacles
	 */
	private void renderObstacles(List<Obstacle> obstacles, double time) {
		float waterOX = (float)(time * 0.030), waterOY = (float)(time * 0.017);
		for (int pass = 0; pass < 3; pass++) {
			for (Obstacle o : obstacles) {
				int order = switch (o.getType()) { case EAU -> 0; case ROCHER -> 1; case FORET -> 2; };
				if (order != pass) continue;

				Rectangle r = o.getRepresentation();
				if (o.getType() == Obstacle.TypeObstacle.EAU) {
					// Eau : fond profond, texture en tuiles qui défile lentement, liseré de mousse doux
					fillRect(r.expand(3), Color.rgb(0x0E2E40).withAlpha(0.9f));
					tiled(TEXTURES.getWater(), r.getX1(), r.getY1(), r.getX2(), r.getY2(), 60, waterOX, waterOY,
						new Color(0.82f, 0.9f, 0.92f, 1));
					Color foam = new Color(0.82f, 0.95f, 1f, 0.4f), none = foam.withAlpha(0);
					float f = 5;
					gradientRect(r.getX1(), r.getY1() - f, r.getX2(), r.getY1(), none, foam);
					gradientRect(r.getX1(), r.getY2(), r.getX2(), r.getY2() + f, foam, none);
					gradientLine(r.getX1(), r.getCenterY(), r.getX1() - f, r.getCenterY(), r.getHeight(), foam, none);
					gradientLine(r.getX2(), r.getCenterY(), r.getX2() + f, r.getCenterY(), r.getHeight(), foam, none);
					strokeRect(r, 2, foam.withAlpha(0.7f));
					continue;
				}

				glow(TEXTURES.getGlow(), r.getCenterX() + 4, r.getCenterY() + 6, r.getWidth() * 0.62f, r.getHeight() * 0.62f,
					new Color(0, 0, 0, 0.35f));

				SubTexture tile = TEXTURES.getSubTexture(o.getTextureNumber());
				int cols = Math.max(1, Math.round(r.getWidth() / tile.width())), rows = Math.max(1, Math.round(r.getHeight() / tile.height()));
				float tw = r.getWidth() / cols, th = r.getHeight() / rows;
				for (int i = 0; i < rows; i++)
					for (int j = 0; j < cols; j++)
						texture(tile, r.getX1() + j * tw, r.getY1() + i * th, r.getX1() + (j + 1) * tw, r.getY1() + (i + 1) * th, Color.WHITE);
			}
		}
	}

	/**
	 * Dessine la lave animée hors de la zone sûre
	 * @param zone Zone sûre
	 * @param time Instant courant
	 * @param s Image courante
	 */
	private void renderLava(Rectangle zone, double time, BoardSnapshot s) {
		if (zone.getX1() <= 0.5f && zone.getY1() <= 0.5f && zone.getX2() >= MAP_WIDTH - 0.5f && zone.getY2() >= MAP_HEIGHT - 0.5f) return;

		float ox = (float)(time * 0.035), oy = (float)(time * 0.021);
		float pulse = 0.85f + 0.15f * (float)Math.sin(time * 2.4);
		Rectangle[] parts = lavaParts(zone);

		for (Rectangle r : parts) {
			tiled(TEXTURES.getLava(), r.getX1(), r.getY1(), r.getX2(), r.getY2(), 190, ox, oy, new Color(1, 1, 1, 0.94f));
			additive(true);
			tiled(TEXTURES.getLava(), r.getX1(), r.getY1(), r.getX2(), r.getY2(), 90, -oy * 1.7f, ox * 1.3f, new Color(1, 0.45f, 0.1f, 0.25f * pulse));
			additive(false);
		}

		// Lueur le long du bord de la zone, côté lave
		float g = 14;
		Color hot = new Color(1, 0.75f, 0.2f, 0.55f * pulse), none = new Color(1, 0.4f, 0, 0);
		additive(true);
		gradientRect(zone.getX1(), zone.getY1() - g, zone.getX2(), zone.getY1(), none, hot);
		gradientRect(zone.getX1(), zone.getY2(), zone.getX2(), zone.getY2() + g, hot, none);
		gradientLine(zone.getX1(), zone.getCenterY(), zone.getX1() - g, zone.getCenterY(), zone.getHeight(), hot, none);
		gradientLine(zone.getX2(), zone.getCenterY(), zone.getX2() + g, zone.getCenterY(), zone.getHeight(), hot, none);
		additive(false);

		// Assombrit la lave vers les bords extérieurs de la carte
		Color edge = new Color(0.12f, 0.01f, 0, 0.4f), clear = new Color(0.12f, 0.01f, 0, 0);
		float e = 50;
		gradientRect(0, 0, MAP_WIDTH, e, edge, clear);
		gradientRect(0, MAP_HEIGHT - e, MAP_WIDTH, MAP_HEIGHT, clear, edge);
		gradientLine(0, MAP_HEIGHT / 2, e, MAP_HEIGHT / 2, MAP_HEIGHT, edge, clear);
		gradientLine(MAP_WIDTH, MAP_HEIGHT / 2, MAP_WIDTH - e, MAP_HEIGHT / 2, MAP_HEIGHT, edge, clear);
	}

	/**
	 * Découpe la carte hors de la zone sûre en quatre rectangles
	 * @param zone Zone sûre
	 * @return Rectangles de lave (éventuellement vides)
	 */
	static Rectangle[] lavaParts(Rectangle zone) {
		float x1 = Math.max(0, zone.getX1()), y1 = Math.max(0, zone.getY1());
		float x2 = Math.min(MAP_WIDTH, zone.getX2()), y2 = Math.min(MAP_HEIGHT, zone.getY2());
		return new Rectangle[] {
			new Rectangle(0, 0, MAP_WIDTH, y1),
			new Rectangle(0, y2, MAP_WIDTH, MAP_HEIGHT),
			new Rectangle(0, y1, x1, y2),
			new Rectangle(x2, y1, MAP_WIDTH, y2)
		};
	}

	/**
	 * Dessine le bord de la zone sûre et la prochaine zone
	 * @param zone Zone sûre interpolée
	 * @param s Image courante
	 * @param time Instant courant
	 */
	private void renderZone(Rectangle zone, BoardSnapshot s, double time) {
		if (s.zoneStage() == SafeZone.Stage.INACTIVE) return;

		Rectangle next = s.nextZone();
		boolean upcoming = (s.zoneStage() == SafeZone.Stage.WAITING || s.zoneStage() == SafeZone.Stage.SHRINKING) && !next.equals(zone);
		if (upcoming) {
			float blink = s.zoneStage() == SafeZone.Stage.SHRINKING ? 0.9f : 0.55f + 0.25f * (float)Math.sin(time * 4);
			dashedRect(next, 2, 12, (float)(time * 30), Color.WHITE.withAlpha(blink));
		}

		strokeRect(zone.expand(2), 6, Color.CYAN.withAlpha(0.18f));
		strokeRect(zone, 2.5f, Color.CYAN.mix(Color.WHITE, 0.4f).withAlpha(0.95f));

		// Pointillés fluides qui avancent le long du bord de la zone courante
		dashedRect(zone, 3.5f, 16, (float)(time * 42), Color.CYAN.mix(Color.WHITE, 0.55f).withAlpha(0.8f));

		// Coins lumineux pulsants
		float pulse = 0.55f + 0.45f * (float)Math.sin(time * 5);
		Color corner = Color.CYAN.mix(Color.WHITE, 0.5f).withAlpha(0.55f * pulse);
		additive(true);
		glow(TEXTURES.getGlow(), zone.getX1(), zone.getY1(), 16, 16, corner);
		glow(TEXTURES.getGlow(), zone.getX2(), zone.getY1(), 16, 16, corner);
		glow(TEXTURES.getGlow(), zone.getX1(), zone.getY2(), 16, 16, corner);
		glow(TEXTURES.getGlow(), zone.getX2(), zone.getY2(), 16, 16, corner);
		additive(false);
	}

	/**
	 * Dessine les projectiles avec leur traînée
	 * @param bullets Projectiles
	 * @param alpha Avancement de l'interpolation
	 */
	private void renderBullets(List<BulletState> bullets, float alpha) {
		additive(true);
		for (BulletState b : bullets) {
			BulletState prev = PREVIOUS_BULLETS.get(b.id());
			float x = prev != null ? lerp(prev.x(), b.x(), alpha) : b.x();
			float y = prev != null ? lerp(prev.y(), b.y(), alpha) : b.y();
			float tail = 26;
			gradientLine(x - b.dx() * tail, y - b.dy() * tail, x, y, 3, new Color(1, 0.8f, 0.3f, 0), new Color(1, 0.9f, 0.5f, 0.9f));
			glow(TEXTURES.getGlow(), x, y, 9, 9, new Color(1, 0.85f, 0.4f, 0.9f));
			disc(x, y, BULLET_RADIUS * 0.8f, Color.WHITE);
		}
		additive(false);
	}

	/**
	 * Dessine le coup d'épée d'un joueur
	 * @param p Joueur
	 * @param pos Position interpolée
	 */
	private void renderSwing(PlayerState p, float[] pos) {
		if (p.swingProgress() < 0) return;

		float t = p.swingProgress();
		float center = Direction.screenAngle(p.viewDirection());
		float from = center - MELEE_HALF_ANGLE, sweep = 2 * MELEE_HALF_ANGLE;
		float head = from + sweep * Math.min(1, t * 1.6f), tailAngle = from + sweep * Math.max(0, t * 1.6f - 0.7f);
		float a = 1 - t;

		additive(true);
		arc(pos[0], pos[1], AVATAR_SIZE * 0.55f, MELEE_RANGE, tailAngle, head, new Color(1, 1, 1, 0.05f * a), new Color(0.85f, 0.95f, 1, 0.75f * a));
		additive(false);
		float hx = pos[0] + (float)Math.cos(head) * MELEE_RANGE, hy = pos[1] + (float)Math.sin(head) * MELEE_RANGE;
		line(pos[0] + (float)Math.cos(head) * AVATAR_SIZE * 0.5f, pos[1] + (float)Math.sin(head) * AVATAR_SIZE * 0.5f, hx, hy, 3,
			new Color(0.9f, 0.95f, 1, 0.9f * a));
	}

	/**
	 * Dessine un joueur vivant : ombre, emoji, clignotement, regard, barre de vie et pseudo
	 * @param p Joueur
	 * @param pos Position interpolée
	 * @param tick Pas courant
	 * @param time Instant courant
	 * @param local true pour le joueur local
	 */
	private void renderPlayer(PlayerState p, float[] pos, long tick, double time, boolean local, Effects effects) {
		float x = pos[0], y = pos[1], h = AVATAR_SIZE / 2;
		glow(TEXTURES.getGlow(), x + 2, y + h - 2, h * 1.1f, h * 0.55f, new Color(0, 0, 0, 0.55f));

		if (p.inLava()) {
			float pulse = 0.5f + 0.5f * (float)Math.sin(time * 12);
			additive(true);
			glow(TEXTURES.getGlow(), x, y, h * 2.2f, h * 2.2f, new Color(1, 0.3f, 0.05f, 0.35f + 0.35f * pulse));
			additive(false);
		}

		Color ring = local ? Color.GOLD : Color.fromId(p.id());
		arc(x, y, h + 1, h + (local ? 4 : 2.5f), 0, (float)(2 * Math.PI), ring.withAlpha(0.95f), ring.withAlpha(0.95f));

		SubTexture avatar = TEXTURES.getPlayerTexture(p.id(), false);
		texture(avatar, x - h, y - h, x + h, y + h, Color.WHITE);

		long sinceHit = p.lastHitTick() < 0 ? Long.MAX_VALUE : tick - p.lastHitTick();
		if (sinceHit < HIT_FLASH_TICKS) {
			float f = 1 - sinceHit / (float)HIT_FLASH_TICKS;
			additive(true);
			texture(avatar, x - h, y - h, x + h, y + h, new Color(1, 0.25f, 0.2f, 0.9f * f));
			additive(false);
		}

		// Indicateur directionnel des derniers dégâts subis (joueur local)
		if (local && effects.damageAlpha() > 0) {
			float da = effects.damageAlpha(), dAngle = effects.damageAngle();
			additive(true);
			arc(x, y, h + 9, h + 20, dAngle - 0.55f, dAngle + 0.55f,
				new Color(1, 0.15f, 0.1f, 0), new Color(1, 0.15f, 0.1f, 0.85f * da));
			additive(false);
		}

		// Indicateur de regard
		float a = Direction.screenAngle(p.viewDirection()), r = h + 7;
		float px = x + (float)Math.cos(a) * r, py = y + (float)Math.sin(a) * r;
		float lx = x + (float)Math.cos(a + 0.35f) * (r - 5), ly = y + (float)Math.sin(a + 0.35f) * (r - 5);
		float rx = x + (float)Math.cos(a - 0.35f) * (r - 5), ry = y + (float)Math.sin(a - 0.35f) * (r - 5);
		color(ring);
		org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLES);
		org.lwjgl.opengl.GL11.glVertex2f(px, py);
		org.lwjgl.opengl.GL11.glVertex2f(lx, ly);
		org.lwjgl.opengl.GL11.glVertex2f(rx, ry);
		org.lwjgl.opengl.GL11.glEnd();

		// Barre de vie
		float ratio = p.life() / (float)MAX_LIFE_POINTS, bw = 30, by = y - h - 9;
		fillRect(x - bw / 2 - 1.5f, by - 1.5f, x + bw / 2 + 1.5f, by + 5.5f, new Color(0, 0, 0, 0.85f));
		fillRect(x - bw / 2, by, x - bw / 2 + bw * ratio, by + 4, Color.life(ratio));

		LABEL_FONT.drawShadowed(p.pseudo(), x, by - LABEL_FONT.getSize() - 3, local ? Color.GOLD : Color.WHITE, Font.Align.CENTER);
	}

	/**
	 * Dessine un joueur éliminé, grisé
	 * @param p Joueur
	 */
	private void renderDeadPlayer(PlayerState p) {
		float h = AVATAR_SIZE * 0.42f;
		texture(TEXTURES.getPlayerTexture(p.id(), true), p.x() - h, p.y() - h, p.x() + h, p.y() + h, new Color(1, 1, 1, 0.7f));
		Color c = new Color(0.9f, 0.2f, 0.2f, 0.75f);
		line(p.x() - h, p.y() - h, p.x() + h, p.y() + h, 2, c);
		line(p.x() + h, p.y() - h, p.x() - h, p.y() + h, 2, c);
	}

	/**
	 * Calcule la position interpolée d'un joueur
	 * @param p État courant
	 * @param alpha Avancement
	 * @return Position {x, y}
	 */
	private float[] interpolated(PlayerState p, float alpha) {
		PlayerState prev = PREVIOUS_PLAYERS.get(p.id());
		if (prev == null || !prev.alive()) return new float[] { p.x(), p.y() };
		return new float[] { lerp(prev.x(), p.x(), alpha), lerp(prev.y(), p.y(), alpha) };
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static Rectangle lerp(Rectangle a, Rectangle b, float t) {
		return a.lerp(b, t);
	}
}
