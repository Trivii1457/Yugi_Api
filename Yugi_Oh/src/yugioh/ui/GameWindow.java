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
import java.net.URI;
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

    // Colores temáticos de Yu-Gi-Oh mejorados para mejor contraste
    private static final Color DARK_BLUE = new Color(0x0d1117);      // Más oscuro para mejor contraste
    private static final Color PURPLE = new Color(0x161b22);         // Gris oscuro profesional
    private static final Color GOLD = new Color(0xffd700);
    private static final Color LIGHT_BLUE = new Color(0x58a6ff);     // Azul más claro y vibrante
    private static final Color DARK_RED = new Color(0xf85149);       // Rojo más vibrante
    private static final Color CARD_BORDER = new Color(0xf78166);    // Naranja más suave
    private static final Color AI_CARD_BORDER = new Color(0xa5a5a5); // Gris claro para IA
    private static final Color TEXT_PRIMARY = new Color(0xf0f6fc);   // Blanco casi puro
    private static final Color TEXT_SECONDARY = new Color(0xc9d1d9); // Gris claro

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
        super("🎴 Yu-Gi-Oh! Duel Arena - Laboratorio DS3");
        setupTheme();
        configureLayout();
        registerListeners();
    }

    private void setupTheme() {
        // Configurar el fondo oscuro y tema general
        getContentPane().setBackground(DARK_BLUE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Maximizar ventana para aprovechar toda la pantalla
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1200, 800));
        setLocationRelativeTo(null);
    }

    private void configureLayout() {
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topBar.setBackground(PURPLE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, GOLD),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // Etiquetas con estilo mejorado
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(GOLD);
        statusLabel.setText("⚡ Preparando duelo...");

        scoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        scoreLabel.setForeground(TEXT_PRIMARY);

        playerLivesLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        playerLivesLabel.setForeground(LIGHT_BLUE);

        aiLivesLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        aiLivesLabel.setForeground(TEXT_SECONDARY);

        // Botón de recarga estilizado
        styleButton(reloadButton, GOLD, DARK_BLUE);
        reloadButton.setText("🎲 Cargar Cartas");
        reloadButton.addActionListener(e -> fetchHandsAsync());

        // Botón de duelo
        styleButton(duelButton, GOLD, Color.BLACK);  // Dorado con texto negro para mejor visibilidad
        duelButton.setText("⚔️ Duelar");
        duelButton.setEnabled(false);
        duelButton.addActionListener(e -> onDuelButton());

        topBar.add(statusLabel);
        topBar.add(Box.createHorizontalStrut(20));
        topBar.add(scoreLabel);
        topBar.add(Box.createHorizontalStrut(20));
        topBar.add(playerLivesLabel);
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(aiLivesLabel);
        topBar.add(Box.createHorizontalStrut(20));
        topBar.add(reloadButton);
        topBar.add(Box.createHorizontalStrut(10));
        topBar.add(duelButton);

        add(topBar, BorderLayout.NORTH);

    
    JPanel center = new JPanel(new BorderLayout());
    center.setBackground(DARK_BLUE);

    aiCardsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));
    aiCardsPanel.setBackground(PURPLE);
    JScrollPane aiScroll = new JScrollPane(aiCardsPanel);
    aiScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    aiScroll.setPreferredSize(new Dimension(980, 220));
    aiScroll.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(AI_CARD_BORDER, 2),
        "🤖 Cartas IA",
        0, 0,
        new Font("Segoe UI", Font.BOLD, 12),
        TEXT_PRIMARY
    ));
    aiScroll.getViewport().setBackground(PURPLE);
    center.add(aiScroll, BorderLayout.NORTH);

    // Zona de batalla con mejor estilo
    battlePanel.setBackground(DARK_BLUE);
    battlePanel.setPreferredSize(new Dimension(980, 340));
    JPanel playerBattlePanel = new JPanel(new BorderLayout());
    playerBattlePanel.setBackground(PURPLE);
    playerBattlePanel.setBorder(BorderFactory.createLineBorder(LIGHT_BLUE, 2));
    playerBattlePanel.add(playerBattleImage, BorderLayout.CENTER);
    playerBattlePanel.add(playerBattleInfo, BorderLayout.SOUTH);
    
    JPanel aiBattlePanel = new JPanel(new BorderLayout());
    aiBattlePanel.setBackground(PURPLE);
    aiBattlePanel.setBorder(BorderFactory.createLineBorder(DARK_RED, 2));
    aiBattlePanel.add(aiBattleImage, BorderLayout.CENTER);
    aiBattlePanel.add(aiBattleInfo, BorderLayout.SOUTH);
    
    battlePanel.add(playerBattlePanel);
    battlePanel.add(aiBattlePanel);
    battlePanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(GOLD, 3),
        "⚔️ Zona de Batalla",
        0, 0,
        new Font("Segoe UI", Font.BOLD, 16),
        GOLD
    ));
    center.add(battlePanel, BorderLayout.CENTER);

    playerCardsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 15));
    playerCardsPanel.setBackground(DARK_BLUE);
    JScrollPane cardsScroll = new JScrollPane(playerCardsPanel);
    cardsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    cardsScroll.setPreferredSize(new Dimension(1080, 300));  // Menos altura ya que las cartas son más pequeñas
    cardsScroll.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(CARD_BORDER, 2),
        "🧙‍♂️ Tus Cartas",
        0, 0,
        new Font("Segoe UI", Font.BOLD, 14),
        TEXT_PRIMARY
    ));
    cardsScroll.getViewport().setBackground(DARK_BLUE);
    center.add(cardsScroll, BorderLayout.SOUTH);

    add(center, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(DARK_BLUE);
        logArea.setForeground(TEXT_PRIMARY);
        logArea.setCaretColor(GOLD);
        
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(350, 600));
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(GOLD, 2),
            "📜 Registro de Duelo",
            0, 0,
            new Font("Segoe UI", Font.BOLD, 14),
            GOLD
        ));
        logScroll.getViewport().setBackground(DARK_BLUE);
        add(logScroll, BorderLayout.EAST);

        appendLog("🎮 ¡Bienvenido al Duel Arena!");
        appendLog("💫 Presiona 'Cargar Cartas' para comenzar tu aventura.");
    }

    private void styleButton(JButton button, Color bgColor, Color textColor) {
        button.setBackground(bgColor);
        button.setForeground(textColor);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createRaisedBevelBorder(),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        button.setOpaque(true);
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
            // initially face-down: not selectable and hidden info
            CardPanel panel = new CardPanel(card, false, true);
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

    @Override
    public void onAiSelectedFirst(CardSelection aiSelection) {
        SwingUtilities.invokeLater(() -> {
            // Mostrar la selección de la IA en la zona de batalla
            aiBattleInfo.setText("🤖 " + aiSelection.getCard().getName() + " (" + aiSelection.getPosition() + ")");
            loadImageAsync(aiSelection.getCard().getImageUrl(), aiBattleImage);
            // Revelar la carta de la IA en su banco
            CardPanel aiPanel = aiCardPanelMap.get(aiSelection.getCard());
            if (aiPanel != null) aiPanel.reveal();
            // Informar al jugador que ahora debe responder
            appendLog("🤖 IA seleccionó: " + aiSelection.getCard().getName() + " (" + aiSelection.getPosition() + ")");
            appendLog("🧙‍♂️ Tu turno: selecciona tu carta para responder");
            statusLabel.setText("⚡ Tu turno - La IA ya seleccionó");
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
            BufferedImage image = ImageIO.read(URI.create(url).toURL());
            if (image == null) {
                return null;
            }
            // Escalar imagen a tamaño más pequeño y apropiado
            Image scaled = image.getScaledInstance(160, 240, Image.SCALE_SMOOTH);
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
    private boolean faceDown = false;

        CardPanel(Card card, boolean selectable) {
            this(card, selectable, false);
        }

        CardPanel(Card card, boolean selectable, boolean faceDown) {
            this.card = card;
            this.faceDown = faceDown;
            setLayout(new BorderLayout(5, 5));
            setPreferredSize(new Dimension(240, 350));  // Más pequeñas para que se vean completas
            setBackground(PURPLE);
            setBorder(BorderFactory.createLineBorder(CARD_BORDER, 2));

            String title = faceDown ? "<html><center>Carta oculta</center></html>" : "<html><center>" + card.getName() + "</center></html>";
            JLabel nameLabel = new JLabel(title, SwingConstants.CENTER);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
            add(nameLabel, BorderLayout.NORTH);

            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setPreferredSize(new Dimension(160, 240));  // Más pequeña pero proporcional
            imageLabel.setOpaque(true);
            imageLabel.setBackground(DARK_BLUE);
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
            if (faceDown) {
                // hide image and stats when face-down
                imageLabel.setText("Carta oculta");
                stats.setText(" ");
            }
            bottomPanel.add(Box.createVerticalStrut(6));
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
            repaint();
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
