package communication.message;

/**
 * Exception levée lorsqu'un message bien formé porte un type inconnu du protocole
 * @author mourtaza
 *
 */
public class UnknownMessageTypeException extends InvalidMessageException {
	/**
	 * Type reçu
	 */
	private final String type;

	/**
	 * Construit l'exception
	 * @param type Type reçu
	 */
	public UnknownMessageTypeException(String type) {
		super("type inconnu : " + type);
		this.type = type;
	}

	/**
	 * Retourne le type reçu
	 * @return Type inconnu
	 */
	public String getType() { return type; }
}
