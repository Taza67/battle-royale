package communication.game;

/**
 * Joueur transmis au jeu lors de la poignée de main
 * @param id Identifiant du joueur (0 à 127)
 * @param pseudo Pseudo du joueur
 * @author mourtaza
 */
public record Participant(int id, String pseudo) {
	/**
	 * Vérifie les valeurs transmises au jeu
	 * @param id Identifiant du joueur
	 * @param pseudo Pseudo du joueur
	 */
	public Participant {
		if (id < 0 || id > Byte.MAX_VALUE)
			throw new IllegalArgumentException("Identifiant hors limites : " + id);
		if (pseudo == null)
			throw new IllegalArgumentException("Pseudo manquant");
	}
}
