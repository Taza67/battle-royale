package inside;

import java.util.ArrayList;
import java.util.List;

import inside.geometry.Rectangle;
import inside.geometry.Vertice;
import outside.graphic.GraphicUtilities;

public class Map implements IConfig {
	private Rectangle mapRepresentation;
	public volatile Rectangle lavaRepresentation;
	private final List<Obstacle> OBSTACLES;
	private final List<Land> LANDS;
	private final Zone[][] AREAS;


	// Constructeurs
	public Map(Zone[][] areas) {
		mapRepresentation = new Rectangle(
			new Vertice(MAP_WIDTH / 2f, MAP_HEIGHT / 2f),
			new Vertice(0, 0),
			MAP_WIDTH, MAP_HEIGHT);
		lavaRepresentation = new Rectangle(
			new Vertice(MAP_WIDTH / 2f, MAP_HEIGHT / 2f),
			new Vertice(0, 0),
			MAP_WIDTH, MAP_HEIGHT);
		OBSTACLES = new ArrayList<>();
		LANDS = new ArrayList<>();
		AREAS = areas;
		generateObstacles();
		// generateLands();
	}


	// Accesseurs
	public List<Obstacle> getOBSTACLES() {
		return OBSTACLES;
	}
	public Zone getArea(int i, int j) throws IndexOutOfBoundsException {
		return AREAS[i][j];
	}
	public Rectangle getMapRepresentation() { return mapRepresentation; }


	/**
	 * Génère les obstacles
	 * 
	 * @see IConfig#OBSTACLES_NUMBER
	 * @see Map#AREAS
	 * @see Map#OBSTACLES
	 * @see IConfig#OBSTACLE_RADIUS_X
	 * @see IConfig#OBSTACLE_RADIUS_Y
	 * @see IConfig#ONE_ZONE_HEIGHT
	 * @see IConfig#ONE_ZONE_WIDTH
	 */
	public void generateObstacles() {
		for (int i = 0; i < OBSTACLES_NUMBER; i++) {
			// Création d'un joueur placé aléatoirement
			Obstacle o = new Obstacle(this, Obstacle.TypeObstacle.random(),
				Element.getFreePosition(
					OBSTACLES, new ArrayList<Integer>() ,null, OBSTACLE_RADIUS_X, OBSTACLE_RADIUS_Y
				)
			);

			// Ajout de l'obstacle à la zone adéquate
			int zoneX = (int)(o.getPosition().getX() / ONE_ZONE_WIDTH),
				zoneY = (int)(o.getPosition().getY() / ONE_ZONE_HEIGHT);
			AREAS[zoneY][zoneX].addObstacle(o);
			o.setZone(AREAS[zoneY][zoneX]);

			// Ajout de l'obstacle à la liste
			OBSTACLES.add(o);
		}
	}
	
	/**
	 * Génère les sols
	 * 
	 * @see IConfig#LANDS_NUMBER
	 * @see TypeLand
	 * @see Map#LANDS
	 */
	public void generateLands() {
		for (int i = 0; i < LANDS_NUMBER; i++) {
			// Création du sol
			Land l = new Land(
				this, Land.TypeLand.BUSH, Vertice.random(50, MAP_WIDTH - 100, 50, MAP_HEIGHT - 100)
			);
			
			// Ajout du sol à la liste des sols
			LANDS.add(l);
		}
	}
	
	

	// Réduit le rectangle représentant la map ou le champ de bataille
//	public void reduceMapRepresentation(float coef) {
//		mapRepresentation.scale(1.0f - coef);
//	}
	public synchronized void reduceMapRepresentation(float red) {
		mapRepresentation.scale(red);
	}


	// Méthodes graphiques
	// Dessine le champ de bataille
	public synchronized void draw() {
		// Dessin de la lave
		GraphicUtilities.drawPolygon(lavaRepresentation, COLOR_LAVA);

		// Dessin du sol
		GraphicUtilities.drawPolygon(mapRepresentation, COLOR_WHITE);
		GraphicUtilities.drawRectangleTexture(mapRepresentation, TEXTURE_BATTLEFIELD);

		// Dessin des obstacles
		for (Obstacle o : OBSTACLES)
			o.draw();

		// Dessin des sols
//		for (Land s : LANDS) {
//			s.draw();
//			System.out.println(s.getRepresentation());
//		}
	}
}
