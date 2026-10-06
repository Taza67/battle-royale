package outside;

import inside.Board;
import inside.Command;
import inside.IConfig;

/**
 * Traduit l'état du clavier en commandes pour le joueur local, à chaque image affichée.
 * Les touches maintenues donnent un déplacement continu ; relâcher toutes les flèches arrête le joueur.
 * @author mourtaza
 */
public class KeyboardInput implements IConfig {
	/**
	 * État des touches utiles
	 * @param up Haut
	 * @param down Bas
	 * @param left Gauche
	 * @param right Droite
	 * @param slow Marche lente (Maj)
	 * @param melee Épée
	 * @param shoot Tir
	 */
	public record Keys(boolean up, boolean down, boolean left, boolean right, boolean slow, boolean melee, boolean shoot) {}

	/**
	 * Vitesse de course
	 */
	public static final int RUN_SPEED = MAX_SPEED_LEVEL;
	/**
	 * Vitesse de marche (Maj)
	 */
	public static final int WALK_SPEED = 2;

	/**
	 * Nombre d'images entre deux renvois d'une commande maintenue : l'intention de
	 * déplacement expire après ~250 ms sans commande, un renvoi toutes les ~10 images
	 * suffit à la garder vivante sans remplir la file d'instances identiques
	 */
	private static final int RESEND_FRAMES = 10;

	/**
	 * Dernière direction envoyée (-1 si le joueur est à l'arrêt)
	 */
	private int lastDirection = -1;
	/**
	 * Dernière vitesse envoyée
	 */
	private int lastSpeed;
	/**
	 * Épée enfoncée à l'image précédente
	 */
	private boolean melee;
	/**
	 * Tir enfoncé à l'image précédente
	 */
	private boolean shoot;
	/**
	 * Images écoulées depuis le dernier renvoi
	 */
	private int frames;

	/**
	 * Calcule la direction correspondant aux flèches enfoncées
	 * @param up Haut
	 * @param down Bas
	 * @param left Gauche
	 * @param right Droite
	 * @return Direction (0-7), ou -1 si aucune direction
	 */
	public static int direction(boolean up, boolean down, boolean left, boolean right) {
		int dx = (right ? 1 : 0) - (left ? 1 : 0), dy = (down ? 1 : 0) - (up ? 1 : 0);
		if (dx == 0 && dy == 0) return -1;
		return inside.Direction.fromVector(dx, dy);
	}

	/**
	 * Dépose les commandes du joueur local
	 * @param board Plateau
	 * @param playerId Identifiant du joueur local
	 * @param keys État des touches
	 */
	public void apply(Board board, int playerId, Keys keys) {
		boolean resend = ++frames >= RESEND_FRAMES;
		if (resend) frames = 0;

		int d = direction(keys.up(), keys.down(), keys.left(), keys.right());
		int speed = d >= 0 ? (keys.slow() ? WALK_SPEED : RUN_SPEED) : 0;
		if (d >= 0) {
			// Renfile quand la commande change ou périodiquement pour rafraîchir l'intention
			if (d != lastDirection || speed != lastSpeed || resend)
				board.enqueue(new Command.Move(playerId, d, speed));
		} else if (lastDirection >= 0) {
			board.enqueue(new Command.Move(playerId, 0, 0));
		}
		lastDirection = d;
		lastSpeed = speed;

		if (keys.melee() && (!melee || resend)) board.enqueue(new Command.Attack(playerId, ATTACK_MELEE));
		if (keys.shoot() && (!shoot || resend)) board.enqueue(new Command.Attack(playerId, ATTACK_SHOOT));
		melee = keys.melee();
		shoot = keys.shoot();
	}

	/**
	 * Oublie l'état précédent (nouvelle partie)
	 */
	public void reset() {
		lastDirection = -1;
		lastSpeed = 0;
		melee = false;
		shoot = false;
		frames = 0;
	}
}
