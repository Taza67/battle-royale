package outside.audio;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

import java.nio.ShortBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import inside.GameEvent;

/**
 * Effets sonores facultatifs, synthétisés au lancement et joués avec OpenAL.
 * Si aucun périphérique audio n'est disponible, le son est simplement désactivé.
 * @author mourtaza
 */
public class AudioUtilities implements AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(AudioUtilities.class.getName());
	private static final int RATE = 22050;
	private static final int SOURCES_NUMBER = 16;

	/**
	 * Sons disponibles
	 */
	public enum Sound { SHOT, SWING, HIT, BLOCKED, ELIMINATION, ZONE, START, END }

	private long device, context;
	private boolean enabled;
	private final EnumMap<Sound, Integer> BUFFERS = new EnumMap<>(Sound.class);
	private final int[] SOURCES = new int[SOURCES_NUMBER];
	private int nextSource;


	/**
	 * Ouvre le périphérique audio par défaut, sans erreur s'il est absent
	 * @param wanted false pour désactiver le son
	 */
	public AudioUtilities(boolean wanted) {
		if (!wanted) return;
		try {
			device = alcOpenDevice((java.nio.ByteBuffer)null);
			if (device == 0) {
				LOGGER.info("Aucun périphérique audio : son désactivé");
				return;
			}
			ALCCapabilities caps = ALC.createCapabilities(device);
			context = alcCreateContext(device, (java.nio.IntBuffer)null);
			if (context == 0 || !alcMakeContextCurrent(context)) {
				close();
				return;
			}
			AL.createCapabilities(caps);
			for (int i = 0; i < SOURCES_NUMBER; i++) SOURCES[i] = alGenSources();
			for (Sound s : Sound.values()) BUFFERS.put(s, createBuffer(synthesize(s)));
			enabled = alGetError() == AL_NO_ERROR;
		} catch (Throwable t) {
			LOGGER.log(Level.INFO, "Son indisponible : " + t.getMessage());
			enabled = false;
		}
	}

	/**
	 * Indique si le son est actif
	 * @return true si le son est actif
	 */
	public boolean isEnabled() { return enabled; }

	/**
	 * Joue les sons correspondant à des événements de la simulation
	 * @param events Événements
	 * @param localId Identifiant du joueur local
	 */
	public void play(List<GameEvent> events, int localId) {
		if (!enabled) return;
		int shots = 0;
		for (GameEvent e : events) {
			switch (e.type()) {
			case SHOT -> { if (shots++ < 2) play(Sound.SHOT, e.actorId() == localId ? 0.5f : 0.22f); }
			case SWING -> play(Sound.SWING, e.actorId() == localId ? 0.45f : 0.15f);
			case HIT -> { if (e.amount() > 0) play(Sound.HIT, e.targetId() == localId || e.actorId() == localId ? 0.6f : 0.25f); }
			case BULLET_BLOCKED -> play(Sound.BLOCKED, 0.12f);
			case ELIMINATION -> play(Sound.ELIMINATION, 0.5f);
			case ZONE_SHRINKING -> play(Sound.ZONE, 0.6f);
			case BATTLE_STARTED -> play(Sound.START, 0.6f);
			case GAME_OVER -> play(Sound.END, 0.7f);
			default -> {}
			}
		}
	}

	/**
	 * Joue un son
	 * @param s Son
	 * @param gain Volume
	 */
	public void play(Sound s, float gain) {
		if (!enabled) return;
		int source = SOURCES[nextSource];
		nextSource = (nextSource + 1) % SOURCES_NUMBER;
		alSourceStop(source);
		alSourcei(source, AL_BUFFER, BUFFERS.get(s));
		alSourcef(source, AL_GAIN, gain);
		alSourcePlay(source);
	}

	/**
	 * Crée un tampon OpenAL
	 * @param samples Échantillons
	 * @return Identifiant du tampon
	 */
	private static int createBuffer(short[] samples) {
		ShortBuffer data = BufferUtils.createShortBuffer(samples.length);
		data.put(samples).flip();
		int buffer = alGenBuffers();
		alBufferData(buffer, AL_FORMAT_MONO16, data, RATE);
		return buffer;
	}

	/**
	 * Synthétise un son
	 * @param s Son
	 * @return Échantillons 16 bits
	 */
	static short[] synthesize(Sound s) {
		Random random = new Random(s.ordinal());
		float duration = switch (s) {
			case SHOT -> 0.14f; case SWING -> 0.18f; case HIT -> 0.12f; case BLOCKED -> 0.08f;
			case ELIMINATION -> 0.5f; case ZONE -> 0.9f; case START -> 0.6f; case END -> 1.2f;
		};
		int n = (int)(duration * RATE);
		short[] out = new short[n];
		double phase = 0;
		float low = 0;

		for (int i = 0; i < n; i++) {
			float t = i / (float)RATE, k = i / (float)n, v;
			float noise = random.nextFloat() * 2 - 1;
			switch (s) {
			case SHOT:
				phase += 2 * Math.PI * (900 - 700 * k) / RATE;
				v = (0.5f * (float)Math.sin(phase) + 0.5f * noise) * (float)Math.exp(-k * 6);
				break;
			case SWING:
				low += (noise - low) * (0.05f + 0.25f * (float)Math.sin(Math.PI * k));
				v = low * 2.2f * (float)Math.sin(Math.PI * k);
				break;
			case HIT:
				phase += 2 * Math.PI * (180 - 100 * k) / RATE;
				v = ((float)Math.sin(phase) * 0.8f + noise * 0.3f) * (float)Math.exp(-k * 5);
				break;
			case BLOCKED:
				low += (noise - low) * 0.3f;
				v = low * (float)Math.exp(-k * 7);
				break;
			case ELIMINATION:
				phase += 2 * Math.PI * (520 * (1 - 0.6f * k)) / RATE;
				v = (float)Math.signum(Math.sin(phase)) * 0.35f * (1 - k);
				break;
			case ZONE:
				phase += 2 * Math.PI * (440 + 220 * (float)Math.sin(t * 2 * Math.PI * 3)) / RATE;
				v = (float)Math.sin(phase) * 0.5f * Math.min(1, (1 - k) * 4) * Math.min(1, k * 20);
				break;
			case START:
				phase += 2 * Math.PI * (k < 0.5f ? 523.25 : 783.99) / RATE;
				v = (float)Math.sin(phase) * 0.5f * (float)Math.exp(-((k * 2) % 1) * 3);
				break;
			default:
				double f = k < 0.33f ? 523.25 : k < 0.66f ? 659.25 : 783.99;
				phase += 2 * Math.PI * f / RATE;
				v = ((float)Math.sin(phase) + 0.3f * (float)Math.sin(phase * 2)) * 0.4f * (1 - k);
			}
			out[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v * 26000));
		}
		return out;
	}

	/**
	 * Libère les ressources audio
	 */
	@Override
	public void close() {
		try {
			if (enabled) {
				for (int source : SOURCES) alDeleteSources(source);
				for (int buffer : BUFFERS.values()) alDeleteBuffers(buffer);
			}
			if (context != 0) {
				alcMakeContextCurrent(0);
				alcDestroyContext(context);
			}
			if (device != 0) alcCloseDevice(device);
		} catch (Throwable t) {
			LOGGER.log(Level.FINE, "Erreur à la fermeture du son", t);
		}
		enabled = false;
		context = 0;
		device = 0;
	}
}
