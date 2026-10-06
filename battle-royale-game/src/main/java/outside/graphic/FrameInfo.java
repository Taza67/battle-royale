package outside.graphic;

import inside.BoardSnapshot;

/**
 * Contexte d'une image affichée, partagé par le dessinateur du monde et celui de l'interface
 * @param previous Image précédente de la simulation (peut être null)
 * @param current Image courante
 * @param alpha Avancement entre les deux images (0 à 1)
 * @param time Instant courant (secondes, pour les animations)
 * @param localId Identifiant du joueur local (-1 si aucun)
 * @author mourtaza
 */
public record FrameInfo(BoardSnapshot previous, BoardSnapshot current, float alpha, double time, int localId) {}
