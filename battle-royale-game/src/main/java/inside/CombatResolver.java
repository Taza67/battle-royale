package inside;

import static inside.Clamps.clamp;
import static inside.IConfig.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import inside.geometry.Rectangle;

/**
 * Résout le combat sur le plateau : coups d'épée en cours et avance des projectiles
 * avec leurs collisions (obstacles, joueurs, limites de la carte, portée).
 * Utilisé uniquement par le fil de la simulation ({@link Board#tick()}).
 * @author mourtaza
 *
 * @see Board
 * @see Weapon
 * @see Bullet
 */
final class CombatResolver {
	/**
	 * Écart minimal entre le joueur et le départ d'un projectile, en pixels
	 */
	private static final float BULLET_SPAWN_GAP = 1;

	/**
	 * Carte du jeu
	 */
	private final GameMap map;
	/**
	 * Joueurs de la partie
	 */
	private final Collection<Player> players;
	/**
	 * Projectiles actifs (modifiés en place par la résolution)
	 */
	private final List<Bullet> bullets;
	/**
	 * Journal recevant les événements de combat
	 */
	private final EventLog eventLog;
	/**
	 * Identifiant du prochain projectile
	 */
	private int nextBulletId;


	/**
	 * Construit le résolveur de combat
	 * @param map Carte
	 * @param players Joueurs
	 * @param bullets Liste des projectiles actifs
	 * @param eventLog Journal des événements
	 */
	CombatResolver(GameMap map, Collection<Player> players, List<Bullet> bullets, EventLog eventLog) {
		this.map = map;
		this.players = players;
		this.bullets = bullets;
		this.eventLog = eventLog;
	}


	/**
	 * Fait tirer un joueur dans la direction de son regard
	 * @param p Joueur
	 * @param phase Phase de la partie
	 * @param tick Pas de simulation courant
	 */
	void shoot(Player p, Phase phase, long tick) {
		int d = p.getViewDirection();
		float offset = Math.max(p.getRadiusX(), p.getRadiusY()) + BULLET_RADIUS + BULLET_SPAWN_GAP;
		float bx = p.getX() + Direction.dx(d) * offset, by = p.getY() + Direction.dy(d) * offset;
		Bullet b = new Bullet(nextBulletId++, p.getId(), bx, by, d);

		eventLog.add(new GameEvent(GameEvent.Type.SHOT, tick, p.getId(), -1, 0, bx, by, DamageCause.BULLET));

		if (!map.getBounds().contains(bx, by) || map.obstacleIntersecting(b.getRepresentation(), true) != null) {
			eventLog.add(new GameEvent(GameEvent.Type.BULLET_BLOCKED, tick, p.getId(), -1, 0, bx, by, DamageCause.BULLET));
			return;
		}

		bullets.add(b);
	}

	/**
	 * Applique les coups d'épée en cours (chaque cible est touchée au plus une fois par coup)
	 * @param phase Phase de la partie
	 * @param tick Pas de simulation courant
	 */
	void resolveMelee(Phase phase, long tick) {
		for (Player p : players) {
			if (!p.isAlive() || !p.getWeapon().isSwinging()) continue;

			Rectangle reach = Rectangle.centered(p.getX(), p.getY(), MELEE_RANGE, MELEE_RANGE);
			for (Player target : map.playersNear(reach.expand(PLAYER_RADIUS_X))) {
				if (target == p || !target.isAlive() || !Weapon.isInReach(p, target)) continue;
				if (!p.getWeapon().registerHit(target.getId())) continue;

				int damage = phase == Phase.BATTLE ? target.reduceLifePoints(MELEE_DAMAGE, p.getId(), DamageCause.MELEE, tick) : 0;
				eventLog.add(new GameEvent(GameEvent.Type.HIT, tick, p.getId(), target.getId(), damage,
					target.getX(), target.getY(), DamageCause.MELEE));
			}
		}
	}

	/**
	 * Fait avancer les projectiles par petits pas et résout leurs collisions
	 * @param phase Phase de la partie
	 * @param tick Pas de simulation courant
	 */
	void updateBullets(Phase phase, long tick) {
		float distance = BULLET_SPEED * TICK_DURATION;
		int substeps = Math.max(1, (int)Math.ceil(distance / BULLET_SUBSTEP));
		float substep = distance / substeps;

		Iterator<Bullet> it = bullets.iterator();
		while (it.hasNext()) {
			Bullet b = it.next();

			for (int s = 0; s < substeps && b.isActive(); s++) {
				b.advance(substep);
				stepBullet(b, phase, tick);
			}

			if (!b.isActive()) it.remove();
		}
	}

	/**
	 * Résout les collisions d'un projectile à sa position actuelle
	 * @param b Projectile
	 * @param phase Phase de la partie
	 * @param tick Pas de simulation courant
	 */
	private void stepBullet(Bullet b, Phase phase, long tick) {
		if (!map.getBounds().contains(b.getX(), b.getY())) {
			b.destroy();
			eventLog.add(new GameEvent(GameEvent.Type.BULLET_BLOCKED, tick, b.getOwnerId(), -1, 0,
				clamp(b.getX(), 0, MAP_WIDTH), clamp(b.getY(), 0, MAP_HEIGHT), DamageCause.BULLET));
			return;
		}

		Rectangle r = b.getRepresentation();
		if (map.obstacleIntersecting(r, true) != null) {
			b.destroy();
			eventLog.add(new GameEvent(GameEvent.Type.BULLET_BLOCKED, tick, b.getOwnerId(), -1, 0, b.getX(), b.getY(), DamageCause.BULLET));
			return;
		}

		// Portée épuisée : la balle s'arrête avant de pouvoir toucher qui que ce soit
		if (b.isOutOfRange()) {
			b.destroy();
			return;
		}

		for (Player target : map.playersNear(r.expand(PLAYER_RADIUS_X))) {
			if (target.getId() == b.getOwnerId() || !target.isAlive() || !target.getRepresentation().intersect(r)) continue;

			int damage = phase == Phase.BATTLE ? target.reduceLifePoints(BULLET_DAMAGE, b.getOwnerId(), DamageCause.BULLET, tick) : 0;
			eventLog.add(new GameEvent(GameEvent.Type.HIT, tick, b.getOwnerId(), target.getId(), damage,
				b.getX(), b.getY(), DamageCause.BULLET));
			b.destroy();
			return;
		}
	}
}
