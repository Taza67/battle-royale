package communication.session;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import communication.game.ActionSource;
import communication.game.GameLink;

/**
 * Actions des joueurs d'une manche en attente d'envoi au jeu.
 * Le déplacement est une intention : la dernière valeur est envoyée quand elle change,
 * puis renvoyée régulièrement tant qu'elle n'est pas un arrêt, pour ne pas expirer
 * côté jeu (250 ms sans commande). Les attaques sont mises en file.
 * @author mourtaza
 *
 */
public final class PendingActions implements ActionSource {
	/**
	 * Intervalle de renvoi d'une intention de déplacement active, inférieur
	 * aux 250 ms au bout desquelles le jeu arrête un joueur sans nouvelle commande
	 */
	public static final long MOVE_REFRESH_MILLIS = 150;
	/**
	 * Nombre maximal d'attaques en attente par joueur, les suivantes sont ignorées
	 */
	public static final int MAX_QUEUED_ATTACKS = 4;

	/**
	 * Actions en attente d'un joueur
	 */
	private static final class Slot {
		private int direction, speed;
		private int sentDirection, sentSpeed;
		private long lastSent;
		private final ArrayDeque<Byte> attacks = new ArrayDeque<>();
	}

	private final Map<Integer, Slot> slots = new HashMap<>();
	private final int[] order;

	/**
	 * Construit les actions en attente des joueurs d'une manche
	 * @param ids Identifiants des joueurs, dans l'ordre d'envoi
	 */
	public PendingActions(Collection<Integer> ids) {
		order = ids.stream().mapToInt(Integer::intValue).toArray();
		for (int id : order)
			slots.put(id, new Slot());
	}

	/**
	 * Enregistre l'intention de déplacement d'un joueur
	 * @param id Identifiant du joueur
	 * @param direction Direction de 0 à 7
	 * @param speed Vitesse de 0 (arrêt) à 4
	 * @return false si le joueur ne participe pas à la manche
	 */
	public synchronized boolean move(int id, int direction, int speed) {
		Slot slot = slots.get(id);
		if (slot == null)
			return false;
		slot.speed = speed;
		slot.direction = speed == 0 ? 0 : direction;
		return true;
	}

	/**
	 * Ajoute une attaque à la file d'un joueur
	 * @param id Identifiant du joueur
	 * @param form Forme de l'attaque (1 ou 2)
	 * @return false si le joueur ne participe pas à la manche ou si sa file est pleine
	 */
	public synchronized boolean attack(int id, int form) {
		Slot slot = slots.get(id);
		if (slot == null || slot.attacks.size() >= MAX_QUEUED_ATTACKS)
			return false;
		slot.attacks.add((byte) form);
		return true;
	}

	/**
	 * Arrête le déplacement de tous les joueurs et vide les files d'attaques
	 */
	public synchronized void clear() {
		for (Slot slot : slots.values()) {
			slot.direction = 0;
			slot.speed = 0;
			slot.attacks.clear();
		}
	}

	/**
	 * Encode les actions à envoyer : déplacements modifiés ou à rafraîchir, puis attaques en file
	 * @param nowMillis Instant courant en millisecondes
	 * @return Octets des actions
	 */
	@Override
	public synchronized byte[] drainActions(long nowMillis) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int id : order) {
			Slot slot = slots.get(id);
			boolean changed = slot.direction != slot.sentDirection || slot.speed != slot.sentSpeed;
			boolean refresh = slot.speed > 0 && nowMillis - slot.lastSent >= MOVE_REFRESH_MILLIS;
			if (changed || refresh) {
				out.write(id);
				out.write(GameLink.ACTION_MOVE);
				out.write(slot.direction);
				out.write(slot.speed);
				slot.sentDirection = slot.direction;
				slot.sentSpeed = slot.speed;
				slot.lastSent = nowMillis;
			}
			while (!slot.attacks.isEmpty()) {
				out.write(id);
				out.write(GameLink.ACTION_ATTACK);
				out.write(slot.attacks.poll());
			}
		}
		return out.toByteArray();
	}
}
