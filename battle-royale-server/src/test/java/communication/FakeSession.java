package communication;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.websocket.CloseReason;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;

/**
 * Session WebSocket factice : les envois asynchrones restent en cours jusqu'à ce que
 * le test les termine, ce qui permet de simuler un client bloqué
 */
final class FakeSession {
	private final Map<String, Object> userProperties = new HashMap<>();
	private final List<String> sent = new ArrayList<>();
	private final List<SendHandler> pending = new ArrayList<>();
	private final CompletableFuture<CloseReason> closed = new CompletableFuture<>();
	private volatile long sendTimeout = -1;
	private volatile int pings;
	private volatile boolean failPings;
	private volatile int maxIdleTimeout;
	final Session session;

	FakeSession(String id) {
		RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { RemoteEndpoint.Async.class }, (proxy, method, args) -> {
				switch (method.getName()) {
					case "setSendTimeout":
						sendTimeout = (Long) args[0];
						return null;
					case "getSendTimeout":
						return sendTimeout;
					case "sendText":
						synchronized (this) {
							if (closed.isDone())
								throw new IllegalStateException("session fermée");
							sent.add((String) args[0]);
							pending.add((SendHandler) args[1]);
							notifyAll();
						}
						return null;
					case "sendPing":
						if (failPings)
							throw new java.io.IOException("ping impossible");
						pings++;
						return null;
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
		session = (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Session.class },
			(proxy, method, args) -> {
				switch (method.getName()) {
					case "getId":
						return id;
					case "isOpen":
						return !closed.isDone();
					case "getAsyncRemote":
						return async;
					case "getUserProperties":
						return userProperties;
					case "setMaxTextMessageBufferSize":
					case "addMessageHandler":
						return null;
					case "setMaxIdleTimeout":
						maxIdleTimeout = ((Long) args[0]).intValue();
						return null;
					case "close":
						closed.complete(args == null ? new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "") : (CloseReason) args[0]);
						return null;
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					case "toString":
						return "FakeSession[" + id + "]";
					default:
						throw new UnsupportedOperationException(method.getName());
				}
			});
	}

	long sendTimeout() { return sendTimeout; }
	Map<String, Object> userProperties() { return userProperties; }
	int pings() { return pings; }
	int maxIdleTimeout() { return maxIdleTimeout; }
	void failPings() { failPings = true; }
	boolean isClosed() { return closed.isDone(); }
	CloseReason awaitClose(long millis) throws Exception { return closed.get(millis, TimeUnit.MILLISECONDS); }
	synchronized List<String> sent() { return new ArrayList<>(sent); }

	/** Attend que le nombre de messages envoyés atteigne la valeur donnée */
	synchronized void awaitSent(int count, long millis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + millis;
		while (sent.size() < count) {
			long left = deadline - System.currentTimeMillis();
			if (left <= 0)
				throw new AssertionError("messages envoyés : " + sent);
			wait(left);
		}
	}

	/** Termine le plus ancien envoi en cours */
	void complete(SendResult result) {
		SendHandler handler;
		synchronized (this) {
			handler = pending.remove(0);
		}
		handler.onResult(result);
	}
}
