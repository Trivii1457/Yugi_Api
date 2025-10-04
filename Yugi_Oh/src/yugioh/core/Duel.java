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
    // lives remaining per player (number of monsters left / life points for the duel)
    private int playerLivesRemaining;
    private int aiLivesRemaining;
    // life maps for each monster (current remaining defense/vida)
    private final java.util.Map<Card, Integer> playerLife = new java.util.LinkedHashMap<>();
    private final java.util.Map<Card, Integer> aiLife = new java.util.LinkedHashMap<>();

    // pending selections (set when player chooses a card)
    private CardSelection pendingPlayerSelection;
    private CardSelection pendingAiSelection;
    // chain state for continued fights
    private Card currentDefender;
    private boolean waitingForPlayerReplacement = false;

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
    // reset lives to 3 at start of each duel
    this.playerLivesRemaining = 3;
    this.aiLivesRemaining = 3;
        listener.onScoreChanged(playerScore, aiScore);
        listener.onDuelStarted(playerTurn ? "Jugador" : "IA");
        // initialize life maps from DEF value (fallback to 100 if 0 or negative)
        playerLife.clear();
        aiLife.clear();
        for (Card c : playerAvailable) {
            int base = c.getDef() > 0 ? c.getDef() : 100;
            int life = base + 1000; // per-card HP used in combat
            playerLife.put(c, life);
        }
        for (Card c : aiAvailable) {
            int base = c.getDef() > 0 ? c.getDef() : 100;
            int life = base + 1000; // per-card HP used in combat
            aiLife.put(c, life);
        }
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

    public List<Card> getAiAvailable() {
        return Collections.unmodifiableList(aiAvailable);
    }

    public int getPlayerLife(Card card) {
        return playerLife.getOrDefault(card, 0);
    }

    public int getAiLife(Card card) {
        return aiLife.getOrDefault(card, 0);
    }

    public int getPlayerRemainingLives() {
        return playerLivesRemaining;
    }

    public int getAiRemainingLives() {
        return aiLivesRemaining;
    }

    public synchronized void playRound(CardSelection playerSelection) {
        // legacy path: not used. New flow uses setPlayerSelection + resolvePendingRound
        setPlayerSelection(playerSelection);
    }

    public synchronized boolean setPlayerSelection(CardSelection playerSelection) {
        if (!active) {
            listener.onError("El duelo no está activo", null);
            return false;
        }
        if (playerSelection == null || playerSelection.getCard() == null) {
            listener.onError("Debes seleccionar una carta válida", null);
            return false;
        }
        if (!playerAvailable.contains(playerSelection.getCard())) {
            listener.onError("Esa carta no está disponible", null);
            return false;
        }
        if (waitingForPlayerReplacement) {
            // player is choosing a replacement after one of their monsters died
            this.pendingPlayerSelection = playerSelection;
            // keep defender unchanged; resolvePendingRound will use pendingPlayerSelection as attacker
            this.waitingForPlayerReplacement = false;
            return true;
        }

        // choose AI card to defend (highest ATK)
        Card aiCard = chooseAiCard();
        if (aiCard == null) {
            listener.onError("La IA no tiene cartas disponibles", null);
            return false;
        }
        CardPosition aiPosition = random.nextBoolean() ? CardPosition.ATTACK : CardPosition.DEFENSE;
        this.pendingPlayerSelection = playerSelection;
        this.pendingAiSelection = new CardSelection(aiCard, aiPosition);
        // initialize chain state for a fresh exchange
        this.currentDefender = aiCard;
        this.waitingForPlayerReplacement = false;
        return true;
    }

    public synchronized CardSelection getPendingPlayerSelection() {
        return pendingPlayerSelection;
    }

    public synchronized CardSelection getPendingAiSelection() {
        return pendingAiSelection;
    }

    
    public synchronized void resolvePendingRound() {
        if (!active) return;
        if (pendingPlayerSelection == null || pendingAiSelection == null) {
            listener.onError("No hay una selección pendiente", null);
            return;
        }
    Card playerCard = pendingPlayerSelection.getCard();

    // track exact cards removed during resolution
    List<Card> removedPlayers = new ArrayList<>();
    List<Card> removedAi = new ArrayList<>();

    // attacker for the round is determined by current playerTurn (randomly chosen at duel start, then alternates)
    boolean playerAttacksFirst = playerTurn;

        if (playerAttacksFirst) {
            // Player chain: attacker is player's selected card
            Card attacker = playerCard;
            while (attacker != null && !aiAvailable.isEmpty()) {
                Card defender = currentDefender != null ? currentDefender : chooseAiCard();
                if (defender == null) break;
                int atk = attacker.getAtk();
                int defLife = aiLife.getOrDefault(defender, Math.max(defender.getDef(), 100));

                if (atk > defLife) {
                    // attacker kills defender
                    aiAvailable.remove(defender);
                    aiLife.remove(defender);
                    removedAi.add(defender);
                    // decrement AI lives remaining
                    if (aiLivesRemaining > 0) aiLivesRemaining--;
                    playerScore++;
                    if (aiAvailable.isEmpty()) break;
                    currentDefender = chooseAiCard();
                } else if (atk < defLife) {
                    // defender survives and loses life equal to atk
                    defLife -= atk;
                    aiLife.put(defender, defLife);
                    break;
                } else {
                    // atk == defLife -> tie: random winner
                    boolean playerWins = random.nextBoolean();
                    if (playerWins) {
                        aiAvailable.remove(defender);
                        aiLife.remove(defender);
                        removedAi.add(defender);
                        playerScore++;
                        if (aiAvailable.isEmpty()) break;
                        currentDefender = chooseAiCard();
                    } else {
                        // attacker dies
                        playerAvailable.remove(attacker);
                        playerLife.remove(attacker);
                        removedPlayers.add(attacker);
                        // decrement player lives remaining
                        if (playerLivesRemaining > 0) playerLivesRemaining--;
                        aiScore++;
                        if (!playerAvailable.isEmpty()) {
                            waitingForPlayerReplacement = true;
                            listener.onReplacementRequested(true);
                        }
                        // stop player's chain
                        break;
                    }
                }
            }
        } else {
            // AI attacks first: use pendingAiSelection as attacker and player's selected card as defender
            Card aiAttacker = pendingAiSelection.getCard();
            while (aiAttacker != null && !playerAvailable.isEmpty()) {
                Card defender = pendingPlayerSelection.getCard();
                if (defender == null) break;
                int atk = aiAttacker.getAtk();
                int defLife = playerLife.getOrDefault(defender, Math.max(defender.getDef(), 100));

                if (atk > defLife) {
                    // attacker kills defender
                    playerAvailable.remove(defender);
                    playerLife.remove(defender);
                    removedPlayers.add(defender);
                    if (playerLivesRemaining > 0) playerLivesRemaining--;
                    aiScore++;
                    if (!playerAvailable.isEmpty()) {
                        waitingForPlayerReplacement = true;
                        listener.onReplacementRequested(true);
                        break;
                    }
                } else if (atk < defLife) {
                    defLife -= atk;
                    playerLife.put(defender, defLife);
                    break;
                } else {
                    boolean aiWins = random.nextBoolean();
                    if (aiWins) {
                        playerAvailable.remove(defender);
                        playerLife.remove(defender);
                        removedPlayers.add(defender);
                        if (playerLivesRemaining > 0) playerLivesRemaining--;
                        aiScore++;
                        if (!playerAvailable.isEmpty()) {
                            waitingForPlayerReplacement = true;
                            listener.onReplacementRequested(true);
                        }
                        break;
                    } else {
                        aiAvailable.remove(aiAttacker);
                        aiLife.remove(aiAttacker);
                        removedAi.add(aiAttacker);
                        playerScore++;
                        if (aiAvailable.isEmpty()) break;
                    }
                }
            }
        }

        // If waiting for replacement, notify and return (UI will call resolvePendingRound again after selection)
        if (waitingForPlayerReplacement) {
            listener.onScoreChanged(playerScore, aiScore);
            return;
        }

        // AI chain: pick its best attacker and attack player's monsters
        while (!aiAvailable.isEmpty() && !playerAvailable.isEmpty()) {
            Card aiAttacker = chooseAiCard();
            if (aiAttacker == null) break;
            Card defender = playerAvailable.get(0);
            int atk = aiAttacker.getAtk();
            int defLife = playerLife.getOrDefault(defender, Math.max(defender.getDef(), 100));
            if (atk > defLife) {
                playerAvailable.remove(defender);
                playerLife.remove(defender);
                // decrement player lives remaining
                if (playerLivesRemaining > 0) playerLivesRemaining--;
                aiScore++;
                // request replacement if player still has monsters
                if (!playerAvailable.isEmpty()) {
                    waitingForPlayerReplacement = true;
                    listener.onReplacementRequested(true);
                    break;
                }
            } else if (atk < defLife) {
                defLife -= atk;
                playerLife.put(defender, defLife);
                break;
            } else {
                boolean aiWins = random.nextBoolean();
                if (aiWins) {
                    playerAvailable.remove(defender);
                    playerLife.remove(defender);
                    aiScore++;
                    if (!playerAvailable.isEmpty()) {
                        waitingForPlayerReplacement = true;
                        listener.onReplacementRequested(true);
                    }
                    break;
                } else {
                    aiAvailable.remove(aiAttacker);
                    aiLife.remove(aiAttacker);
                    playerScore++;
                    if (aiAvailable.isEmpty()) break;
                }
            }
        }

    // notify which cards were removed so UI can disable exactly those panels
    listener.onCardsRemoved(removedPlayers, removedAi);

    // notify results of the exchange
        String roundWinner;
        if (playerScore > aiScore) roundWinner = "Jugador";
        else if (aiScore > playerScore) roundWinner = "IA";
        else roundWinner = "Empate";

        listener.onTurnResolved(pendingPlayerSelection, pendingAiSelection, playerTurn ? "Jugador" : "IA", roundWinner);
        listener.onScoreChanged(playerScore, aiScore);

        // clear pending selections for next round
        pendingPlayerSelection = null;
        pendingAiSelection = null;

        // check end condition
        if (playerScore >= 2 || aiScore >= 2 || playerAvailable.isEmpty() || aiAvailable.isEmpty()) {
            endDuel();
            return;
        }

        // toggle turn
        playerTurn = !playerTurn;
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
        // choose the available card with highest ATK
        Card best = aiAvailable.get(0);
        for (Card c : aiAvailable) {
            if (c.getAtk() > best.getAtk()) {
                best = c;
            }
        }
        return best;
    }

    
}
