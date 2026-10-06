package outside.graphic;

import inside.IConfig;

/**
 * Zone d'affichage 16:9 centrée dans le tampon d'image (bandes noires autour)
 * @param x Abscisse en pixels du tampon
 * @param y Ordonnée en pixels du tampon (depuis le bas, convention OpenGL)
 * @param width Largeur en pixels
 * @param height Hauteur en pixels
 * @author mourtaza
 */
public record Viewport(int x, int y, int width, int height) implements IConfig {
	/**
	 * Calcule la plus grande zone au format de la carte contenue dans un tampon
	 * @param framebufferWidth Largeur du tampon en pixels
	 * @param framebufferHeight Hauteur du tampon en pixels
	 * @return Zone d'affichage
	 */
	public static Viewport letterbox(int framebufferWidth, int framebufferHeight) {
		if (framebufferWidth <= 0 || framebufferHeight <= 0) return new Viewport(0, 0, 0, 0);

		float ratio = MAP_WIDTH / MAP_HEIGHT;
		int w = framebufferWidth, h = Math.round(w / ratio);
		if (h > framebufferHeight) {
			h = framebufferHeight;
			w = Math.round(h * ratio);
		}
		return new Viewport((framebufferWidth - w) / 2, (framebufferHeight - h) / 2, w, h);
	}

	/**
	 * Retourne le facteur d'échelle entre l'espace logique (1280 × 720) et les pixels
	 * @return Pixels par unité logique
	 */
	public float scale() {
		return width / MAP_WIDTH;
	}
}
