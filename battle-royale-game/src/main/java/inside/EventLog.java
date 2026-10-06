package inside;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import inside.BoardSnapshot.KillFeedEntry;

/**
 * Journal des événements produits pendant la simulation (coups, tirs, changements de phase,
 * éliminations) et du fil des dernières éliminations publié dans l'image du plateau.
 * Alimenté par le fil de la simulation, vidé par {@link #drain()} une fois le pas publié.
 * @author mourtaza
 *
 * @see Board#drainEvents()
 * @see GameEvent
 * @see KillFeedEntry
 */
final class EventLog {
	/**
	 * Nombre d'éliminations conservées dans le fil des éliminations
	 */
	private static final int KILL_FEED_SIZE = 6;
	/**
	 * Nombre maximal d'événements conservés en attente de lecture
	 */
	private static final int MAX_PENDING_EVENTS = 2048;

	/**
	 * Événements produits et pas encore lus
	 */
	private final Deque<GameEvent> events = new ArrayDeque<>();
	/**
	 * Dernières éliminations
	 */
	private final Deque<KillFeedEntry> killFeed = new ArrayDeque<>();
	/**
	 * Événements abandonnés par saturation de la file depuis la dernière lecture
	 */
	private int droppedEvents;


	/**
	 * Ajoute un événement en attente ; si la file est saturée, le plus ancien est abandonné
	 * @param e Événement
	 */
	void add(GameEvent e) {
		if (events.size() >= MAX_PENDING_EVENTS) {
			events.pollFirst();
			droppedEvents++;
		}
		events.addLast(e);
	}

	/**
	 * Ajoute une élimination au fil, en ne gardant que les plus récentes
	 * @param e Élimination
	 */
	void addKill(KillFeedEntry e) {
		killFeed.addLast(e);
		while (killFeed.size() > KILL_FEED_SIZE) killFeed.removeFirst();
	}

	/**
	 * Retourne et vide la liste des événements produits depuis le dernier appel
	 * @return Événements
	 */
	List<GameEvent> drain() {
		List<GameEvent> drained = new ArrayList<>(events);
		events.clear();
		return drained;
	}

	/**
	 * Retourne et remet à zéro le nombre d'événements abandonnés depuis la dernière lecture
	 * @return Nombre d'événements abandonnés
	 */
	int takeDropped() {
		int dropped = droppedEvents;
		droppedEvents = 0;
		return dropped;
	}

	/**
	 * Retourne le fil des dernières éliminations
	 * @return Éliminations, de la plus ancienne à la plus récente
	 */
	List<KillFeedEntry> killFeed() {
		return new ArrayList<>(killFeed);
	}
}
