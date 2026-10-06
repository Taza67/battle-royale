package communication.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import communication.message.Json;
import communication.message.ServerMessage;

/**
 * Place d'administrateur : mot de passe, tentatives erronées et connexion détentrice.
 * Non thread-safe : toutes les méthodes sont appelées sous le verrou de {@link GameSession}.
 * @author mourtaza
 */
final class AdminGate {
	private static final Logger LOGGER = Logger.getLogger(AdminGate.class.getName());

	private final String password;
	/**
	 * Mots de passe administrateur erronés par connexion ; les connexions fermées sont oubliées
	 */
	private final Map<ClientConnection, Integer> wrongPasswords = new WeakHashMap<>();
	private ClientConnection admin;

	/**
	 * Construit la place d'administrateur
	 * @param password Mot de passe exigé, ou null pour laisser la place libre
	 */
	AdminGate(String password) {
		this.password = password;
	}

	/**
	 * Indique si la place exige un mot de passe
	 * @return true si un mot de passe est configuré
	 */
	boolean requiresPassword() { return password != null; }

	/**
	 * Retourne la connexion administrateur en place
	 * @return Connexion, ou null si la place est libre
	 */
	ClientConnection get() { return admin; }

	/**
	 * Indique si la connexion détient la place d'administrateur
	 * @param c Connexion
	 * @return true si c'est l'administrateur
	 */
	boolean isAdmin(ClientConnection c) { return c == admin; }

	/**
	 * Libère la place si la connexion la détient
	 * @param c Connexion
	 * @return true si la place a été libérée
	 */
	boolean release(ClientConnection c) {
		if (admin != c)
			return false;
		admin = null;
		return true;
	}

	/**
	 * Tente de prendre la place d'administrateur
	 * @param c Connexion demandeuse
	 * @param password Mot de passe fourni, éventuellement null
	 * @param nowMillis Instant courant en millisecondes
	 * @return true si la connexion détient la place
	 */
	boolean claim(ClientConnection c, String password, long nowMillis) {
		if (isAdmin(c))
			return true;
		if (requiresPassword()) {
			if (password == null || !MessageDigest.isEqual(
					password.getBytes(StandardCharsets.UTF_8), this.password.getBytes(StandardCharsets.UTF_8))) {
				recordFailure(c);
				return false;
			}
			ClientConnection previous = admin;
			if (previous != null && previous != c) {
				LOGGER.info(() -> "Session administrateur " + previous.id() + " remplacée par " + c.id());
				previous.send(Json.write(new ServerMessage.Rejected(GameSession.ADMIN_REPLACED)));
				previous.close(GameSession.ADMIN_REPLACED);
			}
		} else if (admin != null && admin.isOpen()) {
			LOGGER.info(() -> "Place d'administrateur refusée à " + c.id());
			reject(c, GameSession.ADMIN_TAKEN);
			return false;
		}
		admin = c;
		return true;
	}

	/**
	 * Compte un mot de passe erroné et ferme la connexion au bout du maximum
	 * @param c Connexion fautive
	 */
	private void recordFailure(ClientConnection c) {
		int attempts = wrongPasswords.merge(c, 1, Integer::sum);
		LOGGER.warning(() -> "Mot de passe administrateur incorrect (" + c.id() + ", tentative " + attempts + ")");
		reject(c, GameSession.WRONG_PASSWORD);
		if (attempts >= GameSession.MAX_WRONG_PASSWORDS) {
			wrongPasswords.remove(c);
			LOGGER.warning(() -> "Session " + c.id() + " fermée après " + attempts
				+ " mots de passe administrateur incorrects");
			c.close(GameSession.TOO_MANY_WRONG_PASSWORDS);
		}
	}

	private static void reject(ClientConnection c, String reason) {
		c.send(Json.write(new ServerMessage.Rejected(reason)));
	}
}
