package communication.session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;

import communication.message.Json;

/**
 * Connexion factice qui mémorise les messages reçus
 */
final class FakeConnection implements ClientConnection {
	private final String id;
	private final List<JsonObject> received = new ArrayList<>();
	private int cursor;
	private volatile boolean open = true;
	private volatile String closeReason;

	FakeConnection(String id) {
		this.id = id;
	}

	@Override public String id() { return id; }
	@Override public boolean isOpen() { return open; }
	@Override public void send(String json) { record(json); }
	@Override public void sendLatest(String json) { record(json); }

	@Override
	public void close(String reason) {
		open = false;
		closeReason = reason;
	}

	void drop() { open = false; }
	String closeReason() { return closeReason; }

	private synchronized void record(String json) {
		received.add(Json.GSON.fromJson(json, JsonObject.class));
		notifyAll();
	}

	synchronized List<JsonObject> all() { return new ArrayList<>(received); }

	synchronized List<JsonObject> ofType(String type) {
		return received.stream().filter(m -> type.equals(m.get("type").getAsString())).toList();
	}

	/** Retourne le prochain message du type donné reçu après le dernier message lu */
	synchronized JsonObject next(String type) throws InterruptedException {
		return next(type, 3000);
	}

	synchronized JsonObject next(String type, long timeoutMillis) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (true) {
			while (cursor < received.size()) {
				JsonObject m = received.get(cursor++);
				if (type.equals(m.get("type").getAsString()))
					return m;
			}
			long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
			if (left <= 0)
				throw new AssertionError(id + " : aucun message " + type + " reçu, messages : " + received);
			wait(left);
		}
	}

	/** Vérifie qu'aucun message du type donné n'arrive pendant le délai */
	synchronized boolean receivesNo(String type, long millis) throws InterruptedException {
		int start = received.size();
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		long left;
		while ((left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())) > 0)
			wait(left);
		return received.subList(start, received.size()).stream().noneMatch(m -> type.equals(m.get("type").getAsString()));
	}

	/** Place le curseur après tous les messages déjà reçus */
	synchronized void skipAll() { cursor = received.size(); }
}
