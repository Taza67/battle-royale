package protocol;

/**
 * Contrat binaire partagé entre le jeu et le serveur web (voir docs/PROTOCOLE.md) :
 * codes de contrôle, types d'actions et disposition des états. Toutes les valeurs
 * sont définies ici une seule fois pour empêcher toute dérive entre les deux modules.
 * @author mourtaza
 */
public final class GameProtocol {
	/**
	 * Code envoyé par le serveur au début de la poignée de main
	 */
	public static final int START = 0;
	/**
	 * Code de pause
	 */
	public static final int PAUSE = -1;
	/**
	 * Code d'arrêt demandé par l'administrateur
	 */
	public static final int STOP = -2;
	/**
	 * Code de reprise
	 */
	public static final int RESUME = -3;
	/**
	 * Taille d'un bloc d'actions vide, envoyé à chaque cycle après un arrêt
	 */
	public static final int EMPTY_ACTIONS = 0;
	/**
	 * Type d'action : déplacement
	 */
	public static final int ACTION_MOVE = 0;
	/**
	 * Type d'action : attaque
	 */
	public static final int ACTION_ATTACK = 1;
	/**
	 * Taille de l'en-tête de l'état, en octets
	 */
	public static final int HEADER_SIZE = 23;
	/**
	 * Taille de l'état d'un joueur, en octets
	 */
	public static final int PLAYER_SIZE = 9;
	/**
	 * Taille maximale acceptée pour un bloc d'actions
	 */
	public static final int MAX_ACTIONS_SIZE = 1 << 16;
	/**
	 * Nombre maximal de joueurs admis par le protocole (identifiants sur un octet)
	 */
	public static final int MAX_PLAYERS = 100;
	/**
	 * Valeur maximale d'un champ codé sur un octet non signé
	 */
	public static final int UNSIGNED_BYTE_MAX = 0xFF;

	private GameProtocol() {}
}
