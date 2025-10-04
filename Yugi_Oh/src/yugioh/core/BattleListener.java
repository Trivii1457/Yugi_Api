package yugioh.core;

import yugioh.model.CardSelection;

public interface BattleListener {
    void onDuelStarted(String startingPlayer);

    void onTurnResolved(CardSelection playerSelection, CardSelection aiSelection, String attacker, String roundWinner);

    void onScoreChanged(int playerScore, int aiScore);

    void onDuelEnded(String winner);

    void onError(String message, Throwable throwable);

    /**
     * Called when the duel requires the player to select a replacement monster because one of theirs was defeated.
     * @param playerSide true if replacement is required from the human player, false if AI (unused currently)
     */
    void onReplacementRequested(boolean playerSide);
}
