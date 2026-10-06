package communication.message;

import java.util.List;

import communication.game.Zone;

/**
 * Message envoyé par le serveur aux clients web, sérialisé avec {@link Json#write(ServerMessage)}
 * @author mourtaza
 *
 */
public sealed interface ServerMessage {
	/**
	 * Largeur de la carte, en pixels
	 */
	int MAP_WIDTH = 1280;
	/**
	 * Hauteur de la carte, en pixels
	 */
	int MAP_HEIGHT = 720;

	/**
	 * Inscription d'un joueur acceptée
	 * @param type Toujours "welcome"
	 * @param id Identifiant attribué
	 * @param pseudo Pseudo retenu
	 * @param state État courant de la partie
	 * @param token Jeton de reprise du pseudo
	 */
	record Welcome(String type, int id, String pseudo, String state, String token) implements ServerMessage {
		/**
		 * Construit le message
		 * @param id Identifiant attribué
		 * @param pseudo Pseudo retenu
		 * @param state État courant de la partie
		 * @param token Jeton de reprise du pseudo
		 */
		public Welcome(int id, String pseudo, String state, String token) { this("welcome", id, pseudo, state, token); }

		/**
		 * Masque le jeton dans les journaux
		 * @return Représentation textuelle sans le jeton
		 */
		@Override
		public String toString() { return "Welcome[id=" + id + ", pseudo=" + pseudo + ", state=" + state + ", token=***]"; }
	}

	/**
	 * Inscription (joueur ou administrateur) refusée
	 * @param type Toujours "rejected"
	 * @param code Code stable du refus, comparé par les clients
	 * @param reason Raison du refus, en français, destinée à l'affichage
	 */
	record Rejected(String type, String code, String reason) implements ServerMessage {
		/**
		 * Code du refus générique, quand aucun code plus précis ne s'applique
		 */
		public static final String GAME_REFUSED = "game-refused";

		/**
		 * Construit le message
		 * @param code Code stable du refus
		 * @param reason Raison du refus, destinée à l'affichage
		 */
		public Rejected(String code, String reason) { this("rejected", code, reason); }

		/**
		 * Construit le message avec le code générique
		 * @param reason Raison du refus, destinée à l'affichage
		 */
		public Rejected(String reason) { this(GAME_REFUSED, reason); }
	}

	/**
	 * Changement d'état de la partie
	 * @param type Toujours "game"
	 * @param state Nouvel état
	 */
	record Game(String type, String state) implements ServerMessage {
		/**
		 * Construit le message
		 * @param state Nouvel état
		 */
		public Game(String state) { this("game", state); }
	}

	/**
	 * Dimensions de la carte
	 * @param width Largeur en pixels
	 * @param height Hauteur en pixels
	 */
	record MapSize(int width, int height) {
		/**
		 * Dimensions de la carte du jeu
		 */
		public static final MapSize DEFAULT = new MapSize(MAP_WIDTH, MAP_HEIGHT);
	}

	/**
	 * État d'un joueur, envoyé à ce seul joueur
	 * @param type Toujours "state"
	 * @param status Statut ("eliminated", "alive" ou "winner")
	 * @param life Points de vie
	 * @param maxLife Points de vie maximum
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param kills Éliminations
	 * @param rank Classement final (0 tant que le joueur est en vie)
	 * @param alive Joueurs vivants
	 * @param total Joueurs au total
	 * @param phase Phase ("warmup", "battle" ou "over")
	 * @param secondsLeft Secondes avant la prochaine étape
	 * @param zone Zone sûre actuelle
	 * @param nextZone Prochaine zone sûre
	 * @param map Dimensions de la carte
	 */
	record State(String type, String status, int life, int maxLife, int x, int y, int kills, int rank,
			int alive, int total, String phase, int secondsLeft, Zone zone, Zone nextZone, MapSize map)
			implements ServerMessage {
		/**
		 * Construit le message
		 * @param status Statut
		 * @param life Points de vie
		 * @param maxLife Points de vie maximum
		 * @param x Abscisse
		 * @param y Ordonnée
		 * @param kills Éliminations
		 * @param rank Classement
		 * @param alive Joueurs vivants
		 * @param total Joueurs au total
		 * @param phase Phase
		 * @param secondsLeft Secondes avant la prochaine étape
		 * @param zone Zone sûre actuelle
		 * @param nextZone Prochaine zone sûre
		 */
		public State(String status, int life, int maxLife, int x, int y, int kills, int rank,
				int alive, int total, String phase, int secondsLeft, Zone zone, Zone nextZone) {
			this("state", status, life, maxLife, x, y, kills, rank, alive, total, phase, secondsLeft,
				zone, nextZone, MapSize.DEFAULT);
		}
	}

	/**
	 * Référence courte vers un joueur
	 * @param id Identifiant
	 * @param pseudo Pseudo
	 */
	record PlayerRef(int id, String pseudo) {}

	/**
	 * Ligne du classement final
	 * @param id Identifiant
	 * @param pseudo Pseudo
	 * @param kills Éliminations
	 * @param rank Classement
	 */
	record RankingEntry(int id, String pseudo, int kills, int rank) {}

	/**
	 * Fin de partie
	 * @param type Toujours "end"
	 * @param winner Vainqueur, ou null
	 * @param ranking Classement final des joueurs humains
	 * @param total Nombre de participants, robots compris
	 * @param stopped true si la manche a été arrêtée avant son terme
	 */
	record End(String type, PlayerRef winner, List<RankingEntry> ranking, int total, boolean stopped) implements ServerMessage {
		/**
		 * Construit le message
		 * @param winner Vainqueur, ou null
		 * @param ranking Classement final des joueurs humains
		 * @param total Nombre de participants, robots compris
		 * @param stopped true si la manche a été arrêtée avant son terme
		 */
		public End(PlayerRef winner, List<RankingEntry> ranking, int total, boolean stopped) {
			this("end", winner, List.copyOf(ranking), total, stopped);
		}
	}

	/**
	 * Connexion de l'administrateur acceptée
	 * @param type Toujours "admin-welcome"
	 * @param state État courant de la partie
	 */
	record AdminWelcome(String type, String state) implements ServerMessage {
		/**
		 * Construit le message
		 * @param state État courant de la partie
		 */
		public AdminWelcome(String state) { this("admin-welcome", state); }
	}

	/**
	 * Ligne de la liste des joueurs envoyée à l'administrateur
	 * @param id Identifiant
	 * @param pseudo Pseudo
	 * @param connected Session ouverte ou non
	 * @param status Statut
	 * @param life Points de vie
	 * @param kills Éliminations
	 * @param rank Classement
	 */
	record PlayerEntry(int id, String pseudo, boolean connected, String status, int life, int kills, int rank) {}

	/**
	 * Liste des joueurs, envoyée à l'administrateur
	 * @param type Toujours "players"
	 * @param players Joueurs inscrits, par identifiant croissant
	 */
	record Players(String type, List<PlayerEntry> players) implements ServerMessage {
		/**
		 * Construit le message
		 * @param players Joueurs inscrits
		 */
		public Players(List<PlayerEntry> players) { this("players", List.copyOf(players)); }
	}

	/**
	 * Résultat d'une commande de l'administrateur
	 * @param type Toujours "ack"
	 * @param command Commande concernée
	 * @param ok Succès de la commande
	 * @param error Message d'erreur, null en cas de succès
	 */
	record Ack(String type, String command, boolean ok, String error) implements ServerMessage {
		/**
		 * Construit un acquittement positif
		 * @param command Commande concernée
		 * @return Message d'acquittement
		 */
		public static Ack success(String command) { return new Ack("ack", command, true, null); }

		/**
		 * Construit un acquittement négatif
		 * @param command Commande concernée
		 * @param error Message d'erreur
		 * @return Message d'acquittement
		 */
		public static Ack failure(String command, String error) { return new Ack("ack", command, false, error); }
	}
}
