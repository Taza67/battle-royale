package inside.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import inside.Bullet;
import inside.IConfig;
import inside.Land;
import inside.Obstacle;
import inside.Player;
import inside.Weapon;

public class Polygon implements IConfig {
	protected final List<Vertice> VERTICES;
	protected int verticesNumber;
	protected Vertice center;

	// Constructeurs
	public Polygon() {
		VERTICES = new ArrayList<>();
		verticesNumber = 0;
	}
	public Polygon(Vertice c) {
		this();
		center = c;
	}


	// Accesseurs
	public Vertice getVertice(int i) throws IndexOutOfBoundsException {
		return VERTICES.get(i);
	}
	public int getVerticesNumber() { return verticesNumber; }

	// Mutateurs
	public void setVertice(int i, Vertice v) throws IndexOutOfBoundsException {
		VERTICES.get(i).setX(v.getX());
		VERTICES.get(i).setY(v.getY());
	}


	// Méthodes
	public String toString() {
		String rep = "";
		for (Vertice oneVertice : VERTICES)
			rep += oneVertice.toString() + "\n";

		return rep;
	}

	// Ajoute le point aux extrémités du polygon
	public void addVertice(Vertice v) {
		VERTICES.add(new Vertice(v.getX(), v.getY()));
		verticesNumber++;
	}

	// Effectue une rotation du polygone
	public void rotate(float angle) {
		for (int i = 0; i < verticesNumber; i++) {
            // Translation du point pour que le centre soit en (0,0)
            Vertice vertice = getVertice(i).substract(center);

            // Rotation
            vertice.rotate(angle);

            // Translation du point à sa position initiale
            setVertice(i, vertice.add(center));
		}
	}

	// Retourne un polygone représentatif du joueur
	public static Polygon getPlayerRepresentation(Player p) {
		return new Rectangle(
			p.getPosition(),
			p.getPosition().substract(new Vertice(PLAYER_RADIUS_X, PLAYER_RADIUS_Y)),
			PLAYER_WIDTH, PLAYER_HEIGHT
		);
	}

	// Retourne un polygone représentatif de l'obstacle
	public static Polygon getObstacleRepresentation(Obstacle o) {
		float radiusX, radiusY;
		
		switch (o.getTYPE()) {
		case EAU:
			radiusX = (WATER_WIDTH * generateRandomNumber(LAKE_MIN_WIDTH, LAKE_MAX_WIDTH)) / 2f;
			radiusY = (WATER_HEIGHT * generateRandomNumber(LAKE_MIN_HEIGHT, LAKE_MAX_HEIGHT)) / 2f;
			break;
		case FORET:
			radiusX = (TREE_WIDTH * generateRandomNumber(FOREST_MIN_WIDTH, FOREST_MAX_WIDTH)) / 2f;
			radiusY = (TREE_HEIGHT * generateRandomNumber(FOREST_MIN_HEIGHT, FOREST_MAX_HEIGHT)) / 2f;
			break;
		case ROCHER:
		default:
			radiusX = (STONE_WIDTH * generateRandomNumber(MOUNTAIN_MIN_WIDTH, MOUNTAIN_MAX_WIDTH)) / 2f;
			radiusY = (STONE_HEIGHT * generateRandomNumber(MOUNTAIN_MIN_HEIGHT, MOUNTAIN_MAX_HEIGHT)) / 2f;
		}
		
		return new Rectangle(
			o.getPosition(),
			o.getPosition().substract(new Vertice(radiusX, radiusY)),
			radiusX * 2, radiusY * 2
		);
	}
	
	// Retourne un polygone représentatif du sol
	public static Polygon getLandRepresentation(Land l) {
		Random rand = new Random();
		float radiusX, radiusY;
		
		switch (l.getTYPE()) {
		case BUSH:
			System.err.println("- BUSH");
			radiusX = (BUSH_WIDTH * 1) / 2f;
			radiusY = (BUSH_HEIGHT * 1) / 2f;
			break;
		case FLOWERS:
			System.err.println("- FLOWERS");
			radiusX = (FLOWER_WIDTH * 
				(rand.nextInt() * (FLOWERS_MAX_WIDTH - FLOWERS_MIN_WIDTH) + FLOWERS_MIN_WIDTH)) / 2f;
			radiusY = (FLOWER_HEIGHT *
				(rand.nextInt() * (FLOWERS_MAX_HEIGHT - FLOWERS_MIN_HEIGHT) + FLOWERS_MIN_HEIGHT)) / 2f;
			break;
		case TRAIL:
			System.err.println("- TRAIL");
			radiusX = (TRAIL_WIDTH * 
				(rand.nextInt() * (ROAD_MAX_WIDTH - ROAD_MIN_WIDTH) + ROAD_MIN_WIDTH)) / 2f;
			radiusY = (TRAIL_HEIGHT *
				(rand.nextInt() * (ROAD_MAX_HEIGHT - ROAD_MIN_HEIGHT) + ROAD_MIN_HEIGHT)) / 2f;
			
			// Orientation de la route
			if (rand.nextInt(10) % 2 == 0) {
				float tmp = radiusX;
				radiusX = radiusY;
				radiusY = tmp;
			}
		default:
			System.err.println("- DEFAULT");
			radiusX = (TRAIL_WIDTH * 
				(rand.nextInt() * (ROAD_MAX_WIDTH - ROAD_MIN_WIDTH) + ROAD_MIN_WIDTH)) / 2f;
			radiusY = (TRAIL_HEIGHT *
				(rand.nextInt() * (ROAD_MAX_HEIGHT - ROAD_MIN_HEIGHT) + ROAD_MIN_HEIGHT)) / 2f;
			
			// Orientation de la route
			if (rand.nextInt(10) % 2 == 0) {
				float tmp = radiusX;
				radiusX = radiusY;
				radiusY = tmp;
			}
		}
		
		System.out.println(radiusX + " " + radiusY);
		
		radiusX *= 5;
		radiusY *= 5;
		
		return new Rectangle(
			l.getPosition(),
			l.getPosition().substract(new Vertice(radiusX, radiusY)),
			radiusX * 2, radiusY * 2
		);
	}

	// Retourne un polygone représentatif d'une arme
	public static Polygon getWeaponRepresentation(Weapon w) {
		int direction = w.getDirection();
		float height, width, angle = 0;
		float tmp;

		if (!w.getIsDrawn())
			height = width = WEAPON_HEIGHT;
		else {
			height = WEAPON_HEIGHT;
			width = WEAPON_WIDTH;
		}

		// La forme de l'arme dépendra de sa direction
		switch (direction) {
		case NORTH:
		case SOUTH:
			angle = 0;
			tmp = width;
			width = height;
			height = tmp;
			break;		
		case EAST:
		case WEST:
			angle = 0;
			break;
		case NORTH_EAST:
			angle = DEGREES_60;
			tmp = width;
			width = height;
			height = tmp;
			break;
		case NORTH_WEST:
			angle = DEGREES_30;
			break;
		case SOUTH_WEST:
			angle = DEGREES_60;
			tmp = width;
			width = height;
			height = tmp;
			break;
		case SOUTH_EAST:
			angle = DEGREES_30;
		}
		
		Rectangle r = new Rectangle(
			w.getPosition(),
			w.getPosition().substract(new Vertice(width / 2f, height / 2f)),
			width, height
		);
		
		r.rotate(angle);
		
		return r;
	}

	// Retourne un polygone représentant la balle
	public static Polygon getBulletRepresentation(Bullet b) {
		return new Rectangle(
				b.getPosition(),
				b.getPosition().substract(new Vertice(BULLET_RADIUS_X, BULLET_RADIUS_Y)),
				BULLET_WIDTH, BULLET_WIDTH
			);
	}
	
	
    public static int generateRandomNumber(int min, int max) {
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }
}
