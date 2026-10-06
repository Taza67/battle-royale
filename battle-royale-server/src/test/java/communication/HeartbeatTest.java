package communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;

import javax.websocket.CloseReason;

import org.apache.tomcat.websocket.Constants;
import org.junit.jupiter.api.Test;

import communication.game.GameLinkSettings;
import communication.session.GameSession;

class HeartbeatTest {
	@Test
	void pingsEveryOpenConnectionAndForgetsClosedOnes() throws Exception {
		FakeSession a = new FakeSession("a");
		FakeSession b = new FakeSession("b");
		try (Heartbeat heartbeat = new Heartbeat(50)) {
			ExecutorService dispatcher = WebSocketConnection.newDispatcher();
			heartbeat.register(new WebSocketConnection(a.session, dispatcher));
			heartbeat.register(new WebSocketConnection(b.session, dispatcher));
			Thread.sleep(300);
			assertTrue(a.pings() >= 3 && b.pings() >= 3, a.pings() + " et " + b.pings() + " pings en 300 ms");

			b.session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""));
			Thread.sleep(150);
			int before = b.pings();
			Thread.sleep(150);
			assertEquals(before, b.pings());
			assertEquals(1, heartbeat.size());
		}
	}

	@Test
	void watchesEverySessionFromItsOpening() {
		GameSession game = new GameSession(GameLinkSettings.of("localhost", 38231), null);
		try (Heartbeat heartbeat = new Heartbeat(50)) {
			FakeSession fake = new FakeSession("w");
			WebSocketServer endpoint = new WebSocketServer(game, heartbeat, WebSocketConnection.newDispatcher());
			endpoint.onOpen(fake.session, null);
			assertEquals(WebSocketServer.IDLE_TIMEOUT_MILLIS, fake.maxIdleTimeout());
			assertEquals(WebSocketServer.IDLE_TIMEOUT_MILLIS, fake.userProperties().get(Constants.READ_IDLE_TIMEOUT_MS));
			assertEquals(1, heartbeat.size());
			endpoint.onClose(fake.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""));
			assertEquals(0, heartbeat.size());
		} finally {
			game.close();
		}
	}
}
