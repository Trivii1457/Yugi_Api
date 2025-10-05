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

    // Colores temáticos de Yu-Gi-Oh mejorados para mejor contraste
    private static final Color DARK_BLUE = new Color(0x0d1117);      
    private static final Color PURPLE = new Color(0x161b22);         
    private static final Color GOLD = new Color(0xffd700);
    private static final Color LIGHT_BLUE = new Color(0x58a6ff);     
    private static final Color DARK_RED = new Color(0xf85149);       
    private static final Color CARD_BORDER = new Color(0xf78166);    
    private static final Color AI_CARD_BORDER = new Color(0xa5a5a5); 
    private static final Color TEXT_PRIMARY = new Color(0xf0f6fc);   
    private static final Color TEXT_SECONDARY = new Color(0xc9d1d9); 

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
    private final JButton startDuelButton = new JButton("Iniciar duelo");

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
        super("🎴 Yu-Gi-Oh! Duel Arena - Laboratorio DS3");
        setupTheme();
        configureLayout();
        registerListeners();
    }

    private void setupTheme() {
        getContentPane().setBackground(DARK_BLUE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1200, 800));
        setLocationRelativeTo(null);
    }

    // ... resto del código se mantiene igual al original
    
    // Solo voy a corregir los métodos problemáticos que estaban bien antes
}
