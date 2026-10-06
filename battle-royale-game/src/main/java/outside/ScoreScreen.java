package outside;

import inside.Player;
import inside.Board;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class ScoreScreen {

	private JFrame f = new JFrame();
	private JPanel p = new JPanel();
	private Board b;
	private Player[] ScoreJoueurs = new Player[100];

	public ScoreScreen(Board c) {
		f.setSize(1400, 800);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		p.setLayout(new GridLayout(10, 5));
		b = c;
	}
	
	// Tri les joueurs par score
	public void sortByScore() {
		System.arraycopy(b.getDEADS(), 0, ScoreJoueurs, 0, 100);

		for (int i = 0; i < ScoreJoueurs.length - 1; i++) {
			int maxIndex = i;

			for (int j = i + 1; j < ScoreJoueurs.length; j++) {
				if (ScoreJoueurs[j].getVictims().size() > ScoreJoueurs[maxIndex].getVictims().size()) {
					maxIndex = j;
				}
			}
			if (maxIndex != i) {
				Player temp = ScoreJoueurs[i];
				ScoreJoueurs[i] = ScoreJoueurs[maxIndex];
				ScoreJoueurs[maxIndex] = temp;
			}
		}
	}

	public void afficheScore() {
		sortByScore();
		try {
			int idgagnant = b.getPLAYERS_IDS().get(0); // le joueur gagnant
			int nbVictimes = b.getPLAYERS()[idgagnant].getVictims().size();
			p.add(new JLabel(b.getPLAYERS()[idgagnant].toString()+ "  " + nbVictimes));
			for(int i = 0; i < ScoreJoueurs.length;i++) {
			    nbVictimes = ScoreJoueurs[i].getVictims().size();
			    p.add(new JLabel(ScoreJoueurs[i].toString()+ "  " + nbVictimes));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		f.add(p);
		f.setVisible(true);

	}

	public static void main(String[] args) {
	
	}

}
