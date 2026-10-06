package communication.game;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Construit à la main des états binaires conformes au protocole, pour les tests
 */
public final class SnapshotBytes {
	private final ByteArrayOutputStream players = new ByteArrayOutputStream();
	private int phase = 1, alive, total, winner = -1, maxLife = 100, secondsLeft = 30;
	private int[] zone = { 0, 0, 1280, 720 }, nextZone = { 100, 50, 1100, 650 };

	public static SnapshotBytes battle() { return new SnapshotBytes(); }

	public SnapshotBytes phase(int p) { phase = p; return this; }
	public SnapshotBytes counts(int a, int t) { alive = a; total = t; return this; }
	public SnapshotBytes winner(int w) { winner = w; return this; }
	public SnapshotBytes maxLife(int m) { maxLife = m; return this; }
	public SnapshotBytes secondsLeft(int s) { secondsLeft = s; return this; }
	public SnapshotBytes zone(int x1, int y1, int x2, int y2) { zone = new int[] { x1, y1, x2, y2 }; return this; }
	public SnapshotBytes nextZone(int x1, int y1, int x2, int y2) { nextZone = new int[] { x1, y1, x2, y2 }; return this; }

	public SnapshotBytes player(int id, int status, int life, int x, int y, int kills, int rank) {
		DataOutputStream out = new DataOutputStream(players);
		try {
			out.writeByte(id);
			out.writeByte(status);
			out.writeByte(life);
			out.writeShort(x);
			out.writeShort(y);
			out.writeByte(kills);
			out.writeByte(rank);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public byte[] build() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		try {
			out.writeByte(phase);
			out.writeByte(alive);
			out.writeByte(total);
			out.writeByte(winner);
			out.writeByte(maxLife);
			for (int v : zone)
				out.writeShort(v);
			for (int v : nextZone)
				out.writeShort(v);
			out.writeShort(secondsLeft);
			out.write(players.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}
}
