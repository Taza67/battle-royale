package inside;

import static inside.IConfig.*;
/**
 * Classe représentant un projectile sur le plateau de jeu
 * @author mourtaza
 *
 * @see Element
 */
public class Bullet extends Element {
	/**
	 * Identifiant du projectile
	 */
	private final int id;
	/**
	 * Identifiant du joueur ayant tiré le projectile
	 */
	private final int ownerId;
	/**
	 * Composantes du vecteur unitaire de déplacement
	 */
	private final float dx, dy;
	/**
	 * Distance parcourue
	 */
	private float traveled;
	/**
	 * Indique si le projectile est encore actif
	 */
	private boolean active = true;


	/**
	 * Construit un projectile
	 * @param id Identifiant
	 * @param ownerId Identifiant du tireur
	 * @param x Abscisse de départ
	 * @param y Ordonnée de départ
	 * @param direction Direction du tir (0 à 7)
	 */
	public Bullet(int id, int ownerId, float x, float y, int direction) {
		super(x, y, BULLET_RADIUS, BULLET_RADIUS);
		this.id = id;
		this.ownerId = ownerId;
		dx = Direction.dx(direction);
		dy = Direction.dy(direction);
	}


	/**
	 * Retourne l'identifiant du projectile
	 * @return Identifiant
	 */
	public int getID() { return id; }
	/**
	 * Retourne l'identifiant du tireur
	 * @return Identifiant du tireur
	 */
	public int getOwnerId() { return ownerId; }
	/**
	 * Retourne la composante horizontale de la direction
	 * @return Composante horizontale
	 */
	public float getDx() { return dx; }
	/**
	 * Retourne la composante verticale de la direction
	 * @return Composante verticale
	 */
	public float getDy() { return dy; }
	/**
	 * Retourne la distance parcourue
	 * @return Distance
	 */
	public float getTraveled() { return traveled; }
	/**
	 * Indique si le projectile est actif
	 * @return true si le projectile n'a pas été détruit
	 */
	public boolean isActive() { return active; }


	/**
	 * Fait avancer le projectile
	 * @param distance Distance à parcourir
	 */
	void advance(float distance) {
		x += dx * distance;
		y += dy * distance;
		traveled += distance;
	}

	/**
	 * Indique si le projectile a atteint sa portée maximale
	 * @return true si la portée est atteinte
	 */
	boolean isOutOfRange() {
		return traveled >= BULLET_RANGE;
	}

	/**
	 * Détruit le projectile
	 */
	void destroy() {
		active = false;
	}
}
