package inside;

import static inside.Clamps.clamp;
import static inside.IConfig.*;

import inside.geometry.Rectangle;

/**
 * Résout les déplacements des joueurs sur le plateau : chaque joueur avance axe par axe
 * en s'arrêtant au contact du premier obstacle, ce qui le fait glisser le long des murs.
 * Utilisé uniquement par le fil de la simulation ({@link Board#tick()}).
 * @author mourtaza
 *
 * @see Board
 */
final class MovementResolver {
	/**
	 * Profondeur de chevauchement en dessous de laquelle deux éléments sont considérés en contact (arrondis)
	 */
	private static final float OVERLAP_TOLERANCE = 0.01f;
	/**
	 * Nombre maximal d'itérations de résolution d'un contact par axe (glissement le long des obstacles)
	 */
	private static final int MAX_SLIDE_STEPS = 4;

	/**
	 * Carte du jeu
	 */
	private final GameMap map;


	/**
	 * Construit le résolveur de mouvement d'une carte
	 * @param map Carte
	 */
	MovementResolver(GameMap map) {
		this.map = map;
	}


	/**
	 * Déplace un joueur selon son intention, axe par axe, en glissant le long des obstacles
	 * @param p Joueur
	 * @param tick Pas de simulation courant
	 */
	void move(Player p, long tick) {
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
		map.updatePlayerArea(p);
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

		for (int attempt = 0; attempt < MAX_SLIDE_STEPS; attempt++) {
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
		for (GridCell cell : map.areasOverlapping(r))
			for (Obstacle o : cell.getObstacles()) {
				Rectangle or = o.getRepresentation();
				if (or.intersect(r) && !or.intersect(start)) return or;
			}

		for (Player other : map.playersNear(r.expand(Math.max(PLAYER_RADIUS_X, PLAYER_RADIUS_Y)))) {
			if (other == self || !other.isAlive()) continue;
			Rectangle or = other.getRepresentation();
			if (or.intersect(r) && !or.intersect(start)) return or;
		}

		return null;
	}
}
