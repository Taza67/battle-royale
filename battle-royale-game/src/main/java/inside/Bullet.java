package inside;

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
	private final int ID;
	/**
	 * Identifiant du joueur ayant tiré le projectile
	 */
	private final int OWNER_ID;
	/**
	 * Composantes du vecteur unitaire de déplacement
	 */
	private final float DX, DY;
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
		ID = id;
		OWNER_ID = ownerId;
		DX = Direction.dx(direction);
		DY = Direction.dy(direction);
	}


	/**
	 * Retourne l'identifiant du projectile
	 * @return Identifiant
	 */
	public int getID() { return ID; }
	/**
	 * Retourne l'identifiant du tireur
	 * @return Identifiant du tireur
	 */
	public int getOwnerId() { return OWNER_ID; }
	/**
	 * Retourne la composante horizontale de la direction
	 * @return Composante horizontale
	 */
	public float getDx() { return DX; }
	/**
	 * Retourne la composante verticale de la direction
	 * @return Composante verticale
	 */
	public float getDy() { return DY; }
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
		x += DX * distance;
		y += DY * distance;
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
