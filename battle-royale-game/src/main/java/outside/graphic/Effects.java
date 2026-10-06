package outside.graphic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import inside.BoardSnapshot;
import inside.BoardSnapshot.PlayerState;
import inside.DamageCause;
import inside.GameEvent;

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
		float x, y, vx, vy, life, maxLife, size, drag;
		Color color;
		boolean additive;
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

	private final List<Particle> PARTICLES = new ArrayList<>();
	private final List<FloatingText> TEXTS = new ArrayList<>();
	private final Random RANDOM = new Random();
	private Banner banner;
	private double now;


	/**
	 * Réinitialise les effets (nouvelle partie)
	 */
	public void clear() {
		PARTICLES.clear();
		TEXTS.clear();
		banner = null;
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
				burst(e.x(), e.y(), 5, 60, 0.15f, 2.5f, Color.rgb(0xFFE38A), true);
				break;
			case HIT: {
				boolean melee = e.cause() == DamageCause.MELEE;
				burst(e.x(), e.y(), e.amount() > 0 ? 12 : 5, melee ? 120 : 90, 0.35f, 3, melee ? Color.WHITE : Color.rgb(0xFFB347), true);
				if (e.amount() > 0) {
					burst(e.x(), e.y(), 6, 50, 0.6f, 3.5f, Color.rgb(0xC0262D), false);
					text("-" + e.amount(), e.x(), e.y() - 18, e.targetId() == localId ? Color.RED : Color.rgb(0xFFF0A0));
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
				if (e.targetId() == localId)
					showBanner("Vous êtes éliminé", rankText(e.amount()) + " sur " + s.total(), Color.RED, 3.5);
				else if (localId >= 0 && e.actorId() == localId && victim != null)
					showBanner("Élimination !", victim.pseudo(), Color.GOLD, 1.6);
				break;
			}
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
				GraphicUtilities.glow(glow, p.x, p.y, size * 2, size * 2, p.color.withAlpha(p.color.a() * t));
			}
		}
		GraphicUtilities.additive(false);

		for (FloatingText t : TEXTS) {
			float a = Math.min(1, t.life / (t.maxLife * 0.5f));
			font.drawShadowed(t.text, t.x, t.y, t.color.withAlpha(a), Font.Align.CENTER);
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
