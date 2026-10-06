package inside;

/**
 * Événement produit par la simulation, utilisé par l'affichage (effets, fil des éliminations) et le son
 * @param type Type de l'événement
 * @param tick Pas de simulation de l'événement
 * @param actorId Identifiant du joueur à l'origine de l'événement (-1 si aucun)
 * @param targetId Identifiant du joueur visé (-1 si aucun)
 * @param amount Valeur associée (dégâts, numéro de vague...)
 * @param x Abscisse de l'événement
 * @param y Ordonnée de l'événement
 * @param cause Origine des dégâts pour les touches et éliminations (null sinon)
 * @author mourtaza
 */
public record GameEvent(Type type, long tick, int actorId, int targetId, int amount, float x, float y, DamageCause cause) {
	/**
	 * Types d'événements
	 */
	public enum Type {
		/**
		 * Un joueur a tiré
		 */
		SHOT,
		/**
		 * Un joueur a donné un coup d'épée
		 */
		SWING,
		/**
		 * Un joueur a été touché (amount = dégâts, 0 pendant l'échauffement)
		 */
		HIT,
		/**
		 * Une balle a été arrêtée par un obstacle ou le bord de la carte
		 */
		BULLET_BLOCKED,
		/**
		 * Un joueur a été éliminé (actorId = auteur, -1 pour la lave)
		 */
		ELIMINATION,
		/**
		 * Le combat commence
		 */
		BATTLE_STARTED,
		/**
		 * La zone commence à se resserrer (amount = numéro de vague, à partir de 1)
		 */
		ZONE_SHRINKING,
		/**
		 * La zone a fini de se resserrer
		 */
		ZONE_SHRUNK,
		/**
		 * La partie est mise en pause
		 */
		PAUSED,
		/**
		 * La partie reprend
		 */
		RESUMED,
		/**
		 * La partie est terminée (actorId = vainqueur, -1 si aucun)
		 */
		GAME_OVER
	}

	/**
	 * Construit un événement sans position ni joueurs
	 * @param type Type de l'événement
	 * @param tick Pas de simulation
	 * @param amount Valeur associée
	 * @return Événement
	 */
	public static GameEvent global(Type type, long tick, int amount) {
		return new GameEvent(type, tick, -1, -1, amount, 0, 0, null);
	}
}
