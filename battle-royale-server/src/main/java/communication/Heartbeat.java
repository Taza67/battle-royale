package communication;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Envoie périodiquement un ping WebSocket à toutes les connexions ouvertes,
 * depuis un unique planificateur, pour que les clients muets soient détectés
 * @author mourtaza
 *
 */
final class Heartbeat implements AutoCloseable {
	/**
	 * Intervalle entre deux pings, en millisecondes
	 */
	static final long PING_INTERVAL_MILLIS = 10_000;

	private static final Heartbeat SHARED = new Heartbeat(PING_INTERVAL_MILLIS);

	private final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();
	private final ScheduledExecutorService scheduler;

	/**
	 * Démarre un planificateur de pings
	 * @param intervalMillis Intervalle entre deux pings
	 */
	Heartbeat(long intervalMillis) {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "websocket-heartbeat");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::beat, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * Retourne le planificateur partagé par toutes les sessions du serveur
	 * @return Planificateur partagé
	 */
	static Heartbeat shared() { return SHARED; }

	/**
	 * Ajoute une connexion à surveiller
	 * @param connection Connexion ouverte
	 */
	void register(WebSocketConnection connection) {
		connections.add(connection);
	}

	/**
	 * Retire une connexion fermée
	 * @param connection Connexion fermée
	 */
	void unregister(WebSocketConnection connection) {
		connections.remove(connection);
	}

	/**
	 * Retourne le nombre de connexions surveillées
	 * @return Nombre de connexions
	 */
	int size() { return connections.size(); }

	/**
	 * Envoie un ping à chaque connexion ouverte et oublie les connexions fermées
	 */
	private void beat() {
		for (WebSocketConnection c : connections) {
			if (c.isOpen())
				c.ping();
			else
				connections.remove(c);
		}
	}

	/**
	 * Arrête le planificateur
	 */
	@Override
	public void close() {
		scheduler.shutdownNow();
	}
}
