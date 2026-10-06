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
	 * Indique si le joueur se déplaçait à l'image précédente
	 */
	private boolean moving;

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
		int d = direction(keys.up(), keys.down(), keys.left(), keys.right());
		if (d >= 0) {
			board.enqueue(new Command.Move(playerId, d, keys.slow() ? WALK_SPEED : RUN_SPEED));
			moving = true;
		} else if (moving) {
			board.enqueue(new Command.Move(playerId, 0, 0));
			moving = false;
		}

		if (keys.melee()) board.enqueue(new Command.Attack(playerId, ATTACK_MELEE));
		if (keys.shoot()) board.enqueue(new Command.Attack(playerId, ATTACK_SHOOT));
	}

	/**
	 * Oublie l'état précédent (nouvelle partie)
	 */
	public void reset() {
		moving = false;
	}
}
