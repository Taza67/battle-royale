package communication;

import java.util.Properties;

import communication.game.GameLinkSettings;

/**
 * Configuration du serveur web, lue depuis les propriétés système `battle-royale.*`
 * @param port Port HTTP du serveur web (`battle-royale.port`, 8080 par défaut)
 * @param webapp Répertoire des fichiers statiques (`battle-royale.webapp`), null pour le rechercher
 * @param game Paramètres de connexion au jeu (`battle-royale.game-host`, `battle-royale.game-port`)
 * @param adminPassword Mot de passe administrateur (`battle-royale.admin-password`), null si absent
 * @author mourtaza
 */
public record ServerConfig(int port, String webapp, GameLinkSettings game, String adminPassword) {
	/** Propriété du port HTTP */
	public static final String PORT = "battle-royale.port";
	/** Propriété du répertoire webapp */
	public static final String WEBAPP = "battle-royale.webapp";
	/** Propriété de l'hôte du jeu */
	public static final String GAME_HOST = "battle-royale.game-host";
	/** Propriété du port du jeu */
	public static final String GAME_PORT = "battle-royale.game-port";
	/** Propriété du mot de passe administrateur */
	public static final String ADMIN_PASSWORD = "battle-royale.admin-password";

	/**
	 * Lit la configuration depuis les propriétés système
	 * @return Configuration
	 * @throws IllegalArgumentException si une valeur est invalide
	 */
	public static ServerConfig fromSystemProperties() {
		return from(System.getProperties());
	}

	/**
	 * Lit la configuration depuis des propriétés
	 * @param properties Propriétés à lire
	 * @return Configuration
	 * @throws IllegalArgumentException si une valeur est invalide
	 */
	public static ServerConfig from(Properties properties) {
		int port = parsePort(properties, PORT, 8080);
		String webapp = properties.getProperty(WEBAPP);
		String host = properties.getProperty(GAME_HOST, "localhost").strip();
		int gamePort = parsePort(properties, GAME_PORT, 8000);
		String password = properties.getProperty(ADMIN_PASSWORD);
		if (password != null && password.isEmpty())
			password = null;
		return new ServerConfig(port, webapp, GameLinkSettings.of(host.isEmpty() ? "localhost" : host, gamePort), password);
	}

	/**
	 * Masque le mot de passe dans les journaux
	 * @return Représentation textuelle sans le mot de passe
	 */
	@Override
	public String toString() {
		return "ServerConfig[port=" + port + ", webapp=" + webapp + ", game=" + game.host() + ":" + game.port()
			+ ", adminPassword=" + (adminPassword == null ? "aucun" : "***") + "]";
	}

	private static int parsePort(Properties properties, String name, int fallback) {
		String value = properties.getProperty(name);
		if (value == null || value.isBlank())
			return fallback;
		try {
			int port = Integer.parseInt(value.strip());
			if (port < 1 || port > 65535)
				throw new IllegalArgumentException("Port hors limites pour " + name + " : " + value);
			return port;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Port invalide pour " + name + " : " + value, e);
		}
	}
}
