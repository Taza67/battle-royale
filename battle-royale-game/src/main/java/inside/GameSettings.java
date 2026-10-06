package inside;

import java.util.List;

/**
 * Classe regroupant les réglages d'une partie (durée d'échauffement, graine aléatoire, vagues de la zone sûre)
 * @author mourtaza
 *
 */
public final class GameSettings implements IConfig {
	/**
	 * Description d'une vague de rétrécissement de la zone sûre
	 * @param waitSeconds Durée d'attente avant le rétrécissement, en secondes
	 * @param shrinkSeconds Durée du rétrécissement, en secondes
	 * @param targetScale Taille de la zone visée, proportionnellement à la carte (0 = fermeture totale)
	 * @param lavaDamagePerSecond Dégâts infligés par seconde hors de la zone pendant cette vague
	 */
	public record ZoneWave(float waitSeconds, float shrinkSeconds, float targetScale, int lavaDamagePerSecond) {}

	/**
	 * Durée par défaut de l'échauffement, en secondes
	 */
	public static final int DEFAULT_WARMUP_SECONDS = 12;

	/**
	 * Vagues par défaut : la zone se resserre jusqu'à une petite zone finale, puis se referme complètement
	 */
	public static final List<ZoneWave> DEFAULT_WAVES = List.of(
		new ZoneWave(15, 12, 0.70f, 3),
		new ZoneWave(15, 12, 0.48f, 5),
		new ZoneWave(15, 10, 0.32f, 8),
		new ZoneWave(12, 9, 0.20f, 12),
		new ZoneWave(10, 8, 0.11f, 16),
		new ZoneWave(15, 12, 0f, 25));

	/**
	 * Durée de l'échauffement, en secondes
	 */
	private final float warmupSeconds;
	/**
	 * Graine du générateur aléatoire de la partie
	 */
	private final long seed;
	/**
	 * Nombre d'obstacles à générer
	 */
	private final int obstaclesNumber;
	/**
	 * Vagues de la zone sûre
	 */
	private final List<ZoneWave> waves;


	/**
	 * Construit des réglages
	 * @param warmupSeconds Durée de l'échauffement, en secondes
	 * @param seed Graine aléatoire
	 * @param obstaclesNumber Nombre d'obstacles
	 * @param waves Vagues de la zone sûre (au moins une)
	 */
	public GameSettings(float warmupSeconds, long seed, int obstaclesNumber, List<ZoneWave> waves) {
		if (waves == null || waves.isEmpty())
			throw new IllegalArgumentException("Au moins une vague est nécessaire");
		this.warmupSeconds = Math.max(0, warmupSeconds);
		this.seed = seed;
		this.obstaclesNumber = Math.max(0, obstaclesNumber);
		this.waves = List.copyOf(waves);
	}

	/**
	 * Retourne les réglages par défaut avec une graine donnée
	 * @param seed Graine aléatoire
	 * @return Réglages par défaut
	 */
	public static GameSettings defaults(long seed) {
		return new GameSettings(DEFAULT_WARMUP_SECONDS, seed, OBSTACLES_NUMBER, DEFAULT_WAVES);
	}


	/**
	 * Retourne la durée de l'échauffement
	 * @return Durée en secondes
	 */
	public float getWarmupSeconds() { return warmupSeconds; }
	/**
	 * Retourne la durée de l'échauffement en pas de simulation
	 * @return Nombre de pas
	 */
	public long getWarmupTicks() { return Math.round(warmupSeconds * TICKS_PER_SECOND); }
	/**
	 * Retourne la graine aléatoire
	 * @return Graine
	 */
	public long getSeed() { return seed; }
	/**
	 * Retourne le nombre d'obstacles
	 * @return Nombre d'obstacles
	 */
	public int getObstaclesNumber() { return obstaclesNumber; }
	/**
	 * Retourne les vagues de la zone sûre
	 * @return Liste non modifiable
	 */
	public List<ZoneWave> getWaves() { return waves; }

	/**
	 * Retourne une copie des réglages avec une autre durée d'échauffement
	 * @param seconds Durée en secondes
	 * @return Nouveaux réglages
	 */
	public GameSettings withWarmup(float seconds) {
		return new GameSettings(seconds, seed, obstaclesNumber, waves);
	}

	/**
	 * Retourne une copie des réglages avec une autre graine
	 * @param newSeed Graine
	 * @return Nouveaux réglages
	 */
	public GameSettings withSeed(long newSeed) {
		return new GameSettings(warmupSeconds, newSeed, obstaclesNumber, waves);
	}

	/**
	 * Retourne une copie des réglages avec un autre nombre d'obstacles
	 * @param number Nombre d'obstacles
	 * @return Nouveaux réglages
	 */
	public GameSettings withObstacles(int number) {
		return new GameSettings(warmupSeconds, seed, number, waves);
	}

	/**
	 * Retourne une copie des réglages avec d'autres vagues
	 * @param newWaves Vagues
	 * @return Nouveaux réglages
	 */
	public GameSettings withWaves(List<ZoneWave> newWaves) {
		return new GameSettings(warmupSeconds, seed, obstaclesNumber, newWaves);
	}
}
