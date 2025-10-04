package yugioh.core;

import yugioh.model.Card;
import yugioh.model.CardPosition;
import yugioh.model.CardSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Duel {

    private final List<Card> playerDeck;
    private final List<Card> aiDeck;
    private final BattleListener listener;
    private final Random random = new Random();

    private final List<Card> playerAvailable;
    private final List<Card> aiAvailable;

    private int playerScore;
    private int aiScore;
    private boolean playerTurn;
    private boolean active;

    public Duel(List<Card> playerDeck, List<Card> aiDeck, BattleListener listener) {
        this.playerDeck = new ArrayList<>(Objects.requireNonNull(playerDeck));
        this.aiDeck = new ArrayList<>(Objects.requireNonNull(aiDeck));
        this.listener = Objects.requireNonNull(listener);
        this.playerAvailable = new ArrayList<>(this.playerDeck);
        this.aiAvailable = new ArrayList<>(this.aiDeck);
    }

    public void start() {
        if (playerDeck.size() < 3 || aiDeck.size() < 3) {
            listener.onError("Cada duelista debe tener al menos 3 cartas para iniciar", null);
            return;
        }
        this.playerScore = 0;
        this.aiScore = 0;
        this.active = true;
        this.playerTurn = random.nextBoolean();
        listener.onScoreChanged(playerScore, aiScore);
        listener.onDuelStarted(playerTurn ? "Jugador" : "IA");
    }

    public boolean isActive() {
        return active;
    }

    public int getPlayerScore() {
        return playerScore;
    }

    public int getAiScore() {
        return aiScore;
    }

    public List<Card> getPlayerAvailable() {
        return Collections.unmodifiableList(playerAvailable);
    }

    public synchronized void playRound(CardSelection playerSelection) {
        if (!active) {
            listener.onError("El duelo no está activo", null);
            return;
        }
        if (playerSelection == null || playerSelection.getCard() == null) {
            listener.onError("Debes seleccionar una carta válida", null);
            return;
        }
        if (!playerAvailable.contains(playerSelection.getCard())) {
            listener.onError("Esa carta ya fue usada en el duelo", null);
            return;
        }
        Card aiCard = chooseAiCard();
        if (aiCard == null) {
            listener.onError("La IA no tiene cartas disponibles", null);
            return;
        }
        CardPosition aiPosition = random.nextBoolean() ? CardPosition.ATTACK : CardPosition.DEFENSE;
        CardSelection aiSelection = new CardSelection(aiCard, aiPosition);

        boolean playerIsAttacker = playerTurn;
        int outcome = resolveBattle(playerSelection, aiSelection, playerIsAttacker);

        if (outcome > 0) {
            playerScore++;
            listener.onTurnResolved(playerSelection, aiSelection, playerIsAttacker ? "Jugador" : "IA", "Jugador");
        } else if (outcome < 0) {
            aiScore++;
            listener.onTurnResolved(playerSelection, aiSelection, playerIsAttacker ? "Jugador" : "IA", "IA");
        } else {
            listener.onTurnResolved(playerSelection, aiSelection, playerIsAttacker ? "Jugador" : "IA", "Empate");
        }

        listener.onScoreChanged(playerScore, aiScore);

        playerAvailable.remove(playerSelection.getCard());
        aiAvailable.remove(aiCard);

        if (playerScore >= 2 || aiScore >= 2 || playerAvailable.isEmpty() || aiAvailable.isEmpty()) {
            endDuel();
        } else {
            playerTurn = !playerTurn;
        }
    }

    private void endDuel() {
        this.active = false;
        String winner;
        if (playerScore > aiScore) {
            winner = "Jugador";
        } else if (aiScore > playerScore) {
            winner = "IA";
        } else {
            winner = "Empate";
        }
        listener.onDuelEnded(winner);
    }

    private Card chooseAiCard() {
        if (aiAvailable.isEmpty()) {
            return null;
        }
        int idx = random.nextInt(aiAvailable.size());
        return aiAvailable.get(idx);
    }

    private int resolveBattle(CardSelection playerSelection, CardSelection aiSelection, boolean playerIsAttacker) {
        CardSelection attacker = playerIsAttacker ? playerSelection : aiSelection;
        CardSelection defender = playerIsAttacker ? aiSelection : playerSelection;

        int attackerValue = attacker.getPosition() == CardPosition.ATTACK ? attacker.getCard().getAtk() : attacker.getCard().getDef();
        int defenderValue;
        if (defender.getPosition() == CardPosition.ATTACK) {
            defenderValue = defender.getCard().getAtk();
        } else {
            defenderValue = defender.getCard().getDef();
        }

        if (attacker.getPosition() == CardPosition.DEFENSE && defender.getPosition() == CardPosition.DEFENSE) {
            return 0;
        }

        int comparison = Integer.compare(attackerValue, defenderValue);
        if (comparison > 0) {
            return playerIsAttacker ? 1 : -1;
        }
        if (comparison < 0) {
            return playerIsAttacker ? -1 : 1;
        }
        return 0;
    }
}
