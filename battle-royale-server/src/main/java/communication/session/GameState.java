package communication.session;

/**
 * État de la partie vu par le serveur web
 * @author mourtaza
 *
 */
public enum GameState {
	/** Salle d'attente, inscriptions ouvertes */
	LOBBY("lobby"),
	/** Manche en cours */
	RUNNING("running"),
	/** Manche en pause */
	PAUSED("paused"),
	/** Manche terminée par le jeu */
	OVER("over"),
	/** Manche arrêtée par l'administrateur ou perte du jeu */
	STOPPED("stopped");

	private final String wireName;

	GameState(String wireName) { this.wireName = wireName; }

	/**
	 * Retourne le nom de l'état dans les messages JSON
	 * @return Nom JSON
	 */
	public String wireName() { return wireName; }

	/**
	 * Indique si une manche est en cours (éventuellement en pause)
	 * @return true pour {@link #RUNNING} et {@link #PAUSED}
	 */
	public boolean isInProgress() { return this == RUNNING || this == PAUSED; }

	/**
	 * Indique si de nouveaux joueurs peuvent s'inscrire et si une manche peut être lancée
	 * @return true en dehors d'une manche
	 */
	public boolean isBetweenRounds() { return !isInProgress(); }
}
