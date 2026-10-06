package outside.graphic;

import static inside.IConfig.*;
import static org.lwjgl.opengl.GL11.*;

import inside.geometry.Rectangle;

/**
 * Primitives de dessin OpenGL dans l'espace logique de la carte (1280 × 720, axe Y vers le bas)
 * @author mourtaza
 */
public final class GraphicUtilities {
	private GraphicUtilities() {}

	/**
	 * Prépare l'image : efface le tampon, applique la zone d'affichage et la projection logique
	 * @param framebufferWidth Largeur du tampon en pixels
	 * @param framebufferHeight Hauteur du tampon en pixels
	 * @return Zone d'affichage utilisée
	 */
	public static Viewport beginFrame(int framebufferWidth, int framebufferHeight) {
		Viewport v = Viewport.letterbox(framebufferWidth, framebufferHeight);

		glViewport(0, 0, Math.max(1, framebufferWidth), Math.max(1, framebufferHeight));
		glDisable(GL_SCISSOR_TEST);
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);

		glViewport(v.x(), v.y(), Math.max(1, v.width()), Math.max(1, v.height()));
		glEnable(GL_SCISSOR_TEST);
		glScissor(v.x(), v.y(), Math.max(1, v.width()), Math.max(1, v.height()));

		glMatrixMode(GL_PROJECTION);
		glLoadIdentity();
		glOrtho(0, MAP_WIDTH, MAP_HEIGHT, 0, -1, 1);
		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();

		glDisable(GL_DEPTH_TEST);
		glDisable(GL_TEXTURE_2D);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glEnable(GL_LINE_SMOOTH);
		return v;
	}

	/**
	 * Applique une couleur
	 * @param c Couleur
	 */
	public static void color(Color c) {
		glColor4f(c.r(), c.g(), c.b(), c.a());
	}

	/**
	 * Passe en mélange additif (lueurs) ou normal
	 * @param additive true pour le mélange additif
	 */
	public static void additive(boolean additive) {
		glBlendFunc(GL_SRC_ALPHA, additive ? GL_ONE : GL_ONE_MINUS_SRC_ALPHA);
	}

	/**
	 * Dessine un rectangle plein
	 * @param x1 Gauche
	 * @param y1 Haut
	 * @param x2 Droite
	 * @param y2 Bas
	 * @param c Couleur
	 */
	public static void fillRect(float x1, float y1, float x2, float y2, Color c) {
		color(c);
		glBegin(GL_QUADS);
		glVertex2f(x1, y1);
		glVertex2f(x2, y1);
		glVertex2f(x2, y2);
		glVertex2f(x1, y2);
		glEnd();
	}

	/**
	 * Dessine un rectangle plein
	 * @param r Rectangle
	 * @param c Couleur
	 */
	public static void fillRect(Rectangle r, Color c) {
		fillRect(r.getX1(), r.getY1(), r.getX2(), r.getY2(), c);
	}

	/**
	 * Dessine un rectangle plein avec un dégradé vertical
	 * @param x1 Gauche
	 * @param y1 Haut
	 * @param x2 Droite
	 * @param y2 Bas
	 * @param top Couleur du haut
	 * @param bottom Couleur du bas
	 */
	public static void gradientRect(float x1, float y1, float x2, float y2, Color top, Color bottom) {
		glBegin(GL_QUADS);
		color(top);
		glVertex2f(x1, y1);
		glVertex2f(x2, y1);
		color(bottom);
		glVertex2f(x2, y2);
		glVertex2f(x1, y2);
		glEnd();
	}

	/**
	 * Dessine le contour d'un rectangle
	 * @param r Rectangle
	 * @param width Épaisseur
	 * @param c Couleur
	 */
	public static void strokeRect(Rectangle r, float width, Color c) {
		float h = width / 2;
		fillRect(r.getX1() - h, r.getY1() - h, r.getX2() + h, r.getY1() + h, c);
		fillRect(r.getX1() - h, r.getY2() - h, r.getX2() + h, r.getY2() + h, c);
		fillRect(r.getX1() - h, r.getY1() + h, r.getX1() + h, r.getY2() - h, c);
		fillRect(r.getX2() - h, r.getY1() + h, r.getX2() + h, r.getY2() - h, c);
	}

	/**
	 * Dessine le contour en pointillés d'un rectangle
	 * @param r Rectangle
	 * @param width Épaisseur
	 * @param dash Longueur d'un tiret
	 * @param offset Décalage des tirets (animation)
	 * @param c Couleur
	 */
	public static void dashedRect(Rectangle r, float width, float dash, float offset, Color c) {
		dashedLine(r.getX1(), r.getY1(), r.getX2(), r.getY1(), width, dash, offset, c);
		dashedLine(r.getX2(), r.getY1(), r.getX2(), r.getY2(), width, dash, offset, c);
		dashedLine(r.getX2(), r.getY2(), r.getX1(), r.getY2(), width, dash, offset, c);
		dashedLine(r.getX1(), r.getY2(), r.getX1(), r.getY1(), width, dash, offset, c);
	}

	/**
	 * Dessine un segment en pointillés
	 * @param x1 Abscisse de départ
	 * @param y1 Ordonnée de départ
	 * @param x2 Abscisse d'arrivée
	 * @param y2 Ordonnée d'arrivée
	 * @param width Épaisseur
	 * @param dash Longueur d'un tiret
	 * @param offset Décalage des tirets
	 * @param c Couleur
	 */
	public static void dashedLine(float x1, float y1, float x2, float y2, float width, float dash, float offset, Color c) {
		float dx = x2 - x1, dy = y2 - y1, length = (float)Math.sqrt(dx * dx + dy * dy);
		if (length < 1e-3f) return;
		float ux = dx / length, uy = dy / length;
		float period = dash * 2, start = -(offset % period);

		for (float t = start; t < length; t += period) {
			float a = Math.max(0, t), b = Math.min(length, t + dash);
			if (b > a) line(x1 + ux * a, y1 + uy * a, x1 + ux * b, y1 + uy * b, width, c);
		}
	}

	/**
	 * Dessine un segment épais
	 * @param x1 Abscisse de départ
	 * @param y1 Ordonnée de départ
	 * @param x2 Abscisse d'arrivée
	 * @param y2 Ordonnée d'arrivée
	 * @param width Épaisseur
	 * @param c Couleur
	 */
	public static void line(float x1, float y1, float x2, float y2, float width, Color c) {
		float dx = x2 - x1, dy = y2 - y1, length = (float)Math.sqrt(dx * dx + dy * dy);
		if (length < 1e-4f) return;
		float nx = -dy / length * width / 2, ny = dx / length * width / 2;
		color(c);
		glBegin(GL_QUADS);
		glVertex2f(x1 + nx, y1 + ny);
		glVertex2f(x2 + nx, y2 + ny);
		glVertex2f(x2 - nx, y2 - ny);
		glVertex2f(x1 - nx, y1 - ny);
		glEnd();
	}

	/**
	 * Dessine un segment dont la couleur varie d'un bout à l'autre
	 * @param x1 Abscisse de départ
	 * @param y1 Ordonnée de départ
	 * @param x2 Abscisse d'arrivée
	 * @param y2 Ordonnée d'arrivée
	 * @param width Épaisseur
	 * @param from Couleur au départ
	 * @param to Couleur à l'arrivée
	 */
	public static void gradientLine(float x1, float y1, float x2, float y2, float width, Color from, Color to) {
		float dx = x2 - x1, dy = y2 - y1, length = (float)Math.sqrt(dx * dx + dy * dy);
		if (length < 1e-4f) return;
		float nx = -dy / length * width / 2, ny = dx / length * width / 2;
		glBegin(GL_QUADS);
		color(from);
		glVertex2f(x1 + nx, y1 + ny);
		color(to);
		glVertex2f(x2 + nx, y2 + ny);
		glVertex2f(x2 - nx, y2 - ny);
		color(from);
		glVertex2f(x1 - nx, y1 - ny);
		glEnd();
	}

	/**
	 * Dessine un secteur de couronne (coup d'épée, indicateurs de recharge)
	 * @param cx Abscisse du centre
	 * @param cy Ordonnée du centre
	 * @param inner Rayon intérieur
	 * @param outer Rayon extérieur
	 * @param from Angle de départ (radians, à l'écran)
	 * @param to Angle d'arrivée
	 * @param innerColor Couleur intérieure
	 * @param outerColor Couleur extérieure
	 */
	public static void arc(float cx, float cy, float inner, float outer, float from, float to, Color innerColor, Color outerColor) {
		int segments = Math.max(6, (int)(Math.abs(to - from) / (Math.PI / 24)));
		glBegin(GL_QUAD_STRIP);
		for (int i = 0; i <= segments; i++) {
			float a = from + (to - from) * i / segments;
			float cos = (float)Math.cos(a), sin = (float)Math.sin(a);
			color(innerColor);
			glVertex2f(cx + cos * inner, cy + sin * inner);
			color(outerColor);
			glVertex2f(cx + cos * outer, cy + sin * outer);
		}
		glEnd();
	}

	/**
	 * Dessine un disque
	 * @param cx Abscisse du centre
	 * @param cy Ordonnée du centre
	 * @param radius Rayon
	 * @param c Couleur
	 */
	public static void disc(float cx, float cy, float radius, Color c) {
		color(c);
		glBegin(GL_TRIANGLE_FAN);
		glVertex2f(cx, cy);
		int segments = Math.max(12, (int)(radius * 1.5f));
		for (int i = 0; i <= segments; i++) {
			double a = 2 * Math.PI * i / segments;
			glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
		}
		glEnd();
	}

	/**
	 * Dessine une région de texture dans un rectangle
	 * @param t Région de texture
	 * @param x1 Gauche
	 * @param y1 Haut
	 * @param x2 Droite
	 * @param y2 Bas
	 * @param c Couleur de modulation
	 */
	public static void texture(SubTexture t, float x1, float y1, float x2, float y2, Color c) {
		glEnable(GL_TEXTURE_2D);
		t.texture().bind();
		color(c);
		glBegin(GL_QUADS);
		glTexCoord2f(t.u1(), t.v1()); glVertex2f(x1, y1);
		glTexCoord2f(t.u2(), t.v1()); glVertex2f(x2, y1);
		glTexCoord2f(t.u2(), t.v2()); glVertex2f(x2, y2);
		glTexCoord2f(t.u1(), t.v2()); glVertex2f(x1, y2);
		glEnd();
		glDisable(GL_TEXTURE_2D);
	}

	/**
	 * Dessine une texture répétée sur un rectangle
	 * @param t Texture répétable
	 * @param x1 Gauche
	 * @param y1 Haut
	 * @param x2 Droite
	 * @param y2 Bas
	 * @param tile Taille d'une répétition en unités logiques
	 * @param offsetX Décalage horizontal de la texture (en répétitions)
	 * @param offsetY Décalage vertical de la texture (en répétitions)
	 * @param c Couleur de modulation
	 */
	public static void tiled(Texture t, float x1, float y1, float x2, float y2, float tile, float offsetX, float offsetY, Color c) {
		if (x2 <= x1 || y2 <= y1) return;
		glEnable(GL_TEXTURE_2D);
		t.bind();
		color(c);
		float u1 = x1 / tile + offsetX, v1 = y1 / tile + offsetY, u2 = x2 / tile + offsetX, v2 = y2 / tile + offsetY;
		glBegin(GL_QUADS);
		glTexCoord2f(u1, v1); glVertex2f(x1, y1);
		glTexCoord2f(u2, v1); glVertex2f(x2, y1);
		glTexCoord2f(u2, v2); glVertex2f(x2, y2);
		glTexCoord2f(u1, v2); glVertex2f(x1, y2);
		glEnd();
		glDisable(GL_TEXTURE_2D);
	}

	/**
	 * Dessine un fondu sur les quatre bords d'un rectangle (vignettage, mousse, assombrissement)
	 * @param r Rectangle dont les bords sont fondu
	 * @param depth Profondeur du fondu
	 * @param c Couleur au niveau du bord (transparente au bout du fondu)
	 * @param inside true pour un fondu vers l'intérieur du rectangle, false vers l'extérieur
	 */
	public static void edgeFade(Rectangle r, float depth, Color c, boolean inside) {
		Color none = c.withAlpha(0);
		float x1 = r.getX1(), y1 = r.getY1(), x2 = r.getX2(), y2 = r.getY2();
		float cy = r.getCenterY(), h = r.getHeight();
		if (inside) {
			gradientRect(x1, y1, x2, y1 + depth, c, none);
			gradientRect(x1, y2 - depth, x2, y2, none, c);
			gradientLine(x1, cy, x1 + depth, cy, h, c, none);
			gradientLine(x2, cy, x2 - depth, cy, h, c, none);
		} else {
			gradientRect(x1, y1 - depth, x2, y1, none, c);
			gradientRect(x1, y2, x2, y2 + depth, c, none);
			gradientLine(x1, cy, x1 - depth, cy, h, c, none);
			gradientLine(x2, cy, x2 + depth, cy, h, c, none);
		}
	}

	/**
	 * Dessine un halo radial centré
	 * @param glow Texture de halo
	 * @param cx Abscisse du centre
	 * @param cy Ordonnée du centre
	 * @param rx Rayon horizontal
	 * @param ry Rayon vertical
	 * @param c Couleur
	 */
	public static void glow(Texture glow, float cx, float cy, float rx, float ry, Color c) {
		texture(SubTexture.whole(glow), cx - rx, cy - ry, cx + rx, cy + ry, c);
	}

	/**
	 * Dessine un panneau semi-transparent à bords arrondis
	 * @param x1 Gauche
	 * @param y1 Haut
	 * @param x2 Droite
	 * @param y2 Bas
	 * @param radius Rayon des coins
	 * @param c Couleur
	 */
	public static void panel(float x1, float y1, float x2, float y2, float radius, Color c) {
		radius = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2);
		fillRect(x1 + radius, y1, x2 - radius, y2, c);
		fillRect(x1, y1 + radius, x1 + radius, y2 - radius, c);
		fillRect(x2 - radius, y1 + radius, x2, y2 - radius, c);
		corner(x1 + radius, y1 + radius, radius, (float)Math.PI, c);
		corner(x2 - radius, y1 + radius, radius, (float)(1.5 * Math.PI), c);
		corner(x2 - radius, y2 - radius, radius, 0, c);
		corner(x1 + radius, y2 - radius, radius, (float)(0.5 * Math.PI), c);
	}

	/**
	 * Dessine un quart de disque
	 * @param cx Abscisse du centre
	 * @param cy Ordonnée du centre
	 * @param r Rayon
	 * @param start Angle de départ
	 * @param c Couleur
	 */
	private static void corner(float cx, float cy, float r, float start, Color c) {
		color(c);
		glBegin(GL_TRIANGLE_FAN);
		glVertex2f(cx, cy);
		for (int i = 0; i <= 6; i++) {
			double a = start + Math.PI / 2 * i / 6;
			glVertex2f(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r);
		}
		glEnd();
	}
}
