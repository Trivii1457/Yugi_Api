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
        reloadButton.addActionListener(e -> fetchHandsAsync());
        topBar.add(reloadButton);

        add(topBar, BorderLayout.NORTH);

    // center area: AI bank (top), battle zone (center), player bank (bottom)
    JPanel center = new JPanel(new BorderLayout());

    aiCardsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));
    JScrollPane aiScroll = new JScrollPane(aiCardsPanel);
    aiScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    aiScroll.setPreferredSize(new Dimension(780, 180));
    aiScroll.setBorder(BorderFactory.createTitledBorder("Banco IA"));
    center.add(aiScroll, BorderLayout.NORTH);

    // battle zone
    battlePanel.setPreferredSize(new Dimension(780, 240));
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

    playerCardsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 25, 15));
    JScrollPane cardsScroll = new JScrollPane(playerCardsPanel);
    cardsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    cardsScroll.setPreferredSize(new Dimension(780, 180));
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
            CardPanel panel = new CardPanel(card, false);
            aiCardPanelMap.put(card, panel);
            aiCardsPanel.add(panel);
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
        duel.playRound(new CardSelection(card, position));
        if (!duel.getPlayerAvailable().contains(card)) {
            panel.setUsed(true);
        }
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
            // update battle zone visuals
            playerBattleInfo.setText("Jugador: " + playerSelection.getCard().getName() + " (" + playerSelection.getPosition() + ")");
            aiBattleInfo.setText("IA: " + aiSelection.getCard().getName() + " (" + aiSelection.getPosition() + ")");
            // load images
            loadImageAsync(playerSelection.getCard().getImageUrl(), playerBattleImage);
            loadImageAsync(aiSelection.getCard().getImageUrl(), aiBattleImage);
            // mark AI card as used in its bank
            CardPanel aiPanel = aiCardPanelMap.get(aiSelection.getCard());
            if (aiPanel != null) aiPanel.markUsedExternally();
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
            Image scaled = image.getScaledInstance(200, 300, Image.SCALE_SMOOTH);
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

        CardPanel(Card card, boolean selectable) {
            this.card = card;
            setLayout(new BorderLayout(5, 5));
            setPreferredSize(new Dimension(220, 380));
            setBorder(BorderFactory.createLineBorder(Color.ORANGE, 2));

            JLabel nameLabel = new JLabel("<html><center>" + card.getName() + "</center></html>", SwingConstants.CENTER);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
            add(nameLabel, BorderLayout.NORTH);

            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            add(imageLabel, BorderLayout.CENTER);

            JTextArea stats = new JTextArea();
            stats.setEditable(false);
            stats.setOpaque(false);
            stats.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            stats.setText(String.format("ATK: %d%nDEF: %d%nTipo: %s", card.getAtk(), card.getDef(), card.getType()));

            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
            bottomPanel.add(stats);
            bottomPanel.add(Box.createVerticalStrut(10));

            if (selectable) {
                selectButton.addActionListener(e -> onCardClicked(this.card));
                bottomPanel.add(selectButton);
            }
            add(bottomPanel, BorderLayout.SOUTH);

            loadImageAsync(card.getImageUrl(), imageLabel);
        }

        void setUsed(boolean used) {
            this.used = used;
            selectButton.setEnabled(!used);
            setBorder(BorderFactory.createLineBorder(used ? Color.GRAY : Color.ORANGE, used ? 1 : 2));
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
