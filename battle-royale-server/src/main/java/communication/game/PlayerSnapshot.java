package communication.game;

/**
 * État d'un joueur tel que transmis par le jeu (9 octets)
 * @param id Identifiant du joueur
 * @param status Statut du joueur
 * @param life Points de vie
 * @param x Abscisse en pixels
 * @param y Ordonnée en pixels
 * @param kills Nombre d'éliminations
 * @param rank Classement final (0 tant que le joueur est en vie)
 * @author mourtaza
 */
public record PlayerSnapshot(int id, Status status, int life, int x, int y, int kills, int rank) {
	/**
	 * Statut d'un joueur
	 */
	public enum Status {
		/** Joueur éliminé */
		ELIMINATED(0, "eliminated"),
		/** Joueur en vie */
		ALIVE(1, "alive"),
		/** Vainqueur de la manche */
		WINNER(2, "winner");

		private final int code;
		private final String wireName;

		Status(int code, String wireName) {
			this.code = code;
			this.wireName = wireName;
		}

		/**
		 * Retourne le code binaire du statut
		 * @return Code envoyé par le jeu
		 */
		public int code() { return code; }

		/**
		 * Retourne le nom du statut dans les messages JSON
		 * @return Nom JSON
		 */
		public String wireName() { return wireName; }

		/**
		 * Retrouve un statut à partir de son code binaire
		 * @param code Code reçu
		 * @return Statut correspondant
		 * @throws ProtocolException si le code est inconnu
		 */
		public static Status fromCode(int code) throws ProtocolException {
			for (Status s : values())
				if (s.code == code)
					return s;
			throw new ProtocolException("Statut de joueur inconnu : " + code);
		}
	}
}
