package communication.game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lien TCP avec le jeu pour une manche : un thread dédié se connecte, réalise la poignée
 * de main puis échange actions et états toutes les 50 ms, en relayant pause, reprise et arrêt
 * @author mourtaza
 *
 * @see GameLinkListener
 */
public final class GameLink {
	/**
	 * Code annonçant le démarrage d'une partie
	 */
	public static final int CODE_START = 0;
	/**
	 * Code de mise en pause
	 */
	public static final int CODE_PAUSE = -1;
	/**
	 * Code d'arrêt demandé par l'administrateur
	 */
	public static final int CODE_STOP = -2;
	/**
	 * Code de reprise après une pause
	 */
	public static final int CODE_RESUME = -3;
	/**
	 * Type d'action : déplacement
	 */
	public static final byte ACTION_MOVE = 0;
	/**
	 * Type d'action : attaque
	 */
	public static final byte ACTION_ATTACK = 1;
	/**
	 * Raison d'échec : connexion impossible
	 */
	public static final String UNREACHABLE = "Jeu injoignable";
	/**
	 * Raison d'échec : le jeu a refusé la partie
	 */
	public static final String REFUSED = "Partie refusée par le jeu";
	/**
	 * Raison d'échec : le jeu n'a pas répondu à la poignée de main
	 */
	public static final String NO_HANDSHAKE = "Le jeu n'a pas répondu au démarrage";

	private static final Logger LOG = Logger.getLogger(GameLink.class.getName());
	private static final AtomicInteger COUNTER = new AtomicInteger();

	/**
	 * Demandes traitées par la boucle d'échange
	 */
	private enum Request { TICK, PAUSE, RESUME, STOP }

	private final GameLinkSettings settings;
	private final List<Participant> roster;
	private final ActionSource actions;
	private final GameLinkListener listener;
	private final Thread thread;
	private final AtomicBoolean launched = new AtomicBoolean();
	/**
	 * Moniteur protégeant les demandes de pause et d'arrêt
	 */
	private final Object monitor = new Object();
	private boolean pauseRequested;
	private boolean stopRequested;
	private volatile Socket socket;
	private volatile long exchanges;

	/**
	 * Construit le lien, sans le démarrer
	 * @param settings Paramètres de connexion
	 * @param roster Joueurs de la manche
	 * @param actions Source des actions à transmettre
	 * @param listener Destinataire des événements
	 */
	public GameLink(GameLinkSettings settings, List<Participant> roster, ActionSource actions, GameLinkListener listener) {
		if (roster.isEmpty() || roster.size() > GameSnapshot.MAX_PLAYERS)
			throw new IllegalArgumentException("Nombre de joueurs invalide : " + roster.size());
		this.settings = settings;
		this.roster = List.copyOf(roster);
		this.actions = actions;
		this.listener = listener;
		this.thread = new Thread(this::run, "game-link-" + COUNTER.incrementAndGet());
		this.thread.setDaemon(true);
	}

	/**
	 * Démarre le thread du lien
	 * @throws IllegalStateException si le lien a déjà été démarré
	 */
	public void start() {
		if (!launched.compareAndSet(false, true))
			throw new IllegalStateException("Lien déjà démarré");
		thread.start();
	}

	/**
	 * Demande la mise en pause (code -1 envoyé au prochain échange)
	 */
	public void pause() {
		synchronized (monitor) {
			pauseRequested = true;
			monitor.notifyAll();
		}
	}

	/**
	 * Demande la reprise (code -3 envoyé immédiatement si la pause a été transmise)
	 */
	public void resume() {
		synchronized (monitor) {
			pauseRequested = false;
			monitor.notifyAll();
		}
	}

	/**
	 * Demande l'arrêt (code -2 envoyé dès que possible, puis fermeture)
	 */
	public void stop() {
		synchronized (monitor) {
			stopRequested = true;
			monitor.notifyAll();
		}
	}

	/**
	 * Indique si le thread du lien est actif
	 * @return true tant que le lien n'est pas terminé
	 */
	public boolean isAlive() { return thread.isAlive(); }

	/**
	 * Retourne le nombre d'échanges réalisés depuis le début de la partie
	 * @return Nombre d'échanges
	 */
	public long exchanges() { return exchanges; }

	/**
	 * Retourne les joueurs de la manche
	 * @return Joueurs transmis au jeu
	 */
	public List<Participant> roster() { return roster; }

	/**
	 * Attend la fin du thread du lien
	 * @param millis Délai maximal d'attente
	 * @return true si le lien est terminé
	 * @throws InterruptedException si l'attente est interrompue
	 */
	public boolean awaitTermination(long millis) throws InterruptedException {
		thread.join(millis);
		return !thread.isAlive();
	}

	/**
	 * Arrête le lien et force la fermeture de la connexion s'il ne se termine pas à temps
	 * @param graceMillis Délai laissé au lien pour transmettre l'arrêt
	 */
	public void close(long graceMillis) {
		stop();
		if (!launched.get())
			return;
		try {
			if (!awaitTermination(graceMillis)) {
				closeQuietly(socket);
				awaitTermination(graceMillis);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			closeQuietly(socket);
		}
	}

	/**
	 * Méthode principale du thread : connexion, poignée de main puis boucle d'échange
	 */
	private void run() {
		Socket s = new Socket();
		socket = s;
		try {
			DataInputStream in;
			DataOutputStream out;
			try {
				s.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMillis());
				s.setTcpNoDelay(true);
				s.setSoTimeout(settings.readTimeoutMillis());
				in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
				out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
			} catch (IOException e) {
				LOG.warning(() -> "Connexion au jeu impossible (" + address() + ") : " + e);
				fire(l -> l.onStartFailed(this, UNREACHABLE));
				return;
			}
			LOG.info(() -> "Connecté au jeu " + address() + ", " + roster.size() + " joueur(s)");

			boolean accepted;
			try {
				sendHandshake(out);
				accepted = in.readBoolean();
			} catch (IOException e) {
				LOG.warning(() -> "Poignée de main avec le jeu interrompue : " + e);
				fire(l -> l.onStartFailed(this, NO_HANDSHAKE));
				return;
			}
			if (!accepted) {
				LOG.warning("Le jeu a refusé la partie");
				fire(l -> l.onStartFailed(this, REFUSED));
				return;
			}
			LOG.info("Partie acceptée par le jeu");
			fire(l -> l.onStarted(this));

			exchange(in, out);
		} finally {
			closeQuietly(s);
			LOG.fine(() -> "Lien avec le jeu fermé après " + exchanges + " échange(s)");
		}
	}

	/**
	 * Envoie la poignée de main : code 0, nombre de joueurs puis identifiants et pseudos
	 * @param out Flux vers le jeu
	 * @throws IOException en cas d'erreur d'écriture
	 */
	private void sendHandshake(DataOutputStream out) throws IOException {
		out.writeInt(CODE_START);
		out.writeInt(roster.size());
		for (Participant p : roster) {
			out.writeByte(p.id());
			out.writeUTF(p.pseudo());
		}
		out.flush();
	}

	/**
	 * Boucle d'échange avec le jeu jusqu'à la fin de la partie, l'arrêt ou une erreur
	 * @param in Flux depuis le jeu
	 * @param out Flux vers le jeu
	 */
	private void exchange(DataInputStream in, DataOutputStream out) {
		long tickNanos = TimeUnit.MILLISECONDS.toNanos(settings.tickMillis());
		long nextTick = System.nanoTime();
		boolean paused = false;

		try {
			while (true) {
				switch (awaitRequest(paused, nextTick)) {
					case STOP:
						out.writeInt(CODE_STOP);
						out.flush();
						LOG.info("Arrêt transmis au jeu");
						fire(l -> l.onStopped(this));
						return;
					case PAUSE:
						out.writeInt(CODE_PAUSE);
						out.flush();
						paused = true;
						LOG.info("Pause transmise au jeu");
						break;
					case RESUME:
						out.writeInt(CODE_RESUME);
						out.flush();
						paused = false;
						nextTick = System.nanoTime();
						LOG.info("Reprise transmise au jeu");
						break;
					case TICK:
						long now = System.nanoTime();
						nextTick += tickNanos;
						if (nextTick < now)
							nextTick = now + tickNanos;
						if (!tick(in, out))
							return;
						break;
				}
			}
		} catch (IOException e) {
			boolean stopping;
			synchronized (monitor) {
				stopping = stopRequested;
			}
			if (stopping) {
				LOG.info(() -> "Connexion fermée pendant l'arrêt : " + e);
				fire(l -> l.onStopped(this));
			} else {
				String reason = e instanceof SocketTimeoutException
					? "Le jeu ne répond plus"
					: "Connexion au jeu perdue";
				LOG.log(Level.WARNING, reason + " après " + exchanges + " échange(s)", e);
				fire(l -> l.onLinkLost(this, reason));
			}
		}
	}

	/**
	 * Attend la prochaine demande à traiter
	 * @param paused true si la pause a déjà été transmise au jeu
	 * @param nextTick Instant du prochain échange (horloge monotone, en nanosecondes)
	 * @return Demande à traiter
	 */
	private Request awaitRequest(boolean paused, long nextTick) {
		synchronized (monitor) {
			try {
				while (true) {
					if (stopRequested)
						return Request.STOP;
					if (pauseRequested && !paused)
						return Request.PAUSE;
					if (!pauseRequested && paused)
						return Request.RESUME;
					if (paused) {
						monitor.wait();
					} else {
						long waitNanos = nextTick - System.nanoTime();
						if (waitNanos <= 0)
							return Request.TICK;
						TimeUnit.NANOSECONDS.timedWait(monitor, waitNanos);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				stopRequested = true;
				return Request.STOP;
			}
		}
	}

	/**
	 * Réalise un échange : envoi des actions, lecture de l'état et de l'indicateur `enCours`
	 * @param in Flux depuis le jeu
	 * @param out Flux vers le jeu
	 * @return true si la partie continue
	 * @throws IOException en cas d'erreur de communication ou de taille d'état invalide
	 */
	private boolean tick(DataInputStream in, DataOutputStream out) throws IOException {
		byte[] payload;
		try {
			payload = actions.drainActions(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
		} catch (RuntimeException e) {
			LOG.log(Level.SEVERE, "Impossible de récupérer les actions des joueurs", e);
			payload = new byte[0];
		}
		out.writeInt(payload.length);
		out.write(payload);
		out.flush();

		int size = in.readInt();
		if (size < 0 || size > GameSnapshot.MAX_SIZE)
			throw new ProtocolException("Taille d'état invalide : " + size);
		byte[] data = new byte[size];
		in.readFully(data);
		boolean running = in.readBoolean();
		exchanges++;

		final int sent = payload.length;
		LOG.finest(() -> "Échange " + exchanges + " : " + sent + " octet(s) d'actions, " + size + " octet(s) d'état");

		GameSnapshot snapshot = null;
		try {
			snapshot = GameSnapshot.decode(data);
		} catch (ProtocolException e) {
			LOG.warning(() -> "État ignoré : " + e.getMessage());
		}

		final GameSnapshot decoded = snapshot;
		if (!running) {
			LOG.info("Fin de partie annoncée par le jeu");
			fire(l -> l.onFinished(this, decoded));
			return false;
		}
		if (decoded != null)
			fire(l -> l.onSnapshot(this, decoded));
		return true;
	}

	/**
	 * Transmet un événement à l'écouteur en isolant ses erreurs
	 * @param event Événement à transmettre
	 */
	private void fire(Consumer<GameLinkListener> event) {
		try {
			event.accept(listener);
		} catch (RuntimeException e) {
			LOG.log(Level.SEVERE, "Erreur lors du traitement d'un événement du jeu", e);
		}
	}

	/**
	 * Retourne l'adresse du jeu sous forme lisible
	 * @return Hôte et port
	 */
	private String address() {
		return settings.host() + ":" + settings.port();
	}

	/**
	 * Ferme une socket sans propager d'erreur
	 * @param s Socket à fermer, éventuellement null
	 */
	private static void closeQuietly(Socket s) {
		if (s == null)
			return;
		try {
			s.close();
		} catch (IOException e) {
			LOG.fine(() -> "Fermeture de la connexion au jeu : " + e);
		}
	}
}
