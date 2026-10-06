package communication.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import communication.game.GameLink;
import communication.game.GameLinkListener;
import communication.game.GameLinkSettings;
import communication.game.GameSnapshot;
import communication.game.Participant;
import communication.game.PlayerSnapshot;
import communication.message.ClientMessage;
import communication.message.Json;
import communication.message.ServerMessage;

/**
 * État partagé de la partie : joueurs inscrits, administrateur, état de la manche
 * et lien avec le jeu. Toutes les méthodes publiques sont thread-safe ; l'état est
 * protégé par un unique verrou et les envois vers les clients ne bloquent jamais.
 * @author mourtaza
 *
 */
public final class GameSession implements AutoCloseable {
	/**
	 * Nombre maximal de joueurs (une image par identifiant dans le panneau d'administration)
	 */
	public static final int MAX_PLAYERS = 50;
	/**
	 * Points de vie affichés avant le premier état reçu du jeu
	 */
	public static final int DEFAULT_MAX_LIFE = 100;
	/**
	 * Intervalle minimal entre deux listes de joueurs envoyées pendant une manche (2 Hz)
	 */
	public static final long PLAYERS_INTERVAL_MILLIS = 500;
	/**
	 * Intervalle minimal entre deux états envoyés à un joueur (environ 20 Hz)
	 */
	public static final long STATE_MIN_INTERVAL_MILLIS = 40;
	/**
	 * Intervalle au bout duquel un état identique est renvoyé
	 */
	public static final long STATE_KEEPALIVE_MILLIS = 1000;

	/** Refus : pseudo associé à une session ouverte */
	public static final String PSEUDO_TAKEN = "Pseudo déjà utilisé";
	/** Refus : inscription pendant une manche */
	public static final String REGISTRATION_CLOSED = "Inscriptions fermées : la partie a déjà commencé";
	/** Refus : nombre maximal de joueurs atteint */
	public static final String GAME_FULL = "Partie complète";
	/** Refus : mauvais mot de passe administrateur */
	public static final String WRONG_PASSWORD = "Mot de passe administrateur incorrect";
	/** Refus envoyé à l'ancienne session d'un joueur reprise grâce au jeton */
	public static final String SESSION_TAKEN_OVER = "Session reprise par une autre connexion";
	/** Raison de fermeture après trop de mots de passe administrateur erronés */
	public static final String TOO_MANY_WRONG_PASSWORDS = "Trop de mots de passe administrateur incorrects";
	/** Nombre de mots de passe administrateur erronés au bout duquel la session est fermée */
	public static final int MAX_WRONG_PASSWORDS = 5;
	/** Refus : place d'administrateur occupée */
	public static final String ADMIN_TAKEN = "Un administrateur est déjà connecté";
	/** Refus envoyé à l'ancienne session administrateur remplacée */
	public static final String ADMIN_REPLACED = "Session administrateur reprise par une autre connexion";
	/** Erreur : aucun joueur */
	public static final String NO_PLAYER = "Aucun joueur inscrit";
	/** Erreur : aucun joueur connecté au lancement */
	public static final String NO_CONNECTED_PLAYER = "Aucun joueur connecté";
	/** Erreur : lancement déjà demandé */
	public static final String ALREADY_STARTING = "Démarrage déjà en cours";
	/** Erreur : manche déjà lancée */
	public static final String ALREADY_RUNNING = "La partie est déjà lancée";
	/** Erreur : pause hors manche */
	public static final String NOT_RUNNING = "La partie n'est pas en cours";
	/** Erreur : reprise sans pause */
	public static final String NOT_PAUSED = "La partie n'est pas en pause";
	/** Erreur : arrêt sans manche */
	public static final String NOTHING_TO_STOP = "Aucune partie en cours";
	/** Erreur : lancement annulé par un arrêt */
	public static final String START_CANCELLED = "Démarrage annulé";
	/** Erreur : la manche précédente n'a pas fini de se fermer */
	public static final String PREVIOUS_ROUND = "La manche précédente n'est pas encore terminée";
	/** Erreur : serveur en cours d'arrêt */
	public static final String SHUTTING_DOWN = "Serveur en cours d'arrêt";

	private static final Logger LOG = Logger.getLogger(GameSession.class.getName());
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Object lock = new Object();
	private final GameLinkSettings linkSettings;
	private final String adminPassword;
	private final ScheduledExecutorService scheduler;
	private final GameLinkListener events = new LinkEvents();

	private final Map<String, Player> playersByKey = new HashMap<>();
	private final TreeMap<Integer, Player> playersById = new TreeMap<>();
	/**
	 * Jetons de reprise par pseudo (en minuscules), conservés pendant toute la vie du serveur
	 */
	private final Map<String, String> tokens = new HashMap<>();
	/**
	 * Mots de passe administrateur erronés par connexion ; les connexions fermées sont oubliées
	 */
	private final Map<ClientConnection, Integer> wrongPasswords = new WeakHashMap<>();
	private ClientConnection admin;
	private GameState state = GameState.LOBBY;
	private boolean starting;
	private boolean closed;
	private GameLink link;
	/**
	 * Connexion qui a demandé le lancement en cours : seule elle reçoit l'acquittement de `start`
	 */
	private ClientConnection startInitiator;
	/**
	 * Vrai quand le lancement a été annulé par un arrêt : les événements du lien sont
	 * consommés sans démarrer ni terminer de manche
	 */
	private boolean startAborted;
	private PendingActions actions;
	private GameSnapshot lastSnapshot;
	private String lastEndJson;
	/**
	 * Vrai entre l'arrêt d'une manche et la réception de son état final
	 */
	private boolean awaitingFinalState;
	private String lastPlayersJson;
	private long lastPlayersMillis;
	private boolean playersFlushScheduled;

	/**
	 * Construit la session de jeu
	 * @param linkSettings Paramètres de connexion au jeu
	 * @param adminPassword Mot de passe administrateur, null ou vide pour attribuer
	 *                      la place à la première session qui la réclame
	 */
	public GameSession(GameLinkSettings linkSettings, String adminPassword) {
		this.linkSettings = linkSettings;
		this.adminPassword = adminPassword == null || adminPassword.isEmpty() ? null : adminPassword;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "game-session-scheduler");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Retourne l'état courant de la partie
	 * @return État de la partie
	 */
	public GameState state() {
		synchronized (lock) {
			return state;
		}
	}

	/**
	 * Indique si un lancement attend la réponse du jeu
	 * @return true pendant la connexion et la poignée de main
	 */
	public boolean isStarting() {
		synchronized (lock) {
			return starting;
		}
	}

	/**
	 * Indique si un mot de passe administrateur est exigé
	 * @return true si la propriété de mot de passe est définie
	 */
	public boolean requiresAdminPassword() { return adminPassword != null; }

	/**
	 * Retourne la liste des joueurs inscrits, par identifiant croissant
	 * @return Lignes de la liste des joueurs
	 */
	public List<ServerMessage.PlayerEntry> playerEntries() {
		synchronized (lock) {
			return entries();
		}
	}

	/**
	 * Inscrit un joueur ou le reconnecte s'il existe déjà, sans jeton de reprise
	 * @param c Connexion du joueur
	 * @param pseudo Pseudo validé
	 * @return Joueur inscrit, ou null si l'inscription est refusée
	 * @see #join(ClientConnection, String, String)
	 */
	public Player join(ClientConnection c, String pseudo) {
		return join(c, pseudo, null);
	}

	/**
	 * Inscrit un joueur ou le reconnecte s'il existe déjà ; envoie `welcome` ou `rejected`.
	 * Si le pseudo est associé à une session ouverte, le bon jeton de reprise permet à la
	 * nouvelle connexion de remplacer l'ancienne, qui reçoit `rejected` puis est fermée.
	 * @param c Connexion du joueur
	 * @param pseudo Pseudo validé
	 * @param token Jeton de reprise fourni, null s'il est absent
	 * @return Joueur inscrit, ou null si l'inscription est refusée
	 */
	public Player join(ClientConnection c, String pseudo, String token) {
		synchronized (lock) {
			if (closed) {
				reject(c, SHUTTING_DOWN);
				return null;
			}
			Player existing = playersByKey.get(key(pseudo));
			if (existing != null) {
				if (starting && !inPendingRoster(existing)) {
					reject(c, REGISTRATION_CLOSED);
					return null;
				}
				ClientConnection current = existing.connection();
				if (current != null && current != c && current.isOpen()) {
					if (!tokenMatches(pseudo, token)) {
						LOG.info(() -> "Pseudo " + pseudo + " refusé pour " + c.id() + " : session " + current.id() + " ouverte");
						reject(c, PSEUDO_TAKEN);
						return null;
					}
					LOG.info(() -> "Session " + current.id() + " de " + existing + " reprise par " + c.id());
					reject(current, SESSION_TAKEN_OVER);
					current.close(SESSION_TAKEN_OVER);
				}
				existing.attach(c);
				LOG.info(() -> existing + " reconnecté (" + c.id() + "), partie " + state.wireName());
				c.send(Json.write(new ServerMessage.Welcome(existing.getId(), existing.getPseudo(), state.wireName(),
					tokenFor(existing.getPseudo()))));
				replayTo(existing);
				playersChanged();
				return existing;
			}

			if (starting || !state.isBetweenRounds()) {
				reject(c, REGISTRATION_CLOSED);
				return null;
			}
			int id = freeId();
			if (id < 0) {
				reject(c, GAME_FULL);
				return null;
			}
			Player player = new Player(id, pseudo, c);
			playersByKey.put(key(pseudo), player);
			playersById.put(id, player);
			LOG.info(() -> player + " inscrit (" + c.id() + ")");
			c.send(Json.write(new ServerMessage.Welcome(id, player.getPseudo(), state.wireName(), tokenFor(player.getPseudo()))));
			playersChanged();
			return player;
		}
	}

	/**
	 * Attribue la place d'administrateur ; envoie `admin-welcome` puis la liste des joueurs, ou `rejected`
	 * @param c Connexion qui réclame la place
	 * @param password Mot de passe fourni
	 * @return true si la connexion devient administrateur
	 */
	public boolean claimAdmin(ClientConnection c, String password) {
		synchronized (lock) {
			if (closed) {
				reject(c, SHUTTING_DOWN);
				return false;
			}
			if (adminPassword != null) {
				if (!MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), adminPassword.getBytes(StandardCharsets.UTF_8))) {
					int attempts = wrongPasswords.merge(c, 1, Integer::sum);
					LOG.warning(() -> "Mot de passe administrateur incorrect (" + c.id() + ", tentative " + attempts + ")");
					reject(c, WRONG_PASSWORD);
					if (attempts >= MAX_WRONG_PASSWORDS) {
						wrongPasswords.remove(c);
						LOG.warning(() -> "Session " + c.id() + " fermée après " + attempts + " mots de passe administrateur incorrects");
						c.close(TOO_MANY_WRONG_PASSWORDS);
					}
					return false;
				}
				ClientConnection previous = admin;
				if (previous != null && previous != c) {
					LOG.info(() -> "Session administrateur " + previous.id() + " remplacée par " + c.id());
					previous.send(Json.write(new ServerMessage.Rejected(ADMIN_REPLACED)));
					previous.close(ADMIN_REPLACED);
				}
			} else if (admin != null && admin != c && admin.isOpen()) {
				LOG.info(() -> "Place d'administrateur refusée à " + c.id());
				reject(c, ADMIN_TAKEN);
				return false;
			}

			admin = c;
			LOG.info(() -> "Administrateur connecté (" + c.id() + ")");
			c.send(Json.write(new ServerMessage.AdminWelcome(state.wireName())));
			sendPlayers(true);
			if (hasResult())
				c.send(lastEndJson);
			return true;
		}
	}

	/**
	 * Libère la place d'administrateur si elle est occupée par cette connexion
	 * @param c Connexion fermée
	 */
	public void releaseAdmin(ClientConnection c) {
		synchronized (lock) {
			if (admin == c) {
				admin = null;
				lastPlayersJson = null;
				LOG.info(() -> "Administrateur déconnecté (" + c.id() + ")");
			}
		}
	}

	/**
	 * Détache un joueur de sa connexion fermée ; il pourra se reconnecter avec son pseudo
	 * @param c Connexion fermée
	 * @param player Joueur associé à la connexion
	 */
	public void disconnect(ClientConnection c, Player player) {
		synchronized (lock) {
			if (player.connection() != c)
				return;
			player.detach();
			if (actions != null)
				actions.move(player.getId(), 0, 0);
			LOG.info(() -> player + " déconnecté (" + c.id() + ")");
			playersChanged();
		}
	}

	/**
	 * Enregistre l'intention de déplacement d'un joueur
	 * @param c Connexion émettrice
	 * @param player Joueur associé
	 * @param direction Direction de 0 à 7
	 * @param speed Vitesse de 0 à 4
	 */
	public void move(ClientConnection c, Player player, int direction, int speed) {
		synchronized (lock) {
			if (player.connection() != c || actions == null || state != GameState.RUNNING)
				return;
			actions.move(player.getId(), direction, speed);
		}
	}

	/**
	 * Met en file l'attaque d'un joueur, ignorée hors d'une manche en cours
	 * @param c Connexion émettrice
	 * @param player Joueur associé
	 * @param form Forme de l'attaque (1 ou 2)
	 */
	public void attack(ClientConnection c, Player player, int form) {
		synchronized (lock) {
			if (player.connection() != c || actions == null || state != GameState.RUNNING)
				return;
			if (!actions.attack(player.getId(), form))
				LOG.fine(() -> "Attaque ignorée pour " + player + " : file pleine");
		}
	}

	/**
	 * Exécute une commande de l'administrateur et lui répond par `ack`.
	 * Pour `start`, l'acquittement est envoyé quand le jeu a répondu.
	 * @param c Connexion émettrice, qui doit être celle de l'administrateur
	 * @param command Commande demandée
	 */
	public void command(ClientConnection c, ClientMessage.Command command) {
		if (!isAdmin(c, command))
			return;
		if (command == ClientMessage.Command.START)
			awaitPreviousRound();

		synchronized (lock) {
			if (!isAdmin(c, command))
				return;
			String error;
			switch (command) {
				case START:
					error = startRound(c);
					if (error == null)
						return;
					break;
				case PAUSE:
					error = pauseRound();
					break;
				case RESUME:
					error = resumeRound();
					break;
				case STOP:
					error = stopRound();
					break;
				default:
					throw new IllegalStateException("Commande inconnue : " + command);
			}
			if (error != null)
				LOG.info(() -> "Commande " + command.wireName() + " refusée : " + error);
			c.send(Json.write(error == null
				? ServerMessage.Ack.success(command.wireName())
				: ServerMessage.Ack.failure(command.wireName(), error)));
		}
	}

	/**
	 * Arrête la manche en cours et libère les ressources
	 */
	@Override
	public void close() {
		GameLink current;
		synchronized (lock) {
			closed = true;
			current = link;
		}
		if (current != null)
			current.close(1000);
		scheduler.shutdownNow();
	}

	/**
	 * Vérifie que la connexion occupe la place d'administrateur
	 * @param c Connexion émettrice
	 * @param command Commande demandée, pour le journal
	 * @return true si la connexion est l'administrateur
	 */
	private boolean isAdmin(ClientConnection c, ClientMessage.Command command) {
		synchronized (lock) {
			if (c == admin)
				return true;
		}
		LOG.warning(() -> "Commande " + command.wireName() + " refusée : " + c.id() + " n'est pas administrateur");
		return false;
	}

	/**
	 * Laisse à la manche précédente le temps de fermer sa connexion avant un nouveau lancement
	 */
	private void awaitPreviousRound() {
		GameLink previous;
		synchronized (lock) {
			previous = starting || state.isInProgress() ? null : link;
		}
		if (previous == null)
			return;
		try {
			previous.awaitTermination(GameLink.STOP_TIMEOUT_MILLIS + 1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Lance une manche avec les joueurs connectés
	 * @param initiator Connexion de l'administrateur qui demande le lancement
	 * @return Message d'erreur, ou null si le lancement est en cours
	 */
	private String startRound(ClientConnection initiator) {
		if (closed)
			return SHUTTING_DOWN;
		if (starting)
			return ALREADY_STARTING;
		if (state.isInProgress())
			return ALREADY_RUNNING;
		if (link != null && link.isAlive())
			return PREVIOUS_ROUND;
		if (playersById.isEmpty())
			return NO_PLAYER;

		List<Participant> roster = new ArrayList<>();
		for (Player p : playersById.values())
			if (p.isConnected())
				roster.add(new Participant(p.getId(), p.getPseudo()));
		if (roster.isEmpty())
			return NO_CONNECTED_PLAYER;

		actions = new PendingActions(roster.stream().map(Participant::id).toList());
		starting = true;
		startInitiator = initiator;
		link = new GameLink(linkSettings, roster, actions, events);
		link.start();
		LOG.info(() -> "Lancement d'une manche avec " + roster.size() + " joueur(s) sur "
			+ linkSettings.host() + ":" + linkSettings.port());
		return null;
	}

	/**
	 * Prépare la session pour la manche acceptée par le jeu : retire les joueurs absents
	 * au lancement, remet les statistiques à zéro et oublie le résultat précédent
	 * @param roster Joueurs transmis au jeu
	 */
	private void beginRound(List<Participant> roster) {
		Set<Integer> ids = new HashSet<>();
		for (Participant p : roster)
			ids.add(p.id());
		for (Iterator<Player> it = playersById.values().iterator(); it.hasNext();) {
			Player p = it.next();
			if (!ids.contains(p.getId())) {
				it.remove();
				playersByKey.remove(key(p.getPseudo()));
				LOG.info(() -> p + " retiré : absent au lancement");
			} else {
				p.resetForRound();
			}
		}
		lastSnapshot = null;
		lastEndJson = null;
		awaitingFinalState = false;
	}

	/**
	 * Indique si un joueur fait partie de la manche en cours de lancement
	 * @param player Joueur
	 * @return true si le joueur a été transmis au jeu
	 */
	private boolean inPendingRoster(Player player) {
		for (Participant p : link.roster())
			if (p.id() == player.getId())
				return true;
		return false;
	}

	/**
	 * Met la manche en pause
	 * @return Message d'erreur, ou null en cas de succès
	 */
	private String pauseRound() {
		if (state != GameState.RUNNING)
			return NOT_RUNNING;
		link.pause();
		changeState(GameState.PAUSED);
		flushStates();
		return null;
	}

	/**
	 * Reprend la manche en pause
	 * @return Message d'erreur, ou null en cas de succès
	 */
	private String resumeRound() {
		if (state != GameState.PAUSED)
			return NOT_PAUSED;
		link.resume();
		changeState(GameState.RUNNING);
		return null;
	}

	/**
	 * Arrête la manche en cours ou annule un lancement en attente
	 * @return Message d'erreur, ou null en cas de succès
	 */
	private String stopRound() {
		if (starting) {
			starting = false;
			startAborted = true;
			link.stop();
			actions = null;
			LOG.info("Lancement annulé par l'administrateur");
			if (startInitiator != null)
				startInitiator.send(Json.write(ServerMessage.Ack.failure(ClientMessage.Command.START.wireName(), START_CANCELLED)));
			return null;
		}
		if (!state.isInProgress())
			return NOTHING_TO_STOP;
		link.stop();
		actions.clear();
		awaitingFinalState = true;
		LOG.info("Manche arrêtée par l'administrateur");
		changeState(GameState.STOPPED);
		sendPlayers(false);
		return null;
	}

	/**
	 * Change l'état de la partie et le diffuse à tous les clients
	 * @param next Nouvel état
	 */
	private void changeState(GameState next) {
		state = next;
		LOG.info(() -> "Partie : " + next.wireName());
		broadcast(Json.write(new ServerMessage.Game(next.wireName())));
	}

	/**
	 * Envoie un message à tous les joueurs connectés et à l'administrateur
	 * @param json Message JSON
	 */
	private void broadcast(String json) {
		for (Player p : playersById.values()) {
			ClientConnection c = p.connection();
			if (c != null)
				c.send(json);
		}
		if (admin != null)
			admin.send(json);
	}

	/**
	 * Renvoie à un joueur qui se reconnecte ce qu'il a manqué : son dernier état ou le résultat
	 * @param player Joueur reconnecté
	 */
	private void replayTo(Player player) {
		ClientConnection c = player.connection();
		if (state.isInProgress() && lastSnapshot != null) {
			PlayerSnapshot ps = lastSnapshot.player(player.getId());
			if (ps != null) {
				String json = Json.write(stateMessage(lastSnapshot, ps));
				c.sendLatest(json);
				player.stateSent(json, now());
			}
		} else if (hasResult()) {
			c.send(lastEndJson);
		}
	}

	/**
	 * Indique si le résultat de la dernière manche terminée ou arrêtée est disponible
	 * @return true si `end` doit être renvoyé aux sessions qui se reconnectent
	 */
	private boolean hasResult() {
		return (state == GameState.OVER || state == GameState.STOPPED) && lastEndJson != null;
	}

	/**
	 * Applique un état reçu du jeu et l'envoie aux joueurs concernés
	 * @param snapshot État reçu
	 * @param force true pour ignorer la limitation de fréquence
	 */
	private void applySnapshot(GameSnapshot snapshot, boolean force) {
		long now = now();
		for (PlayerSnapshot ps : snapshot.players()) {
			Player p = playersById.get(ps.id());
			if (p == null)
				continue;
			p.update(ps);
			ClientConnection c = p.connection();
			if (c == null)
				continue;
			String json = Json.write(stateMessage(snapshot, ps));
			if (force || p.shouldSendState(json, now)) {
				c.sendLatest(json);
				p.stateSent(json, now);
			}
		}
	}

	/**
	 * Envoie le dernier état connu aux joueurs dont l'affichage n'est pas à jour
	 */
	private void flushStates() {
		if (lastSnapshot == null)
			return;
		long now = now();
		for (PlayerSnapshot ps : lastSnapshot.players()) {
			Player p = playersById.get(ps.id());
			ClientConnection c = p == null ? null : p.connection();
			if (c == null)
				continue;
			String json = Json.write(stateMessage(lastSnapshot, ps));
			if (!json.equals(p.lastStateJson())) {
				c.sendLatest(json);
				p.stateSent(json, now);
			}
		}
	}

	/**
	 * Construit le message `state` d'un joueur
	 * @param snapshot État de la partie
	 * @param ps État du joueur
	 * @return Message `state`
	 */
	private static ServerMessage.State stateMessage(GameSnapshot snapshot, PlayerSnapshot ps) {
		return new ServerMessage.State(ps.status().wireName(), ps.life(), snapshot.maxLife(), ps.x(), ps.y(),
			ps.kills(), ps.rank(), snapshot.alive(), snapshot.total(), snapshot.phase().wireName(),
			snapshot.secondsLeft(), snapshot.zone(), snapshot.nextZone());
	}

	/**
	 * Termine la manche : applique l'état final, construit `end`, le mémorise, l'envoie
	 * aux joueurs et à l'administrateur puis envoie à ce dernier la liste des joueurs à jour
	 * @param finalSnapshot État final, ou null pour reprendre le dernier état reçu
	 * @param stopped true si la manche a été arrêtée avant son terme (l'état est déjà diffusé)
	 */
	private void endRound(GameSnapshot finalSnapshot, boolean stopped) {
		awaitingFinalState = false;
		actions = null;
		if (finalSnapshot != null) {
			lastSnapshot = finalSnapshot;
			applySnapshot(finalSnapshot, true);
		}
		ServerMessage.End end = endMessage(lastSnapshot, stopped);
		lastEndJson = Json.write(end);
		LOG.info(() -> (stopped ? "Manche arrêtée" : "Fin de manche") + ", vainqueur : "
			+ (end.winner() == null ? "aucun" : end.winner().pseudo()));
		if (!stopped)
			changeState(GameState.OVER);
		broadcast(lastEndJson);
		sendPlayers(true);
	}

	/**
	 * Construit le message de fin de partie pour les joueurs de la manche
	 * @param snapshot État final, éventuellement null
	 * @param stopped true si la manche a été arrêtée avant son terme
	 * @return Message `end`
	 */
	private ServerMessage.End endMessage(GameSnapshot snapshot, boolean stopped) {
		Set<Integer> ids = new HashSet<>();
		for (Participant p : link.roster())
			ids.add(p.id());
		Player winner = null;
		if (snapshot != null && snapshot.winnerId() >= 0 && ids.contains(snapshot.winnerId()))
			winner = playersById.get(snapshot.winnerId());
		if (winner == null)
			winner = playersById.values().stream()
				.filter(p -> ids.contains(p.getId()) && p.status() == PlayerSnapshot.Status.WINNER)
				.findFirst().orElse(null);

		List<ServerMessage.RankingEntry> ranking = playersById.values().stream()
			.filter(p -> ids.contains(p.getId()))
			.sorted(Comparator.<Player>comparingInt(p -> p.rank() > 0 ? p.rank() : Integer.MAX_VALUE)
				.thenComparing(Comparator.comparingInt(Player::kills).reversed())
				.thenComparingInt(Player::getId))
			.map(Player::toRankingEntry)
			.toList();
		int total = snapshot != null ? snapshot.total() : ids.size();
		return new ServerMessage.End(winner == null ? null : new ServerMessage.PlayerRef(winner.getId(), winner.getPseudo()),
			ranking, total, stopped);
	}

	/**
	 * Signale un changement de la liste des joueurs : envoi immédiat hors manche,
	 * au plus deux fois par seconde pendant une manche
	 */
	private void playersChanged() {
		if (admin == null)
			return;
		if (state.isBetweenRounds() && !starting) {
			sendPlayers(false);
			return;
		}
		long wait = lastPlayersMillis + PLAYERS_INTERVAL_MILLIS - now();
		if (wait <= 0 && !playersFlushScheduled) {
			sendPlayers(false);
		} else if (!playersFlushScheduled && !closed) {
			playersFlushScheduled = true;
			scheduler.schedule(this::flushPlayers, Math.max(wait, 1), TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Envoie la liste des joueurs différée
	 */
	private void flushPlayers() {
		synchronized (lock) {
			playersFlushScheduled = false;
			sendPlayers(false);
		}
	}

	/**
	 * Envoie la liste des joueurs à l'administrateur
	 * @param force true pour l'envoyer même si elle n'a pas changé
	 */
	private void sendPlayers(boolean force) {
		if (admin == null)
			return;
		String json = Json.write(new ServerMessage.Players(entries()));
		if (!force && json.equals(lastPlayersJson))
			return;
		admin.send(json);
		lastPlayersJson = json;
		lastPlayersMillis = now();
	}

	private List<ServerMessage.PlayerEntry> entries() {
		return playersById.values().stream().map(Player::toEntry).toList();
	}

	private int freeId() {
		for (int id = 0; id < MAX_PLAYERS; id++)
			if (!playersById.containsKey(id))
				return id;
		return -1;
	}

	/**
	 * Retourne le jeton de reprise d'un pseudo, créé à la première demande
	 * @param pseudo Pseudo
	 * @return Jeton aléatoire de 128 bits en hexadécimal
	 */
	private String tokenFor(String pseudo) {
		return tokens.computeIfAbsent(key(pseudo), k -> {
			byte[] bytes = new byte[16];
			RANDOM.nextBytes(bytes);
			return HexFormat.of().formatHex(bytes);
		});
	}

	/**
	 * Compare en temps constant le jeton fourni à celui du pseudo
	 * @param pseudo Pseudo
	 * @param token Jeton fourni, éventuellement null
	 * @return true si le jeton est celui du pseudo
	 */
	private boolean tokenMatches(String pseudo, String token) {
		String expected = tokens.get(key(pseudo));
		return token != null && expected != null
			&& MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
	}

	private static void reject(ClientConnection c, String reason) {
		c.send(Json.write(new ServerMessage.Rejected(reason)));
	}

	private static String key(String pseudo) {
		return pseudo.toLowerCase(Locale.ROOT);
	}

	private static long now() {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
	}

	/**
	 * Consomme un événement d'un lancement annulé par un arrêt : la manche n'a jamais
	 * démarré côté serveur, le lien se termine sans produire ni classement ni état
	 * @param l Lien émetteur de l'événement
	 * @return true si l'événement appartient au lancement annulé et doit être ignoré
	 */
	private boolean consumeAbortedStart(GameLink l) {
		if (l != link || !startAborted)
			return false;
		startAborted = false;
		startInitiator = null;
		return true;
	}

	/**
	 * Traite les événements du lien avec le jeu en ignorant ceux d'une manche périmée
	 */
	private final class LinkEvents implements GameLinkListener {
		@Override
		public void onStarted(GameLink l) {
			synchronized (lock) {
				if (consumeAbortedStart(l) || !starting)
					return;
				starting = false;
				beginRound(l.roster());
				ClientConnection initiator = startInitiator;
				startInitiator = null;
				if (initiator != null)
					initiator.send(Json.write(ServerMessage.Ack.success(ClientMessage.Command.START.wireName())));
				changeState(GameState.RUNNING);
				sendPlayers(false);
			}
		}

		@Override
		public void onStartFailed(GameLink l, String reason) {
			synchronized (lock) {
				if (consumeAbortedStart(l) || !starting)
					return;
				starting = false;
				actions = null;
				LOG.warning(() -> "Lancement impossible : " + reason);
				ClientConnection initiator = startInitiator;
				startInitiator = null;
				if (initiator != null)
					initiator.send(Json.write(ServerMessage.Ack.failure(ClientMessage.Command.START.wireName(), reason)));
				sendPlayers(false);
			}
		}

		@Override
		public void onSnapshot(GameLink l, GameSnapshot snapshot) {
			synchronized (lock) {
				if (l != link || startAborted || !state.isInProgress())
					return;
				lastSnapshot = snapshot;
				applySnapshot(snapshot, false);
				playersChanged();
			}
		}

		@Override
		public void onFinished(GameLink l, GameSnapshot finalSnapshot) {
			synchronized (lock) {
				if (consumeAbortedStart(l))
					return;
				if (l != link)
					return;
				if (state.isInProgress())
					endRound(finalSnapshot, false);
				else if (awaitingFinalState)
					endRound(finalSnapshot, true);
			}
		}

		@Override
		public void onStopped(GameLink l, GameSnapshot finalSnapshot) {
			synchronized (lock) {
				if (consumeAbortedStart(l))
					return;
				if (l != link || !awaitingFinalState)
					return;
				if (finalSnapshot == null)
					LOG.warning("Aucun état final reçu après l'arrêt, classement établi sur le dernier état connu");
				endRound(finalSnapshot, true);
			}
		}

		@Override
		public void onLinkLost(GameLink l, String reason) {
			synchronized (lock) {
				if (consumeAbortedStart(l))
					return;
				if (l != link)
					return;
				if (state.isInProgress()) {
					LOG.severe(() -> "Manche interrompue : " + reason);
					changeState(GameState.STOPPED);
					endRound(null, true);
				} else if (awaitingFinalState) {
					endRound(null, true);
				}
			}
		}
	}
}
