package inside;

import java.util.ArrayList;
import java.util.List;

public class TSandbox extends Thread implements IConfig {
	private final Board BOARD;
	private final Player[] PLAYERS;
	private final List<Integer> PLAYERS_IDS;
	private final List<Bullet> BULLETS;
	
	
	// Constructeurs
	public TSandbox(Board b) {
		BOARD = b;
		PLAYERS = b.getPLAYERS();
		PLAYERS_IDS = b.getPLAYERS_IDS();
		BULLETS = b.getBULLETS();
	}
	
	
	public void run() {
		int sandboxTimer = 0;
		
		System.out.println("- Mode sandbox démarré");
		do {
			boolean isChanged = false;

			List<Bullet> bulletsToDestroy = new ArrayList<>();
			// Déplacement des balles et dommages des balles
			for (Bullet b : BULLETS) {
				if (!b.stepAheadBullet())
					bulletsToDestroy.add(b);
				else if (b.makeContact(true))
					bulletsToDestroy.add(b);
			}

			isChanged = BULLETS.size() > 0;

			// État des joueurs
			for (Integer id : PLAYERS_IDS) {

				// Le joueur rengaine son arme
				if (PLAYERS[id].getWeapon().getIsDrawn()) {
					isChanged = true;
					PLAYERS[id].holsterWeapon();
				}
			}

			if (isChanged)
				BOARD.setIsWindowDirty(true);

			// On détruit les balles
			for (Bullet b : bulletsToDestroy)
				b.destroy();

			// Délai entre chaque traitement
			try {
				Thread.sleep(PLAYERS_CHECKING);
			} catch (InterruptedException e) {
				break;
			}
			if (sandboxTimer > SANDBOX_DELAY) {
				for (int i = 0; i < BULLETS.size(); i++)
					BULLETS.get(i).destroy();
				break;
			}
			sandboxTimer++;
		} while (!isInterrupted());
		
		// Démarre le jeu
		synchronized (BOARD) {
			BOARD.notifyAll();
		}
	}
}
