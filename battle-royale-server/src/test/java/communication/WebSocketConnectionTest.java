package communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.SocketTimeoutException;
import java.util.List;

import javax.websocket.CloseReason;
import javax.websocket.SendResult;

import org.apache.tomcat.websocket.Constants;
import org.junit.jupiter.api.Test;

class WebSocketConnectionTest {
	private final FakeSession fake = new FakeSession("1");
	private final WebSocketConnection connection = new WebSocketConnection(fake.session);

	@Test
	void boundsTheDurationOfEverySend() {
		assertEquals(WebSocketConnection.SEND_TIMEOUT_MILLIS, fake.sendTimeout());
		assertEquals(WebSocketConnection.SEND_TIMEOUT_MILLIS, fake.userProperties().get(Constants.BLOCKING_SEND_TIMEOUT_PROPERTY));
	}

	@Test
	void sendsInOrderAndReplacesPendingStates() throws Exception {
		connection.send("a");
		fake.awaitSent(1, 1000);
		connection.sendLatest("s1");
		connection.send("b");
		connection.sendLatest("s2");
		for (int i = 0; i < 3; i++) {
			fake.complete(new SendResult());
			if (i < 2)
				fake.awaitSent(i + 2, 1000);
		}
		Thread.sleep(100);
		assertEquals(List.of("a", "b", "s2"), fake.sent());
		assertTrue(connection.isOpen());
	}

	@Test
	void closesTheSessionAtOnceWhenTheQueueOverflowsDuringAStalledSend() throws Exception {
		connection.send("bloqué");
		fake.awaitSent(1, 1000);
		for (int i = 0; i <= WebSocketConnection.MAX_QUEUED_MESSAGES; i++)
			connection.send("m" + i);

		CloseReason reason = fake.awaitClose(1000);
		assertEquals(CloseReason.CloseCodes.TRY_AGAIN_LATER, reason.getCloseCode());
		assertEquals(WebSocketConnection.TOO_SLOW, reason.getReasonPhrase());
		assertFalse(connection.isOpen());
		assertEquals(List.of("bloqué"), fake.sent());
	}

	@Test
	void closesTheSessionInsteadOfDroppingMessagesAfterAFailedSend() throws Exception {
		connection.send("game");
		fake.awaitSent(1, 1000);
		connection.send("end");
		fake.complete(new SendResult(new java.io.IOException("connexion réinitialisée")));

		CloseReason reason = fake.awaitClose(1000);
		assertEquals(CloseReason.CloseCodes.UNEXPECTED_CONDITION, reason.getCloseCode());
		assertFalse(connection.isOpen());
		connection.send("ack");
		Thread.sleep(100);
		assertEquals(List.of("game"), fake.sent());
	}

	@Test
	void closesTheSessionAfterASendTimeout() throws Exception {
		connection.sendLatest("state");
		fake.awaitSent(1, 1000);
		fake.complete(new SendResult(new SocketTimeoutException("délai dépassé")));
		assertEquals(CloseReason.CloseCodes.TRY_AGAIN_LATER, fake.awaitClose(1000).getCloseCode());
		assertFalse(connection.isOpen());
	}

	@Test
	void closesAfterTheQueuedMessagesOnRequest() throws Exception {
		connection.send("rejected");
		connection.close("Session reprise");
		fake.awaitSent(1, 1000);
		assertFalse(connection.isOpen());
		assertFalse(fake.isClosed(), "le message en cours part avant la fermeture");
		fake.complete(new SendResult());
		CloseReason reason = fake.awaitClose(1000);
		assertEquals(CloseReason.CloseCodes.NORMAL_CLOSURE, reason.getCloseCode());
		assertEquals("Session reprise", reason.getReasonPhrase());
	}

	@Test
	void pingsAndClosesTheSessionWhenThePingFails() throws Exception {
		connection.ping();
		long deadline = System.currentTimeMillis() + 1000;
		while (fake.pings() == 0 && System.currentTimeMillis() < deadline)
			Thread.sleep(10);
		assertEquals(1, fake.pings());

		fake.failPings();
		Thread.sleep(50);
		connection.ping();
		assertEquals(CloseReason.CloseCodes.UNEXPECTED_CONDITION, fake.awaitClose(1000).getCloseCode());
		assertFalse(connection.isOpen());
	}
}
