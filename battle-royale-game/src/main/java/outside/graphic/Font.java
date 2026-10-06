package outside.graphic;

import static org.lwjgl.opengl.GL11.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Police TrueType rendue avec stb_truetype : les caractères Latin-1 sont pré-rendus
 * dans une texture, puis dessinés comme des quadrilatères texturés.
 * @author mourtaza
 */
public class Font implements AutoCloseable {
	/**
	 * Alignement horizontal d'un texte
	 */
	public enum Align { LEFT, CENTER, RIGHT }

	/**
	 * Premier caractère pré-rendu
	 */
	private static final int FIRST_CHAR = 32;
	/**
	 * Nombre de caractères pré-rendus (Latin-1)
	 */
	private static final int CHARS_NUMBER = 224;
	/**
	 * Suréchantillonnage : la police est rendue à une taille double puis réduite
	 */
	private static final float OVERSAMPLING = 2;
	/**
	 * Taille maximale de la texture des caractères
	 */
	private static final int MAX_BITMAP_SIZE = 4096;

	/**
	 * Taille de la police en unités logiques
	 */
	private final float SIZE;
	/**
	 * Hauteur au-dessus de la ligne de base, en unités logiques
	 */
	private final float ASCENT;
	/**
	 * Taille de la texture des caractères
	 */
	private final int BITMAP_SIZE;
	/**
	 * Données des caractères pré-rendus
	 */
	private final STBTTBakedChar.Buffer CHARS;
	/**
	 * Texture des caractères
	 */
	private final Texture TEXTURE;
	/**
	 * Tampons réutilisés pour le calcul des quadrilatères
	 */
	private final FloatBuffer XB = BufferUtils.createFloatBuffer(1), YB = BufferUtils.createFloatBuffer(1);
	private final STBTTAlignedQuad QUAD = STBTTAlignedQuad.malloc();


	/**
	 * Charge une police du classpath
	 * @param path Chemin de la ressource
	 * @param size Taille en unités logiques
	 */
	public Font(String path, float size) {
		SIZE = size;
		ByteBuffer ttf = Texture.readResourceBytes(path);
		float pixelHeight = size * OVERSAMPLING;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			STBTTFontinfo info = STBTTFontinfo.malloc(stack);
			if (!STBTruetype.stbtt_InitFont(info, ttf))
				throw new IllegalStateException("Police invalide : " + path);
			IntBuffer ascent = stack.mallocInt(1), descent = stack.mallocInt(1), gap = stack.mallocInt(1);
			STBTruetype.stbtt_GetFontVMetrics(info, ascent, descent, gap);
			ASCENT = ascent.get(0) * STBTruetype.stbtt_ScaleForPixelHeight(info, pixelHeight) / OVERSAMPLING;
		}

		CHARS = STBTTBakedChar.malloc(CHARS_NUMBER);
		int bitmapSize = 256;
		ByteBuffer alpha = null;
		try {
			while (true) {
				if (alpha != null) MemoryUtil.memFree(alpha);
				alpha = BufferUtils.createByteBuffer(bitmapSize * bitmapSize);
				int result = STBTruetype.stbtt_BakeFontBitmap(ttf, pixelHeight, alpha, bitmapSize, bitmapSize, FIRST_CHAR, CHARS);
				if (result > 0 || bitmapSize >= MAX_BITMAP_SIZE) break;
				bitmapSize *= 2;
			}
		} finally {
			MemoryUtil.memFree(ttf);
		}
		BITMAP_SIZE = bitmapSize;

		ByteBuffer rgba = BufferUtils.createByteBuffer(BITMAP_SIZE * BITMAP_SIZE * 4);
		for (int i = 0; i < BITMAP_SIZE * BITMAP_SIZE; i++)
			rgba.put((byte)255).put((byte)255).put((byte)255).put(alpha.get(i));
		MemoryUtil.memFree(alpha);
		rgba.flip();
		TEXTURE = new Texture(rgba, BITMAP_SIZE, BITMAP_SIZE, false, true);
		MemoryUtil.memFree(rgba);
	}

	/**
	 * Retourne la taille de la police
	 * @return Taille en unités logiques
	 */
	public float getSize() { return SIZE; }

	/**
	 * Mesure la largeur d'un texte
	 * @param text Texte
	 * @return Largeur en unités logiques
	 */
	public float width(String text) {
		float w = 0;
		for (int i = 0; i < text.length(); i++)
			w += CHARS.get(index(text.charAt(i))).xadvance() / OVERSAMPLING;
		return w;
	}

	/**
	 * Dessine un texte
	 * @param text Texte
	 * @param x Abscisse (selon l'alignement)
	 * @param y Ordonnée du haut du texte
	 * @param color Couleur
	 * @param align Alignement horizontal
	 */
	public void draw(String text, float x, float y, Color color, Align align) {
		if (text == null || text.isEmpty()) return;

		float startX = switch (align) {
			case LEFT -> x;
			case CENTER -> x - width(text) / 2;
			case RIGHT -> x - width(text);
		};

		glEnable(GL_TEXTURE_2D);
		TEXTURE.bind();
		glColor4f(color.r(), color.g(), color.b(), color.a());

		XB.put(0, 0);
		YB.put(0, 0);
		float baseline = y + ASCENT;
		glBegin(GL_QUADS);
		for (int i = 0; i < text.length(); i++) {
			STBTruetype.stbtt_GetBakedQuad(CHARS, BITMAP_SIZE, BITMAP_SIZE, index(text.charAt(i)), XB, YB, QUAD, true);
			float x0 = startX + QUAD.x0() / OVERSAMPLING, x1 = startX + QUAD.x1() / OVERSAMPLING;
			float y0 = baseline + QUAD.y0() / OVERSAMPLING, y1 = baseline + QUAD.y1() / OVERSAMPLING;
			glTexCoord2f(QUAD.s0(), QUAD.t0()); glVertex2f(x0, y0);
			glTexCoord2f(QUAD.s1(), QUAD.t0()); glVertex2f(x1, y0);
			glTexCoord2f(QUAD.s1(), QUAD.t1()); glVertex2f(x1, y1);
			glTexCoord2f(QUAD.s0(), QUAD.t1()); glVertex2f(x0, y1);
		}
		glEnd();
		glDisable(GL_TEXTURE_2D);
	}

	/**
	 * Dessine un texte avec une ombre portée
	 * @param text Texte
	 * @param x Abscisse (selon l'alignement)
	 * @param y Ordonnée du haut du texte
	 * @param color Couleur
	 * @param align Alignement horizontal
	 */
	public void drawShadowed(String text, float x, float y, Color color, Align align) {
		float offset = Math.max(1, SIZE / 14);
		draw(text, x + offset, y + offset, new Color(0, 0, 0, 0.75f * color.a()), align);
		draw(text, x, y, color, align);
	}

	/**
	 * Retourne l'indice d'un caractère dans les caractères pré-rendus
	 * @param c Caractère
	 * @return Indice ('?' pour un caractère non disponible)
	 */
	private static int index(char c) {
		int i = c - FIRST_CHAR;
		return (i >= 0 && i < CHARS_NUMBER) ? i : '?' - FIRST_CHAR;
	}

	/**
	 * Libère la police
	 */
	@Override
	public void close() {
		TEXTURE.delete();
		CHARS.free();
		QUAD.free();
		MemoryUtil.memFree(XB);
		MemoryUtil.memFree(YB);
	}
}
