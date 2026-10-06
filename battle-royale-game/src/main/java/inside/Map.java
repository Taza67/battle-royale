package inside;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import inside.geometry.Rectangle;
import inside.geometry.Vertice;

/**
 * Classe représentant la carte du jeu : ses limites, ses obstacles et la grille de zones
 * qui accélère la recherche des éléments proches
 * @author mourtaza
 *
 * @see Zone
 * @see Obstacle
 */
public class Map implements IConfig {
	/**
	 * Marge laissée entre les obstacles générés et le bord de la carte
	 */
	private static final float BORDER_MARGIN = 30;
	/**
	 * Nombre maximal d'essais pour placer un élément aléatoirement
	 */
	private static final int MAX_ATTEMPTS = 400;

	/**
	 * Rectangle représentant les limites de la carte
	 */
	private final Rectangle BOUNDS;
	/**
	 * Obstacles de la carte
	 */
	private final List<Obstacle> OBSTACLES;
	/**
	 * Grille de zones, indexée par [ligne][colonne]
	 */
	private final Zone[][] AREAS;


	/**
	 * Construit une carte avec les obstacles donnés
	 * @param obstacles Obstacles de la carte
	 */
	public Map(List<Obstacle> obstacles) {
		BOUNDS = new Rectangle(0, 0, MAP_WIDTH, MAP_HEIGHT);
		OBSTACLES = new ArrayList<>(obstacles);
		AREAS = new Zone[AREAS_HEIGHT][AREAS_WIDTH];

		for (int i = 0; i < AREAS_HEIGHT; i++)
			for (int j = 0; j < AREAS_WIDTH; j++)
				AREAS[i][j] = new Zone(j, i);

		for (Obstacle o : OBSTACLES)
			for (Zone z : areasOverlapping(o.getRepresentation()))
				z.addObstacle(o);
	}

	/**
	 * Génère une carte aléatoire
	 * @param random Générateur aléatoire
	 * @param obstaclesNumber Nombre d'obstacles souhaité
	 * @return Nouvelle carte
	 */
	public static Map generate(Random random, int obstaclesNumber) {
		List<Obstacle> obstacles = new ArrayList<>();

		for (int i = 0; i < obstaclesNumber; i++) {
			for (int attempt = 0; attempt < MAX_ATTEMPTS / 4; attempt++) {
				Obstacle.TypeObstacle type = randomType(random);
				Obstacle candidate = Obstacle.random(type, random, 0, 0);
				float rx = candidate.getRadiusX(), ry = candidate.getRadiusY();
				Vertice p = Vertice.random(random,
					BORDER_MARGIN + rx, MAP_WIDTH - BORDER_MARGIN - rx,
					BORDER_MARGIN + ry, MAP_HEIGHT - BORDER_MARGIN - ry);
				Obstacle o = new Obstacle(type, p.getX(), p.getY(), rx, ry);
				Rectangle inflated = o.getRepresentation().expand(OBSTACLES_GAP);

				boolean free = true;
				for (Obstacle other : obstacles)
					if (other.getRepresentation().intersect(inflated)) {
						free = false;
						break;
					}

				if (free) {
					obstacles.add(o);
					break;
				}
			}
		}

		return new Map(obstacles);
	}

	/**
	 * Tire un type d'obstacle (forêts plus fréquentes, lacs plus rares)
	 * @param random Générateur aléatoire
	 * @return Type d'obstacle
	 */
	private static Obstacle.TypeObstacle randomType(Random random) {
		float r = random.nextFloat();
		if (r < 0.4f) return Obstacle.TypeObstacle.FORET;
		if (r < 0.75f) return Obstacle.TypeObstacle.ROCHER;
		return Obstacle.TypeObstacle.EAU;
	}


	/**
	 * Retourne les limites de la carte
	 * @return Rectangle de la carte
	 */
	public Rectangle getBounds() { return BOUNDS; }
	/**
	 * Retourne les obstacles de la carte
	 * @return Liste non modifiable
	 */
	public List<Obstacle> getOBSTACLES() { return Collections.unmodifiableList(OBSTACLES); }

	/**
	 * Retourne une zone de la grille ; les indices hors limites sont ramenés sur le bord
	 * @param i Ligne
	 * @param j Colonne
	 * @return Zone
	 */
	public Zone getArea(int i, int j) {
		return AREAS[clamp(i, AREAS_HEIGHT)][clamp(j, AREAS_WIDTH)];
	}

	/**
	 * Retourne la zone contenant un point (les points hors de la carte donnent la zone du bord la plus proche)
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @return Zone
	 */
	public Zone getAreaAt(float x, float y) {
		return getArea(rowOf(y), columnOf(x));
	}

	/**
	 * Retourne la colonne de la grille correspondant à une abscisse
	 * @param x Abscisse
	 * @return Colonne, entre 0 et AREAS_WIDTH - 1
	 */
	public static int columnOf(float x) {
		return clamp((int)Math.floor(x / ONE_ZONE_WIDTH), AREAS_WIDTH);
	}

	/**
	 * Retourne la ligne de la grille correspondant à une ordonnée
	 * @param y Ordonnée
	 * @return Ligne, entre 0 et AREAS_HEIGHT - 1
	 */
	public static int rowOf(float y) {
		return clamp((int)Math.floor(y / ONE_ZONE_HEIGHT), AREAS_HEIGHT);
	}

	/**
	 * Ramène un indice dans [0, size - 1]
	 * @param v Indice
	 * @param size Taille
	 * @return Indice borné
	 */
	private static int clamp(int v, int size) {
		return Math.max(0, Math.min(size - 1, v));
	}

	/**
	 * Retourne les zones chevauchant un rectangle
	 * @param r Rectangle
	 * @return Liste des zones
	 */
	public List<Zone> areasOverlapping(Rectangle r) {
		List<Zone> zones = new ArrayList<>();
		int c1 = columnOf(r.getX1()), c2 = columnOf(r.getX2()),
			r1 = rowOf(r.getY1()), r2 = rowOf(r.getY2());

		for (int i = r1; i <= r2; i++)
			for (int j = c1; j <= c2; j++)
				zones.add(AREAS[i][j]);

		return zones;
	}

	/**
	 * Retourne le premier obstacle chevauchant un rectangle
	 * @param r Rectangle
	 * @param bulletBlockersOnly true pour ignorer les obstacles traversés par les balles
	 * @return Obstacle trouvé ou null
	 */
	public Obstacle obstacleIntersecting(Rectangle r, boolean bulletBlockersOnly) {
		for (Zone z : areasOverlapping(r))
			for (Obstacle o : z.getOBSTACLES())
				if ((!bulletBlockersOnly || o.getTYPE().blocksBullets()) && o.getRepresentation().intersect(r))
					return o;

		return null;
	}

	/**
	 * Vérifie si un rectangle est dans la carte et ne touche aucun obstacle
	 * @param r Rectangle
	 * @return true si l'emplacement est libre
	 */
	public boolean isFree(Rectangle r) {
		return BOUNDS.contain(r) && obstacleIntersecting(r, false) == null;
	}

	/**
	 * Vérifie qu'aucun obstacle arrêtant les balles ne se trouve entre deux points
	 * @param x1 Abscisse de départ
	 * @param y1 Ordonnée de départ
	 * @param x2 Abscisse d'arrivée
	 * @param y2 Ordonnée d'arrivée
	 * @return true si la ligne de tir est dégagée
	 */
	public boolean hasLineOfSight(float x1, float y1, float x2, float y2) {
		float dx = x2 - x1, dy = y2 - y1;
		float length = (float)Math.sqrt(dx * dx + dy * dy);
		int steps = Math.max(1, (int)Math.ceil(length / 6f));

		for (int s = 0; s <= steps; s++) {
			float t = s / (float)steps;
			Rectangle probe = Rectangle.centered(x1 + dx * t, y1 + dy * t, 1, 1);
			if (obstacleIntersecting(probe, true) != null)
				return false;
		}

		return true;
	}

	/**
	 * Retourne les joueurs vivants référencés dans les zones chevauchant un rectangle
	 * @param r Rectangle de recherche
	 * @return Liste des joueurs
	 */
	public List<Player> playersNear(Rectangle r) {
		List<Player> players = new ArrayList<>();
		for (Zone z : areasOverlapping(r))
			players.addAll(z.getPLAYERS());
		return players;
	}

	/**
	 * Place un joueur dans la zone correspondant à sa position
	 * @param p Joueur
	 */
	void updatePlayerArea(Player p) {
		Zone target = getAreaAt(p.getX(), p.getY());
		Zone current = p.getZone();

		if (current == target) return;
		if (current != null) current.deletePlayer(p);
		target.addPlayer(p);
		p.setZone(target);
	}

	/**
	 * Retire un joueur de la grille
	 * @param p Joueur
	 */
	void removePlayer(Player p) {
		if (p.getZone() != null) p.getZone().deletePlayer(p);
		p.setZone(null);
	}

	/**
	 * Cherche une position libre pour un élément, en préférant les emplacements les mieux notés
	 * @param random Générateur aléatoire
	 * @param radiusX Rayon horizontal de l'élément
	 * @param radiusY Rayon vertical de l'élément
	 * @param area Zone de recherche
	 * @param score Note d'un emplacement libre (par exemple la distance au plus proche voisin)
	 * @param target Note suffisante : le premier emplacement qui l'atteint est retenu
	 * @return Premier emplacement libre atteignant la note visée, sinon l'emplacement libre le mieux noté
	 * @throws IllegalStateException si la zone de recherche n'offre aucun emplacement libre
	 */
	public Vertice findFreePosition(Random random, float radiusX, float radiusY, Rectangle area,
		ToDoubleFunction<Rectangle> score, double target) {
		Rectangle searchArea = area.expand(-Math.max(radiusX, radiusY));
		Vertice best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			Vertice p = Vertice.random(random, searchArea.getX1(), searchArea.getX2(), searchArea.getY1(), searchArea.getY2());
			Rectangle r = Rectangle.centered(p.getX(), p.getY(), radiusX, radiusY);
			if (!isFree(r)) continue;

			double s = score.applyAsDouble(r);
			if (s >= target) return p;
			if (s > bestScore) {
				best = p;
				bestScore = s;
			}
		}

		if (best != null) return best;

		// Recherche exhaustive sur une grille fine, bornée à la zone demandée
		for (float y = searchArea.getY1(); y <= searchArea.getY2(); y += 4)
			for (float x = searchArea.getX1(); x <= searchArea.getX2(); x += 4) {
				Rectangle r = Rectangle.centered(x, y, radiusX, radiusY);
				if (!isFree(r)) continue;

				double s = score.applyAsDouble(r);
				if (s >= target) return new Vertice(x, y);
				if (s > bestScore) {
					best = new Vertice(x, y);
					bestScore = s;
				}
			}

		if (best != null) return best;

		// Dernier recours : le centre de la zone demandée, seulement s'il est libre
		Vertice center = searchArea.getCenter();
		if (isFree(Rectangle.centered(center.getX(), center.getY(), radiusX, radiusY))) return center;
		throw new IllegalStateException("Aucun emplacement libre dans la zone de recherche");
	}
}
