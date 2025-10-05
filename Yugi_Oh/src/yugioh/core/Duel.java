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
    // lives remaining per player (number of monsters left)
    private int playerLivesRemaining;
    private int aiLivesRemaining;

    // pending selections (set when player chooses a card)
    private CardSelection pendingPlayerSelection;
    private CardSelection pendingAiSelection;

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
        
        // Si es turno de la IA, que ella seleccione primero automáticamente
        if (!playerTurn) {
            selectAiCardFirst();
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
        // En el sistema simplificado, mostramos las vidas restantes del jugador
        // (no depende de la carta específica)
        return playerLivesRemaining;
    }

    public int getAiLife(Card card) {
        // En el sistema simplificado, mostramos las vidas restantes de la IA
        // (no depende de la carta específica)
        return aiLivesRemaining;
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

        this.pendingPlayerSelection = playerSelection;
        
        // Si es turno del jugador, la IA responde automáticamente
        if (playerTurn) {
            // Elegir carta de la IA al azar como respuesta
            Card aiCard = chooseAiCard();
            if (aiCard == null) {
                listener.onError("La IA no tiene cartas disponibles", null);
                return false;
            }
            CardPosition aiPosition = random.nextBoolean() ? CardPosition.ATTACK : CardPosition.DEFENSE;
            this.pendingAiSelection = new CardSelection(aiCard, aiPosition);
        }
        // Si es turno de la IA, ya tiene su selección pendiente, solo se completa el par
        
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
        Card aiCard = pendingAiSelection.getCard();
        CardPosition playerPosition = pendingPlayerSelection.getPosition();
        CardPosition aiPosition = pendingAiSelection.getPosition();

        String roundWinner = "Empate";
        boolean playerWins = false;
        
        // Aplicar lógica según los requisitos del laboratorio:
        // Si ambos en ataque → gana el mayor ATK
        // Si uno en ataque y otro en defensa → ATK del atacante vs DEF del defensor
        
        if (playerPosition == CardPosition.ATTACK && aiPosition == CardPosition.ATTACK) {
            // Ambos en ataque → comparar ATK vs ATK
            if (playerCard.getAtk() > aiCard.getAtk()) {
                playerWins = true;
            } else if (playerCard.getAtk() < aiCard.getAtk()) {
                playerWins = false;
            } else {
                // Empate en ATK → nadie pierde vida
                roundWinner = "Empate";
                playerWins = false; // Para no procesar ganador
            }
        } else if (playerPosition == CardPosition.ATTACK && aiPosition == CardPosition.DEFENSE) {
            // Jugador ataca, IA defiende → ATK jugador vs DEF IA
            playerWins = playerCard.getAtk() > aiCard.getDef();
        } else if (playerPosition == CardPosition.DEFENSE && aiPosition == CardPosition.ATTACK) {
            // IA ataca, jugador defiende → ATK IA vs DEF jugador
            playerWins = aiCard.getAtk() <= playerCard.getDef();
        } else {
            // Ambos en defensa → empate, nadie pierde vida
            roundWinner = "Empate (Ambos Defendiendo)";
            playerWins = false; // Para no procesar ganador
        }

        // Actualizar vidas y puntuación
        if (playerPosition == CardPosition.ATTACK && aiPosition == CardPosition.ATTACK && 
            playerCard.getAtk() != aiCard.getAtk()) {
            // Solo hay ganador/perdedor si no es empate
            if (playerWins) {
                aiLivesRemaining--;
                playerScore++;
                roundWinner = "Jugador";
            } else {
                playerLivesRemaining--;
                aiScore++;
                roundWinner = "IA";
            }
        } else if (playerPosition == CardPosition.ATTACK && aiPosition == CardPosition.DEFENSE) {
            if (playerWins) {
                aiLivesRemaining--;
                playerScore++;
                roundWinner = "Jugador";
            } else {
                roundWinner = "IA (Defensa Exitosa)";
            }
        } else if (playerPosition == CardPosition.DEFENSE && aiPosition == CardPosition.ATTACK) {
            if (playerWins) {
                roundWinner = "Jugador (Defensa Exitosa)";
            } else {
                playerLivesRemaining--;
                aiScore++;
                roundWinner = "IA";
            }
        }

        // Asegurar que las vidas no sean negativas
        playerLivesRemaining = Math.max(0, playerLivesRemaining);
        aiLivesRemaining = Math.max(0, aiLivesRemaining);

        // Remover las cartas usadas de las listas disponibles
        playerAvailable.remove(playerCard);
        aiAvailable.remove(aiCard);

        // Notificar resultados
        listener.onTurnResolved(pendingPlayerSelection, pendingAiSelection, 
                               playerTurn ? "Jugador" : "IA", roundWinner);
        listener.onScoreChanged(playerScore, aiScore);

        // Limpiar selecciones pendientes
        pendingPlayerSelection = null;
        pendingAiSelection = null;

        // Verificar condición de fin
        if (playerScore >= 2 || aiScore >= 2 || playerAvailable.isEmpty() || aiAvailable.isEmpty()) {
            endDuel();
            return;
        }

        // Alternar turno
        playerTurn = !playerTurn;
        
        // Si ahora es turno de la IA, que seleccione automáticamente
        if (!playerTurn) {
            selectAiCardFirst();
        }
    }

    private void endDuel() {
        this.active = false;
        String winner;
        // Determinar ganador: mejor de 3 rondas
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

    private void selectAiCardFirst() {
        // IA selecciona primero cuando es su turno
        Card aiCard = chooseAiCard();
        if (aiCard != null) {
            CardPosition aiPosition = random.nextBoolean() ? CardPosition.ATTACK : CardPosition.DEFENSE;
            this.pendingAiSelection = new CardSelection(aiCard, aiPosition);
            // Notificar que la IA ya seleccionó y ahora es turno del jugador para responder
            listener.onAiSelectedFirst(pendingAiSelection);
        }
    }

    
}
