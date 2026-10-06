package inside;

import inside.geometry.Rectangle;
import inside.geometry.Vertice;

/**
 * Classe représentant un élément positionné sur le plateau de jeu
 * @author mourtaza
 *
 * @see IConfig
 */
public abstract class Element implements IConfig {
	/**
	 * Position du centre de l'élément
	 */
	protected float x, y;
	/**
	 * Rayons de la représentation de l'élément (horizontal, vertical)
	 */
	protected final float radiusX, radiusY;


	/**
	 * Construit un élément
	 * @param x Abscisse du centre
	 * @param y Ordonnée du centre
	 * @param radiusX Rayon horizontal
	 * @param radiusY Rayon vertical
	 */
	protected Element(float x, float y, float radiusX, float radiusY) {
		this.x = x;
		this.y = y;
		this.radiusX = radiusX;
		this.radiusY = radiusY;
	}


	/**
	 * Retourne l'abscisse du centre de l'élément
	 * @return Abscisse
	 */
	public float getX() { return x; }
	/**
	 * Retourne l'ordonnée du centre de l'élément
	 * @return Ordonnée
	 */
	public float getY() { return y; }
	/**
	 * Retourne la position du centre de l'élément
	 * @return Position
	 */
	public Vertice getPosition() { return new Vertice(x, y); }
	/**
	 * Retourne le rayon horizontal de l'élément
	 * @return Rayon horizontal
	 */
	public float getRadiusX() { return radiusX; }
	/**
	 * Retourne le rayon vertical de l'élément
	 * @return Rayon vertical
	 */
	public float getRadiusY() { return radiusY; }

	/**
	 * Retourne la représentation (boîte englobante) de l'élément
	 * @return Rectangle centré sur l'élément
	 */
	public Rectangle getRepresentation() {
		return Rectangle.centered(x, y, radiusX, radiusY);
	}

	/**
	 * Retourne la représentation qu'aurait l'élément à une autre position
	 * @param px Abscisse du centre
	 * @param py Ordonnée du centre
	 * @return Rectangle centré sur la position
	 */
	public Rectangle getRepresentationAt(float px, float py) {
		return Rectangle.centered(px, py, radiusX, radiusY);
	}

	/**
	 * Retourne la distance entre les centres de deux éléments
	 * @param e Autre élément
	 * @return Distance
	 */
	public float distance(Element e) {
		float dx = e.x - x, dy = e.y - y;
		return (float)Math.sqrt(dx * dx + dy * dy);
	}
}
