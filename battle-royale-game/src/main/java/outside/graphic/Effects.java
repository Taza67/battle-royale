package outside.graphic;

import static inside.IConfig.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import inside.BoardSnapshot;
import inside.BoardSnapshot.PlayerState;
import inside.DamageCause;
import inside.GameEvent;
import inside.geometry.Rectangle;

/**
 * Effets visuels éphémères de la vue : particules, nombres de dégâts et bandeaux.
 * Ils sont produits à partir des événements de la simulation et n'ont aucune influence sur elle.
 * @author mourtaza
 */
public class Effects {
	/**
	 * Particule
	 */
	private static final class Particle {
		float x, y, vx, vy, life, maxLife, size, drag, gravity, phase;
		Color color;
		boolean additive, square, ambient;
	}

	/**
	 * Texte flottant (dégâts)
	 */
	private static final class FloatingText {
		String text;
		float x, y, life, maxLife;
		Color color;
	}

	/**
	 * Bandeau affiché au centre de l'écran
	 * @param title Titre
	 * @param subtitle Sous-titre (peut être vide)
	 * @param color Couleur du titre
	 * @param start Instant d'apparition (secondes)
	 * @param duration Durée d'affichage (secondes)
	 */
	public record Banner(String title, String subtitle, Color color, double start, double duration) {
		/**
		 * Opacité du bandeau à un instant
		 * @param now Instant (secondes)
		 * @return Opacité entre 0 et 1
		 */
		public float alpha(double now) {
			double t = now - start;
			if (t < 0 || t > duration) return 0;
			return (float)Math.min(1, Math.min(t / 0.25, (duration - t) / 0.5));
		}
	}

	private static final int MAX_PARTICLES = 1500;
	/**
	 * Nombre maximal de particules d'ambiance (braises et pollen)
	 */
	private static final int MAX_AMBIENT = 140;
	/**
	 * Palette des confettis de victoire
	 */
	private static final Color[] CONFETTI = {
		Color.rgb(0xF94144), Color.rgb(0xF9C74F), Color.rgb(0x90BE6D),
		Color.rgb(0x43AA8B), Color.rgb(0x4D96FF), Color.rgb(0xC77DFF)
	};

	private final List<Particle> PARTICLES = new ArrayList<>();
	private final List<FloatingText> TEXTS = new ArrayList<>();
	private final Random RANDOM = new Random();
	private Banner banner;
	private double now;
	private int ambient;

	// Tremblement de caméra (dégâts subis par le joueur local)
	private double shakeEnd = -1, shakeDuration = 1;
	private float shakePower, shakeX, shakeY;

	// Indicateur directionnel des dégâts subis par le joueur local
	private double damageEnd = -1;
	private float damageAngle;


	/**
	 * Réinitialise les effets (nouvelle partie)
	 */
	public void clear() {
		PARTICLES.clear();
		TEXTS.clear();
		banner = null;
		ambient = 0;
		shakeEnd = -1;
		shakePower = 0;
		shakeX = shakeY = 0;
		damageEnd = -1;
	}

	/**
	 * Retourne le bandeau courant
	 * @return Bandeau ou null
	 */
	public Banner getBanner() {
		return banner != null && now - banner.start() <= banner.duration() ? banner : null;
	}

	/**
	 * Affiche un bandeau
	 * @param title Titre
	 * @param subtitle Sous-titre
	 * @param color Couleur
	 * @param duration Durée en secondes
	 */
	public void showBanner(String title, String subtitle, Color color, double duration) {
		banner = new Banner(title, subtitle, color, now, duration);
	}

	/**
	 * Produit les effets correspondant à des événements de la simulation
	 * @param events Événements
	 * @param s Image du plateau après les événements
	 * @param localId Identifiant du joueur local (-1 si aucun)
	 */
	public void consume(List<GameEvent> events, BoardSnapshot s, int localId) {
		for (GameEvent e : events) {
			switch (e.type()) {
			case SHOT:
				burst(e.x(), e.y(), 6, 60, 0.15f, 2.5f, Color.rgb(0xFFE38A), true);
				flash(e.x(), e.y(), 10, Color.rgb(0xFFF3C0));
				break;
			case HIT: {
				boolean melee = e.cause() == DamageCause.MELEE;
				burst(e.x(), e.y(), e.amount() > 0 ? 12 : 5, melee ? 120 : 90, 0.35f, 3, melee ? Color.WHITE : Color.rgb(0xFFB347), true);
				if (e.amount() > 0) {
					burst(e.x(), e.y(), 6, 50, 0.6f, 3.5f, Color.rgb(0xC0262D), false);
					text("-" + e.amount(), e.x(), e.y() - 18, e.targetId() == localId ? Color.RED : Color.rgb(0xFFF0A0));
				}
				if (e.targetId() == localId && e.amount() > 0) {
					shake(0.35f, 5);
					PlayerState source = s.player(e.actorId());
					if (source != null)
						damageFrom((float)Math.atan2(source.y() - e.y(), source.x() - e.x()));
				}
				break;
			}
			case BULLET_BLOCKED:
				burst(e.x(), e.y(), 7, 55, 0.4f, 3, Color.rgb(0xB8A68A), false);
				break;
			case ELIMINATION: {
				PlayerState victim = s.player(e.targetId());
				Color c = Color.fromId(e.targetId());
				burst(e.x(), e.y(), 40, 160, 0.9f, 4, c, true);
				burst(e.x(), e.y(), 18, 70, 1.2f, 6, Color.rgb(0x333333).withAlpha(0.8f), false);
				if (e.targetId() == localId) {
					shake(0.7f, 9);
					showBanner("Vous êtes éliminé", rankText(e.amount()) + " sur " + s.total(), Color.RED, 3.5);
				} else if (localId >= 0 && e.actorId() == localId && victim != null) {
					shake(0.25f, 3);
					showBanner("Élimination !", victim.pseudo(), Color.GOLD, 1.6);
				}
				break;
			}
			case GAME_OVER:
				if (localId >= 0 && e.actorId() == localId) {
					PlayerState winner = s.player(localId);
					if (winner != null) {
						burst(winner.x(), winner.y(), 70, 240, 1.4f, 5, Color.GOLD, true);
						burst(winner.x(), winner.y(), 45, 140, 1.8f, 4, Color.WHITE, true);
					}
					confetti(140);
					shake(0.4f, 4);
				}
				break;
			case BATTLE_STARTED:
				showBanner("Que le combat commence !", "Les dégâts sont activés", Color.GOLD, 2.8);
				break;
			case ZONE_SHRINKING:
				showBanner("La zone se resserre !", "Vague " + e.amount() + " - restez dans la zone", Color.ORANGE, 3);
				break;
			case ZONE_SHRUNK:
				if (e.amount() == s.waveCount())
					showBanner("Zone finale", "La lave recouvre tout", Color.RED, 3);
				break;
			case RESUMED:
				showBanner("Reprise", "", Color.WHITE, 1.2);
				break;
			default:
			}
		}
	}

	/**
	 * Fait avancer les effets
	 * @param time Instant courant (secondes)
	 * @param dt Durée écoulée (secondes)
	 */
	public void update(double time, float dt) {
		now = time;

		double remaining = shakeEnd - now;
		if (remaining > 0) {
			float k = (float)(remaining / shakeDuration);
			shakeX = shakePower * k * k * (float)Math.sin(now * 53);
			shakeY = shakePower * k * k * (float)Math.cos(now * 41);
		} else {
			shakeX = shakeY = 0;
			shakePower = 0;
		}

		ambient = 0;
		for (Particle p : PARTICLES) if (p.ambient) ambient++;

		Iterator<Particle> it = PARTICLES.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.life -= dt;
			if (p.life <= 0) {
				it.remove();
				continue;
			}
			float k = (float)Math.pow(p.drag, dt);
			p.vx *= k;
			p.vy *= k;
			p.vy += p.gravity * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
		}

		Iterator<FloatingText> ti = TEXTS.iterator();
		while (ti.hasNext()) {
			FloatingText t = ti.next();
			t.life -= dt;
			t.y -= 28 * dt;
			if (t.life <= 0) ti.remove();
		}
	}

	/**
	 * Dessine les particules et les textes flottants
	 * @param glow Texture de halo
	 * @param font Police des nombres
	 */
	public void render(Texture glow, Font font) {
		for (int pass = 0; pass < 2; pass++) {
			boolean additive = pass == 1;
			GraphicUtilities.additive(additive);
			for (Particle p : PARTICLES) {
				if (p.additive != additive) continue;
				float t = p.life / p.maxLife;
				float size = p.size * (additive ? 0.6f + 0.6f * t : 1.2f - 0.4f * t);
				Color c = p.color.withAlpha(p.color.a() * t);
				if (p.square) {
					GraphicUtilities.color(c);
					float a = p.phase + (float)now * 6;
					float ca = (float)Math.cos(a) * size, sa = (float)Math.sin(a) * size;
					org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
					org.lwjgl.opengl.GL11.glVertex2f(p.x - ca - sa, p.y - sa + ca);
					org.lwjgl.opengl.GL11.glVertex2f(p.x + ca - sa, p.y + sa + ca);
					org.lwjgl.opengl.GL11.glVertex2f(p.x + ca + sa, p.y + sa - ca);
					org.lwjgl.opengl.GL11.glVertex2f(p.x - ca + sa, p.y - sa - ca);
					org.lwjgl.opengl.GL11.glEnd();
				} else {
					GraphicUtilities.glow(glow, p.x, p.y, size * 2, size * 2, c);
				}
			}
		}
		GraphicUtilities.additive(false);

		for (FloatingText t : TEXTS) {
			float a = Math.min(1, t.life / (t.maxLife * 0.5f));
			font.drawShadowed(t.text, t.x, t.y, t.color.withAlpha(a), Font.Align.CENTER);
		}
	}

	/**
	 * Décalement horizontal du tremblement de caméra
	 * @return Décalage en unités logiques
	 */
	public float shakeX() { return shakeX; }
	/**
	 * Décalement vertical du tremblement de caméra
	 * @return Décalage en unités logiques
	 */
	public float shakeY() { return shakeY; }

	/**
	 * Opacité de l'indicateur de dégâts subis (0 si inactif)
	 * @return Opacité entre 0 et 1
	 */
	public float damageAlpha() {
		double remaining = damageEnd - now;
		return remaining > 0 ? (float)Math.min(1, remaining / 0.45) : 0;
	}

	/**
	 * Direction de la source des derniers dégâts subis (angle à l'écran)
	 * @return Angle en radians
	 */
	public float damageAngle() { return damageAngle; }

	/**
	 * Déclenche un tremblement de caméra
	 * @param duration Durée en secondes
	 * @param power Amplitude en unités logiques
	 */
	private void shake(float duration, float power) {
		shakeEnd = Math.max(shakeEnd, now + duration);
		shakeDuration = Math.max(0.1, shakeEnd - now);
		shakePower = Math.max(shakePower, power);
	}

	/**
	 * Mémorise la direction d'une source de dégâts pour l'indicateur à l'écran
	 * @param angle Angle vers la source (repère écran)
	 */
	private void damageFrom(float angle) {
		damageAngle = angle;
		damageEnd = now + 0.6;
	}

	/**
	 * Fait naître les particules d'ambiance : braises au-dessus de la lave et pollen sur l'herbe
	 * @param dt Durée écoulée (secondes)
	 * @param zone Zone sûre courante (la lave recouvre le reste de la carte)
	 */
	public void ambient(float dt, Rectangle zone) {
		if (ambient >= MAX_AMBIENT || PARTICLES.size() >= MAX_PARTICLES) return;

		// Quelques braises par seconde au-dessus de la lave, proportionnelles à sa surface
		float lavaArea = Math.max(0, MAP_WIDTH * MAP_HEIGHT - zone.getWidth() * zone.getHeight());
		if (lavaArea > 1000 && RANDOM.nextFloat() < dt * 18) spawnEmber(zone);
		// Fines particules de pollen dérivant dans la zone sûre
		if (RANDOM.nextFloat() < dt * 26) spawnPollen(zone);
	}

	/**
	 * Fait apparaître une braise qui monte dans la lave, hors de la zone sûre
	 * @param zone Zone sûre
	 */
	private void spawnEmber(Rectangle zone) {
		for (int i = 0; i < 6; i++) {
			float x = RANDOM.nextFloat() * MAP_WIDTH;
			float y = RANDOM.nextFloat() * MAP_HEIGHT;
			if (x >= zone.getX1() && x <= zone.getX2() && y >= zone.getY1() && y <= zone.getY2())
				continue;
			Particle p = new Particle();
			p.x = x;
			p.y = y;
			p.vx = (RANDOM.nextFloat() - 0.5f) * 16;
			p.vy = -22 - RANDOM.nextFloat() * 30;
			p.maxLife = p.life = 1.6f + RANDOM.nextFloat() * 1.4f;
			p.size = 1.6f + RANDOM.nextFloat() * 1.6f;
			p.drag = 0.7f;
			p.color = new Color(1f, 0.45f + RANDOM.nextFloat() * 0.3f, 0.08f, 0.8f);
			p.additive = true;
			p.ambient = true;
			PARTICLES.add(p);
			ambient++;
			return;
		}
	}

	/**
	 * Fait apparaître une particule de pollen dérivant dans la zone sûre
	 * @param zone Zone sûre
	 */
	private void spawnPollen(Rectangle zone) {
		float x = zone.getX1() + RANDOM.nextFloat() * zone.getWidth();
		float y = zone.getY1() + RANDOM.nextFloat() * zone.getHeight();
		Particle p = new Particle();
		p.x = x;
		p.y = y;
		p.vx = (RANDOM.nextFloat() - 0.5f) * 24;
		p.vy = -6 + (RANDOM.nextFloat() - 0.5f) * 10;
		p.maxLife = p.life = 2.5f + RANDOM.nextFloat() * 1.5f;
		p.size = 0.9f + RANDOM.nextFloat() * 1.2f;
		p.drag = 0.85f;
		p.phase = RANDOM.nextFloat() * 6.28f;
		p.color = new Color(0.95f, 1f, 0.72f, 0.5f);
		p.additive = true;
		p.ambient = true;
		PARTICLES.add(p);
		ambient++;
	}

	/**
	 * Ajoute un éclair bref et lumineux (bouche du canon)
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param size Taille
	 * @param color Couleur
	 */
	private void flash(float x, float y, float size, Color color) {
		if (PARTICLES.size() >= MAX_PARTICLES) return;
		Particle p = new Particle();
		p.x = x;
		p.y = y;
		p.maxLife = p.life = 0.1f;
		p.size = size;
		p.drag = 1;
		p.color = color;
		p.additive = true;
		PARTICLES.add(p);
	}

	/**
	 * Fait pleuvoir des confettis multicolores sur la carte (victoire locale)
	 * @param count Nombre de confettis
	 */
	private void confetti(int count) {
		for (int i = 0; i < count && PARTICLES.size() < MAX_PARTICLES; i++) {
			Particle p = new Particle();
			p.x = RANDOM.nextFloat() * MAP_WIDTH;
			p.y = -20 - RANDOM.nextFloat() * 220;
			p.vx = (RANDOM.nextFloat() - 0.5f) * 60;
			p.vy = 60 + RANDOM.nextFloat() * 70;
			p.maxLife = p.life = 2.5f + RANDOM.nextFloat() * 1.8f;
			p.size = 2.2f + RANDOM.nextFloat() * 1.8f;
			p.drag = 0.9f;
			p.gravity = 55;
			p.phase = RANDOM.nextFloat() * 6.28f;
			p.color = CONFETTI[RANDOM.nextInt(CONFETTI.length)];
			p.square = true;
			PARTICLES.add(p);
		}
	}

	/**
	 * Projette des particules depuis un point
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param count Nombre de particules
	 * @param speed Vitesse maximale
	 * @param life Durée de vie
	 * @param size Taille
	 * @param color Couleur
	 * @param additive true pour une particule lumineuse
	 */
	private void burst(float x, float y, int count, float speed, float life, float size, Color color, boolean additive) {
		for (int i = 0; i < count && PARTICLES.size() < MAX_PARTICLES; i++) {
			Particle p = new Particle();
			double a = RANDOM.nextDouble() * Math.PI * 2;
			float v = speed * (0.3f + 0.7f * RANDOM.nextFloat());
			p.x = x;
			p.y = y;
			p.vx = (float)Math.cos(a) * v;
			p.vy = (float)Math.sin(a) * v;
			p.maxLife = p.life = life * (0.6f + 0.4f * RANDOM.nextFloat());
			p.size = size * (0.7f + 0.6f * RANDOM.nextFloat());
			p.drag = 0.02f;
			p.color = color;
			p.additive = additive;
			PARTICLES.add(p);
		}
	}

	/**
	 * Ajoute un texte flottant
	 * @param s Texte
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param color Couleur
	 */
	private void text(String s, float x, float y, Color color) {
		FloatingText t = new FloatingText();
		t.text = s;
		t.x = x + RANDOM.nextFloat() * 10 - 5;
		t.y = y;
		t.maxLife = t.life = 0.9f;
		t.color = color;
		TEXTS.add(t);
	}

	/**
	 * Écrit un classement en toutes lettres
	 * @param rank Classement
	 * @return Texte (« 1er », « 2e »…)
	 */
	public static String rankText(int rank) {
		return rank == 1 ? "1er" : rank + "e";
	}
}
