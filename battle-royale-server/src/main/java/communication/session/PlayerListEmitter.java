package communication.session;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import communication.message.Json;
import communication.message.ServerMessage;

/**
 * Émet la liste des joueurs vers l'administrateur : envoi immédiat hors manche,
 * au plus {@link GameSession#PLAYERS_INTERVAL_MILLIS} fois par seconde pendant une
 * manche, sans renvoyer une liste identique.
 * Non thread-safe : {@link #changed}, {@link #send} et {@link #reset} sont appelés
 * sous le verrou de {@link GameSession}.
 * @author mourtaza
 */
final class PlayerListEmitter {
	private final Object lock;
	private final AdminGate admin;
	private final Supplier<List<ServerMessage.PlayerEntry>> entries;
	private final ScheduledExecutorService scheduler;
	private final BooleanSupplier closed;
	private final LongSupplier clock;
	private String lastJson;
	private long lastMillis;
	private boolean flushScheduled;

	/**
	 * Construit l'émetteur de la liste des joueurs
	 * @param lock Verrou de la session, repris par l'envoi différé
	 * @param admin Place d'administrateur, source du destinataire
	 * @param entries Liste des joueurs à émettre
	 * @param scheduler Planificateur des envois différés
	 * @param closed true quand la session est fermée (aucun envoi n'est alors planifié)
	 * @param clock Horloge en millisecondes
	 */
	PlayerListEmitter(Object lock, AdminGate admin, Supplier<List<ServerMessage.PlayerEntry>> entries,
			ScheduledExecutorService scheduler, BooleanSupplier closed, LongSupplier clock) {
		this.lock = lock;
		this.admin = admin;
		this.entries = entries;
		this.scheduler = scheduler;
		this.closed = closed;
		this.clock = clock;
	}

	/**
	 * Signale un changement de la liste des joueurs : envoi immédiat hors manche,
	 * différé et fusionné pendant une manche
	 * @param immediate true pour envoyer sans limitation de fréquence
	 */
	void changed(boolean immediate) {
		if (immediate) {
			send(false);
			return;
		}
		long wait = lastMillis + GameSession.PLAYERS_INTERVAL_MILLIS - clock.getAsLong();
		if (wait <= 0 && !flushScheduled) {
			send(false);
		} else if (!flushScheduled && !closed.getAsBoolean()) {
			flushScheduled = true;
			scheduler.schedule(this::flush, Math.max(wait, 1), TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Envoie la liste des joueurs à l'administrateur
	 * @param force true pour l'envoyer même si elle n'a pas changé
	 */
	void send(boolean force) {
		ClientConnection c = admin.get();
		if (c == null)
			return;
		String json = Json.write(new ServerMessage.Players(entries.get()));
		if (!force && json.equals(lastJson))
			return;
		c.send(json);
		lastJson = json;
		lastMillis = clock.getAsLong();
	}

	/**
	 * Oublie la dernière liste envoyée, à la libération de la place d'administrateur
	 */
	void reset() {
		lastJson = null;
	}

	/**
	 * Envoie la liste différée, sous le verrou de la session
	 */
	private void flush() {
		synchronized (lock) {
			flushScheduled = false;
			send(false);
		}
	}
}
