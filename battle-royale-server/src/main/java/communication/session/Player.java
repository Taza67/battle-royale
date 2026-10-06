package communication.session;

import communication.game.PlayerSnapshot;
import communication.message.ServerMessage;

/**
 * Joueur inscrit auprès du serveur web. L'identifiant et le pseudo sont conservés
 * d'une manche à l'autre ; les autres champs sont protégés par le verrou de {@link GameSession}.
 * @author mourtaza
 *
 */
public final class Player {
	/**
	 * Identifiant associé au joueur
	 */
	private final int id;
	/**
	 * Pseudo associé au joueur
	 */
	private final String pseudo;
	/**
	 * Connexion actuelle, null si le joueur est déconnecté
	 */
	private volatile ClientConnection connection;
	private PlayerSnapshot.Status status = PlayerSnapshot.Status.ALIVE;
	private int life = GameSession.DEFAULT_MAX_LIFE;
	private int kills;
	private int rank;
	private String lastStateJson;
	private long lastStateMillis;

	/**
	 * Construit un joueur
	 * @param id Identifiant associé au joueur
	 * @param pseudo Pseudo validé
	 * @param connection Connexion du joueur
	 */
	Player(int id, String pseudo, ClientConnection connection) {
		this.id = id;
		this.pseudo = pseudo;
		this.connection = connection;
	}

	/**
	 * Retourne l'identifiant du joueur
	 * @return Identifiant
	 */
	public int getId() { return id; }

	/**
	 * Retourne le pseudo du joueur
	 * @return Pseudo
	 */
	public String getPseudo() { return pseudo; }

	/**
	 * Indique si le joueur a une connexion ouverte
	 * @return true si le joueur est connecté
	 */
	public boolean isConnected() {
		ClientConnection c = connection;
		return c != null && c.isOpen();
	}

	ClientConnection connection() { return connection; }

	void attach(ClientConnection c) {
		connection = c;
		lastStateJson = null;
	}

	void detach() { connection = null; }

	/**
	 * Remet à zéro les statistiques au lancement d'une manche
	 */
	void resetForRound() {
		status = PlayerSnapshot.Status.ALIVE;
		life = GameSession.DEFAULT_MAX_LIFE;
		kills = 0;
		rank = 0;
		lastStateJson = null;
	}

	/**
	 * Met à jour les statistiques à partir de l'état reçu du jeu
	 * @param snapshot État du joueur
	 */
	void update(PlayerSnapshot snapshot) {
		status = snapshot.status();
		life = snapshot.life();
		kills = snapshot.kills();
		rank = snapshot.rank();
	}

	PlayerSnapshot.Status status() { return status; }
	int kills() { return kills; }
	int rank() { return rank; }

	/**
	 * Indique si un état doit être envoyé au joueur (au plus ~20 par seconde,
	 * un état identique n'étant renvoyé qu'au bout d'une seconde)
	 * @param json État à envoyer
	 * @param nowMillis Instant courant
	 * @return true si l'état doit être envoyé
	 */
	boolean shouldSendState(String json, long nowMillis) {
		if (lastStateJson == null)
			return true;
		long elapsed = nowMillis - lastStateMillis;
		if (elapsed < GameSession.STATE_MIN_INTERVAL_MILLIS)
			return false;
		return !json.equals(lastStateJson) || elapsed >= GameSession.STATE_KEEPALIVE_MILLIS;
	}

	String lastStateJson() { return lastStateJson; }

	void stateSent(String json, long nowMillis) {
		lastStateJson = json;
		lastStateMillis = nowMillis;
	}

	/**
	 * Retourne la ligne du joueur dans la liste envoyée à l'administrateur
	 * @return Ligne de la liste des joueurs
	 */
	ServerMessage.PlayerEntry toEntry() {
		return new ServerMessage.PlayerEntry(id, pseudo, isConnected(), status.wireName(), life, kills, rank);
	}

	/**
	 * Retourne la ligne du joueur dans le classement final
	 * @return Ligne du classement
	 */
	ServerMessage.RankingEntry toRankingEntry() {
		return new ServerMessage.RankingEntry(id, pseudo, kills, rank);
	}

	@Override
	public String toString() {
		return "Player[" + id + ", " + pseudo + "]";
	}
}
