package outside.graphic;

/**
 * Polices du jeu (Audiowide et Press Start 2P, licence SIL OFL, copiées depuis le serveur web)
 * @author mourtaza
 */
public class Fonts implements AutoCloseable {
	private static final String AUDIOWIDE = "fonts/Audiowide/Audiowide-Regular.ttf";
	private static final String PRESS_START = "fonts/PressStart2P/PressStart2P-Regular.ttf";

	/** Pseudos au-dessus des joueurs */
	public final Font LABEL;
	/** Textes courants du HUD */
	public final Font SMALL;
	/** Textes mis en avant */
	public final Font MEDIUM;
	/** Titres des bandeaux */
	public final Font LARGE;
	/** Grands titres (pause, fin de partie) */
	public final Font TITLE;

	/**
	 * Charge les polices (nécessite un contexte OpenGL courant)
	 */
	public Fonts() {
		LABEL = new Font(AUDIOWIDE, 11);
		SMALL = new Font(AUDIOWIDE, 15);
		MEDIUM = new Font(AUDIOWIDE, 21);
		LARGE = new Font(AUDIOWIDE, 38);
		TITLE = new Font(PRESS_START, 40);
	}

	@Override
	public void close() {
		LABEL.close();
		SMALL.close();
		MEDIUM.close();
		LARGE.close();
		TITLE.close();
	}
}
