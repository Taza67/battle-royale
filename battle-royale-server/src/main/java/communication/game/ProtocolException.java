package communication.game;

import java.io.IOException;

/**
 * Exception levée lorsque le jeu envoie des données qui ne respectent pas le protocole
 * @author mourtaza
 *
 */
public class ProtocolException extends IOException {
	/**
	 * Construit l'exception
	 * @param message Description de l'écart au protocole
	 */
	public ProtocolException(String message) {
		super(message);
	}
}
