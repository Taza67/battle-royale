package outside.graphic;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.BufferUtils;

import inside.IConfig;

/**
 * Classe chargeant les textures du jeu depuis le classpath.
 * <p>
 * Les sous-textures sont numérotées comme dans la version d'origine : 0 forêt, 1 rocher,
 * 2 eau, 3 champ de bataille, puis l'emoji du joueur d'identifiant {@code id} à l'indice
 * {@code id + 4} (même numérotation que les avatars de la manette web).
 * @author mourtaza
 */
public class TextureManager implements IConfig {
	private final static String TEXTURE_ATLAS_PATH = "textures/tileset.png";
	private final static String TEXTURE_PLAYERS_ATLAS_PATH = "textures/emojisset.png";
	/**
	 * Abscisses des colonnes d'emojis dans l'atlas des joueurs
	 */
	private final static int[] EMOJI_COLUMNS = { 2, 88, 170, 255, 333, 421, 506, 585, 670, 755 };
	/**
	 * Ordonnées des lignes d'emojis dans l'atlas des joueurs
	 */
	private final static int[] EMOJI_ROWS = { 3, 98, 195, 290, 385 };
	/**
	 * Taille d'un emoji dans l'atlas
	 */
	private final static int EMOJI_SIZE = 61;

	private final Texture TEXTURE_ATLAS, TEXTURE_PLAYERS_ATLAS, TEXTURE_PLAYERS_GREY;
	private final Texture GRASS, LAVA, GLOW;
	private final List<SubTexture> SUBTEXTURES;
	private final List<SubTexture> GREY_PLAYERS;


	/**
	 * Charge toutes les textures (nécessite un contexte OpenGL courant)
	 */
	public TextureManager() {
		Texture.Image tiles = Texture.readResource(TEXTURE_ATLAS_PATH);
		Texture.Image emojis = Texture.readResource(TEXTURE_PLAYERS_ATLAS_PATH);

		TEXTURE_ATLAS = new Texture(tiles.pixels(), tiles.width(), tiles.height(), false, false);
		TEXTURE_PLAYERS_ATLAS = new Texture(emojis.pixels(), emojis.width(), emojis.height(), false, true);
		TEXTURE_PLAYERS_GREY = new Texture(greyscale(emojis.pixels()), emojis.width(), emojis.height(), false, true);
		GRASS = generateGrass();
		LAVA = generateLava();
		GLOW = generateGlow();

		SUBTEXTURES = new ArrayList<>();
		SUBTEXTURES.add(SubTexture.of(TEXTURE_ATLAS, 82, 120, TREE_WIDTH, TREE_HEIGHT));		// Forêt
		SUBTEXTURES.add(SubTexture.of(TEXTURE_ATLAS, 92, 67, STONE_WIDTH, STONE_HEIGHT));		// Rocher
		SUBTEXTURES.add(SubTexture.of(TEXTURE_ATLAS, 394, 445, WATER_WIDTH, WATER_HEIGHT));		// Eau
		SUBTEXTURES.add(SubTexture.whole(GRASS));												// Champ de bataille

		GREY_PLAYERS = new ArrayList<>();
		for (int y : EMOJI_ROWS)
			for (int x : EMOJI_COLUMNS) {
				SUBTEXTURES.add(SubTexture.of(TEXTURE_PLAYERS_ATLAS, x, y, EMOJI_SIZE, EMOJI_SIZE));
				GREY_PLAYERS.add(SubTexture.of(TEXTURE_PLAYERS_GREY, x, y, EMOJI_SIZE, EMOJI_SIZE));
			}
	}


	/**
	 * Retourne une sous-texture par son numéro
	 * @param id Numéro
	 * @return Sous-texture
	 */
	public SubTexture getSubTexture(int id) { return SUBTEXTURES.get(id); }
	/**
	 * Retourne le nombre d'emojis disponibles
	 * @return Nombre d'emojis
	 */
	public int getPlayersTexturesNumber() { return GREY_PLAYERS.size(); }
	/**
	 * Retourne l'emoji d'un joueur (texture {@code id + 4}, en boucle au-delà du nombre d'emojis)
	 * @param playerId Identifiant du joueur
	 * @param grey true pour la version grisée (joueur éliminé)
	 * @return Sous-texture
	 */
	public SubTexture getPlayerTexture(int playerId, boolean grey) {
		int index = Math.floorMod(playerId, GREY_PLAYERS.size());
		return grey ? GREY_PLAYERS.get(index) : SUBTEXTURES.get(TEXTURE_FIRST_PLAYER + index);
	}
	/**
	 * Retourne la texture d'herbe (répétable)
	 * @return Texture
	 */
	public Texture getGrass() { return GRASS; }
	/**
	 * Retourne la texture de lave (répétable)
	 * @return Texture
	 */
	public Texture getLava() { return LAVA; }
	/**
	 * Retourne la texture de halo (dégradé radial blanc)
	 * @return Texture
	 */
	public Texture getGlow() { return GLOW; }

	/**
	 * Libère les textures
	 */
	public void delete() {
		for (Texture t : List.of(TEXTURE_ATLAS, TEXTURE_PLAYERS_ATLAS, TEXTURE_PLAYERS_GREY, GRASS, LAVA, GLOW))
			t.delete();
	}


	/**
	 * Convertit des pixels RVBA en niveaux de gris assombris
	 * @param src Pixels source
	 * @return Nouveaux pixels
	 */
	private static ByteBuffer greyscale(ByteBuffer src) {
		ByteBuffer dst = BufferUtils.createByteBuffer(src.remaining());
		for (int i = src.position(); i + 3 < src.limit(); i += 4) {
			int r = src.get(i) & 0xFF, g = src.get(i + 1) & 0xFF, b = src.get(i + 2) & 0xFF;
			byte l = (byte)Math.round((0.299f * r + 0.587f * g + 0.114f * b) * 0.75f);
			dst.put(l).put(l).put(l).put(src.get(i + 3));
		}
		return dst.flip();
	}

	/**
	 * Génère une texture d'herbe répétable
	 * @return Texture
	 */
	private static Texture generateGrass() {
		int size = 128;
		Random random = new Random(7);
		float[] noise = smoothNoise(size, 16, random);
		ByteBuffer px = BufferUtils.createByteBuffer(size * size * 4);
		for (int i = 0; i < size * size; i++) {
			float n = noise[i] * 0.6f + random.nextFloat() * 0.4f;
			float blade = random.nextFloat() < 0.04f ? 0.12f : 0;
			px.put(toByte(0.30f + 0.10f * n + blade)).put(toByte(0.52f + 0.14f * n + blade)).put(toByte(0.24f + 0.06f * n)).put((byte)255);
		}
		return new Texture(px.flip(), size, size, true, true);
	}

	/**
	 * Génère une texture de lave répétable
	 * @return Texture
	 */
	private static Texture generateLava() {
		int size = 128;
		Random random = new Random(11);
		float[] large = smoothNoise(size, 32, random), small = smoothNoise(size, 8, random);
		ByteBuffer px = BufferUtils.createByteBuffer(size * size * 4);
		for (int i = 0; i < size * size; i++) {
			float n = large[i] * 0.65f + small[i] * 0.35f;
			float hot = (float)Math.pow(n, 2.2);
			px.put(toByte(0.55f + 0.45f * n)).put(toByte(0.08f + 0.62f * hot)).put(toByte(0.02f + 0.20f * hot * hot)).put((byte)255);
		}
		return new Texture(px.flip(), size, size, true, true);
	}

	/**
	 * Génère un dégradé radial blanc (halos, ombres, particules)
	 * @return Texture
	 */
	private static Texture generateGlow() {
		int size = 64;
		ByteBuffer px = BufferUtils.createByteBuffer(size * size * 4);
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5f) / size * 2 - 1, dy = (y + 0.5f) / size * 2 - 1;
				float d = Math.min(1, (float)Math.sqrt(dx * dx + dy * dy));
				float a = (1 - d) * (1 - d);
				px.put((byte)255).put((byte)255).put((byte)255).put(toByte(a));
			}
		return new Texture(px.flip(), size, size, false, true);
	}

	/**
	 * Bruit de valeur lissé et répétable
	 * @param size Taille de la texture
	 * @param cell Taille d'une cellule de bruit (diviseur de size)
	 * @param random Générateur aléatoire
	 * @return Valeurs entre 0 et 1
	 */
	private static float[] smoothNoise(int size, int cell, Random random) {
		int cells = size / cell;
		float[] grid = new float[cells * cells];
		for (int i = 0; i < grid.length; i++) grid[i] = random.nextFloat();

		float[] out = new float[size * size];
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++) {
				int cx = x / cell, cy = y / cell;
				float fx = smooth((x % cell) / (float)cell), fy = smooth((y % cell) / (float)cell);
				float a = grid[cy * cells + cx], b = grid[cy * cells + (cx + 1) % cells];
				float c = grid[((cy + 1) % cells) * cells + cx], d = grid[((cy + 1) % cells) * cells + (cx + 1) % cells];
				out[y * size + x] = (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
			}
		return out;
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	private static byte toByte(float v) {
		return (byte)Math.round(Math.max(0, Math.min(1, v)) * 255);
	}
}
