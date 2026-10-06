package inside;

/**
 * Utilitaires de bornage des valeurs du moteur
 * @author mourtaza
 */
public final class Clamps {
	private Clamps() {}

	/**
	 * Borne un entier dans [min, max]
	 * @param v Valeur
	 * @param min Minimum
	 * @param max Maximum
	 * @return Valeur bornée
	 */
	public static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	/**
	 * Borne un flottant dans [min, max]
	 * @param v Valeur
	 * @param min Minimum
	 * @param max Maximum
	 * @return Valeur bornée
	 */
	public static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}
