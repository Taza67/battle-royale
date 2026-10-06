package communication.session;

/**
 * Connexion vers un client web, indépendante de la couche WebSocket.
 * Les envois ne doivent jamais bloquer l'appelant.
 * @author mourtaza
 *
 */
public interface ClientConnection {
	/**
	 * Retourne un identifiant lisible de la connexion, pour les journaux
	 * @return Identifiant de la connexion
	 */
	String id();

	/**
	 * Indique si la connexion est encore ouverte
	 * @return true si des messages peuvent encore être envoyés
	 */
	boolean isOpen();

	/**
	 * Envoie un message, dans l'ordre des appels
	 * @param json Message JSON
	 */
	void send(String json);

	/**
	 * Envoie un message remplaçable : s'il n'est pas encore parti, un message
	 * remplaçable plus récent le remplace (utilisé pour les états des joueurs)
	 * @param json Message JSON
	 */
	void sendLatest(String json);

	/**
	 * Ferme la connexion
	 * @param reason Raison de la fermeture
	 */
	void close(String reason);
}
