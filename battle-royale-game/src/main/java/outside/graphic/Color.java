package outside.graphic;

/**
 * Couleur RVBA, composantes entre 0 et 1
 * @param r Rouge
 * @param g Vert
 * @param b Bleu
 * @param a Opacité
 * @author mourtaza
 */
public record Color(float r, float g, float b, float a) {
	/** Blanc */
	public static final Color WHITE = new Color(1, 1, 1, 1);
	/** Noir */
	public static final Color BLACK = new Color(0, 0, 0, 1);
	/** Doré (joueur local, vainqueur) */
	public static final Color GOLD = rgb(0xFFD54A);
	/** Rouge (dégâts, erreurs) */
	public static final Color RED = rgb(0xFF4D4D);
	/** Vert (points de vie) */
	public static final Color GREEN = rgb(0x5BE37D);
	/** Cyan (zone sûre) */
	public static final Color CYAN = rgb(0x7FE7FF);
	/** Gris clair (textes secondaires) */
	public static final Color LIGHT_GREY = rgb(0xC8CCD6);
	/** Orange (lave) */
	public static final Color ORANGE = rgb(0xFF8A2B);

	/**
	 * Construit une couleur opaque à partir de composantes entre 0 et 1
	 * @param r Rouge
	 * @param g Vert
	 * @param b Bleu
	 */
	public Color(float r, float g, float b) {
		this(r, g, b, 1);
	}

	/**
	 * Construit une couleur opaque à partir d'un entier 0xRRGGBB
	 * @param hex Couleur hexadécimale
	 * @return Couleur
	 */
	public static Color rgb(int hex) {
		return new Color(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f, 1);
	}

	/**
	 * Retourne la même couleur avec une autre opacité
	 * @param alpha Opacité
	 * @return Nouvelle couleur
	 */
	public Color withAlpha(float alpha) {
		return new Color(r, g, b, Math.max(0, Math.min(1, alpha)));
	}

	/**
	 * Mélange deux couleurs
	 * @param o Autre couleur
	 * @param t Proportion de l'autre couleur (0 à 1)
	 * @return Couleur intermédiaire
	 */
	public Color mix(Color o, float t) {
		t = Math.max(0, Math.min(1, t));
		return new Color(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t, a + (o.a - a) * t);
	}

	/**
	 * Retourne une couleur vive et stable associée à un identifiant
	 * @param seed Identifiant
	 * @return Couleur
	 */
	public static Color fromId(int seed) {
		float hue = ((seed * 0.61803398875f) % 1f) * 6, s = 0.55f;
		int sector = (int)hue % 6;
		float f = hue - (int)hue, p = 1 - s, q = 1 - s * f, t = 1 - s * (1 - f);
		switch (sector) {
		case 0: return new Color(1, t, p);
		case 1: return new Color(q, 1, p);
		case 2: return new Color(p, 1, t);
		case 3: return new Color(p, q, 1);
		case 4: return new Color(t, p, 1);
		default: return new Color(1, p, q);
		}
	}

	/**
	 * Couleur de la barre de vie selon la proportion de vie restante
	 * @param ratio Proportion de vie (0 à 1)
	 * @return Couleur du vert au rouge
	 */
	public static Color life(float ratio) {
		if (ratio > 0.5f) return rgb(0xF5D547).mix(GREEN, (ratio - 0.5f) * 2);
		return RED.mix(rgb(0xF5D547), ratio * 2);
	}
}
