package inside;

import static inside.IConfig.*;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import inside.GameSettings.ZoneWave;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;

/**
 * Classe représentant la zone sûre du battle royale : elle se resserre par vagues
 * (attente, rétrécissement, attente...) vers une prochaine zone tirée au hasard dans la zone actuelle.
 * Hors de la zone, la lave inflige des dégâts croissants à chaque vague.
 * @author mourtaza
 *
 * @see GameSettings.ZoneWave
 */
public class SafeZone {
	/**
	 * Étapes possibles de la zone
	 */
	public enum Stage {
		/**
		 * GridCell pas encore active (échauffement)
		 */
		INACTIVE,
		/**
		 * Attente avant le prochain rétrécissement
		 */
		WAITING,
		/**
		 * Rétrécissement en cours
		 */
		SHRINKING,
		/**
		 * Toutes les vagues sont terminées
		 */
		CLOSED
	}

	/**
	 * Changements d'étape signalés par la mise à jour de la zone
	 */
	public enum Transition {
		/**
		 * Aucun changement
		 */
		NONE,
		/**
		 * Un rétrécissement vient de commencer
		 */
		SHRINK_STARTED,
		/**
		 * Un rétrécissement vient de se terminer, une nouvelle attente commence
		 */
		SHRINK_ENDED,
		/**
		 * La dernière vague vient de se terminer
		 */
		CLOSED
	}

	/**
	 * Nombre d'essais pour placer la prochaine zone sur un emplacement libre
	 */
	private static final int TARGET_ATTEMPTS = 30;

	/**
	 * Vagues de la zone
	 */
	private final List<ZoneWave> WAVES;
	/**
	 * Générateur aléatoire de la partie
	 */
	private final Random RANDOM;
	/**
	 * Condition sur le centre de la prochaine zone (par exemple, pas dans un obstacle)
	 */
	private final Predicate<Vertice> PREFERRED_CENTER;
	/**
	 * Zone sûre actuelle
	 */
	private Rectangle current;
	/**
	 * GridCell au début du rétrécissement en cours
	 */
	private Rectangle start;
	/**
	 * Prochaine zone sûre
	 */
	private Rectangle target;
	/**
	 * Indice de la vague en cours (-1 avant le début)
	 */
	private int waveIndex = -1;
	/**
	 * Étape en cours
	 */
	private Stage stage = Stage.INACTIVE;
	/**
	 * Nombre de pas écoulés dans l'étape
	 */
	private long stageTicks;
	/**
	 * Durée de l'étape, en pas de simulation
	 */
	private long stageDuration;


	/**
	 * Construit une zone sûre couvrant initialement toute la carte
	 * @param bounds Limites de la carte
	 * @param waves Vagues (au moins une)
	 * @param random Générateur aléatoire
	 * @param preferredCenter Condition sur le centre des prochaines zones (peut être null)
	 */
	public SafeZone(Rectangle bounds, List<ZoneWave> waves, Random random, Predicate<Vertice> preferredCenter) {
		WAVES = List.copyOf(waves);
		RANDOM = random;
		PREFERRED_CENTER = preferredCenter;
		current = bounds;
		start = bounds;
		// La première cible est tirée dès la construction pour que getNext() soit
		// valable pendant l'échauffement ; start() se contente de l'activer
		target = WAVES.isEmpty() ? bounds : pickTarget(bounds, WAVES.get(0).targetScale());
	}


	/**
	 * Retourne la zone sûre actuelle
	 * @return Rectangle de la zone
	 */
	public Rectangle getCurrent() { return current; }
	/**
	 * Retourne la prochaine zone sûre (la cible de la prochaine vague, valable dès
	 * l'échauffement ; la zone actuelle si aucune n'est prévue)
	 * @return Rectangle de la prochaine zone
	 */
	public Rectangle getNext() {
		return stage == Stage.INACTIVE || stage == Stage.WAITING || stage == Stage.SHRINKING ? target : current;
	}
	/**
	 * Retourne l'étape en cours
	 * @return Étape
	 */
	public Stage getStage() { return stage; }
	/**
	 * Retourne l'indice de la vague en cours
	 * @return Indice, -1 avant le début
	 */
	public int getWaveIndex() { return waveIndex; }
	/**
	 * Retourne le nombre de vagues
	 * @return Nombre de vagues
	 */
	public int getWaveCount() { return WAVES.size(); }

	/**
	 * Retourne le nombre de secondes avant la prochaine étape
	 * @return Secondes (arrondies au supérieur), 0 si aucune étape n'est prévue
	 */
	public int getSecondsLeft() {
		if (stage != Stage.WAITING && stage != Stage.SHRINKING) return 0;
		long left = Math.max(0, stageDuration - stageTicks);
		return (int)((left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
	}

	/**
	 * Retourne les dégâts par seconde infligés hors de la zone
	 * @return Dégâts par seconde, 0 si la zone n'est pas active
	 */
	public int getLavaDamagePerSecond() {
		if (waveIndex < 0) return 0;
		return WAVES.get(Math.min(waveIndex, WAVES.size() - 1)).lavaDamagePerSecond();
	}

	/**
	 * Vérifie si un point est dans la zone sûre
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @return true si le point est protégé de la lave
	 */
	public boolean isSafe(float x, float y) {
		return current.contains(x, y);
	}


	/**
	 * Active la zone (début du combat) : la première attente commence
	 */
	public void start() {
		if (stage != Stage.INACTIVE) return;
		waveIndex = 0;
		stage = Stage.WAITING;
		stageTicks = 0;
		stageDuration = Math.max(1, toTicks(WAVES.get(0).waitSeconds()));
		// La cible de la première vague a déjà été tirée à la construction
	}

	/**
	 * Fait avancer la zone d'un pas de simulation
	 * @return Changement d'étape éventuel
	 */
	public Transition update() {
		if (stage == Stage.INACTIVE || stage == Stage.CLOSED) return Transition.NONE;

		stageTicks++;

		if (stage == Stage.WAITING) {
			if (stageTicks < stageDuration) return Transition.NONE;
			stage = Stage.SHRINKING;
			start = current;
			stageTicks = 0;
			stageDuration = toTicks(WAVES.get(waveIndex).shrinkSeconds());
			// Un rétrécissement instantané (0 s) émet quand même SHRINK_STARTED :
			// il se terminera au pas suivant, la paire d'événements reste équilibrée
			return Transition.SHRINK_STARTED;
		}

		if (stageDuration > 0 && stageTicks < stageDuration) {
			current = start.lerp(target, stageTicks / (float)stageDuration);
			return Transition.NONE;
		}

		current = target;
		if (waveIndex + 1 < WAVES.size()) {
			waveIndex++;
			beginWait();
			return Transition.SHRINK_ENDED;
		}

		stage = Stage.CLOSED;
		return Transition.CLOSED;
	}

	/**
	 * Commence l'attente de la vague en cours et tire la prochaine zone
	 */
	private void beginWait() {
		ZoneWave wave = WAVES.get(waveIndex);
		stage = Stage.WAITING;
		stageTicks = 0;
		stageDuration = Math.max(1, toTicks(wave.waitSeconds()));
		target = pickTarget(current, wave.targetScale());
	}

	/**
	 * Tire une zone de la taille demandée à l'intérieur d'une zone
	 * @param inside GridCell englobante
	 * @param scale Taille visée, proportionnellement à la carte
	 * @return Nouvelle zone incluse dans la zone englobante
	 */
	private Rectangle pickTarget(Rectangle inside, float scale) {
		float w = Math.min(MAP_WIDTH * Math.max(0, scale), inside.getWidth()),
			  h = Math.min(MAP_HEIGHT * Math.max(0, scale), inside.getHeight());
		Rectangle candidate = null;

		for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
			float x1 = inside.getX1() + RANDOM.nextFloat() * (inside.getWidth() - w),
				  y1 = inside.getY1() + RANDOM.nextFloat() * (inside.getHeight() - h);
			candidate = new Rectangle(x1, y1, x1 + w, y1 + h);
			if (PREFERRED_CENTER == null || PREFERRED_CENTER.test(candidate.getCenter()))
				break;
		}

		return candidate;
	}

	/**
	 * Convertit une durée en nombre de pas de simulation
	 * @param seconds Durée en secondes
	 * @return Nombre de pas
	 */
	private static long toTicks(float seconds) {
		return Math.round(Math.max(0, seconds) * TICKS_PER_SECOND);
	}
}
