package communication.message;

/**
 * Exception levée lorsqu'un message bien formé porte un type inconnu du protocole
 * @author mourtaza
 *
 */
public class UnknownMessageTypeException extends InvalidMessageException {
	/**
	 * Construit l'exception
	 * @param type Type reçu
	 */
	public UnknownMessageTypeException(String type) {
		super(type, "type inconnu : " + type, null);
	}
}
