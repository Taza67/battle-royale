package outside;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import inside.Board;
import inside.BoardSnapshot;
import inside.BotController;
import inside.GameEvent;
import inside.IConfig;

/**
 * Simulation à pas fixe (60 pas par seconde) exécutée sur son propre fil.
 * <p>
 * Seul ce fil fait avancer le plateau et les robots. L'affichage lit la dernière image publiée
 * ({@link #getFrame()}) et les événements produits ({@link #drainEvents()}) ; le clavier et le
 * réseau déposent des commandes dans le plateau. Ainsi, un affichage bloqué (déplacement de la
 * fenêtre, carte graphique lente) n'interrompt pas la partie des joueurs distants.
 * @author mourtaza
 */
public class SimulationLoop implements Runnable, AutoCloseable, IConfig {
	private static final Logger LOGGER = Logger.getLogger(SimulationLoop.class.getName());
	/**
	 * Durée d'un pas, en nanosecondes
	 */
	public static final long TICK_NANOS = 1_000_000_000L / TICKS_PER_SECOND;
	/**
	 * Nombre maximal de pas rattrapés d'un coup après un retard (le reste du retard est abandonné)
	 */
	public static final int MAX_CATCH_UP_TICKS = TICKS_PER_SECOND / 4;
	/**
	 * Nombre maximal de lots d'événements en attente de lecture par l'affichage
	 */
	public static final int MAX_EVENT_BATCHES = 4 * TICKS_PER_SECOND;

	/**
	 * Partie confiée à la simulation
	 * @param board Plateau
	 * @param bots Robots du plateau (null si aucun)
	 * @param localId Identifiant du joueur local (-1 si aucun)
	 */
	public record Session(Board board, BotController bots, int localId) {
		/**
		 * Prépare une partie, avec un contrôleur de robots si le plateau en contient
		 * @param board Plateau
		 * @param localId Identifiant du joueur local (-1 si aucun)
		 * @return Partie
		 */
		public static Session of(Board board, int localId) {
			boolean hasBots = board.getPlayers().stream().anyMatch(p -> p.isBot());
			return new Session(board, hasBots ? new BotController(board, board.getSettings().getSeed()) : null, localId);
		}
	}

	/**
	 * Image publiée après un lot de pas
	 * @param session Partie concernée
	 * @param previous Image précédant le dernier pas (null au démarrage)
	 * @param current Image après le dernier pas
	 * @param time Instant prévu du dernier pas (System.nanoTime), base de l'interpolation
	 */
	public record Frame(Session session, BoardSnapshot previous, BoardSnapshot current, long time) {
		/**
		 * Avancement de l'interpolation entre les deux images à un instant donné
		 * @param now Instant (System.nanoTime)
		 * @return Avancement entre 0 et 1
		 */
		public float alpha(long now) {
			return (float)Math.max(0, Math.min(1, (now - time) / (double)TICK_NANOS));
		}
	}

	/**
	 * Événements produits par un pas
	 * @param session Partie concernée
	 * @param events Événements
	 * @param snapshot Image après le pas
	 */
	public record Events(Session session, List<GameEvent> events, BoardSnapshot snapshot) {}

	/**
	 * Partie à prendre en charge au prochain passage
	 */
	private final AtomicReference<Session> pending = new AtomicReference<>();
	/**
	 * Lots d'événements en attente de lecture
	 */
	private final BlockingQueue<Events> events = new ArrayBlockingQueue<>(MAX_EVENT_BATCHES);
	/**
	 * Fil de la simulation
	 */
	private final Thread thread;

	/**
	 * Dernière image publiée
	 */
	private volatile Frame frame;
	/**
	 * Erreur qui a arrêté la simulation
	 */
	private volatile RuntimeException failure;
	/**
	 * Indique si la simulation doit s'arrêter
	 */
	private volatile boolean closed;
	/**
	 * Partie en cours (fil de la simulation)
	 */
	private Session session;
	/**
	 * Instant prévu du prochain pas (fil de la simulation)
	 */
	private long nextTick;


	/**
	 * Construit la simulation (sans démarrer son fil)
	 */
	public SimulationLoop() {
		thread = new Thread(this, "simulation");
		thread.setDaemon(true);
	}

	/**
	 * Démarre le fil de la simulation
	 */
	public void start() {
		thread.start();
	}

	/**
	 * Confie une nouvelle partie à la simulation (utilisable depuis n'importe quel fil)
	 * @param s Partie
	 */
	public void play(Session s) {
		pending.set(s);
		LockSupport.unpark(thread);
	}

	/**
	 * Retourne la dernière image publiée
	 * @return Image, ou null si aucune partie n'a commencé
	 */
	public Frame getFrame() { return frame; }

	/**
	 * Retourne l'erreur qui a arrêté la simulation
	 * @return Erreur, ou null si la simulation fonctionne
	 */
	public RuntimeException getFailure() { return failure; }

	/**
	 * Retourne et vide les lots d'événements produits depuis le dernier appel
	 * @return Lots d'événements, dans l'ordre
	 */
	public List<Events> drainEvents() {
		List<Events> batches = new ArrayList<>();
		events.drainTo(batches);
		return batches;
	}

	@Override
	public void run() {
		try {
			while (!closed) {
				advance(System.nanoTime());
				long wait = nextTick - System.nanoTime();
				if (wait > 0) LockSupport.parkNanos(this, wait);
			}
		} catch (RuntimeException e) {
			LOGGER.log(Level.SEVERE, "Erreur de la simulation", e);
			failure = e;
		}
	}

	/**
	 * Effectue les pas prévus jusqu'à un instant, puis publie l'image
	 * @param now Instant courant (System.nanoTime)
	 * @return Nombre de pas effectués
	 */
	int advance(long now) {
		Session p = pending.getAndSet(null);
		if (p != null) {
			session = p;
			nextTick = now;
			frame = new Frame(p, null, p.board().getSnapshot(), now);
		}

		if (session == null) {
			nextTick = now + TICK_NANOS;
			return 0;
		}

		if (now - nextTick >= MAX_CATCH_UP_TICKS * TICK_NANOS)
			nextTick = now - (MAX_CATCH_UP_TICKS - 1) * TICK_NANOS;

		BoardSnapshot previous = null;
		int ticks = 0;
		while (now - nextTick >= 0) {
			previous = session.board().getSnapshot();
			step();
			nextTick += TICK_NANOS;
			ticks++;
		}

		if (ticks > 0) frame = new Frame(session, previous, session.board().getSnapshot(), nextTick - TICK_NANOS);
		return ticks;
	}

	/**
	 * Effectue un pas : robots, plateau, puis transmission des événements
	 */
	private void step() {
		Board board = session.board();
		if (session.bots() != null) session.bots().update();
		board.tick();

		List<GameEvent> drained = board.drainEvents();
		if (drained.isEmpty()) return;
		Events batch = new Events(session, drained, board.getSnapshot());
		int dropped = 0;
		while (!events.offer(batch))
			if (events.poll() != null) dropped++;
		if (dropped > 0)
			LOGGER.warning(dropped + " lot(s) d'événements abandonné(s) : l'affichage est en retard de plus de "
				+ MAX_EVENT_BATCHES / TICKS_PER_SECOND + " s");
	}

	/**
	 * Arrête le fil de la simulation
	 */
	@Override
	public void close() {
		closed = true;
		LockSupport.unpark(thread);
		try {
			thread.join(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
