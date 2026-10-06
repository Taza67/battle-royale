package communication;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.websocket.Constants;

import communication.message.ClientMessage;
import communication.message.ClientMessageParser;
import communication.message.InvalidMessageException;
import communication.message.Json;
import communication.message.ServerMessage;
import communication.message.UnknownMessageTypeException;
import communication.session.GameSession;
import communication.session.Player;

/**
 * Point d'accès WebSocket des manettes et du panneau d'administration.
 * Une instance est créée par connexion et mémorise le rôle de sa session.
 * @author mourtaza
 *
 */
public final class WebSocketServer extends Endpoint {
	/**
	 * Chemin du point d'accès, relatif au contexte de l'application
	 */
	public static final String PATH = "/websocketserver";
	/**
	 * Taille maximale d'un message reçu, en octets
	 */
	public static final int MAX_MESSAGE_SIZE = 4096;
	/**
	 * Durée au bout de laquelle une session qui n'a rien envoyé (pas même un pong) est fermée
	 */
	public static final long IDLE_TIMEOUT_MILLIS = 30_000;
	/**
	 * Préfixe du refus envoyé pour un `join` invalide
	 */
	public static final String INVALID_PSEUDO = "Pseudo invalide";
	/**
	 * Préfixe du refus envoyé pour un `admin-join` invalide
	 */
	public static final String INVALID_ADMIN_JOIN = "Connexion administrateur invalide";
	/**
	 * Refus envoyé à une session déjà inscrite qui renvoie `join` ou `admin-join`
	 */
	public static final String ALREADY_REGISTERED = "Session déjà inscrite";

	private static final Logger LOGGER = Logger.getLogger(WebSocketServer.class.getName());

	/**
	 * Rôle d'une session
	 */
	enum Role {
		/** Aucun message d'inscription accepté */
		UNREGISTERED,
		/** Joueur inscrit */
		PLAYER,
		/** Administrateur */
		ADMIN
	}

	private final GameSession game;
	private final Heartbeat heartbeat;
	private final ExecutorService dispatcher;
	private volatile Role role = Role.UNREGISTERED;
	private volatile Player player;
	private volatile WebSocketConnection connection;

	/**
	 * Construit le point d'accès d'une connexion
	 * @param game Session de jeu partagée
	 * @param heartbeat Planificateur de pings partagé
	 * @param dispatcher Pool de threads lançant les envois et les fermetures
	 */
	WebSocketServer(GameSession game, Heartbeat heartbeat, ExecutorService dispatcher) {
		this.game = game;
		this.heartbeat = heartbeat;
		this.dispatcher = dispatcher;
	}

	/**
	 * Construit la configuration du point d'accès, liée à la session de jeu donnée
	 * @param game Session de jeu partagée par toutes les connexions
	 * @param heartbeat Planificateur de pings partagé par toutes les connexions
	 * @param dispatcher Pool de threads lançant les envois et les fermetures
	 * @return Configuration à enregistrer auprès du conteneur WebSocket
	 */
	public static ServerEndpointConfig config(GameSession game, Heartbeat heartbeat, ExecutorService dispatcher) {
		return ServerEndpointConfig.Builder.create(WebSocketServer.class, PATH)
			.configurator(new ServerEndpointConfig.Configurator() {
				@Override
				public <T> T getEndpointInstance(Class<T> endpointClass) {
					return endpointClass.cast(new WebSocketServer(game, heartbeat, dispatcher));
				}

				/**
				 * Accepte un en-tête `Origin` absent ou désignant l'hôte de la requête ;
				 * la requête est fournie par {@link OriginCheck.HandshakeFilter}
				 */
				@Override
				public boolean checkOrigin(String originHeaderValue) {
					return OriginCheck.allowsCurrent(originHeaderValue);
				}
			})
			.build();
	}

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		connection = new WebSocketConnection(session, dispatcher);
		session.setMaxTextMessageBufferSize(MAX_MESSAGE_SIZE);
		session.setMaxIdleTimeout(IDLE_TIMEOUT_MILLIS);
		session.getUserProperties().put(Constants.READ_IDLE_TIMEOUT_MS, Long.valueOf(IDLE_TIMEOUT_MILLIS));
		session.addMessageHandler(String.class, this::onMessage);
		heartbeat.register(connection);
		LOGGER.fine(() -> "Nouvelle connexion " + connection.id());
	}

	/**
	 * Traite un message texte reçu
	 * @param text Contenu du message
	 */
	private synchronized void onMessage(String text) {
		ClientMessage message;
		try {
			message = ClientMessageParser.parse(text);
		} catch (UnknownMessageTypeException e) {
			LOGGER.info(() -> "Message de type inconnu ignoré (" + connection.id() + ") : " + e.getType());
			return;
		} catch (InvalidMessageException e) {
			LOGGER.warning(() -> "Message invalide ignoré (" + connection.id() + ", " + role + ") : " + e.getMessage());
			rejectInvalidRegistration(e);
			return;
		}
		LOGGER.finest(() -> connection.id() + " -> " + message);

		if (message instanceof ClientMessage.Join join)
			onJoin(join);
		else if (message instanceof ClientMessage.AdminJoin adminJoin)
			onAdminJoin(adminJoin);
		else if (message instanceof ClientMessage.Move move)
			onMove(move);
		else if (message instanceof ClientMessage.Attack attack)
			onAttack(attack);
		else if (message instanceof ClientMessage.AdminCommand command)
			onAdminCommand(command);
	}

	/**
	 * Répond `rejected` à une inscription invalide, pour que le client ne reste pas en attente
	 * @param e Erreur de validation
	 */
	private void rejectInvalidRegistration(InvalidMessageException e) {
		boolean registration = "join".equals(e.getType()) || "admin-join".equals(e.getType());
		if (role != Role.UNREGISTERED) {
			if (registration)
				rejectAlreadyRegistered(e.getType());
			return;
		}
		String reason;
		if ("join".equals(e.getType()))
			reason = INVALID_PSEUDO + " : " + e.getMessage();
		else if ("admin-join".equals(e.getType()))
			reason = INVALID_ADMIN_JOIN + " : " + e.getMessage();
		else
			return;
		connection.send(Json.write(new ServerMessage.Rejected(reason)));
	}

	private void onJoin(ClientMessage.Join join) {
		switch (role) {
			case UNREGISTERED:
				Player p = game.join(connection, join.pseudo(), join.token());
				if (p != null) {
					player = p;
					role = Role.PLAYER;
				}
				break;
			case PLAYER:
			case ADMIN:
				rejectAlreadyRegistered("join");
				break;
		}
	}

	private void onAdminJoin(ClientMessage.AdminJoin adminJoin) {
		if (role != Role.UNREGISTERED) {
			rejectAlreadyRegistered("admin-join");
			return;
		}
		if (game.claimAdmin(connection, adminJoin.password()))
			role = Role.ADMIN;
	}

	/**
	 * Refuse une nouvelle inscription d'une session qui a déjà un rôle ; la session le conserve
	 * @param type Type du message refusé
	 */
	private void rejectAlreadyRegistered(String type) {
		LOGGER.warning(() -> type + " refusé : " + connection.id() + " est déjà inscrite ("
			+ (role == Role.PLAYER ? String.valueOf(player) : "administrateur") + ")");
		connection.send(Json.write(new ServerMessage.Rejected(ALREADY_REGISTERED)));
	}

	private void onMove(ClientMessage.Move move) {
		if (role != Role.PLAYER) {
			LOGGER.fine(() -> "Déplacement ignoré depuis une session " + role + " (" + connection.id() + ")");
			return;
		}
		game.move(connection, player, move.direction(), move.speed());
	}

	private void onAttack(ClientMessage.Attack attack) {
		if (role != Role.PLAYER) {
			LOGGER.fine(() -> "Attaque ignorée depuis une session " + role + " (" + connection.id() + ")");
			return;
		}
		game.attack(connection, player, attack.form());
	}

	private void onAdminCommand(ClientMessage.AdminCommand command) {
		if (role != Role.ADMIN) {
			LOGGER.warning(() -> "Commande " + command.command().wireName() + " refusée depuis une session "
				+ role + " (" + connection.id() + ")");
			return;
		}
		game.command(connection, command.command());
	}

	@Override
	public void onClose(Session session, CloseReason closeReason) {
		LOGGER.fine(() -> "Fermeture de " + connection.id() + " : " + closeReason.getCloseCode() + " " + closeReason.getReasonPhrase());
		heartbeat.unregister(connection);
		release();
	}

	@Override
	public void onError(Session session, Throwable error) {
		LOGGER.log(Level.WARNING, "Erreur sur la connexion " + (connection == null ? session.getId() : connection.id()), error);
	}

	/**
	 * Libère le rôle de la session fermée
	 */
	private synchronized void release() {
		WebSocketConnection c = connection;
		if (c == null)
			return;
		if (role == Role.PLAYER)
			game.disconnect(c, player);
		else if (role == Role.ADMIN)
			game.releaseAdmin(c);
		role = Role.UNREGISTERED;
		player = null;
	}
}
