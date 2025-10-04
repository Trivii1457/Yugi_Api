package yugioh.ui;

import yugioh.api.YgoApiClient;
import yugioh.core.BattleListener;
import yugioh.core.Duel;
import yugioh.model.Card;
import yugioh.model.CardPosition;
import yugioh.model.CardSelection;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class GameWindow extends JFrame implements BattleListener {

    private static final int STARTING_HAND = 3;

    private final YgoApiClient apiClient = new YgoApiClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private final JPanel playerCardsPanel = new JPanel();
    private final JPanel aiCardsPanel = new JPanel();
    private final JLabel playerLivesLabel = new JLabel("Vidas jugador: 3");
    private final JLabel aiLivesLabel = new JLabel("Vidas IA: 3");
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Carga inicial pendiente");
    private final JLabel scoreLabel = new JLabel("Jugador 0 - 0 IA");
    private final JButton reloadButton = new JButton("Cargar cartas");

    private Duel duel;
    private List<Card> playerCards = List.of();
    private List<Card> aiCards = List.of();
    private final Map<Card, CardPanel> cardPanelMap = new HashMap<>();
    private final Map<Card, CardPanel> aiCardPanelMap = new HashMap<>();

    // Battle zone components
    private final JPanel battlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 10));
    private final JLabel playerBattleImage = new JLabel();
    private final JLabel playerBattleInfo = new JLabel("Jugador: -");
    private final JLabel aiBattleImage = new JLabel();
    private final JLabel aiBattleInfo = new JLabel("IA: -");
    private final JButton duelButton = new JButton("¡Duelar!");

    public GameWindow() {
        super("Duelo de Cartas - Laboratorio #1");
        configureLayout();
        registerListeners();
    }

    private void configureLayout() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);

    JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        topBar.add(statusLabel);
        topBar.add(Box.createHorizontalStrut(20));
        topBar.add(scoreLabel);
    topBar.add(Box.createHorizontalStrut(20));
    topBar.add(playerLivesLabel);
    topBar.add(Box.createHorizontalStrut(10));
    topBar.add(aiLivesLabel);
        topBar.add(Box.createHorizontalStrut(20));
        reloadButton.addActionListener(e -> fetchHandsAsync());
        topBar.add(reloadButton);
    duelButton.setEnabled(false);
    duelButton.addActionListener(e -> onDuelButton());
    topBar.add(Box.createHorizontalStrut(10));
    topBar.add(duelButton);

        add(topBar, BorderLayout.NORTH);

    
    JPanel center = new JPanel(new BorderLayout());

    aiCardsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));
    JScrollPane aiScroll = new JScrollPane(aiCardsPanel);
    aiScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    aiScroll.setPreferredSize(new Dimension(980, 240));
    aiScroll.setBorder(BorderFactory.createTitledBorder("Banco IA"));
    center.add(aiScroll, BorderLayout.NORTH);

    // battle zone
    battlePanel.setPreferredSize(new Dimension(980, 340));
    JPanel playerBattlePanel = new JPanel(new BorderLayout());
    playerBattlePanel.add(playerBattleImage, BorderLayout.CENTER);
    playerBattlePanel.add(playerBattleInfo, BorderLayout.SOUTH);
    JPanel aiBattlePanel = new JPanel(new BorderLayout());
    aiBattlePanel.add(aiBattleImage, BorderLayout.CENTER);
    aiBattlePanel.add(aiBattleInfo, BorderLayout.SOUTH);
    battlePanel.add(playerBattlePanel);
    battlePanel.add(aiBattlePanel);
    battlePanel.setBorder(BorderFactory.createTitledBorder("Zona de pelea"));
    center.add(battlePanel, BorderLayout.CENTER);

    playerCardsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 35, 20));
    JScrollPane cardsScroll = new JScrollPane(playerCardsPanel);
    cardsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    cardsScroll.setPreferredSize(new Dimension(1080, 380));
    cardsScroll.setBorder(BorderFactory.createTitledBorder("Banco Jugador"));
    center.add(cardsScroll, BorderLayout.SOUTH);

    add(center, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(300, 550));
        logScroll.setBorder(BorderFactory.createTitledBorder("Registro de duelo"));
        add(logScroll, BorderLayout.EAST);

        appendLog("Presiona 'Cargar cartas' para preparar el duelo.");
    }

    private void registerListeners() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                executor.shutdownNow();
            }
        });
    }

    private void fetchHandsAsync() {
        setLoading(true, "Cargando cartas aleatorias...");
        CompletableFuture
                .supplyAsync(this::loadHands, executor)
                .whenComplete((hands, throwable) -> SwingUtilities.invokeLater(() -> {
                    setLoading(false, "Cartas listas");
                    if (throwable != null || hands == null) {
                        String message = throwable != null ? throwable.getMessage() : "No se pudo cargar las cartas";
                        onError(message, throwable);
                        return;
                    }
                    this.playerCards = hands.player;
                    this.aiCards = hands.ai;
                    renderAiCards();
                    renderPlayerCards();
                    startNewDuel();
                }));
    }

    private Hands loadHands() {
        try {
            List<Card> player = apiClient.fetchRandomMonsterCards(STARTING_HAND);
            List<Card> ai = apiClient.fetchRandomMonsterCards(STARTING_HAND);
            return new Hands(player, ai);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("La carga de cartas fue interrumpida", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startNewDuel() {
        this.duel = new Duel(playerCards, aiCards, this);
        duel.start();
        cardPanelMap.values().forEach(panel -> panel.setUsed(false));
    }

    private void setLoading(boolean loading, String message) {
        reloadButton.setEnabled(!loading);
        reloadButton.setText(loading ? "Cargando..." : "Recargar cartas");
        if (message != null) {
            statusLabel.setText(message);
        }
    }

    private void renderPlayerCards() {
        playerCardsPanel.removeAll();
        cardPanelMap.clear();
        for (Card card : playerCards) {
            CardPanel panel = new CardPanel(card, true);
            cardPanelMap.put(card, panel);
            playerCardsPanel.add(panel);
            if (duel != null) panel.setLife(duel.getPlayerLife(card));
        }
        playerCardsPanel.revalidate();
        playerCardsPanel.repaint();
        String names = playerCards.stream().map(Card::getName).collect(Collectors.joining(", "));
        appendLog("Cartas jugador: " + names);
    }

    private void renderAiCards() {
        aiCardsPanel.removeAll();
        aiCardPanelMap.clear();
        for (Card card : aiCards) {
            // initially face-down: not selectable and hidden info
            CardPanel panel = new CardPanel(card, false, true);
            aiCardPanelMap.put(card, panel);
            aiCardsPanel.add(panel);
            if (duel != null) panel.setLife(duel.getAiLife(card));
        }
        aiCardsPanel.revalidate();
        aiCardsPanel.repaint();
        String names = aiCards.stream().map(Card::getName).collect(Collectors.joining(", "));
        appendLog("Cartas IA: " + names);
    }

    private void onCardClicked(Card card) {
        if (duel == null || !duel.isActive()) {
            JOptionPane.showMessageDialog(this, "El duelo aún no está listo", "Advertencia", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CardPanel panel = cardPanelMap.get(card);
        if (panel == null || panel.isUsed()) {
            JOptionPane.showMessageDialog(this, "Esa carta ya fue usada", "Advertencia", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object[] options = {"Ataque", "Defensa"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Selecciona la posición de la carta",
                "Posición de batalla",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == JOptionPane.CLOSED_OPTION) {
            return;
        }
        CardPosition position = CardPosition.fromIndex(choice);
        boolean ok = duel.setPlayerSelection(new CardSelection(card, position));
        if (ok) {
            appendLog("Selección registrada: " + card.getName() + " (" + position + ")");
            // show selected in battle zone immediately
            playerBattleInfo.setText("Jugador: " + card.getName() + " (" + position + ")");
            loadImageAsync(card.getImageUrl(), playerBattleImage);
            // reveal AI chosen card in bank and show it in battle zone immediately
            var aiSel = duel.getPendingAiSelection();
            if (aiSel != null) {
                Card aiCard = aiSel.getCard();
                CardPanel aiPanel = aiCardPanelMap.get(aiCard);
                if (aiPanel != null) aiPanel.reveal();
                aiBattleInfo.setText("IA: " + aiCard.getName() + " (" + aiSel.getPosition() + ")");
                loadImageAsync(aiCard.getImageUrl(), aiBattleImage);
            }
            // enable duel button so user can confirm
            duelButton.setEnabled(true);
        }
    }

    private void onDuelButton() {
        duelButton.setEnabled(false);
        // resolve pending round
        duel.resolvePendingRound();
        // update lives display
        updateLivesDisplay();
        // mark player card used if depleted
        // mark AI card used is done in onTurnResolved
    }

    private void updateLivesDisplay() {
        if (duel == null) return;
        playerLivesLabel.setText("Vidas jugador: " + duel.getPlayerRemainingLives());
        aiLivesLabel.setText("Vidas IA: " + duel.getAiRemainingLives());
    }

    private void appendLog(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    @Override
    public void onDuelStarted(String startingPlayer) {
        SwingUtilities.invokeLater(() -> {
            appendLog("El duelo inicia. Primer turno: " + startingPlayer);
            statusLabel.setText("Turno inicial: " + startingPlayer);
        });
    }

    @Override
    public void onTurnResolved(CardSelection playerSelection, CardSelection aiSelection, String attacker, String roundWinner) {
        SwingUtilities.invokeLater(() -> {
            String message = String.format(
                    "%s ataca. Jugador: %s (%s). IA: %s (%s). Resultado: %s",
                    attacker,
                    playerSelection.getCard().getName(),
                    playerSelection.getPosition(),
                    aiSelection.getCard().getName(),
                    aiSelection.getPosition(),
                    roundWinner
            );
            appendLog(message);
            // also show as popup message
            JOptionPane.showMessageDialog(this, message, "Resultado de turno", JOptionPane.INFORMATION_MESSAGE);
            // reveal AI chosen card in its bank (was face-down)
            CardPanel revealPanel = aiCardPanelMap.get(aiSelection.getCard());
            if (revealPanel != null) revealPanel.reveal();
            // update battle zone visuals
            playerBattleInfo.setText("Jugador: " + playerSelection.getCard().getName() + " (" + playerSelection.getPosition() + ")");
            aiBattleInfo.setText("IA: " + aiSelection.getCard().getName() + " (" + aiSelection.getPosition() + ")");
            // load images
            loadImageAsync(playerSelection.getCard().getImageUrl(), playerBattleImage);
            loadImageAsync(aiSelection.getCard().getImageUrl(), aiBattleImage);
            // mark AI card as used in its bank
            CardPanel aiPanel = aiCardPanelMap.get(aiSelection.getCard());
            if (aiPanel != null) aiPanel.markUsedExternally();
            // mark player's card as used if it has been removed from available list
            CardPanel playerPanel = cardPanelMap.get(playerSelection.getCard());
            if (playerPanel != null) {
                boolean stillAvailable = duel.getPlayerAvailable().contains(playerSelection.getCard());
                if (!stillAvailable) playerPanel.markUsedExternally();
            }
            // update per-card life displays
            aiCardPanelMap.forEach((c, p) -> {
                if (duel != null) p.setLife(duel.getAiLife(c));
            });
            cardPanelMap.forEach((c, p) -> {
                if (duel != null) p.setLife(duel.getPlayerLife(c));
            });
            updateLivesDisplay();
        });
    }

    @Override
    public void onScoreChanged(int playerScore, int aiScore) {
        SwingUtilities.invokeLater(() -> scoreLabel.setText("Jugador " + playerScore + " - " + aiScore + " IA"));
    }

    @Override
    public void onDuelEnded(String winner) {
        SwingUtilities.invokeLater(() -> {
            appendLog("Duelo finalizado. Ganador: " + winner);
            statusLabel.setText("Ganador: " + winner);
            cardPanelMap.values().forEach(panel -> panel.setButtonEnabled(false));
            aiCardPanelMap.values().forEach(panel -> panel.setButtonEnabled(false));
            reloadButton.setText("Nuevo duelo");
            reloadButton.setEnabled(true);
            // popup final result
            JOptionPane.showMessageDialog(this, "Duelo finalizado. Ganador: " + winner, "Fin del duelo", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onReplacementRequested(boolean playerSide) {
        SwingUtilities.invokeLater(() -> {
            appendLog("Se requiere reemplazo de monstruo. Selecciona un monstruo disponible.");
            statusLabel.setText("Selecciona reemplazo");
            // enable only available player's cards
            if (duel != null) {
                for (var entry : cardPanelMap.entrySet()) {
                    Card c = entry.getKey();
                    var panel = entry.getValue();
                    boolean available = duel.getPlayerAvailable().contains(c);
                    panel.setButtonEnabled(available);
                }
            }
            // inform user
            JOptionPane.showMessageDialog(this, "Tu monstruo fue derrotado. Elige un reemplazo haciendo clic en una de tus cartas disponibles.", "Reemplazo requerido", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onCardsRemoved(java.util.List<Card> playerRemoved, java.util.List<Card> aiRemoved) {
        SwingUtilities.invokeLater(() -> {
            if (playerRemoved != null) {
                for (Card c : playerRemoved) {
                    CardPanel p = cardPanelMap.get(c);
                    if (p != null) p.markUsedExternally();
                }
            }
            if (aiRemoved != null) {
                for (Card c : aiRemoved) {
                    CardPanel p = aiCardPanelMap.get(c);
                    if (p != null) p.markUsedExternally();
                }
            }
            updateLivesDisplay();
        });
    }

    @Override
    public void onError(String message, Throwable throwable) {
        SwingUtilities.invokeLater(() -> {
            appendLog("Error: " + message);
            JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void loadImageAsync(String url, JLabel target) {
        if (url == null || url.isBlank()) {
            target.setText("Sin imagen");
            return;
        }
        target.setText("Cargando imagen...");
        CompletableFuture
                .supplyAsync(() -> fetchImage(url), executor)
                .whenComplete((icon, throwable) -> SwingUtilities.invokeLater(() -> {
                    if (throwable != null || icon == null) {
                        target.setText("Sin imagen");
                    } else {
                        target.setIcon(icon);
                        target.setText(null);
                    }
                }));
    }

    private javax.swing.ImageIcon fetchImage(String url) {
        try {
            BufferedImage image = ImageIO.read(new URL(url));
            if (image == null) {
                return null;
            }
            Image scaled = image.getScaledInstance(280, 420, Image.SCALE_SMOOTH);
            return new javax.swing.ImageIcon(scaled);
        } catch (IOException e) {
            return null;
        }
    }

    private class CardPanel extends JPanel {
        private final Card card;
    private final JButton selectButton = new JButton("Elegir carta");
        private final JLabel imageLabel = new JLabel();
        private boolean used;
        private final JLabel lifeLabel;
    private boolean faceDown = false;

        CardPanel(Card card, boolean selectable) {
            this(card, selectable, false);
        }

        CardPanel(Card card, boolean selectable, boolean faceDown) {
            this.card = card;
            this.faceDown = faceDown;
            setLayout(new BorderLayout(5, 5));
                setPreferredSize(new Dimension(320, 540));
            setBorder(BorderFactory.createLineBorder(Color.ORANGE, 2));

            String title = faceDown ? "<html><center>Carta oculta</center></html>" : "<html><center>" + card.getName() + "</center></html>";
            JLabel nameLabel = new JLabel(title, SwingConstants.CENTER);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
            add(nameLabel, BorderLayout.NORTH);

            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setPreferredSize(new Dimension(200, 380));
            add(imageLabel, BorderLayout.CENTER);

            JTextArea stats = new JTextArea();
            stats.setEditable(false);
            stats.setOpaque(false);
            stats.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            stats.setText(String.format("ATK: %d%nDEF: %d%nTipo: %s", card.getAtk(), card.getDef(), card.getType()));

            this.lifeLabel = new JLabel("Vida: " + card.getDef());
            lifeLabel.setFont(lifeLabel.getFont().deriveFont(Font.PLAIN, 12f));

            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
            bottomPanel.add(stats);
            bottomPanel.add(Box.createVerticalStrut(10));

            if (selectable) {
                selectButton.addActionListener(e -> onCardClicked(this.card));
                bottomPanel.add(selectButton);
            }
            if (faceDown) {
                // hide image and stats when face-down
                imageLabel.setText("Carta oculta");
                stats.setText(" ");
                lifeLabel.setText("Vida: ?");
            }
            bottomPanel.add(Box.createVerticalStrut(6));
            bottomPanel.add(lifeLabel);
            add(bottomPanel, BorderLayout.SOUTH);
            if (!faceDown) {
                loadImageAsync(card.getImageUrl(), imageLabel);
            }
        }

        void reveal() {
            if (!faceDown) return;
            faceDown = false;
            // show image and info
            loadImageAsync(card.getImageUrl(), imageLabel);
            // update title and stats
            // rebuild bottom panel life/stats (simple approach: set life label text)
            lifeLabel.setText("Vida: " + (duel != null ? duel.getAiLife(card) : card.getDef()));
            repaint();
        }

        void setUsed(boolean used) {
            this.used = used;
            selectButton.setEnabled(!used);
            setBorder(BorderFactory.createLineBorder(used ? Color.GRAY : Color.ORANGE, used ? 1 : 2));
        }

        void setLife(int life) {
            lifeLabel.setText("Vida: " + life);
            repaint();
        }

        boolean isUsed() {
            return used;
        }

        void setButtonEnabled(boolean enabled) {
            if (!used) {
                selectButton.setEnabled(enabled);
            }
        }

        void markUsedExternally() {
            setUsed(true);
            selectButton.setEnabled(false);
        }
    }

    private static class Hands {
        final List<Card> player;
        final List<Card> ai;

        Hands(List<Card> player, List<Card> ai) {
            this.player = Objects.requireNonNull(player);
            this.ai = Objects.requireNonNull(ai);
        }
    }
}
