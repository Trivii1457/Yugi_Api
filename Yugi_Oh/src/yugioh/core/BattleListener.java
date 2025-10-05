package yugioh.core;

import yugioh.model.CardSelection;

public interface BattleListener {
    void onDuelStarted(String startingPlayer);

    void onTurnResolved(CardSelection playerSelection, CardSelection aiSelection, String attacker, String roundWinner);

    void onScoreChanged(int playerScore, int aiScore);

    void onDuelEnded(String winner);

    void onError(String message, Throwable throwable);

    /**
     * @param playerSide
     */
    void onReplacementRequested(boolean playerSide);


    void onCardsRemoved(java.util.List<yugioh.model.Card> playerRemoved, java.util.List<yugioh.model.Card> aiRemoved);

    void onAiSelectedFirst(CardSelection aiSelection);
}
