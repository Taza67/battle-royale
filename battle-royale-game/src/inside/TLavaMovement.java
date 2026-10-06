package inside;

public class TLavaMovement extends Thread implements IConfig {
	private final Map MAP;
	private final Board BOARD;


	// Constructeurs
	public TLavaMovement(Board b, Map m) {
		MAP = m;
		BOARD = b;
	}

	@Override
	public void run() {
		synchronized (BOARD) {
			try {
				BOARD.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("- Mode lave démarré");
		
		// La lave ne commence pas à bouger directement
		try {
			Thread.sleep(TIME_BEFORE_LAVA_MOVEMENT_START);
		} catch (InterruptedException e) {
			return;
		}
		
		// La lave se déplace par intervalle
		while (!isInterrupted()) {
			int c = 0;
			do {
				float height = MAP.getMapRepresentation().getHeight(),
					  widht = MAP.getMapRepresentation().getWidth();

				// Si le champ de bataille a atteint une taille 0,
				// on ne la réduit plus
				if (height <= 0 || widht <= 0) break;

				// Réduction du champ de bataille
				MAP.reduceMapRepresentation(-0.25f);

				// La fenêtre doit être réaffichée
				BOARD.setIsWindowDirty(true);

				try {
					Thread.sleep(LAVA_MOVEMENT_SPEED);
				} catch (InterruptedException e) {
					break;
				}
				c++;
			} while(c < LAVA_WAVE_TIME && !isInterrupted());

			// Attente entre chaque vague
			try {
				Thread.sleep(TIME_BETWEEN_LAVA_WAVES);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
}
