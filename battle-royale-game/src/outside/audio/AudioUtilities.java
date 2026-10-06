package outside.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.File;
import java.io.IOException;

import org.lwjgl.BufferUtils;

/**
 * Classe implémentant des méthodes utiles à l'aspect audio du jeu
 * @author mourtaza
 *
 */
public class AudioUtilities {
	/**
	 * Initialise OpenAL
	 */
	public static void initOpenAL() {
	    long device = ALC10.alcOpenDevice((ByteBuffer) null);
	    ALCCapabilities deviceCaps = ALC.createCapabilities(device);

	    long context = ALC10.alcCreateContext(device, (IntBuffer) null);
	    ALC10.alcMakeContextCurrent(context);
	    AL.createCapabilities(deviceCaps);
	}
	
	/**
	 * Charge un fichier audio et retourne le buffer correspondant
	 * @param file Chemin vers le fichier à charger
	 */
	public static int loadSound(String file) {
	    int buffer = AL10.alGenBuffers();
	    try {
	        AudioInputStream audioInputStream;
			audioInputStream = AudioSystem.getAudioInputStream(new File(file));
	        AudioFormat audioFormat = audioInputStream.getFormat();
	        int size = (int) (audioFormat.getFrameSize() * audioInputStream.getFrameLength());
	        ByteBuffer data = BufferUtils.createByteBuffer(size);
	        byte[] dataArray = new byte[size];
			audioInputStream.read(dataArray, 0, size);
	        data.put(dataArray);
	        data.flip();
	
	        int format = -1;
	        if (audioFormat.getChannels() == 1) {
	            format = audioFormat.getSampleSizeInBits() == 8 ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16;
	        } else if (audioFormat.getChannels() == 2) {
	            format = audioFormat.getSampleSizeInBits() == 8 ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16;
	        }
	
	        AL10.alBufferData(buffer, format, data, (int) audioFormat.getSampleRate());
	    } catch (UnsupportedAudioFileException | IOException e) {
			e.printStackTrace();
		}
	
	    return buffer;
	}
	
	/**
	 * Lance un morceau de musique
	 * @param buffer Buffer de la musique à charger
	 * @return
	 */
	public static int playSound(int buffer) {
	    int source = AL10.alGenSources();
	    AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
	    AL10.alSourcePlay(source);
	    return source;
	}

}
