package communication.game;

/**
 * Paramètres de connexion au jeu
 * @param host Hôte du jeu
 * @param port Port TCP du jeu
 * @param tickMillis Intervalle entre deux échanges, en millisecondes
 * @param connectTimeoutMillis Délai maximal de connexion, en millisecondes
 * @param readTimeoutMillis Délai maximal d'attente d'une réponse du jeu, en millisecondes
 * @author mourtaza
 */
public record GameLinkSettings(String host, int port, int tickMillis, int connectTimeoutMillis, int readTimeoutMillis) {
	/**
	 * Intervalle entre deux échanges fixé par le protocole
	 */
	public static final int DEFAULT_TICK_MILLIS = 50;
	/**
	 * Délai de connexion par défaut
	 */
	public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
	/**
	 * Délai de lecture par défaut
	 */
	public static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

	/**
	 * Vérifie les paramètres
	 * @param host Hôte du jeu
	 * @param port Port TCP du jeu
	 * @param tickMillis Intervalle entre deux échanges
	 * @param connectTimeoutMillis Délai maximal de connexion
	 * @param readTimeoutMillis Délai maximal de lecture
	 */
	public GameLinkSettings {
		if (host == null || host.isBlank())
			throw new IllegalArgumentException("Hôte du jeu manquant");
		if (port < 1 || port > 65535)
			throw new IllegalArgumentException("Port du jeu invalide : " + port);
		if (tickMillis < 1 || connectTimeoutMillis < 1 || readTimeoutMillis < 1)
			throw new IllegalArgumentException("Délais strictement positifs attendus");
	}

	/**
	 * Construit des paramètres avec les délais par défaut
	 * @param host Hôte du jeu
	 * @param port Port TCP du jeu
	 * @return Paramètres
	 */
	public static GameLinkSettings of(String host, int port) {
		return new GameLinkSettings(host, port, DEFAULT_TICK_MILLIS, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS);
	}
}
