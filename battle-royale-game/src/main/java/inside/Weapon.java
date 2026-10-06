package inside;

import java.util.HashSet;
import java.util.Set;

/**
 * Classe représentant l'équipement d'un joueur : une épée (coup de courte durée suivi d'un temps de recharge)
 * et une arme de tir (temps de recharge entre deux tirs)
 * @author mourtaza
 *
 * @see Player
 */
public class Weapon implements IConfig {
	/**
	 * Nombre de pas de simulation restants pendant lesquels le coup d'épée peut toucher
	 */
	private int activeTicksLeft;
	/**
	 * Nombre de pas de simulation avant de pouvoir donner un nouveau coup d'épée
	 */
	private int meleeCooldownLeft;
	/**
	 * Nombre de pas de simulation avant de pouvoir tirer à nouveau
	 */
	private int shootCooldownLeft;
	/**
	 * Identifiants des joueurs déjà touchés par le coup d'épée en cours
	 */
	private final Set<Integer> HIT_THIS_SWING = new HashSet<>();


	/**
	 * Indique si un coup d'épée peut être donné
	 * @return true si l'épée est rechargée
	 */
	public boolean canSwing() { return meleeCooldownLeft <= 0; }
	/**
	 * Indique si un tir peut être effectué
	 * @return true si l'arme de tir est rechargée
	 */
	public boolean canShoot() { return shootCooldownLeft <= 0; }
	/**
	 * Indique si un coup d'épée est en cours (fenêtre pendant laquelle il peut toucher)
	 * @return true si le coup est actif
	 */
	public boolean isSwinging() { return activeTicksLeft > 0; }
	/**
	 * Retourne l'avancement du coup d'épée en cours
	 * @return Valeur entre 0 (début) et 1 (fin), ou -1 si aucun coup n'est en cours
	 */
	public float getSwingProgress() {
		return isSwinging() ? 1f - activeTicksLeft / (float)MELEE_ACTIVE_TICKS : -1f;
	}
	/**
	 * Retourne le temps de recharge restant de l'épée
	 * @return Nombre de pas de simulation
	 */
	public int getMeleeCooldownLeft() { return Math.max(0, meleeCooldownLeft); }
	/**
	 * Retourne le temps de recharge restant de l'arme de tir
	 * @return Nombre de pas de simulation
	 */
	public int getShootCooldownLeft() { return Math.max(0, shootCooldownLeft); }


	/**
	 * Commence un coup d'épée si l'épée est rechargée
	 * @return true si le coup a commencé
	 */
	public boolean startSwing() {
		if (!canSwing()) return false;
		activeTicksLeft = MELEE_ACTIVE_TICKS;
		meleeCooldownLeft = MELEE_COOLDOWN_TICKS;
		HIT_THIS_SWING.clear();
		return true;
	}

	/**
	 * Déclenche le temps de recharge de l'arme de tir si elle est prête
	 * @return true si le tir est autorisé
	 */
	public boolean tryShoot() {
		if (!canShoot()) return false;
		shootCooldownLeft = SHOOT_COOLDOWN_TICKS;
		return true;
	}

	/**
	 * Enregistre qu'une cible a été touchée par le coup en cours
	 * @param targetId Identifiant de la cible
	 * @return true si la cible n'avait pas encore été touchée par ce coup
	 */
	public boolean registerHit(int targetId) {
		return isSwinging() && HIT_THIS_SWING.add(targetId);
	}

	/**
	 * Fait avancer les temps de recharge d'un pas de simulation (à appeler en fin de pas)
	 */
	public void update() {
		if (activeTicksLeft > 0) activeTicksLeft--;
		if (meleeCooldownLeft > 0) meleeCooldownLeft--;
		if (shootCooldownLeft > 0) shootCooldownLeft--;
	}

	/**
	 * Annule le coup d'épée en cours
	 */
	public void cancelSwing() {
		activeTicksLeft = 0;
		HIT_THIS_SWING.clear();
	}

	/**
	 * Vérifie si une cible est à portée d'un coup d'épée
	 * @param owner Joueur qui frappe
	 * @param target Cible
	 * @return true si la cible est assez proche et devant le joueur
	 */
	public static boolean isInReach(Player owner, Player target) {
		float dx = target.getX() - owner.getX(), dy = target.getY() - owner.getY();
		float distance = (float)Math.sqrt(dx * dx + dy * dy);

		if (distance > MELEE_RANGE) return false;
		if (distance < PLAYER_RADIUS_X) return true;
		return Direction.angleTo(owner.getViewDirection(), dx, dy) <= MELEE_HALF_ANGLE;
	}
}
