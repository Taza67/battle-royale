package communication.game;

/**
 * Fournit au lien avec le jeu les actions à transmettre à chaque échange
 * @author mourtaza
 *
 */
@FunctionalInterface
public interface ActionSource {
	/**
	 * Retire les actions en attente et les encode au format binaire du protocole
	 * @param nowMillis Instant courant (horloge monotone, en millisecondes)
	 * @return Octets des actions, éventuellement vide
	 */
	byte[] drainActions(long nowMillis);
}
