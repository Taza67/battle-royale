package communication.game;

/**
 * Reçoit les événements du lien avec le jeu, appelés depuis le thread du lien
 * @author mourtaza
 *
 * @see GameLink
 */
public interface GameLinkListener {
	/**
	 * Le jeu a accepté la partie
	 * @param link Lien concerné
	 */
	void onStarted(GameLink link);

	/**
	 * La partie n'a pas pu démarrer
	 * @param link Lien concerné
	 * @param reason Raison lisible par l'administrateur
	 */
	void onStartFailed(GameLink link, String reason);

	/**
	 * Un état a été reçu pendant la partie
	 * @param link Lien concerné
	 * @param snapshot État décodé
	 */
	void onSnapshot(GameLink link, GameSnapshot snapshot);

	/**
	 * Le jeu a terminé la partie (`enCours` à false)
	 * @param link Lien concerné
	 * @param finalSnapshot État final, ou null s'il était illisible
	 */
	void onFinished(GameLink link, GameSnapshot finalSnapshot);

	/**
	 * L'arrêt demandé a été transmis au jeu et la connexion est fermée
	 * @param link Lien concerné
	 */
	void onStopped(GameLink link);

	/**
	 * La connexion avec le jeu a été perdue en cours de partie
	 * @param link Lien concerné
	 * @param reason Description de l'erreur
	 */
	void onLinkLost(GameLink link, String reason);
}
