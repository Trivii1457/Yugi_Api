package yugioh.core;

import yugioh.model.CardSelection;

public interface BattleListener {
    void onDuelStarted(String startingPlayer);

    void onTurnResolved(CardSelection playerSelection, CardSelection aiSelection, String attacker, String roundWinner);

    void onScoreChanged(int playerScore, int aiScore);

    void onDuelEnded(String winner);

    void onError(String message, Throwable throwable);
}
