package outside.graphic;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

/**
 * Texture OpenGL RVBA
 * @author mourtaza
 */
public class Texture {
	/**
	 * Identifiant OpenGL
	 */
	private final int ID;
	/**
	 * Dimensions en pixels
	 */
	private final int WIDTH, HEIGHT;


	/**
	 * Crée une texture à partir de pixels RVBA
	 * @param pixels Pixels RVBA (4 octets par pixel, ligne par ligne depuis le haut)
	 * @param width Largeur
	 * @param height Hauteur
	 * @param repeat true pour répéter la texture au-delà de ses bords
	 * @param smooth true pour un filtrage linéaire
	 */
	public Texture(ByteBuffer pixels, int width, int height, boolean repeat, boolean smooth) {
		WIDTH = width;
		HEIGHT = height;
		ID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, ID);

		int wrap = repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE, filter = smooth ? GL_LINEAR : GL_NEAREST;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
	}

	/**
	 * Image décodée en mémoire
	 * @param pixels Pixels RVBA
	 * @param width Largeur
	 * @param height Hauteur
	 */
	public record Image(ByteBuffer pixels, int width, int height) {}

	/**
	 * Décode une image du classpath avec stb_image
	 * @param path Chemin de la ressource (sans / initial)
	 * @return Image RVBA
	 */
	public static Image readResource(String path) {
		ByteBuffer encoded = readResourceBytes(path);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
			ByteBuffer decoded = STBImage.stbi_load_from_memory(encoded, w, h, c, 4);
			if (decoded == null)
				throw new IllegalStateException("Impossible de décoder la texture " + path + " : " + STBImage.stbi_failure_reason());

			ByteBuffer copy = BufferUtils.createByteBuffer(decoded.remaining());
			copy.put(decoded).flip();
			STBImage.stbi_image_free(decoded);
			return new Image(copy, w.get(0), h.get(0));
		}
	}

	/**
	 * Lit une ressource du classpath dans un tampon direct
	 * @param path Chemin de la ressource (sans / initial)
	 * @return Contenu de la ressource
	 */
	public static ByteBuffer readResourceBytes(String path) {
		try (InputStream in = Texture.class.getResourceAsStream("/" + path)) {
			if (in == null) throw new IllegalStateException("Ressource introuvable : " + path);
			byte[] bytes = in.readAllBytes();
			ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
			buffer.put(bytes).flip();
			return buffer;
		} catch (IOException e) {
			throw new IllegalStateException("Impossible de lire la ressource " + path, e);
		}
	}

	/**
	 * Retourne l'identifiant OpenGL
	 * @return Identifiant
	 */
	public int getID() { return ID; }
	/**
	 * Retourne la largeur
	 * @return Largeur en pixels
	 */
	public int getWidth() { return WIDTH; }
	/**
	 * Retourne la hauteur
	 * @return Hauteur en pixels
	 */
	public int getHeight() { return HEIGHT; }

	/**
	 * Lie la texture
	 */
	public void bind() {
		glBindTexture(GL_TEXTURE_2D, ID);
	}

	/**
	 * Libère la texture
	 */
	public void delete() {
		glDeleteTextures(ID);
	}
}
