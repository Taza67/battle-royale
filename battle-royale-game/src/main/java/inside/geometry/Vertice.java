package inside.geometry;

import java.util.Random;

/**
 * Classe représentant un point (ou un vecteur) immuable du plan
 * @author mourtaza
 *
 */
public class Vertice {
	/**
	 * Coordonnées du point
	 */
	private final float x, y;


	/**
	 * Construit un point
	 * @param x Abscisse
	 * @param y Ordonnée
	 */
	public Vertice(float x, float y) {
		this.x = x;
		this.y = y;
	}


	/**
	 * Retourne l'abscisse du point
	 * @return Abscisse
	 */
	public float getX() { return x; }
	/**
	 * Retourne l'ordonnée du point
	 * @return Ordonnée
	 */
	public float getY() { return y; }


	@Override
	public String toString() {
		return "( " + x + ", " + y + " )";
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertice)) return false;
		Vertice v = (Vertice)o;
		return Float.compare(x, v.x) == 0 && Float.compare(y, v.y) == 0;
	}

	@Override
	public int hashCode() {
		return 31 * Float.hashCode(x) + Float.hashCode(y);
	}

	/**
	 * Retourne la somme de ce point et du point donné
	 * @param v Point à ajouter
	 * @return Nouveau point
	 */
	public Vertice add(Vertice v) {
		return new Vertice(x + v.x, y + v.y);
	}

	/**
	 * Retourne la différence entre ce point et le point donné
	 * @param v Point à soustraire
	 * @return Nouveau point
	 */
	public Vertice subtract(Vertice v) {
		return new Vertice(x - v.x, y - v.y);
	}

	/**
	 * Retourne ce vecteur multiplié par un coefficient
	 * @param coef Coefficient
	 * @return Nouveau vecteur
	 */
	public Vertice scale(float coef) {
		return new Vertice(x * coef, y * coef);
	}

	/**
	 * Retourne la norme du vecteur
	 * @return Norme
	 */
	public float length() {
		return (float)Math.sqrt(x * x + y * y);
	}

	/**
	 * Retourne la distance entre ce point et le point donné
	 * @param v Autre point
	 * @return Distance
	 */
	public float distance(Vertice v) {
		return subtract(v).length();
	}

	/**
	 * Retourne le vecteur unitaire de même direction (le vecteur nul reste nul)
	 * @return Vecteur normalisé
	 */
	public Vertice normalize() {
		float l = length();
		return l == 0 ? new Vertice(0, 0) : new Vertice(x / l, y / l);
	}

	/**
	 * Retourne le point après une rotation autour de l'origine
	 * @param angle Angle de rotation, en radians
	 * @return Point tourné
	 */
	public Vertice rotate(float angle) {
		float cos = (float)Math.cos(angle), sin = (float)Math.sin(angle);
		return new Vertice(x * cos - y * sin, x * sin + y * cos);
	}

	/**
	 * Retourne le point translaté dans une direction (les diagonales sont normalisées)
	 * @param direction Direction (0 à 7)
	 * @param distance Distance du déplacement
	 * @return Nouveau point
	 *
	 * @see inside.Direction
	 */
	public Vertice translate(int direction, float distance) {
		return new Vertice(
			x + inside.Direction.dx(direction) * distance,
			y + inside.Direction.dy(direction) * distance);
	}

	/**
	 * Retourne un point aléatoire dans un rectangle
	 * @param random Générateur aléatoire
	 * @param xLow Abscisse minimale
	 * @param xUp Abscisse maximale
	 * @param yLow Ordonnée minimale
	 * @param yUp Ordonnée maximale
	 * @return Point aléatoire
	 */
	public static Vertice random(Random random, float xLow, float xUp, float yLow, float yUp) {
		float x = xLow + (xUp - xLow) * random.nextFloat();
		float y = yLow + (yUp - yLow) * random.nextFloat();

		return new Vertice(x, y);
	}

	/**
	 * Vérifie si le point est à l'intérieur du rectangle formé par les deux coins donnés
	 * @param topLeftCorner Coin supérieur gauche
	 * @param bottomRightCorner Coin inférieur droit
	 * @return true si le point est à l'intérieur (bords inclus)
	 */
	public boolean isInside(Vertice topLeftCorner, Vertice bottomRightCorner) {
		return x >= topLeftCorner.x && x <= bottomRightCorner.x &&
			   y >= topLeftCorner.y && y <= bottomRightCorner.y;
	}
}
