package communication.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import communication.game.Zone;

class ServerMessageTest {
	@Test
	void serializesWelcomeAndRejected() {
		assertEquals("{\"type\":\"welcome\",\"id\":3,\"pseudo\":\"Taza\",\"state\":\"lobby\"}",
			Json.write(new ServerMessage.Welcome(3, "Taza", "lobby")));
		assertEquals("{\"type\":\"rejected\",\"reason\":\"Pseudo déjà utilisé\"}",
			Json.write(new ServerMessage.Rejected("Pseudo déjà utilisé")));
	}

	@Test
	void keepsNullFieldsRequiredByTheProtocol() {
		assertEquals("{\"type\":\"ack\",\"command\":\"start\",\"ok\":true,\"error\":null}",
			Json.write(ServerMessage.Ack.success("start")));
		assertEquals("{\"type\":\"ack\",\"command\":\"start\",\"ok\":false,\"error\":\"Jeu injoignable\"}",
			Json.write(ServerMessage.Ack.failure("start", "Jeu injoignable")));
		assertEquals("{\"type\":\"end\",\"winner\":null,\"ranking\":[],\"total\":4,\"stopped\":true}",
			Json.write(new ServerMessage.End(null, List.of(), 4, true)));
	}

	@Test
	void serializesStateLikeTheSpecification() {
		ServerMessage.State state = new ServerMessage.State("alive", 87, 100, 640, 360, 2, 0, 5, 8, "battle", 12,
			new Zone(100, 50, 1100, 650), new Zone(300, 120, 900, 560));
		assertEquals("{\"type\":\"state\",\"status\":\"alive\",\"life\":87,\"maxLife\":100,\"x\":640,\"y\":360,"
			+ "\"kills\":2,\"rank\":0,\"alive\":5,\"total\":8,\"phase\":\"battle\",\"secondsLeft\":12,"
			+ "\"zone\":{\"x1\":100,\"y1\":50,\"x2\":1100,\"y2\":650},"
			+ "\"nextZone\":{\"x1\":300,\"y1\":120,\"x2\":900,\"y2\":560},"
			+ "\"map\":{\"width\":1280,\"height\":720}}", Json.write(state));
	}

	@Test
	void serializesEndAndPlayers() {
		assertEquals("{\"type\":\"end\",\"winner\":{\"id\":3,\"pseudo\":\"Taza\"},"
			+ "\"ranking\":[{\"id\":3,\"pseudo\":\"Taza\",\"kills\":2,\"rank\":1}],\"total\":5,\"stopped\":false}",
			Json.write(new ServerMessage.End(new ServerMessage.PlayerRef(3, "Taza"),
				List.of(new ServerMessage.RankingEntry(3, "Taza", 2, 1)), 5, false)));
		assertEquals("{\"type\":\"players\",\"players\":[{\"id\":3,\"pseudo\":\"Taza\",\"connected\":true,"
			+ "\"status\":\"alive\",\"life\":87,\"kills\":2,\"rank\":0}]}",
			Json.write(new ServerMessage.Players(List.of(
				new ServerMessage.PlayerEntry(3, "Taza", true, "alive", 87, 2, 0)))));
		assertEquals("{\"type\":\"admin-welcome\",\"state\":\"paused\"}", Json.write(new ServerMessage.AdminWelcome("paused")));
		assertEquals("{\"type\":\"game\",\"state\":\"over\"}", Json.write(new ServerMessage.Game("over")));
	}

	@Test
	void escapesPseudosSafely() {
		String json = Json.write(new ServerMessage.Welcome(1, "a\"b\\<c>", "lobby"));
		assertEquals("a\"b\\<c>", Json.GSON.fromJson(json, com.google.gson.JsonObject.class).get("pseudo").getAsString());
	}
}
