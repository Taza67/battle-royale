package outside.graphic;

/**
 * Région rectangulaire d'une texture (coordonnées de texture normalisées)
 * @param texture Texture source
 * @param u1 Abscisse de texture gauche
 * @param v1 Ordonnée de texture haute
 * @param u2 Abscisse de texture droite
 * @param v2 Ordonnée de texture basse
 * @param width Largeur en pixels
 * @param height Hauteur en pixels
 * @author mourtaza
 */
public record SubTexture(Texture texture, float u1, float v1, float u2, float v2, int width, int height) {
	/**
	 * Construit une région à partir de coordonnées en pixels
	 * @param t Texture source
	 * @param x Abscisse du coin haut-gauche
	 * @param y Ordonnée du coin haut-gauche
	 * @param w Largeur
	 * @param h Hauteur
	 * @return Région
	 */
	public static SubTexture of(Texture t, int x, int y, int w, int h) {
		float tw = t.getWidth(), th = t.getHeight();
		// Décalage d'un demi-pixel pour éviter de déborder sur les tuiles voisines
		return new SubTexture(t, (x + 0.5f) / tw, (y + 0.5f) / th, (x + w - 0.5f) / tw, (y + h - 0.5f) / th, w, h);
	}

	/**
	 * Construit une région couvrant toute la texture
	 * @param t Texture
	 * @return Région
	 */
	public static SubTexture whole(Texture t) {
		return new SubTexture(t, 0, 0, 1, 1, t.getWidth(), t.getHeight());
	}
}
