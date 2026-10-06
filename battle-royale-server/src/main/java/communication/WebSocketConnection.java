package communication;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.SendResult;
import javax.websocket.Session;

import org.apache.tomcat.websocket.Constants;

import communication.session.ClientConnection;

/**
 * Connexion WebSocket vers un client web. Les messages sont mis en file et envoyés
 * un par un de façon asynchrone, hors de tout verrou de l'appelant ; un état de joueur
 * encore en attente est remplacé par le plus récent. Un client qui ne suit pas
 * (file pleine, envoi bloqué ou en échec) est déconnecté plutôt que de perdre des messages.
 * @author mourtaza
 *
 */
final class WebSocketConnection implements ClientConnection {
	/**
	 * Nombre maximal de messages en attente avant de considérer le client comme bloqué
	 */
	static final int MAX_QUEUED_MESSAGES = 512;
	/**
	 * Durée maximale d'un envoi (message, ping ou fermeture), en millisecondes
	 */
	static final long SEND_TIMEOUT_MILLIS = 10_000;
	/**
	 * Raison de fermeture d'un client qui ne lit plus ses messages
	 */
	static final String TOO_SLOW = "Client trop lent";
	/**
	 * Raison de fermeture après un envoi en échec
	 */
	static final String SEND_FAILED = "Échec d'envoi";

	private static final Logger LOG = Logger.getLogger(WebSocketConnection.class.getName());
	/**
	 * Threads partagés qui lancent les envois et les fermetures, pour ne jamais appeler
	 * Tomcat depuis un verrou applicatif ni bloquer un thread de Tomcat
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
	private final AtomicBoolean pinging = new AtomicBoolean();
	private boolean sending;
	private CloseReason closeRequest;

	/**
	 * Construit la connexion et borne la durée des envois sur la session
	 * @param session Session WebSocket associée
	 */
	WebSocketConnection(Session session) {
		this.session = session;
		this.id = "ws-" + session.getId();
		session.getAsyncRemote().setSendTimeout(SEND_TIMEOUT_MILLIS);
		session.getUserProperties().put(Constants.BLOCKING_SEND_TIMEOUT_PROPERTY, Long.valueOf(SEND_TIMEOUT_MILLIS));
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
	 * Envoie un ping WebSocket, sauf si le précédent n'est pas encore parti ;
	 * un ping en échec ferme la session
	 */
	void ping() {
		if (!isOpen() || !pinging.compareAndSet(false, true))
			return;
		DISPATCHER.execute(() -> {
			try {
				session.getAsyncRemote().sendPing(ByteBuffer.allocate(0));
			} catch (IOException | RuntimeException e) {
				LOG.log(Level.FINE, "Ping impossible vers " + id, e);
				fail(e);
			} finally {
				pinging.set(false);
			}
		});
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
	 * Ajoute un message à la file et démarre l'envoi si aucun n'est en cours ;
	 * ferme immédiatement la session si la file déborde
	 * @param message Message à envoyer
	 */
	private void enqueue(Outgoing message) {
		if (!session.isOpen())
			return;
		CloseReason overflow = null;
		synchronized (queue) {
			if (closeRequest != null)
				return;
			if (message.replaceable)
				queue.removeIf(m -> m.replaceable);
			if (queue.size() >= MAX_QUEUED_MESSAGES) {
				queue.clear();
				overflow = new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, TOO_SLOW);
				closeRequest = overflow;
			} else {
				queue.add(message);
				if (sending)
					return;
				sending = true;
			}
		}
		if (overflow != null) {
			LOG.warning(() -> "Client " + id + " trop lent, fermeture de la connexion");
			closeLater(overflow);
			return;
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
			fail(e);
		}
	}

	/**
	 * Traite le résultat d'un envoi et enchaîne sur le suivant
	 * @param result Résultat de l'envoi
	 */
	private void sent(SendResult result) {
		if (!result.isOK()) {
			LOG.log(Level.FINE, "Échec d'envoi vers " + id, result.getException());
			fail(result.getException());
			return;
		}
		transmitNext();
	}

	/**
	 * Abandonne la file après une erreur d'envoi et ferme la session : un client
	 * qui a perdu un message ne doit pas rester connecté
	 * @param cause Erreur rencontrée
	 */
	private void fail(Throwable cause) {
		CloseReason close;
		synchronized (queue) {
			queue.clear();
			if (closeRequest == null)
				closeRequest = cause instanceof SocketTimeoutException
					? new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, TOO_SLOW)
					: new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, SEND_FAILED);
			close = closeRequest;
		}
		LOG.info(() -> "Connexion " + id + " fermée : " + close.getReasonPhrase());
		closeLater(close);
	}

	/**
	 * Ferme la session depuis un thread du répartiteur
	 * @param reason Code et raison de la fermeture
	 */
	private void closeLater(CloseReason reason) {
		DISPATCHER.execute(() -> closeNow(reason));
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
