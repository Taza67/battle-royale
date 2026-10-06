package inside;

/**
 * Énumération des phases d'une partie
 * @author mourtaza
 *
 */
public enum Phase {
	/**
	 * Échauffement : les joueurs peuvent se déplacer et attaquer sans infliger de dégâts
	 */
	WARMUP(0),
	/**
	 * Combat : les dégâts sont actifs et la zone sûre se resserre
	 */
	BATTLE(1),
	/**
	 * Partie terminée
	 */
	ENDED(2);

	/**
	 * Code de la phase dans le protocole
	 */
	private final int CODE;

	/**
	 * Construit une phase
	 * @param code Code de la phase dans le protocole
	 */
	Phase(int code) { CODE = code; }

	/**
	 * Retourne le code de la phase dans le protocole
	 * @return Code (0 échauffement, 1 combat, 2 terminé)
	 */
	public int getCode() { return CODE; }
}
