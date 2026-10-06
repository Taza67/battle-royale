package inside;

/**
 * Commande appliquée au plateau au début d'un pas de simulation.
 * Les commandes proviennent du clavier, du réseau ou des bots.
 * @author mourtaza
 *
 * @see Board#enqueue(Command)
 */
public sealed interface Command permits Command.Move, Command.Attack, Command.Control {
	/**
	 * Applique la commande au plateau (fil de la simulation)
	 * @param board Plateau
	 */
	void apply(Board board);

	/**
	 * Commande de déplacement
	 * @param playerId Identifiant du joueur
	 * @param direction Direction (0 à 7)
	 * @param speed Niveau de vitesse (0 = arrêt, jusqu'à 4)
	 */
	record Move(int playerId, int direction, int speed) implements Command {
		@Override
		public void apply(Board board) {
			board.applyMove(this);
		}
	}

	/**
	 * Commande d'attaque
	 * @param playerId Identifiant du joueur
	 * @param form Forme de l'attaque (1 corps-à-corps, 2 tir)
	 */
	record Attack(int playerId, int form) implements Command {
		@Override
		public void apply(Board board) {
			board.applyAttack(this);
		}
	}

	/**
	 * Commande de contrôle de la partie
	 * @param type Type de contrôle
	 */
	record Control(ControlType type) implements Command {
		@Override
		public void apply(Board board) {
			board.applyControl(type);
		}
	}

	/**
	 * Types de contrôle de la partie
	 */
	enum ControlType {
		/**
		 * Mise en pause
		 */
		PAUSE,
		/**
		 * Reprise
		 */
		RESUME,
		/**
		 * Arrêt de la partie
		 */
		STOP
	}
}
