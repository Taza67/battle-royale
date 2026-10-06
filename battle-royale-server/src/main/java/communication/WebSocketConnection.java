package communication;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.SendResult;
import javax.websocket.Session;

import communication.session.ClientConnection;

/**
 * Connexion WebSocket vers un client web. Les messages sont mis en file et envoyés
 * un par un de façon asynchrone, hors de tout verrou de l'appelant ; un état de joueur
 * encore en attente est remplacé par le plus récent.
 * @author mourtaza
 *
 */
final class WebSocketConnection implements ClientConnection {
	/**
	 * Nombre maximal de messages en attente avant de considérer le client comme bloqué
	 */
	static final int MAX_QUEUED_MESSAGES = 512;

	private static final Logger LOG = Logger.getLogger(WebSocketConnection.class.getName());
	/**
	 * Threads partagés qui lancent les envois, pour ne jamais appeler Tomcat depuis un verrou applicatif
	 */
	private static final ExecutorService DISPATCHER = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "websocket-dispatcher");
		t.setDaemon(true);
		return t;
	});

	/**
	 * Message en attente d'envoi
	 */
	private static final class Outgoing {
		private final String json;
		private final boolean replaceable;

		Outgoing(String json, boolean replaceable) {
			this.json = json;
			this.replaceable = replaceable;
		}
	}

	private final Session session;
	private final String id;
	private final ArrayDeque<Outgoing> queue = new ArrayDeque<>();
	private boolean sending;
	private CloseReason closeRequest;

	/**
	 * Construit la connexion
	 * @param session Session WebSocket associée
	 */
	WebSocketConnection(Session session) {
		this.session = session;
		this.id = "ws-" + session.getId();
	}

	@Override
	public String id() { return id; }

	@Override
	public boolean isOpen() {
		synchronized (queue) {
			if (closeRequest != null)
				return false;
		}
		return session.isOpen();
	}

	@Override
	public void send(String json) {
		enqueue(new Outgoing(json, false));
	}

	@Override
	public void sendLatest(String json) {
		enqueue(new Outgoing(json, true));
	}

	/**
	 * Ferme la connexion une fois les messages déjà en file envoyés
	 * @param reason Raison de la fermeture
	 */
	@Override
	public void close(String reason) {
		requestClose(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
	}

	/**
	 * Demande la fermeture après la file d'envoi
	 * @param reason Raison de la fermeture
	 */
	private void requestClose(CloseReason reason) {
		synchronized (queue) {
			if (closeRequest != null)
				return;
			closeRequest = reason;
			if (sending)
				return;
			sending = true;
		}
		DISPATCHER.execute(this::transmitNext);
	}

	/**
	 * Ajoute un message à la file et démarre l'envoi si aucun n'est en cours
	 * @param message Message à envoyer
	 */
	private void enqueue(Outgoing message) {
		if (!session.isOpen())
			return;
		synchronized (queue) {
			if (closeRequest != null)
				return;
			if (message.replaceable)
				queue.removeIf(m -> m.replaceable);
			if (queue.size() >= MAX_QUEUED_MESSAGES) {
				queue.clear();
				LOG.warning(() -> "Client " + id + " trop lent, fermeture de la connexion");
				closeRequest = new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "Client trop lent");
			} else {
				queue.add(message);
			}
			if (sending)
				return;
			sending = true;
		}
		DISPATCHER.execute(this::transmitNext);
	}

	/**
	 * Envoie le prochain message de la file, ou ferme la session si la fermeture
	 * a été demandée et que la file est vide
	 */
	private void transmitNext() {
		Outgoing next;
		CloseReason close = null;
		synchronized (queue) {
			next = queue.poll();
			if (next == null) {
				sending = false;
				close = closeRequest;
			}
		}
		if (next == null) {
			if (close != null)
				closeNow(close);
			return;
		}
		try {
			session.getAsyncRemote().sendText(next.json, this::sent);
		} catch (RuntimeException e) {
			LOG.log(Level.FINE, "Envoi impossible vers " + id, e);
			abandon();
		}
	}

	/**
	 * Traite le résultat d'un envoi et enchaîne sur le suivant
	 * @param result Résultat de l'envoi
	 */
	private void sent(SendResult result) {
		if (!result.isOK()) {
			LOG.log(Level.FINE, "Échec d'envoi vers " + id, result.getException());
			abandon();
			return;
		}
		transmitNext();
	}

	/**
	 * Vide la file après une erreur d'envoi
	 */
	private void abandon() {
		CloseReason close;
		synchronized (queue) {
			queue.clear();
			sending = false;
			close = closeRequest;
		}
		if (close != null)
			closeNow(close);
	}

	/**
	 * Ferme la session WebSocket
	 * @param reason Code et raison de la fermeture
	 */
	private void closeNow(CloseReason reason) {
		try {
			if (session.isOpen())
				session.close(reason);
		} catch (IOException | IllegalStateException e) {
			LOG.log(Level.FINE, "Fermeture de " + id, e);
		}
	}
}
