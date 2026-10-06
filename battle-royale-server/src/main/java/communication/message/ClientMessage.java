package communication.message;

/**
 * Message reçu d'un client web (manette ou panneau d'administration), déjà validé
 * @author mourtaza
 *
 * @see ClientMessageParser
 */
public sealed interface ClientMessage {
	/**
	 * Taille maximale d'un pseudo, en caractères
	 */
	int MAX_PSEUDO_LENGTH = 16;
	/**
	 * Plus grande direction acceptée (`0` est, sens trigonométrique)
	 */
	int MAX_DIRECTION = 7;
	/**
	 * Plus grande vitesse acceptée (`0` correspond à l'arrêt)
	 */
	int MAX_SPEED = 4;
	/**
	 * Forme d'attaque au corps-à-corps
	 */
	int FORM_MELEE = 1;
	/**
	 * Forme d'attaque à distance (tir)
	 */
	int FORM_SHOT = 2;

	/**
	 * Inscription ou reconnexion d'un joueur
	 * @param pseudo Pseudo nettoyé (sans espaces de bord, 1 à 16 caractères)
	 * @param token Jeton de reprise reçu dans `welcome`, null s'il est absent
	 */
	record Join(String pseudo, String token) implements ClientMessage {
		/**
		 * Construit une inscription sans jeton de reprise
		 * @param pseudo Pseudo nettoyé
		 */
		public Join(String pseudo) { this(pseudo, null); }

		/**
		 * Masque le jeton dans les journaux
		 * @return Représentation textuelle sans le jeton
		 */
		@Override
		public String toString() { return "Join[pseudo=" + pseudo + ", token=" + (token == null ? "aucun" : "***") + "]"; }
	}

	/**
	 * Intention de déplacement d'un joueur
	 * @param direction Direction de 0 à 7
	 * @param speed Vitesse de 0 (arrêt) à 4
	 */
	record Move(int direction, int speed) implements ClientMessage {}

	/**
	 * Attaque d'un joueur
	 * @param form Forme de l'attaque (1 corps-à-corps, 2 tir)
	 */
	record Attack(int form) implements ClientMessage {}

	/**
	 * Demande de connexion de l'administrateur
	 * @param password Mot de passe fourni (chaîne vide si absent)
	 */
	record AdminJoin(String password) implements ClientMessage {
		/**
		 * Masque le mot de passe dans les journaux
		 * @return Représentation textuelle sans le mot de passe
		 */
		@Override
		public String toString() { return "AdminJoin[password=***]"; }
	}

	/**
	 * Commande de l'administrateur
	 * @param command Commande demandée
	 */
	record AdminCommand(Command command) implements ClientMessage {}

	/**
	 * Commandes disponibles pour l'administrateur
	 */
	enum Command {
		/** Lancement d'une manche */
		START("start"),
		/** Mise en pause */
		PAUSE("pause"),
		/** Reprise après une pause */
		RESUME("resume"),
		/** Arrêt de la manche */
		STOP("stop");

		private final String wireName;

		Command(String wireName) { this.wireName = wireName; }

		/**
		 * Retourne le nom de la commande dans le protocole
		 * @return Nom utilisé dans les messages JSON
		 */
		public String wireName() { return wireName; }

		/**
		 * Retrouve une commande à partir de son nom dans le protocole
		 * @param name Nom reçu
		 * @return Commande correspondante, ou null si le nom est inconnu
		 */
		public static Command fromWireName(String name) {
			for (Command c : values())
				if (c.wireName.equals(name))
					return c;
			return null;
		}
	}
}
