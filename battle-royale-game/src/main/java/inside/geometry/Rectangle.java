package inside.geometry;

/**
 * Classe représentant un rectangle immuable aligné sur les axes
 * @author mourtaza
 *
 */
public final class Rectangle {
	/**
	 * Coordonnées des coins supérieur gauche (x1, y1) et inférieur droit (x2, y2)
	 */
	private final float x1, y1, x2, y2;


	/**
	 * Construit un rectangle à partir de deux coins (l'ordre des coordonnées est corrigé si nécessaire)
	 * @param x1 Abscisse d'un coin
	 * @param y1 Ordonnée d'un coin
	 * @param x2 Abscisse du coin opposé
	 * @param y2 Ordonnée du coin opposé
	 */
	public Rectangle(float x1, float y1, float x2, float y2) {
		this.x1 = Math.min(x1, x2);
		this.y1 = Math.min(y1, y2);
		this.x2 = Math.max(x1, x2);
		this.y2 = Math.max(y1, y2);
	}

	/**
	 * Construit un rectangle à partir de son coin supérieur gauche et de ses dimensions
	 * @param topLeftCorner Coin supérieur gauche
	 * @param width Longueur
	 * @param height Largeur
	 */
	public Rectangle(Vertice topLeftCorner, float width, float height) {
		this(topLeftCorner.getX(), topLeftCorner.getY(),
			topLeftCorner.getX() + width, topLeftCorner.getY() + height);
	}

	/**
	 * Construit un rectangle centré sur un point
	 * @param cx Abscisse du centre
	 * @param cy Ordonnée du centre
	 * @param radiusX Demi-longueur
	 * @param radiusY Demi-largeur
	 * @return Rectangle centré
	 */
	public static Rectangle centered(float cx, float cy, float radiusX, float radiusY) {
		return new Rectangle(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY);
	}


	/**
	 * Retourne l'abscisse du bord gauche
	 * @return Abscisse minimale
	 */
	public float getX1() { return x1; }
	/**
	 * Retourne l'ordonnée du bord supérieur
	 * @return Ordonnée minimale
	 */
	public float getY1() { return y1; }
	/**
	 * Retourne l'abscisse du bord droit
	 * @return Abscisse maximale
	 */
	public float getX2() { return x2; }
	/**
	 * Retourne l'ordonnée du bord inférieur
	 * @return Ordonnée maximale
	 */
	public float getY2() { return y2; }
	/**
	 * Retourne la longueur du rectangle
	 * @return Longueur
	 */
	public float getWidth() { return x2 - x1; }
	/**
	 * Retourne la largeur du rectangle
	 * @return Largeur
	 */
	public float getHeight() { return y2 - y1; }
	/**
	 * Retourne le coin supérieur gauche
	 * @return Coin supérieur gauche
	 */
	public Vertice getTopLeftCorner() { return new Vertice(x1, y1); }
	/**
	 * Retourne le coin inférieur droit
	 * @return Coin inférieur droit
	 */
	public Vertice getBottomRightCorner() { return new Vertice(x2, y2); }
	/**
	 * Retourne le centre du rectangle
	 * @return Centre
	 */
	public Vertice getCenter() { return new Vertice((x1 + x2) / 2f, (y1 + y2) / 2f); }
	/**
	 * Retourne l'abscisse du centre
	 * @return Abscisse du centre
	 */
	public float getCenterX() { return (x1 + x2) / 2f; }
	/**
	 * Retourne l'ordonnée du centre
	 * @return Ordonnée du centre
	 */
	public float getCenterY() { return (y1 + y2) / 2f; }


	/**
	 * Vérifie si le rectangle chevauche un autre (des bords qui se touchent ne se chevauchent pas)
	 * @param r Autre rectangle
	 * @return true en cas de chevauchement
	 */
	public boolean intersect(Rectangle r) {
		return x1 < r.x2 && x2 > r.x1 && y1 < r.y2 && y2 > r.y1;
	}

	/**
	 * Vérifie si le rectangle contient entièrement celui donné
	 * @param r Autre rectangle
	 * @return true si r est inclus dans ce rectangle
	 */
	public boolean contain(Rectangle r) {
		return r.x1 >= x1 && r.y1 >= y1 && r.x2 <= x2 && r.y2 <= y2;
	}

	/**
	 * Vérifie si le rectangle contient un point (bords inclus)
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @return true si le point est dans le rectangle
	 */
	public boolean contains(float x, float y) {
		return x >= x1 && x <= x2 && y >= y1 && y <= y2;
	}

	/**
	 * Retourne le rectangle agrandi (ou réduit si la marge est négative) sur chaque bord
	 * @param margin Marge
	 * @return Nouveau rectangle
	 */
	public Rectangle expand(float margin) {
		float cx = getCenterX(), cy = getCenterY();
		float rx = Math.max(0, getWidth() / 2f + margin), ry = Math.max(0, getHeight() / 2f + margin);
		return centered(cx, cy, rx, ry);
	}

	/**
	 * Retourne le rectangle translaté
	 * @param dx Déplacement horizontal
	 * @param dy Déplacement vertical
	 * @return Nouveau rectangle
	 */
	public Rectangle translate(float dx, float dy) {
		return new Rectangle(x1 + dx, y1 + dy, x2 + dx, y2 + dy);
	}

	/**
	 * Retourne le rectangle mis à l'échelle autour de son centre (les deux axes sont réduits proportionnellement)
	 * @param coef Coefficient (1 = identique, 0 = point)
	 * @return Nouveau rectangle
	 */
	public Rectangle scale(float coef) {
		float c = Math.max(0, coef);
		return centered(getCenterX(), getCenterY(), getWidth() / 2f * c, getHeight() / 2f * c);
	}

	/**
	 * Interpole linéairement ce rectangle vers un autre
	 * @param target Rectangle d'arrivée
	 * @param t Avancement (0 = ce rectangle, 1 = rectangle d'arrivée)
	 * @return Rectangle intermédiaire
	 */
	public Rectangle lerp(Rectangle target, float t) {
		float k = Math.max(0, Math.min(1, t));
		return new Rectangle(
			x1 + (target.x1 - x1) * k, y1 + (target.y1 - y1) * k,
			x2 + (target.x2 - x2) * k, y2 + (target.y2 - y2) * k);
	}

	/**
	 * Retourne la distance entre un point et le rectangle (0 si le point est à l'intérieur)
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @return Distance
	 */
	public float distanceTo(float x, float y) {
		float dx = Math.max(Math.max(x1 - x, 0), x - x2);
		float dy = Math.max(Math.max(y1 - y, 0), y - y2);
		return (float)Math.sqrt(dx * dx + dy * dy);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Rectangle)) return false;
		Rectangle r = (Rectangle)o;
		return Float.compare(x1, r.x1) == 0 && Float.compare(y1, r.y1) == 0
			&& Float.compare(x2, r.x2) == 0 && Float.compare(y2, r.y2) == 0;
	}

	@Override
	public int hashCode() {
		int h = Float.hashCode(x1);
		h = 31 * h + Float.hashCode(y1);
		h = 31 * h + Float.hashCode(x2);
		return 31 * h + Float.hashCode(y2);
	}

	@Override
	public String toString() {
		return "[" + x1 + ", " + y1 + " -> " + x2 + ", " + y2 + "]";
	}
}
