package inside;

import static inside.IConfig.*;
/**
 * Classe utilitaire sur les 8 directions du jeu
 * (sens trigonométrique, axe Y vers le bas : 0 est, 2 nord, 4 ouest, 6 sud)
 * @author mourtaza
 *
 * @see IConfig#EAST
 */
public final class Direction {
	/**
	 * Composantes des vecteurs unitaires de chaque direction
	 */
	private static final float[] DX = new float[DIRECTIONS_NUMBER], DY = new float[DIRECTIONS_NUMBER];

	static {
		for (int d = 0; d < DIRECTIONS_NUMBER; d++) {
			double a = d * Math.PI / 4;
			DX[d] = (float)Math.cos(a);
			DY[d] = (float)-Math.sin(a);
			if (Math.abs(DX[d]) < 1e-6f) DX[d] = 0;
			if (Math.abs(DY[d]) < 1e-6f) DY[d] = 0;
		}
	}

	private Direction() {}

	/**
	 * Vérifie qu'un code de direction est valide
	 * @param direction Code de direction
	 * @return true si le code est compris entre 0 et 7
	 */
	public static boolean isValid(int direction) {
		return direction >= 0 && direction < DIRECTIONS_NUMBER;
	}

	/**
	 * Retourne la composante horizontale du vecteur unitaire d'une direction
	 * @param direction Code de direction
	 * @return Composante horizontale (0 si la direction est invalide)
	 */
	public static float dx(int direction) {
		return isValid(direction) ? DX[direction] : 0;
	}

	/**
	 * Retourne la composante verticale (axe vers le bas) du vecteur unitaire d'une direction
	 * @param direction Code de direction
	 * @return Composante verticale (0 si la direction est invalide)
	 */
	public static float dy(int direction) {
		return isValid(direction) ? DY[direction] : 0;
	}

	/**
	 * Retourne l'angle d'une direction à l'écran (axe Y vers le bas), en radians
	 * @param direction Code de direction
	 * @return Angle tel que (cos, sin) donne le vecteur à l'écran
	 */
	public static float screenAngle(int direction) {
		return (float)Math.atan2(dy(direction), dx(direction));
	}

	/**
	 * Retourne la direction la plus proche d'un vecteur à l'écran
	 * @param vx Composante horizontale
	 * @param vy Composante verticale (axe vers le bas)
	 * @return Code de direction, ou -1 pour le vecteur nul
	 */
	public static int fromVector(float vx, float vy) {
		if (vx == 0 && vy == 0) return -1;
		double a = Math.atan2(-vy, vx);
		int d = (int)Math.round(a / (Math.PI / 4));
		return Math.floorMod(d, DIRECTIONS_NUMBER);
	}

	/**
	 * Retourne l'écart angulaire entre un vecteur et une direction
	 * @param direction Code de direction
	 * @param vx Composante horizontale du vecteur
	 * @param vy Composante verticale du vecteur
	 * @return Écart en radians, entre 0 et PI
	 */
	public static float angleTo(int direction, float vx, float vy) {
		double a = Math.atan2(vy, vx) - screenAngle(direction);
		a = Math.abs(Math.IEEEremainder(a, 2 * Math.PI));
		return (float)a;
	}

	/**
	 * Retourne la direction opposée
	 * @param direction Code de direction
	 * @return Direction opposée
	 */
	public static int opposite(int direction) {
		return Math.floorMod(direction + 4, DIRECTIONS_NUMBER);
	}

	/**
	 * Retourne la direction tournée d'un certain nombre de huitièmes de tour
	 * @param direction Code de direction
	 * @param steps Nombre de huitièmes de tour (positif = sens trigonométrique)
	 * @return Nouvelle direction
	 */
	public static int rotate(int direction, int steps) {
		return Math.floorMod(direction + steps, DIRECTIONS_NUMBER);
	}
}
