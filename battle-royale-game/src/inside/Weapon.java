package inside;

import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;

public class Weapon extends Element implements IConfig {
	private Player owner;
	private volatile int direction;
	private volatile boolean isDrawn;

	// Constructeurs
	public Weapon(Map map, Player o) {
		super(map);
		owner = o;
		direction = o.getViewDirection();
		isDrawn = false;
		calculatePosition();
		representation = Polygon.getWeaponRepresentation(this);
		color = COLOR_WEAPON;
		zone = o.getZone();
	}


	// Accesseurs
	public int getDirection() { return direction; }
	public boolean getIsDrawn() { return isDrawn; }

	// Mutateurs
	public synchronized void setIsDrawn(boolean v) {
		isDrawn = v;
		update();
	}


	// Méthodes
	// Calcule la position de l'arme par rapport à la position du propriétaire
	public void calculatePosition() {
		Vertice ownerPosition = owner.getPosition();

		// L'arme sera positionnée en fonction du regard du joueur
		switch (direction) {
		case EAST:
			position = ownerPosition.add(new Vertice(PLAYER_WIDTH, 0f));
			break;
		case NORTH_EAST:
			position = ownerPosition.add(new Vertice(PLAYER_WIDTH, -PLAYER_HEIGHT));
			break;
		case NORTH:
			position = ownerPosition.substract(new Vertice(0f, PLAYER_HEIGHT));
			break;
		case NORTH_WEST:
			position = ownerPosition.substract(new Vertice(PLAYER_WIDTH, PLAYER_HEIGHT));
			break;
		case WEST:
			position = ownerPosition.substract(new Vertice(PLAYER_WIDTH, 0f));
			break;
		case SOUTH_WEST:
			position = ownerPosition.add(new Vertice(-PLAYER_WIDTH, PLAYER_HEIGHT));
			break;
		case SOUTH:
			position = ownerPosition.add(new Vertice(0f, PLAYER_HEIGHT));
			break;
		case SOUTH_EAST:
			position = ownerPosition.add(new Vertice(PLAYER_WIDTH, PLAYER_HEIGHT));
			break;
		}	
	}

	// Mets à jour l'arme
	public synchronized void update() {
		direction = owner.getViewDirection();
		calculatePosition();
		representation = Polygon.getWeaponRepresentation(this);
		zone = owner.getZone();
	}

	// Vérifie si l'arme touche le joueur donné
	public synchronized boolean isTouching(Player p) {
		return ((Rectangle)representation).intersect((Rectangle)p.getRepresentation());
	}
}
