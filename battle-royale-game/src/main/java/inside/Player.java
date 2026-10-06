package inside;

import java.util.ArrayList;
import java.util.List;

import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;
import outside.graphic.Color;
import outside.graphic.GraphicUtilities;

public class Player extends Element implements IConfig {
	private final Board BOARD;
	private final int ID;
	private volatile int finalAttacker;
	private volatile int lifePoints;
	private volatile boolean isAlive;
	private volatile int viewDirection;
	private volatile Weapon weapon;
	private volatile List<Integer> victimsList = new ArrayList<Integer>();

	// Constructeurs
	public Player(Board b, Map m, int id, Vertice p) {
		super(m);
		BOARD = b;
		ID = id;
		textureNumber = id + 4;
		position = new Vertice(p.getX(), p.getY());
		representation = Polygon.getPlayerRepresentation(this);
		color = Color.randomColor(id);
		lifePoints = LIFE_POINTS;
		isAlive = true;
		viewDirection = (position.getX() > MAP_WIDTH / 2f) ? WEST : EAST;
	}


	// Accesseurs
	public synchronized int getID() { return ID; }
	public synchronized int getLifePoints() { return lifePoints; }
	public synchronized boolean getIsAlive() { return isAlive; }
	public synchronized int getViewDirection() { return viewDirection; }
	public synchronized int getFinalAttacker() { return finalAttacker;}
	public synchronized Weapon getWeapon() { return weapon; }
	public synchronized Board getBOARD() { return BOARD; }
	public synchronized List<Integer> getVictims() { return victimsList;}


	// Mutateurs
	public void setPosition(Vertice position) {
		// Vérification de la position
		Vertice newPosition = checkPosition(position);
		this.position = newPosition;
		representation = Polygon.getPlayerRepresentation(this);
	}
	public void setWeapon(Weapon w) {
		weapon = w;
	}

	// Méthodes
	@Override
	public String toString() {
		String desc = "";

		desc += "Joueur N°" + ID + " - ";
		desc += "Position " + position.toString() + " - ";
		desc += "Zone " + zone.toString();

		return desc;
	}
	
	// Ajoute une victime à la liste des victimes du joueur
	public synchronized void addVictim(int id) {
		victimsList.add(id);
	}

	// Retourne la position à laquelle le joueur doit être
	// après un déplacement dans une certaine direction avec une
	// certaine vitesse
	public Vertice getNewPosition(int direction, int speed) {
		switch (direction) {
		case EAST:
			return position.add(new Vertice(speed, 0));
		case NORTH_EAST:
			return position.add(new Vertice(speed, -speed));
		case NORTH:
			return position.substract(new Vertice(0, speed));
		case NORTH_WEST:
			return position.substract(new Vertice(speed, speed));
		case WEST:
			return position.substract(new Vertice(speed, 0));
		case SOUTH_WEST:
			return position.add(new Vertice(-speed, speed));
		case SOUTH:
			return position.add(new Vertice(0, speed));
		case SOUTH_EAST:
			return position.add(new Vertice(speed, speed));
		default:
			return position;
		}
	}

	// Vérifie s'il y a une intersection entre le rectangle
	// et les obstacles et joueurs de la zone
	public boolean checkIntersectionZone(Rectangle r, Zone z) {
		// Intersection avec les obstacles
		for (Obstacle obs : z.getOBSTACLES())
			if (r.intersect((Rectangle)obs.representation))
				return true;

		// Intersection avec les autres joueurs
		for (Integer i : z.getPLAYERS_IDS()) {
			Player p = z.getPlayer(i);
			if (p != null && p != this && r.intersect((Rectangle)p.getRepresentation()))
				return true;
		}

		return false;
	}

	// Déplace le joueur
	public synchronized void move(int direction, int speed) {
		Vertice newPosition;
		
		if (!isAlive) return;

		// Calcul de la nouvelle position si déplacement
		newPosition = getNewPosition(direction, speed);

		// Vérification de la position
		intersectionChecking : if (newPosition != position) {
			Rectangle r = new Rectangle(
				newPosition,
				newPosition.substract(new Vertice(PLAYER_RADIUS_X, PLAYER_RADIUS_Y)),
				PLAYER_WIDTH, PLAYER_HEIGHT
			);

			// Intersection dans la zone du joueur
			if (checkIntersectionZone(r, zone)) {
				newPosition = position;
				break intersectionChecking;
			}

			// Intersection dans les zones voisines
			for (Zone z : zone.getNEIGHBORS()) {
				if (checkIntersectionZone(r, z)) {
					newPosition = position;
					break intersectionChecking;
				}
			}
		}

		// Éventuel changement de zone
		if (newPosition != position) {
			int zoneX = (int)(newPosition.getX() / ONE_ZONE_WIDTH),
				zoneY = (int)(newPosition.getY() / ONE_ZONE_HEIGHT);

			zone.deletePlayer(ID);
			MAP.getArea(zoneY, zoneX).addPlayer(ID, this);
			setZone(MAP.getArea(zoneY, zoneX));
		}

		// Déplacement
		setPosition(newPosition);

		// Vue et arme
		if (newPosition != position) {
			// Direction du regard
			viewDirection = direction;

			// Mise à jour de l'arme
			weapon.update();
		}
	}

	// Vérifie si le joueur est dans la zone de lave ou pas
	public synchronized boolean isInLava() {
		if (!isAlive) return false;
		return !MAP.getMapRepresentation().contain((Rectangle)representation);
	}

	// Réduit les points de vie
	public synchronized void reduceLifePoints(int r) {
		if (!isAlive) return;
		
		lifePoints -= r;
		if (lifePoints <= 0)
			kill();
	}
	
	// Surcharge de la méthode précédente.
	// Celle-ci permet de stocket l'identifiant du dernier 
	// Joueur qui a attaqué
	public synchronized void reduceLifePoints(int r, int lastAttacker) {
		if (!isAlive) return;
		
		lifePoints -= r;
		finalAttacker = lastAttacker;
		if (lifePoints <= 0) {
			if (finalAttacker > -1 && BOARD.getPLAYERS()[lastAttacker] != null)
				BOARD.getPLAYERS()[lastAttacker].addVictim(ID);
			kill();
		}
	}

	// Tue le joueur et l'ajoute aux joueurs décédés du jeu
	public synchronized void kill() {
		isAlive = false;
		color = COLOR_DEAD;
	}

	// Vérifie si le joueur est en train de blesser le joueur donné
	public synchronized boolean isHurting(Player p) {
		if (!isAlive) return false;
		
		return weapon.isTouching(p);
	}

	// Retourne la cible éventuelle que ce joueur est en train de blesser
	public synchronized Player getTarget() {
		if (!isAlive) return null;
		
		if (weapon.getIsDrawn()) {
			// Ennenmis dans la zone du joueur
			for (Integer i : zone.getPLAYERS_IDS()) {
				Player t = zone.getPlayer(i);
				if (this.isHurting(t))
					return t;
			}

			// Ennemis dans les zones voisines
			for (Zone zn : zone.getNEIGHBORS()) {
				for (Integer i : zn.getPLAYERS_IDS()) {
					Player t = zn.getPlayer(i);
					if (this.isHurting(t))
						return t;
				}
			}
		}

		return null;
	}

	// Fait dégainer l'arme
	public synchronized void drawWeapon() {
		if (isAlive)
			weapon.setIsDrawn(true);
	}

	// Fait rengainer l'arme
	public synchronized void holsterWeapon() {
		if (isAlive)
			weapon.setIsDrawn(false);
	}

	// Fait tirer une balle
	public synchronized void shoot() {
		if (isAlive) {
			Bullet bullet = new Bullet(MAP, this);
			BOARD.addBullet(bullet);
		}
	}

	// Vérifie si la position est cohérente aux dimensions de la fenêtre et
	// retourne une position proche qui l'est, le cas échéant
	public synchronized static Vertice checkPosition(Vertice p) {
		float x = p.getX(), y = p.getY();
		float x1 = x - PLAYER_WIDTH / 2f,
			  y1 = y - PLAYER_HEIGHT / 2f,
			  x2 = x + PLAYER_WIDTH / 2f,
			  y2 = y + PLAYER_HEIGHT / 2f;

		// Axe vertical
		if (y1 < 0)
			y = PLAYER_HEIGHT / 2f;
		else if (y2 > WINDOW_HEIGHT)
			y = WINDOW_HEIGHT - PLAYER_HEIGHT / 2f;

		// Axe horizontal
		if (x1 < 0)
			x = PLAYER_WIDTH / 2f;
		else if (x2 > WINDOW_WIDTH)
			x = WINDOW_WIDTH - PLAYER_WIDTH / 2f;

		return new Vertice(x, y);
	}


	// Méthodes graphiques
	// Dessine le joueur et son arme
	@Override
	public synchronized void draw() {
		GraphicUtilities.drawPlayerRectangleTexture((Rectangle)representation, textureNumber);
		weapon.draw();
	}
}
