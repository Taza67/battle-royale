package outside;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import inside.IConfig;
import inside.Player;

public class ConnectionsWindow extends JFrame implements IConfig {
	private static final long serialVersionUID = 8454563759255261416L;
	private JTextPane textPane;
	private String infosUsers;
	private StyledDocument document;


	// Constructeurs
	public ConnectionsWindow() {
		// Crée un JTextPane
        textPane = new JTextPane();
        textPane.setEditable(false);

        // Crée un StyledDocument pour le JTextPane
        document = textPane.getStyledDocument();

		// Ajoute le JTextPane à la fenêtre
        add(new JScrollPane(textPane));

        // Texte
        infosUsers = "";

        // Ajoute le texte formaté dans le StyledDocument
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        document.setParagraphAttributes(0, infosUsers.length(), center, false);
        try {
			document.insertString(0, infosUsers, null);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}

        // Configure la fenêtre
        setTitle("Two Column Text Pane");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_WIDTH);
        setLocationRelativeTo(null);
        setVisible(true);
	}


	// Méthodes
	// Ajoute du texte
    private void appendText(String text, int alignment) {
        try {
            // Ajoute le texte formaté dans le StyledDocument
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setAlignment(attr, alignment);
            document.insertString(document.getLength(), text, attr);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

	// Ajoute un nouvel joueur à la liste des joueurs
	public void addPlayer(Player p) {
		appendText(p.toString() + "\n", StyleConstants.ALIGN_CENTER);

        // Force la mise à jour du JTextPane
        textPane.revalidate();
        textPane.repaint();
	}

	// Ferme la fenêtre
	public void close() {
		this.dispose();
	}
}
