package communication.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientMessageParserTest {
	@Test
	void parsesJoinAndTrimsPseudo() throws Exception {
		ClientMessage message = ClientMessageParser.parse("{\"type\":\"join\",\"pseudo\":\"  Taza \"}");
		assertEquals(new ClientMessage.Join("Taza"), message);
	}

	@Test
	void acceptsSixteenCharactersAndCountsCodePoints() throws Exception {
		assertEquals(new ClientMessage.Join("abcdefghijklmnop"),
			ClientMessageParser.parse("{\"type\":\"join\",\"pseudo\":\"abcdefghijklmnop\"}"));

		String emojis = "\uD83D\uDE00".repeat(16);
		assertEquals(new ClientMessage.Join(emojis),
			ClientMessageParser.parse("{\"type\":\"join\",\"pseudo\":\"" + emojis + "\"}"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"type\":\"join\",\"pseudo\":\"abcdefghijklmnopq\"}",
		"{\"type\":\"join\",\"pseudo\":\"\"}",
		"{\"type\":\"join\",\"pseudo\":\"    \"}",
		"{\"type\":\"join\",\"pseudo\":\"ta\\u0000za\"}",
		"{\"type\":\"join\",\"pseudo\":\"ta\\nza\"}",
		"{\"type\":\"join\",\"pseudo\":\"ta\\u200Bza\"}",
		"{\"type\":\"join\",\"pseudo\":42}",
		"{\"type\":\"join\",\"pseudo\":null}",
		"{\"type\":\"join\"}"
	})
	void rejectsInvalidPseudos(String json) {
		assertThrows(InvalidMessageException.class, () -> ClientMessageParser.parse(json));
	}

	@Test
	void parsesMoveWithinBounds() throws Exception {
		assertEquals(new ClientMessage.Move(0, 0), ClientMessageParser.parse("{\"type\":\"move\",\"direction\":0,\"speed\":0}"));
		assertEquals(new ClientMessage.Move(7, 4), ClientMessageParser.parse("{\"type\":\"move\",\"direction\":7,\"speed\":4}"));
		assertEquals(new ClientMessage.Move(3, 2), ClientMessageParser.parse("{\"speed\":2.0,\"direction\":3,\"type\":\"move\"}"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"type\":\"move\",\"direction\":8,\"speed\":1}",
		"{\"type\":\"move\",\"direction\":-1,\"speed\":1}",
		"{\"type\":\"move\",\"direction\":1,\"speed\":5}",
		"{\"type\":\"move\",\"direction\":1,\"speed\":-1}",
		"{\"type\":\"move\",\"direction\":1.5,\"speed\":1}",
		"{\"type\":\"move\",\"direction\":\"1\",\"speed\":1}",
		"{\"type\":\"move\",\"direction\":1}",
		"{\"type\":\"move\",\"direction\":1,\"speed\":1e400}",
		"{\"type\":\"move\",\"direction\":[1],\"speed\":1}"
	})
	void rejectsInvalidMoves(String json) {
		assertThrows(InvalidMessageException.class, () -> ClientMessageParser.parse(json));
	}

	@Test
	void parsesAttackForms() throws Exception {
		assertEquals(new ClientMessage.Attack(1), ClientMessageParser.parse("{\"type\":\"attack\",\"form\":1}"));
		assertEquals(new ClientMessage.Attack(2), ClientMessageParser.parse("{\"type\":\"attack\",\"form\":2}"));
		assertThrows(InvalidMessageException.class, () -> ClientMessageParser.parse("{\"type\":\"attack\",\"form\":0}"));
		assertThrows(InvalidMessageException.class, () -> ClientMessageParser.parse("{\"type\":\"attack\",\"form\":3}"));
	}

	@Test
	void parsesAdminMessages() throws Exception {
		assertEquals(new ClientMessage.AdminJoin("secret"),
			ClientMessageParser.parse("{\"type\":\"admin-join\",\"password\":\"secret\"}"));
		assertEquals(new ClientMessage.AdminJoin(""), ClientMessageParser.parse("{\"type\":\"admin-join\"}"));
		assertEquals(new ClientMessage.AdminJoin(""), ClientMessageParser.parse("{\"type\":\"admin-join\",\"password\":null}"));

		for (ClientMessage.Command command : ClientMessage.Command.values())
			assertEquals(new ClientMessage.AdminCommand(command), ClientMessageParser.parse(
				"{\"type\":\"admin-command\",\"command\":\"" + command.wireName() + "\"}"));

		assertThrows(InvalidMessageException.class,
			() -> ClientMessageParser.parse("{\"type\":\"admin-command\",\"command\":\"debut\"}"));
		assertThrows(InvalidMessageException.class,
			() -> ClientMessageParser.parse("{\"type\":\"admin-command\",\"command\":\"START\"}"));
	}

	@Test
	void hidesPasswordInLogs() {
		assertEquals("AdminJoin[password=***]", new ClientMessage.AdminJoin("secret").toString());
	}

	@Test
	void reportsUnknownTypesSeparately() {
		UnknownMessageTypeException e = assertThrows(UnknownMessageTypeException.class,
			() -> ClientMessageParser.parse("{\"type\":\"informations\",\"pseudo\":\"admin\"}"));
		assertEquals("informations", e.getType());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"",
		"admin-stop",
		"{\"type\":\"join\",\"pseudo\":\"Taza\"",
		"{type:\"join\",pseudo:\"Taza\"}",
		"{\"type\":\"join\",\"pseudo\":\"Taza\"} {}",
		"[\"join\"]",
		"\"join\"",
		"{\"pseudo\":\"Taza\"}",
		"{\"type\":3}",
		"null"
	})
	void rejectsMalformedMessages(String text) {
		InvalidMessageException e = assertThrows(InvalidMessageException.class, () -> ClientMessageParser.parse(text));
		assertEquals(InvalidMessageException.class, e.getClass());
	}

	@Test
	void rejectsNullText() {
		assertInstanceOf(InvalidMessageException.class,
			assertThrows(Exception.class, () -> ClientMessageParser.parse(null)));
	}

	@Test
	void reportsTheTypeOfInvalidMessages() {
		InvalidMessageException join = assertThrows(InvalidMessageException.class,
			() -> ClientMessageParser.parse("{\"type\":\"join\",\"pseudo\":\"\"}"));
		assertEquals("join", join.getType());
		assertEquals("pseudo vide", join.getMessage());

		InvalidMessageException move = assertThrows(InvalidMessageException.class,
			() -> ClientMessageParser.parse("{\"type\":\"move\",\"direction\":1}"));
		assertEquals("move", move.getType());

		assertEquals("danse", assertThrows(UnknownMessageTypeException.class,
			() -> ClientMessageParser.parse("{\"type\":\"danse\"}")).getType());
		assertNull(assertThrows(InvalidMessageException.class, () -> ClientMessageParser.parse("[]")).getType());
	}
}
