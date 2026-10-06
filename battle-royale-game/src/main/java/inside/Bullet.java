package inside;

import inside.Obstacle.TypeObstacle;
import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;

/**
 * Classe représentant un projectile sur le plateau de jeu
 * @author mourtaza
 *
 * @see Element
 * @see IConfig
 */
public class Bullet extends Element implements IConfig {
	/**
	 * Variable contenant la direction dans laquelle se déplace le projectile
	 */
	private int direction;
	/**
	 * Variable contenant le nombre de pas réalisé (un projectile se déplace un certain nombre de fois)
	 */
	private volatile int stepsDone;
	/**
	 * Variable contenant un objet représentant le joueur ayant lancé le projectile
	 * @see Player
	 */
	private Player OWNER;

	
	/**
	 * Construit une instance de projectile
	 * @param map Carte dans laquelle est le projectile
	 * @param o Joueur ayant projeté le projectile
	 * 
	 * @see Element
	 * @see Bullet#direction
	 * @see Bullet#stepsDone
	 * @see Bullet#OWNER
	 * @see Element#position
	 * @see Element#representation
	 * @see Polygon#getBulletRepresentation(Bullet)
	 * @see Element#color
	 * @see Element#zone
	 */
	public Bullet(Map map, Player o) {
		super(map);
		direction = o.getViewDirection();
		stepsDone = 0;
		OWNER = o;
		position = o.getWeapon().getPosition();
		representation = Polygon.getBulletRepresentation(this);
		color = COLOR_BULLET;
		zone = o.getZone();
	}


	/**
	 * Fais avancer d'un pas la balle
	 * @return true si la balle a avancé, false sinon
	 * 
	 * @see Bullet#stepsDone
	 * @see Bullet#direction
	 * @see Element#position
	 * @see Element#representation
	 * @see Element#zone
	 * @see Element#MAP
	 * @see Element#setZone(Zone)
	 * @see IConfig#BULLET_STEP_DISTANCE
	 * @see IConfig#BULLET_STEPS_TO_MAKE
	 * @see IConfig#ONE_ZONE_HEIGHT
	 * @see IConfig#ONE_ZONE_WIDTH
	 */
	public synchronized boolean stepAheadBullet() {
		if (stepsDone >= BULLET_STEPS_TO_MAKE)
			return false;
		stepsDone++;

		Vertice newPosition = position.translate(direction, BULLET_STEP_DISTANCE);
		position = newPosition;
		representation = Polygon.getBulletRepresentation(this);
		int zoneX = (int)(newPosition.getX() / ONE_ZONE_WIDTH),
			zoneY = (int)(newPosition.getY() / ONE_ZONE_HEIGHT);

		zone.deleteBullet(this);
		MAP.getArea(zoneY, zoneX).addBullet(this);
		setZone(MAP.getArea(zoneY, zoneX));

		return true;
	}

	/**
	 * Détruis la balle
	 * 
	 * @see Element#zone
	 * @see Bullet#OWNER
	 */
	public synchronized void destroy() {
		zone.deleteBullet(this);
		OWNER.getBOARD().destroyBullet(this);
	}

	/**
	 * Vérifie le contact avec d'autres joueurs
	 * @param isSandBox Indique si le jeu est en mode bac à sable
	 * @return true si un contact a eu lieu, false sinon
	 * 
	 * @see Element#zone
	 * @see Element#representation
	 * @see Rectangle
	 * @see IConfig#BULLET_DAMAGE
	 * @see TypeObstacle
	 */
	public synchronized boolean makeContact(boolean isSandBox) {
		// Zone de la balle
		// // Joueurs de la zone
		for (Integer id : zone.getPLAYERS_IDS()) {
			Player p = zone.getPlayer(id);
			if (((Rectangle)representation).intersect((Rectangle)p.getRepresentation())) {
				if (!isSandBox) 
					p.reduceLifePoints(BULLET_DAMAGE,OWNER.getID());
				return true;
			}
		}
		// // Obstacles de la zone
		for (Obstacle o : zone.getOBSTACLES()) {
			if (o.getTYPE() != TypeObstacle.EAU &&
				((Rectangle)representation).intersect((Rectangle)o.getRepresentation()))
				return true;
		}

		// Zones voisines
		for (Zone zn : zone.getNEIGHBORS()) {
			// // Joueurs dans la zone
			for (Integer id : zn.getPLAYERS_IDS()) {
				Player p = zn.getPlayer(id);
				if (((Rectangle)representation).intersect((Rectangle)p.getRepresentation())) {
					if (!isSandBox) 
						p.reduceLifePoints(BULLET_DAMAGE,OWNER.getID());
					return true;
				}
			}
			// // Obstacles dans la zone
			for (Obstacle o : zn.getOBSTACLES()) {
				if (o.getTYPE() != TypeObstacle.EAU &&
					((Rectangle)representation).intersect((Rectangle)o.getRepresentation()))
					return true;
			}
		}

		return false;
	}
	/**
	 * Vérifie le contact avec d'autres joueurs, mais le jeu n'est pas en mode bac à sable par défaut
	 * @return true si un contact a eu lieu, false sinon
	 * 
	 * @see Bullet#makeContact(boolean)
	 */
	public synchronized boolean makeContact() {
		return makeContact(false);
	}
}
