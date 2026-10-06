package inside;

import java.util.HashMap;
import java.util.Random;

import inside.geometry.Rectangle;

/**
 * Classe pilotant les joueurs contrôlés par l'ordinateur.
 * <p>
 * Elle s'exécute dans le fil de la simulation, avant chaque pas, et ne fait que déposer
 * des commandes dans la file du plateau, exactement comme le ferait un joueur humain.
 * Chaque robot :
 * <ul>
 * <li>revient vers la zone sûre lorsqu'il en est sorti ou que la prochaine zone l'exclut ;</li>
 * <li>poursuit l'ennemi le plus proche, ou le fuit lorsqu'il a peu de points de vie ;</li>
 * <li>tire lorsqu'un ennemi est à peu près aligné sur l'une des 8 directions et visible ;</li>
 * <li>frappe à l'épée lorsqu'un ennemi est au contact ;</li>
 * <li>contourne les obstacles et se dégage lorsqu'il est bloqué.</li>
 * </ul>
 * @author mourtaza
 */
public class BotController implements IConfig {
	/**
	 * Nombre de pas entre deux décisions d'un robot (≈ cadence de la manette web)
	 */
	private static final int DECISION_TICKS = 4;
	/**
	 * Distance de perception des ennemis
	 */
	private static final float VISION_RANGE = 240;
	/**
	 * Écart angulaire maximal (radians) pour tirer sur un ennemi
	 */
	private static final float SHOOT_TOLERANCE = 0.16f;
	/**
	 * Probabilité qu'un robot tire lorsqu'un ennemi est aligné (temps de réaction)
	 */
	private static final float SHOOT_PROBABILITY = 0.35f;
	/**
	 * Points de vie en dessous desquels un robot fuit
	 */
	private static final int FLEE_LIFE = 30;
	/**
	 * Distance d'anticipation pour l'évitement d'obstacles
	 */
	private static final float LOOK_AHEAD = 14;
	/**
	 * Marge gardée à l'intérieur de la zone sûre
	 */
	private static final float ZONE_MARGIN = 24;

	/**
	 * Mémoire d'un robot
	 */
	private static final class Memory {
		/** Point visé lors de l'errance */
		float wanderX, wanderY;
		/** Pas de fin de l'errance en cours */
		long wanderUntil;
		/** Dernière position connue (détection de blocage) */
		float lastX, lastY;
		/** Nombre de décisions sans progression */
		int stuck;
		/** Direction de dégagement et pas de fin du dégagement */
		int escapeDirection = -1;
		long escapeUntil;
		/** Sens de contournement préféré (1 ou -1) */
		int turn = 1;
	}

	/**
	 * Plateau piloté
	 */
	private final Board BOARD;
	/**
	 * Générateur aléatoire des robots
	 */
	private final Random RANDOM;
	/**
	 * Mémoire de chaque robot
	 */
	private final java.util.Map<Integer, Memory> MEMORIES = new HashMap<>();


	/**
	 * Construit le contrôleur des robots d'un plateau
	 * @param board Plateau
	 * @param seed Graine aléatoire
	 */
	public BotController(Board board, long seed) {
		BOARD = board;
		RANDOM = new Random(seed ^ 0x5DEECE66DL);
	}

	/**
	 * Fait décider les robots dont c'est le tour (à appeler avant chaque pas de simulation)
	 */
	public void update() {
		if (BOARD.isOver() || BOARD.isPaused()) return;

		long now = BOARD.getTick();
		for (Player bot : BOARD.getPlayers()) {
			if (!bot.isBot() || !bot.getIsAlive()) continue;
			if ((now + bot.getID()) % DECISION_TICKS != 0) continue;
			decide(bot, now);
		}
	}

	/**
	 * Décide de l'action d'un robot
	 * @param bot Robot
	 * @param now Pas courant
	 */
	private void decide(Player bot, long now) {
		Memory m = MEMORIES.computeIfAbsent(bot.getID(), id -> {
			Memory mem = new Memory();
			mem.turn = RANDOM.nextBoolean() ? 1 : -1;
			mem.lastX = bot.getX();
			mem.lastY = bot.getY();
			return mem;
		});

		updateStuck(bot, m, now);
		if (m.escapeDirection >= 0 && now < m.escapeUntil) {
			move(bot, m.escapeDirection, 4);
			return;
		}

		SafeZone zone = BOARD.getSafeZone();
		Rectangle safe = safeTarget(zone);
		boolean battle = BOARD.getPhase() == Phase.BATTLE;

		// 1. Retour dans la zone sûre
		if (battle && !safe.expand(-ZONE_MARGIN / 2).contains(bot.getX(), bot.getY())) {
			goTo(bot, m, safe.getCenterX(), safe.getCenterY(), 4);
			return;
		}

		Player enemy = battle ? nearestEnemy(bot) : null;
		if (enemy == null) {
			wander(bot, m, now, safe);
			return;
		}

		float dx = enemy.getX() - bot.getX(), dy = enemy.getY() - bot.getY();
		float distance = (float)Math.sqrt(dx * dx + dy * dy);

		// 2. Fuite quand le robot est affaibli
		if (bot.getLifePoints() < FLEE_LIFE && enemy.getLifePoints() >= bot.getLifePoints() && distance < VISION_RANGE * 0.6f) {
			float fx = bot.getX() - dx, fy = bot.getY() - dy;
			if (!safe.expand(-ZONE_MARGIN).contains(fx, fy)) {
				fx = safe.getCenterX();
				fy = safe.getCenterY();
			}
			goTo(bot, m, fx, fy, 4);
			tryShoot(bot, dx, dy, distance);
			return;
		}

		// 3. Corps à corps
		if (distance < MELEE_RANGE * 0.9f) {
			int d = Direction.fromVector(dx, dy);
			m.stuck = 0;
			move(bot, d, 1);
			if (bot.getWeapon().canSwing()) BOARD.enqueue(new Command.Attack(bot.getID(), ATTACK_MELEE));
			return;
		}

		// 4. Tir si l'ennemi est aligné, sinon poursuite
		if (tryShoot(bot, dx, dy, distance)) return;
		goTo(bot, m, enemy.getX(), enemy.getY(), 3);
	}

	/**
	 * Tire si l'ennemi est aligné sur une direction et visible
	 * @param bot Robot
	 * @param dx Écart horizontal vers l'ennemi
	 * @param dy Écart vertical vers l'ennemi
	 * @param distance Distance de l'ennemi
	 * @return true si un tir a été commandé
	 */
	private boolean tryShoot(Player bot, float dx, float dy, float distance) {
		if (!bot.getWeapon().canShoot() || distance > BULLET_RANGE * 0.9f) return false;
		if (RANDOM.nextFloat() > SHOOT_PROBABILITY) return false;

		int d = Direction.fromVector(dx, dy);
		if (d < 0 || Direction.angleTo(d, dx, dy) > SHOOT_TOLERANCE) return false;
		if (!BOARD.getMap().hasLineOfSight(bot.getX(), bot.getY(), bot.getX() + dx, bot.getY() + dy)) return false;

		move(bot, d, 1);
		BOARD.enqueue(new Command.Attack(bot.getID(), ATTACK_SHOOT));
		return true;
	}

	/**
	 * Erre vers un point aléatoire de la zone sûre
	 * @param bot Robot
	 * @param m Mémoire
	 * @param now Pas courant
	 * @param safe Zone sûre visée
	 */
	private void wander(Player bot, Memory m, long now, Rectangle safe) {
		float dx = m.wanderX - bot.getX(), dy = m.wanderY - bot.getY();
		if (now >= m.wanderUntil || dx * dx + dy * dy < 400 || !safe.contains(m.wanderX, m.wanderY)) {
			Rectangle area = safe.expand(-ZONE_MARGIN);
			if (area.getWidth() <= 0 || area.getHeight() <= 0) area = safe;
			m.wanderX = area.getX1() + RANDOM.nextFloat() * area.getWidth();
			m.wanderY = area.getY1() + RANDOM.nextFloat() * area.getHeight();
			m.wanderUntil = now + TICKS_PER_SECOND * (2 + RANDOM.nextInt(4));
		}
		goTo(bot, m, m.wanderX, m.wanderY, 2);
	}

	/**
	 * Se dirige vers un point en contournant les obstacles
	 * @param bot Robot
	 * @param m Mémoire
	 * @param tx Abscisse visée
	 * @param ty Ordonnée visée
	 * @param speed Niveau de vitesse
	 */
	private void goTo(Player bot, Memory m, float tx, float ty, int speed) {
		int wanted = Direction.fromVector(tx - bot.getX(), ty - bot.getY());
		if (wanted < 0) {
			BOARD.enqueue(new Command.Move(bot.getID(), 0, 0));
			return;
		}

		for (int k = 0; k <= 3; k++) {
			for (int side : new int[] { m.turn, -m.turn }) {
				int d = Direction.rotate(wanted, k * side);
				if (isClear(bot, d)) {
					move(bot, d, speed);
					return;
				}
				if (k == 0) break;
			}
		}
		move(bot, Direction.opposite(wanted), speed);
	}

	/**
	 * Vérifie que le chemin immédiat dans une direction est libre d'obstacles
	 * @param bot Robot
	 * @param d Direction
	 * @return true si la position anticipée est libre et dans la carte
	 */
	private boolean isClear(Player bot, int d) {
		float nx = bot.getX() + Direction.dx(d) * LOOK_AHEAD, ny = bot.getY() + Direction.dy(d) * LOOK_AHEAD;
		Rectangle r = bot.getRepresentationAt(nx, ny);
		return BOARD.getMap().getBounds().contain(r) && BOARD.getMap().isFree(r);
	}

	/**
	 * Détecte un robot bloqué et déclenche un dégagement dans une direction libre au hasard
	 * @param bot Robot
	 * @param m Mémoire
	 * @param now Pas courant
	 */
	private void updateStuck(Player bot, Memory m, long now) {
		float dx = bot.getX() - m.lastX, dy = bot.getY() - m.lastY;
		boolean wantsToMove = bot.getMoveSpeed() > 0;
		m.lastX = bot.getX();
		m.lastY = bot.getY();

		if (!wantsToMove || dx * dx + dy * dy > 0.25f) {
			m.stuck = 0;
			return;
		}

		if (++m.stuck < 8) return;
		m.stuck = 0;
		m.turn = -m.turn;

		int start = RANDOM.nextInt(DIRECTIONS_NUMBER);
		for (int k = 0; k < DIRECTIONS_NUMBER; k++) {
			int d = Direction.rotate(start, k);
			if (isClear(bot, d)) {
				m.escapeDirection = d;
				m.escapeUntil = now + TICKS_PER_SECOND / 2;
				return;
			}
		}
	}

	/**
	 * Retourne la zone vers laquelle se diriger : la prochaine zone pendant une vague, sinon la zone actuelle
	 * @param zone Zone sûre
	 * @return Rectangle visé
	 */
	private static Rectangle safeTarget(SafeZone zone) {
		Rectangle next = zone.getNext();
		return next.getWidth() > 2 * ZONE_MARGIN && next.getHeight() > 2 * ZONE_MARGIN ? next : zone.getCurrent();
	}

	/**
	 * Cherche l'ennemi vivant le plus proche dans le champ de perception
	 * @param bot Robot
	 * @return Ennemi ou null
	 */
	private Player nearestEnemy(Player bot) {
		Player best = null;
		float bestDistance = VISION_RANGE * VISION_RANGE;
		for (Player p : BOARD.getMap().playersNear(Rectangle.centered(bot.getX(), bot.getY(), VISION_RANGE, VISION_RANGE))) {
			if (p == bot || !p.getIsAlive()) continue;
			float dx = p.getX() - bot.getX(), dy = p.getY() - bot.getY();
			float d = dx * dx + dy * dy;
			if (d < bestDistance) {
				bestDistance = d;
				best = p;
			}
		}
		return best;
	}

	/**
	 * Dépose une commande de déplacement
	 * @param bot Robot
	 * @param d Direction
	 * @param speed Niveau de vitesse
	 */
	private void move(Player bot, int d, int speed) {
		if (d >= 0) BOARD.enqueue(new Command.Move(bot.getID(), d, speed));
	}
}
