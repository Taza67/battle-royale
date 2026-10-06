package communication.message;

/**
 * Exception levée lorsqu'un message reçu d'un client web est invalide
 * @author mourtaza
 *
 */
public class InvalidMessageException extends Exception {
	/**
	 * Type du message s'il a pu être lu, null sinon
	 */
	private final String type;

	/**
	 * Construit l'exception
	 * @param reason Raison du refus
	 */
	public InvalidMessageException(String reason) {
		this(null, reason, null);
	}

	/**
	 * Construit l'exception à partir d'une erreur sous-jacente
	 * @param reason Raison du refus
	 * @param cause Erreur d'origine
	 */
	public InvalidMessageException(String reason, Throwable cause) {
		this(null, reason, cause);
	}

	/**
	 * Construit l'exception pour un message dont le type est connu
	 * @param type Type du message, null s'il n'a pas pu être lu
	 * @param reason Raison du refus
	 * @param cause Erreur d'origine, éventuellement null
	 */
	public InvalidMessageException(String type, String reason, Throwable cause) {
		super(reason, cause);
		this.type = type;
	}

	/**
	 * Retourne le type du message invalide
	 * @return Type reçu, ou null si le message n'a pas de type lisible
	 */
	public String getType() { return type; }
}
