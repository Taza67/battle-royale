package communication.message;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * Analyse et valide strictement les messages JSON envoyés par les clients web
 * @author mourtaza
 *
 * @see ClientMessage
 */
public final class ClientMessageParser {
	/**
	 * Adaptateur Gson produisant un arbre JSON générique
	 */
	private static final TypeAdapter<JsonElement> TREE = Json.GSON.getAdapter(JsonElement.class);

	private ClientMessageParser() {}

	/**
	 * Analyse un message texte reçu d'un client
	 * @param text Contenu brut du message
	 * @return Message typé et validé
	 * @throws UnknownMessageTypeException si le type n'existe pas dans le protocole
	 * @throws InvalidMessageException si le message est mal formé ou si une valeur est hors limites
	 */
	public static ClientMessage parse(String text) throws InvalidMessageException {
		JsonObject object = readObject(text);
		String type = requireString(object, "type");
		try {
			return parseFields(type, object);
		} catch (UnknownMessageTypeException e) {
			throw e;
		} catch (InvalidMessageException e) {
			throw new InvalidMessageException(type, e.getMessage(), e.getCause());
		}
	}

	/**
	 * Lit les champs d'un message dont le type est connu
	 * @param type Type du message
	 * @param object Objet JSON reçu
	 * @return Message typé et validé
	 * @throws InvalidMessageException si un champ est absent, mal typé ou hors limites
	 */
	private static ClientMessage parseFields(String type, JsonObject object) throws InvalidMessageException {
		switch (type) {
			case "join": {
				String pseudo = validatePseudo(requireString(object, "pseudo"));
				String token = optionalString(object, "token");
				return new ClientMessage.Join(pseudo, token.isEmpty() ? null : token);
			}
			case "move":
				return new ClientMessage.Move(
					requireInt(object, "direction", 0, ClientMessage.MAX_DIRECTION),
					requireInt(object, "speed", 0, ClientMessage.MAX_SPEED));
			case "attack":
				return new ClientMessage.Attack(
					requireInt(object, "form", ClientMessage.FORM_MELEE, ClientMessage.FORM_SHOT));
			case "admin-join":
				return new ClientMessage.AdminJoin(optionalString(object, "password"));
			case "admin-command": {
				String name = requireString(object, "command");
				ClientMessage.Command command = ClientMessage.Command.fromWireName(name);
				if (command == null)
					throw new InvalidMessageException("commande inconnue : " + name);
				return new ClientMessage.AdminCommand(command);
			}
			default:
				throw new UnknownMessageTypeException(type);
		}
	}

	/**
	 * Nettoie et valide un pseudo
	 * @param raw Pseudo reçu
	 * @return Pseudo sans espaces de bord
	 * @throws InvalidMessageException si le pseudo est vide, trop long, contient des caractères
	 *         de contrôle ou des caractères que le jeu ne sait pas afficher (hors Latin-1)
	 */
	public static String validatePseudo(String raw) throws InvalidMessageException {
		String pseudo = raw.strip();
		int length = pseudo.codePointCount(0, pseudo.length());

		if (length == 0)
			throw new InvalidMessageException("pseudo vide");
		if (length > ClientMessage.MAX_PSEUDO_LENGTH)
			throw new InvalidMessageException("pseudo trop long (" + ClientMessage.MAX_PSEUDO_LENGTH + " caractères au plus)");
		if (pseudo.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
			throw new InvalidMessageException("pseudo contenant des caractères interdits");
		if (pseudo.codePoints().anyMatch(c -> c > 0xFF))
			throw new InvalidMessageException("pseudo contenant des caractères non affichables par le jeu");
		return pseudo;
	}

	/**
	 * Lit strictement un objet JSON
	 * @param text Texte reçu
	 * @return Objet JSON
	 * @throws InvalidMessageException si le texte n'est pas un unique objet JSON valide
	 */
	private static JsonObject readObject(String text) throws InvalidMessageException {
		if (text == null)
			throw new InvalidMessageException("message vide");
		try (JsonReader reader = new JsonReader(new StringReader(text))) {
			reader.setStrictness(Strictness.STRICT);
			JsonElement element = TREE.read(reader);
			if (reader.peek() != JsonToken.END_DOCUMENT)
				throw new InvalidMessageException("contenu en trop après l'objet JSON");
			if (element == null || !element.isJsonObject())
				throw new InvalidMessageException("un objet JSON est attendu");
			return element.getAsJsonObject();
		} catch (IOException | JsonParseException | IllegalStateException e) {
			throw new InvalidMessageException("JSON invalide", e);
		} catch (StackOverflowError e) {
			throw new InvalidMessageException("JSON trop profond", e);
		}
	}

	/**
	 * Retourne un champ texte obligatoire
	 * @param object Objet JSON
	 * @param name Nom du champ
	 * @return Valeur du champ
	 * @throws InvalidMessageException si le champ est absent ou n'est pas une chaîne
	 */
	private static String requireString(JsonObject object, String name) throws InvalidMessageException {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull())
			throw new InvalidMessageException("champ " + name + " manquant");
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new InvalidMessageException("champ " + name + " : chaîne attendue");
		return value.getAsString();
	}

	/**
	 * Retourne un champ texte facultatif
	 * @param object Objet JSON
	 * @param name Nom du champ
	 * @return Valeur du champ, ou chaîne vide s'il est absent ou nul
	 * @throws InvalidMessageException si le champ est présent mais n'est pas une chaîne
	 */
	private static String optionalString(JsonObject object, String name) throws InvalidMessageException {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull())
			return "";
		return requireString(object, name);
	}

	/**
	 * Retourne un champ entier obligatoire compris dans un intervalle
	 * @param object Objet JSON
	 * @param name Nom du champ
	 * @param min Valeur minimale incluse
	 * @param max Valeur maximale incluse
	 * @return Valeur du champ
	 * @throws InvalidMessageException si le champ est absent, non entier ou hors limites
	 */
	private static int requireInt(JsonObject object, String name, int min, int max) throws InvalidMessageException {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull())
			throw new InvalidMessageException("champ " + name + " manquant");
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new InvalidMessageException("champ " + name + " : nombre attendu");

		JsonPrimitive primitive = value.getAsJsonPrimitive();
		BigDecimal number;
		try {
			number = primitive.getAsBigDecimal();
		} catch (NumberFormatException e) {
			throw new InvalidMessageException("champ " + name + " : nombre invalide", e);
		}
		if (number.compareTo(BigDecimal.valueOf(min)) < 0 || number.compareTo(BigDecimal.valueOf(max)) > 0)
			throw new InvalidMessageException("champ " + name + " hors limites [" + min + ", " + max + "] : " + number.toPlainString());
		if (number.signum() != 0 && number.stripTrailingZeros().scale() > 0)
			throw new InvalidMessageException("champ " + name + " : entier attendu");
		return number.intValueExact();
	}
}
