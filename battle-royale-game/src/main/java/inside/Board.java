package inside;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import inside.BoardSnapshot.BulletState;
import inside.BoardSnapshot.KillFeedEntry;
import inside.BoardSnapshot.PlayerState;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;

/**
 * Classe représentant le plateau de jeu : c'est la simulation qui fait autorité.
 * <p>
 * Elle n'est modifiée que par un seul fil, à pas fixe ({@link #tick()}). Les autres fils
 * (réseau, clavier) déposent des commandes dans une file sûre ({@link #enqueue(Command)}),
 * vidée au début de chaque pas, et lisent l'image immuable publiée à la fin de chaque pas
 * ({@link #getSnapshot()}).
 * @author mourtaza
 *
 * @see IConfig
 */
public class Board implements IConfig {
	/**
	 * Description d'un joueur à créer
	 * @param id Identifiant
	 * @param pseudo Pseudo
	 * @param bot true si le joueur est contrôlé par l'ordinateur
	 */
	public record PlayerSpec(int id, String pseudo, boolean bot) {}

	/**
	 * Nombre d'éliminations conservées dans le fil des éliminations
	 */
	private static final int KILL_FEED_SIZE = 6;
	/**
	 * Nombre maximal d'événements conservés en attente de lecture
	 */
	private static final int MAX_PENDING_EVENTS = 2048;
	/**
	 * Profondeur de chevauchement en dessous de laquelle deux éléments sont considérés en contact (arrondis)
	 */
	private static final float OVERLAP_TOLERANCE = 0.01f;

	/**
	 * Réglages de la partie
	 */
	private final GameSettings SETTINGS;
	/**
	 * Générateur aléatoire de la partie
	 */
	private final Random RANDOM;
	/**
	 * Carte du jeu
	 * @see Map
	 */
	private final Map MAP;
	/**
	 * Zone sûre
	 * @see SafeZone
	 */
	private final SafeZone SAFE_ZONE;
	/**
	 * Joueurs indexés par identifiant (triés)
	 */
	private final TreeMap<Integer, Player> PLAYERS;
	/**
	 * Projectiles actifs
	 */
	private final List<Bullet> BULLETS;
	/**
	 * File des commandes en attente, alimentée par n'importe quel fil
	 */
	private final Queue<Command> COMMANDS;
	/**
	 * Commandes de jeu reçues pendant une pause, réappliquées à la reprise
	 */
	private final Deque<Command> SUSPENDED;
	/**
	 * Événements produits et pas encore lus
	 */
	private final List<GameEvent> EVENTS;
	/**
	 * Dernières éliminations
	 */
	private final Deque<KillFeedEntry> KILL_FEED;

	/**
	 * Dernière image publiée
	 */
	private volatile BoardSnapshot snapshot;
	/**
	 * Phase de la partie
	 */
	private Phase phase = Phase.WARMUP;
	/**
	 * Indique si la partie est en pause
	 */
	private boolean paused;
	/**
	 * Indique si la partie a été arrêtée par l'administrateur
	 */
	private boolean stopped;
	/**
	 * Nombre de pas de simulation effectués (hors pause)
	 */
	private long tick;
	/**
	 * Nombre d'éliminations depuis le début
	 */
	private int eliminations;
	/**
	 * Identifiant du vainqueur, -1 si aucun
	 */
	private int winnerId = -1;
	/**
	 * Identifiant du prochain projectile
	 */
	private int nextBulletId;


	/**
	 * Construit un plateau avec une carte générée à partir de la graine des réglages
	 * @param settings Réglages de la partie
	 * @param players Joueurs à placer
	 */
	public Board(GameSettings settings, List<PlayerSpec> players) {
		this(settings, null, players);
	}

	/**
	 * Construit un plateau sur une carte donnée
	 * @param settings Réglages de la partie
	 * @param map Carte (null pour en générer une)
	 * @param players Joueurs à placer aléatoirement
	 */
	public Board(GameSettings settings, Map map, List<PlayerSpec> players) {
		if (players.size() > MAX_PLAYERS)
			throw new IllegalArgumentException("Trop de joueurs : " + players.size());

		SETTINGS = settings;
		RANDOM = new Random(settings.getSeed());
		MAP = map != null ? map : Map.generate(RANDOM, settings.getObstaclesNumber());
		SAFE_ZONE = new SafeZone(MAP.getBounds(), settings.getWaves(), RANDOM,
			c -> MAP.isFree(Rectangle.centered(c.getX(), c.getY(), PLAYER_RADIUS_X * 2, PLAYER_RADIUS_Y * 2)));
		PLAYERS = new TreeMap<>();
		BULLETS = new ArrayList<>();
		COMMANDS = new ConcurrentLinkedQueue<>();
		SUSPENDED = new ArrayDeque<>();
		EVENTS = new ArrayList<>();
		KILL_FEED = new ArrayDeque<>();

		for (PlayerSpec spec : players) {
			if (PLAYERS.containsKey(spec.id()))
				throw new IllegalArgumentException("Identifiant de joueur en double : " + spec.id());
			Vertice p = spawnPosition();
			Player player = new Player(spec.id(), spec.pseudo(), spec.bot(), p.getX(), p.getY());
			PLAYERS.put(spec.id(), player);
			MAP.updatePlayerArea(player);
		}

		publishSnapshot();
	}

	/**
	 * Cherche une position d'apparition libre et éloignée des autres joueurs ; si aucune ne respecte
	 * la distance minimale, retient la plus éloignée de son plus proche voisin
	 * @return Position
	 */
	private Vertice spawnPosition() {
		Rectangle area = MAP.getBounds().expand(-20);
		return MAP.findFreePosition(RANDOM, PLAYER_RADIUS_X, PLAYER_RADIUS_Y, area, r -> {
			double nearest = Double.POSITIVE_INFINITY;
			for (Player other : PLAYERS.values()) {
				double dx = other.getX() - r.getCenterX(), dy = other.getY() - r.getCenterY();
				nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy));
			}
			return nearest;
		}, SPAWN_MIN_DISTANCE);
	}


	/**
	 * Retourne la dernière image publiée (utilisable depuis n'importe quel fil)
	 * @return Image immuable du plateau
	 */
	public BoardSnapshot getSnapshot() { return snapshot; }

	/**
	 * Dépose une commande, appliquée au début du prochain pas (utilisable depuis n'importe quel fil)
	 * @param c Commande
	 */
	public void enqueue(Command c) {
		if (c != null) COMMANDS.add(c);
	}

	/**
	 * Retourne et vide la liste des événements produits depuis le dernier appel (fil de la simulation)
	 * @return Événements
	 */
	public List<GameEvent> drainEvents() {
		List<GameEvent> events = new ArrayList<>(EVENTS);
		EVENTS.clear();
		return events;
	}

	/**
	 * Retourne la carte
	 * @return Carte
	 */
	public Map getMap() { return MAP; }
	/**
	 * Retourne la zone sûre
	 * @return Zone sûre
	 */
	public SafeZone getSafeZone() { return SAFE_ZONE; }
	/**
	 * Retourne les réglages de la partie
	 * @return Réglages
	 */
	public GameSettings getSettings() { return SETTINGS; }
	/**
	 * Retourne la phase de la partie
	 * @return Phase
	 */
	public Phase getPhase() { return phase; }
	/**
	 * Indique si la partie est en pause
	 * @return true en pause
	 */
	public boolean isPaused() { return paused; }
	/**
	 * Indique si la partie est terminée
	 * @return true si la partie est terminée
	 */
	public boolean isOver() { return phase == Phase.ENDED; }
	/**
	 * Retourne le nombre de pas de simulation effectués
	 * @return Nombre de pas
	 */
	public long getTick() { return tick; }
	/**
	 * Retourne l'identifiant du vainqueur
	 * @return Identifiant, -1 si aucun
	 */
	public int getWinnerId() { return winnerId; }
	/**
	 * Retourne les joueurs, triés par identifiant (fil de la simulation)
	 * @return Collection non modifiable
	 */
	public List<Player> getPlayers() { return List.copyOf(PLAYERS.values()); }
	/**
	 * Retourne un joueur (fil de la simulation)
	 * @param id Identifiant
	 * @return Joueur ou null
	 */
	public Player getPlayer(int id) { return PLAYERS.get(id); }
	/**
	 * Retourne les projectiles actifs (fil de la simulation)
	 * @return Liste non modifiable
	 */
	public List<Bullet> getBullets() { return Collections.unmodifiableList(BULLETS); }
	/**
	 * Retourne le nombre de joueurs vivants
	 * @return Nombre de joueurs vivants
	 */
	public int getAliveCount() {
		int n = 0;
		for (Player p : PLAYERS.values())
			if (p.getIsAlive()) n++;
		return n;
	}
	/**
	 * Retourne le nombre de pas restants avant la fin de l'échauffement
	 * @return Nombre de pas, 0 après l'échauffement
	 */
	public long getWarmupTicksLeft() {
		return phase == Phase.WARMUP ? Math.max(0, SETTINGS.getWarmupTicks() - tick) : 0;
	}


	/**
	 * Fait avancer la simulation d'un pas
	 */
	public void tick() {
		applyCommands();

		if (phase != Phase.ENDED && !paused) {
			tick++;

			if (phase == Phase.WARMUP && tick >= SETTINGS.getWarmupTicks())
				startBattle();

			for (Player p : PLAYERS.values())
				if (p.getIsAlive()) movePlayer(p);

			resolveMelee();
			updateBullets();

			if (phase == Phase.BATTLE) {
				updateZone();
				applyLava();
			}

			resolveEliminations();

			for (Player p : PLAYERS.values())
				p.getWeapon().update();

			checkEnd();
		}

		publishSnapshot();
	}

	/**
	 * Applique toutes les commandes en attente
	 */
	private void applyCommands() {
		Command c;
		while ((c = COMMANDS.poll()) != null) {
			if (c instanceof Command.Control control) applyControl(control.type());
			else if (phase == Phase.ENDED) continue;
			else if (paused) SUSPENDED.addLast(c);
			else if (c instanceof Command.Move move) applyMove(move);
			else if (c instanceof Command.Attack attack) applyAttack(attack);
		}
	}

	/**
	 * Applique une commande de contrôle
	 * @param type Type de contrôle
	 */
	private void applyControl(Command.ControlType type) {
		if (phase == Phase.ENDED) return;

		switch (type) {
		case PAUSE:
			if (!paused) {
				paused = true;
				addEvent(GameEvent.global(GameEvent.Type.PAUSED, tick, 0));
			}
			break;
		case RESUME:
			if (paused) {
				paused = false;
				// Les commandes déposées pendant la pause reprennent leur place dans la file
				Command s;
				while ((s = SUSPENDED.pollFirst()) != null) COMMANDS.offer(s);
				addEvent(GameEvent.global(GameEvent.Type.RESUMED, tick, 0));
			}
			break;
		case STOP:
			stopped = true;
			paused = false;
			SUSPENDED.clear();
			endGame();
			break;
		}
	}

	/**
	 * Applique une commande de déplacement
	 * @param move Commande
	 */
	private void applyMove(Command.Move move) {
		Player p = PLAYERS.get(move.playerId());
		if (p == null || !p.getIsAlive()) return;

		if (move.speed() <= 0) p.stop();
		else if (Direction.isValid(move.direction())) p.setMoveIntent(move.direction(), move.speed(), tick);
	}

	/**
	 * Applique une commande d'attaque
	 * @param attack Commande
	 */
	private void applyAttack(Command.Attack attack) {
		Player p = PLAYERS.get(attack.playerId());
		if (p == null || !p.getIsAlive()) return;

		if (attack.form() == ATTACK_MELEE) {
			if (p.getWeapon().startSwing())
				addEvent(new GameEvent(GameEvent.Type.SWING, tick, p.getID(), -1, 0, p.getX(), p.getY(), DamageCause.MELEE));
		} else if (attack.form() == ATTACK_SHOOT) {
			if (p.getWeapon().tryShoot())
				shoot(p);
		}
	}

	/**
	 * Fait tirer un joueur dans la direction de son regard
	 * @param p Joueur
	 */
	private void shoot(Player p) {
		int d = p.getViewDirection();
		float offset = Math.max(p.getRadiusX(), p.getRadiusY()) + BULLET_RADIUS + 1;
		float bx = p.getX() + Direction.dx(d) * offset, by = p.getY() + Direction.dy(d) * offset;
		Bullet b = new Bullet(nextBulletId++, p.getID(), bx, by, d);

		addEvent(new GameEvent(GameEvent.Type.SHOT, tick, p.getID(), -1, 0, bx, by, DamageCause.BULLET));

		if (!MAP.getBounds().contains(bx, by) || MAP.obstacleIntersecting(b.getRepresentation(), true) != null) {
			addEvent(new GameEvent(GameEvent.Type.BULLET_BLOCKED, tick, p.getID(), -1, 0, bx, by, DamageCause.BULLET));
			return;
		}

		BULLETS.add(b);
	}

	/**
	 * Démarre la phase de combat
	 */
	private void startBattle() {
		phase = Phase.BATTLE;
		BULLETS.clear();
		SAFE_ZONE.start();
		addEvent(GameEvent.global(GameEvent.Type.BATTLE_STARTED, tick, 0));
	}

	/**
	 * Déplace un joueur selon son intention, axe par axe, en glissant le long des obstacles
	 * @param p Joueur
	 */
	private void movePlayer(Player p) {
		float speed = p.currentSpeed(tick);
		if (speed <= 0) {
			p.setMoving(false);
			return;
		}

		int d = p.getMoveDirection();
		float step = speed * TICK_DURATION;
		float ox = p.getX(), oy = p.getY();
		float vx = Direction.dx(d), vy = Direction.dy(d);

		float nx = vx == 0 ? ox : resolveAxis(p, ox + vx * step, oy, true, vx);
		float ny = vy == 0 ? oy : resolveAxis(p, nx, oy + vy * step, false, vy);

		p.setPosition(nx, ny);
		p.setMoving(nx != ox || ny != oy);
		MAP.updatePlayerArea(p);
	}

	/**
	 * Calcule la position atteignable sur un axe en s'arrêtant au contact du premier obstacle.
	 * Les éléments que le joueur chevauche déjà au départ ne le bloquent pas, pour que deux joueurs
	 * superposés puissent se séparer ; seuls les nouveaux contacts l'arrêtent.
	 * @param p Joueur
	 * @param cx Abscisse visée
	 * @param cy Ordonnée visée
	 * @param horizontal true pour l'axe horizontal
	 * @param sign Sens du déplacement sur l'axe
	 * @return Coordonnée atteinte sur l'axe
	 */
	private float resolveAxis(Player p, float cx, float cy, boolean horizontal, float sign) {
		float rx = p.getRadiusX(), ry = p.getRadiusY();
		float origin = horizontal ? p.getX() : p.getY();
		Rectangle start = (horizontal ? p.getRepresentationAt(origin, cy) : p.getRepresentationAt(cx, origin))
			.expand(-OVERLAP_TOLERANCE);

		if (horizontal) cx = clamp(cx, rx, MAP_WIDTH - rx);
		else cy = clamp(cy, ry, MAP_HEIGHT - ry);

		for (int attempt = 0; attempt < 4; attempt++) {
			Rectangle blocker = findBlocker(p, p.getRepresentationAt(cx, cy), start);
			if (blocker == null) return horizontal ? cx : cy;

			float contact;
			if (horizontal) contact = sign > 0 ? blocker.getX1() - rx : blocker.getX2() + rx;
			else contact = sign > 0 ? blocker.getY1() - ry : blocker.getY2() + ry;

			// Contact déjà atteint (arrondis) : le joueur ne bouge pas sur cet axe
			if ((sign > 0 && contact < origin) || (sign < 0 && contact > origin)) return origin;

			if (horizontal) cx = contact;
			else cy = contact;
		}

		return origin;
	}

	/**
	 * Cherche un obstacle ou un joueur vivant chevauchant un rectangle, sans chevaucher la position de départ
	 * @param self Joueur qui se déplace (ignoré)
	 * @param r Rectangle testé
	 * @param start Position de départ du joueur (les éléments qui la chevauchent sont ignorés)
	 * @return Rectangle de l'élément bloquant, ou null
	 */
	private Rectangle findBlocker(Player self, Rectangle r, Rectangle start) {
		for (Zone z : MAP.areasOverlapping(r))
			for (Obstacle o : z.getOBSTACLES()) {
				Rectangle or = o.getRepresentation();
				if (or.intersect(r) && !or.intersect(start)) return or;
			}

		for (Player other : MAP.playersNear(r.expand(Math.max(PLAYER_RADIUS_X, PLAYER_RADIUS_Y)))) {
			if (other == self || !other.getIsAlive()) continue;
			Rectangle or = other.getRepresentation();
			if (or.intersect(r) && !or.intersect(start)) return or;
		}

		return null;
	}

	/**
	 * Applique les coups d'épée en cours (chaque cible est touchée au plus une fois par coup)
	 */
	private void resolveMelee() {
		for (Player p : PLAYERS.values()) {
			if (!p.getIsAlive() || !p.getWeapon().isSwinging()) continue;

			Rectangle reach = Rectangle.centered(p.getX(), p.getY(), MELEE_RANGE, MELEE_RANGE);
			for (Player target : MAP.playersNear(reach.expand(PLAYER_RADIUS_X))) {
				if (target == p || !target.getIsAlive() || !Weapon.isInReach(p, target)) continue;
				if (!p.getWeapon().registerHit(target.getID())) continue;

				int damage = phase == Phase.BATTLE ? target.reduceLifePoints(MELEE_DAMAGE, p.getID(), DamageCause.MELEE, tick) : 0;
				addEvent(new GameEvent(GameEvent.Type.HIT, tick, p.getID(), target.getID(), damage,
					target.getX(), target.getY(), DamageCause.MELEE));
			}
		}
	}

	/**
	 * Fait avancer les projectiles par petits pas et résout leurs collisions
	 */
	private void updateBullets() {
		float distance = BULLET_SPEED * TICK_DURATION;
		int substeps = Math.max(1, (int)Math.ceil(distance / BULLET_SUBSTEP));
		float substep = distance / substeps;

		Iterator<Bullet> it = BULLETS.iterator();
		while (it.hasNext()) {
			Bullet b = it.next();

			for (int s = 0; s < substeps && b.isActive(); s++) {
				b.advance(substep);
				stepBullet(b);
			}

			if (!b.isActive()) it.remove();
		}
	}

	/**
	 * Résout les collisions d'un projectile à sa position actuelle
	 * @param b Projectile
	 */
	private void stepBullet(Bullet b) {
		if (!MAP.getBounds().contains(b.getX(), b.getY())) {
			b.destroy();
			addEvent(new GameEvent(GameEvent.Type.BULLET_BLOCKED, tick, b.getOwnerId(), -1, 0,
				clamp(b.getX(), 0, MAP_WIDTH), clamp(b.getY(), 0, MAP_HEIGHT), DamageCause.BULLET));
			return;
		}

		Rectangle r = b.getRepresentation();
		if (MAP.obstacleIntersecting(r, true) != null) {
			b.destroy();
			addEvent(new GameEvent(GameEvent.Type.BULLET_BLOCKED, tick, b.getOwnerId(), -1, 0, b.getX(), b.getY(), DamageCause.BULLET));
			return;
		}

		// Portée épuisée : la balle s'arrête avant de pouvoir toucher qui que ce soit
		if (b.isOutOfRange()) {
			b.destroy();
			return;
		}

		for (Player target : MAP.playersNear(r.expand(PLAYER_RADIUS_X))) {
			if (target.getID() == b.getOwnerId() || !target.getIsAlive() || !target.getRepresentation().intersect(r)) continue;

			int damage = phase == Phase.BATTLE ? target.reduceLifePoints(BULLET_DAMAGE, b.getOwnerId(), DamageCause.BULLET, tick) : 0;
			addEvent(new GameEvent(GameEvent.Type.HIT, tick, b.getOwnerId(), target.getID(), damage,
				b.getX(), b.getY(), DamageCause.BULLET));
			b.destroy();
			return;
		}
	}

	/**
	 * Fait avancer la zone sûre
	 */
	private void updateZone() {
		switch (SAFE_ZONE.update()) {
		case SHRINK_STARTED:
			addEvent(GameEvent.global(GameEvent.Type.ZONE_SHRINKING, tick, SAFE_ZONE.getWaveIndex() + 1));
			break;
		case SHRINK_ENDED:
		case CLOSED:
			addEvent(GameEvent.global(GameEvent.Type.ZONE_SHRUNK, tick, SAFE_ZONE.getWaveIndex() + 1));
			break;
		default:
		}
	}

	/**
	 * Inflige les dégâts de lave aux joueurs hors de la zone sûre
	 */
	private void applyLava() {
		float damage = SAFE_ZONE.getLavaDamagePerSecond() * TICK_DURATION;

		for (Player p : PLAYERS.values()) {
			if (!p.getIsAlive()) continue;

			boolean outside = !SAFE_ZONE.isSafe(p.getX(), p.getY());
			p.setInLava(outside);
			if (outside) {
				int whole = p.accumulateLava(damage);
				if (whole > 0) p.reduceLifePoints(whole, -1, DamageCause.LAVA, tick);
			}
		}
	}

	/**
	 * Élimine les joueurs qui n'ont plus de points de vie ; les joueurs éliminés au même pas partagent le même classement
	 */
	private void resolveEliminations() {
		List<Player> dying = new ArrayList<>();
		for (Player p : PLAYERS.values())
			if (p.isDying()) dying.add(p);

		if (dying.isEmpty()) return;

		int rank = getAliveCount() - dying.size() + 1;
		for (Player p : dying) {
			int killer = (p.getLastDamageCause() != DamageCause.LAVA) ? p.getLastAttacker() : -1;
			Player k = killer >= 0 ? PLAYERS.get(killer) : null;
			if (k != null) k.addKill();
			else killer = -1;

			p.kill(++eliminations, rank, tick);
			MAP.removePlayer(p);

			KILL_FEED.addLast(new KillFeedEntry(tick, killer, p.getID(), p.getLastDamageCause()));
			while (KILL_FEED.size() > KILL_FEED_SIZE) KILL_FEED.removeFirst();
			addEvent(new GameEvent(GameEvent.Type.ELIMINATION, tick, killer, p.getID(), rank, p.getX(), p.getY(), p.getLastDamageCause()));
		}
	}

	/**
	 * Termine la partie quand il reste au plus un joueur en vie
	 * (une partie à un seul joueur est gagnée dès le début du combat)
	 */
	private void checkEnd() {
		if (phase != Phase.BATTLE) return;

		if (getAliveCount() <= 1)
			endGame();
	}

	/**
	 * Termine la partie : le dernier survivant gagne ; s'il en reste plusieurs (arrêt),
	 * ils reçoivent des rangs distincts (départage par éliminations, puis points de vie, puis identifiant)
	 */
	private void endGame() {
		if (phase == Phase.ENDED) return;

		List<Player> survivors = new ArrayList<>();
		for (Player p : PLAYERS.values())
			if (p.getIsAlive()) survivors.add(p);
		survivors.sort(Comparator.comparingInt(Player::getKills).reversed()
			.thenComparing(Comparator.comparingInt(Player::getLifePoints).reversed())
			.thenComparingInt(Player::getID));

		winnerId = survivors.size() == 1 ? survivors.get(0).getID() : -1;
		for (int i = 0; i < survivors.size(); i++) {
			Player p = survivors.get(i);
			p.setRank(i + 1);
			p.stop();
			p.setMoving(false);
			p.getWeapon().cancelSwing();
		}

		phase = Phase.ENDED;
		BULLETS.clear();
		addEvent(new GameEvent(GameEvent.Type.GAME_OVER, tick, winnerId, -1, survivors.size(), 0, 0, null));
	}

	/**
	 * Ajoute un événement en attente
	 * @param e Événement
	 */
	private void addEvent(GameEvent e) {
		if (EVENTS.size() >= MAX_PENDING_EVENTS) EVENTS.remove(0);
		EVENTS.add(e);
	}

	/**
	 * Publie l'image immuable de l'état courant
	 */
	private void publishSnapshot() {
		List<PlayerState> players = new ArrayList<>(PLAYERS.size());
		for (Player p : PLAYERS.values()) {
			Weapon w = p.getWeapon();
			players.add(new PlayerState(p.getID(), p.getPseudo(), p.isBot(), p.getX(), p.getY(), p.getLifePoints(),
				p.getIsAlive(), p.getID() == winnerId, p.getKills(), p.getRank(), p.getViewDirection(), p.isMoving(),
				w.getSwingProgress(), p.getLastHitTick(), p.isInLava(), w.getMeleeCooldownLeft(), w.getShootCooldownLeft(),
				p.getLastAttacker(), p.getEliminationOrder()));
		}

		List<BulletState> bullets = new ArrayList<>(BULLETS.size());
		for (Bullet b : BULLETS)
			bullets.add(new BulletState(b.getID(), b.getOwnerId(), b.getX(), b.getY(), b.getDx(), b.getDy()));

		int secondsLeft;
		if (phase == Phase.WARMUP) secondsLeft = (int)((getWarmupTicksLeft() + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
		else if (phase == Phase.BATTLE) secondsLeft = SAFE_ZONE.getSecondsLeft();
		else secondsLeft = 0;

		snapshot = new BoardSnapshot(tick, phase, paused, stopped, getAliveCount(), PLAYERS.size(), winnerId,
			SAFE_ZONE.getCurrent(), SAFE_ZONE.getNext(), SAFE_ZONE.getStage(), secondsLeft,
			SAFE_ZONE.getWaveIndex() + 1, SAFE_ZONE.getWaveCount(), SAFE_ZONE.getLavaDamagePerSecond(),
			players, bullets, new ArrayList<>(KILL_FEED));
	}

	/**
	 * Téléporte un joueur (utilisé par les tests pour préparer une situation)
	 * @param id Identifiant du joueur
	 * @param x Nouvelle abscisse
	 * @param y Nouvelle ordonnée
	 */
	void teleport(int id, float x, float y) {
		Player p = PLAYERS.get(id);
		if (p == null) return;
		p.setPosition(x, y);
		if (p.getIsAlive()) MAP.updatePlayerArea(p);
		publishSnapshot();
	}

	/**
	 * Borne une valeur
	 * @param v Valeur
	 * @param min Minimum
	 * @param max Maximum
	 * @return Valeur bornée
	 */
	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}
