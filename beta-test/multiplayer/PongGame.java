/*
Neon Pong - Cyberpunk Edition
Copyright (c) 2026 DeeWHY
LICENSE: Proprietary software. All rights reserved.
Unauthorized copying or distribution is prohibited.
CREDITS:
Development: Davide
Sound Engine: Custom Java implementation
Visual Effects: Java 2D Graphics
Version: 2.2 (beta 1)
*/
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import javax.sound.sampled.*;

public class PongGame extends JPanel implements ActionListener, KeyListener {
    // === CONSTANTS ===
    public static final int GAME_WIDTH = 1000;
    public static final int GAME_HEIGHT = 700;
    public static final int BALL_SIZE = 25;
    public static final int PADDLE_WIDTH = 25;
    public static final int PADDLE_HEIGHT = 120;
    public static final int WIN_SCORE = 5;
    public static final int PLAYER_SPEED = 12;
    public static final int BALL_START_SPEED = 6;
    public static final int BALL_MAX_SPEED = 20;

    // === COLORS ===
    public static final Color BG_DARK = new Color(5, 5, 15);
    public static final Color BG_LIGHT = new Color(20, 20, 40);
    public static final Color PLAYER_GRADIENT_START = new Color(0, 255, 255);
    public static final Color PLAYER_GRADIENT_END = new Color(0, 100, 255);
    public static final Color CPU_GRADIENT_START = new Color(255, 50, 150);
    public static final Color CPU_GRADIENT_END = new Color(255, 0, 80);
    public static final Color BALL_CORE = Color.WHITE;
    public static final Color BALL_GLOW = new Color(100, 200, 255);
    public static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    public static final Color TEXT_SECONDARY = new Color(150, 150, 200);
    public static final Color ACCENT_GREEN = new Color(0, 255, 150);
    public static final Color ACCENT_YELLOW = new Color(255, 220, 50);
    public static final Color ACCENT_ORANGE = new Color(255, 180, 50);
    public static final Color ACCENT_RED = new Color(255, 80, 80);
    public static final Color ACCENT_PURPLE = new Color(200, 50, 255);
    public static final Color PAUSE_COLOR = new Color(255, 200, 0);
    public static final Color SPEED_BAR_BG = new Color(25, 25, 45);
    public static final Color SPEED_BAR_BORDER = new Color(70, 70, 110);
    public static final Color SPEAKER_ON = new Color(0, 255, 100);
    public static final Color SPEAKER_OFF = new Color(255, 80, 80);

    // === EMOJI FONT ===
    public static final Font EMOJI_FONT = new Font("Segoe UI Emoji", Font.PLAIN, 17);
    public static final Font EMOJI_FONT_LARGE = new Font("Segoe UI Emoji", Font.PLAIN, 24);

    // === DIFFICULTY ===
    public enum Difficulty {
        EASY("ROOKIE", 4, 7, 50, 1, ACCENT_GREEN, "Relaxed pace, very forgiving AI"),
        MEDIUM("VETERAN", 6, 10, 30, 1, ACCENT_ORANGE, "Balanced challenge for most players"),
        HARD("ELITE", 8, 12, 16, 2, ACCENT_RED, "Fast gameplay, skilled AI"),
        EXTREME("LEGEND", 11, 15, 8, 3, ACCENT_PURPLE, "Maximum speed, nearly perfect AI");

        public final String name;
        public final int cpuBaseSpeed;
        public final int cpuMaxSpeed;
        public final int cpuError;
        public final int ballAccel;
        public final Color color;
        public final String description;

        Difficulty(String name, int cpuBaseSpeed, int cpuMaxSpeed, int cpuError, int ballAccel, Color color, String description) {
            this.name = name;
            this.cpuBaseSpeed = cpuBaseSpeed;
            this.cpuMaxSpeed = cpuMaxSpeed;
            this.cpuError = cpuError;
            this.ballAccel = ballAccel;
            this.color = color;
            this.description = description;
        }
    }

    // === STATE ===
    static PongGame instance; // for inner static classes to reach game context
    public enum State { MENU, DIFFICULTY, PLAYING, PAUSED, GAMEOVER, EXIT_CONFIRM, MP_LOGIN, MP_REGISTER, MP_LOBBY, MP_WAITING, MP_ACCOUNT, COUNTDOWN }
    private State state = State.MENU;
    private Difficulty selectedDifficulty = Difficulty.MEDIUM;
    private int countdownValue = 3;       // 3, 2, 1, 0=GO
    private long countdownLastTick = 0;
    private State countdownNextState = State.PLAYING; // unused but kept for clarity

    // === SOUND ===
    private SoundManager soundManager;
    private boolean soundEnabled = true;
    private boolean showHelp = false;

    // === VISUAL EFFECTS ===
    private List<Particle> particles = new ArrayList<>();
    private List<Star> stars = new ArrayList<>();
    private int screenShake = 0;
    private float menuAnimationTimer = 0;
    private float titleGlowPhase = 0;
    private float currentSpeedSmooth = 0;

    // === GAME OBJECTS ===
    private Image buffer;
    private Graphics2D g2d;
    private final Random rand = new Random();

    private Paddle player, cpu;
    private Ball ball;
    private Score score;

    private Timer timer;
    private boolean up = false, down = false;
    private int difficultyIndex = 1;

    // === MULTIPLAYER ===
    private MultiplayerManager mpManager;
    private String mpRoomIdInput = "";       // used for joining (by ID or list)
    private String mpCreateRoomInput = "";   // used for creating a new room
    private String mpEmail = "";
    private String mpPassword = "";
    private boolean mpEmailMode = true;
    private String mpLoginError = "";
    private java.util.List<String[]> mpRoomList = new java.util.ArrayList<>(); // [roomId, gameId, status]
    private java.util.List<String[]> mpPersistentRooms = new java.util.ArrayList<>(); // [name, gameId, status]
    private int mpSelectedRoom = -10; // -10..-13=persistent card 0..3; >=0=custom room
    static final String[][] PERSISTENT_ROOMS = {
        {"ROOKIE",  "#00CC66"},
        {"VETERAN", "#DDAA00"},
        {"ELITE",   "#FF4444"},
        {"LEGEND",  "#CC44FF"},
    };
    private long mpLastRoomFetch = 0;
    private int mpOnlinePlayers = 0;   // stats from server
    private int mpActiveRooms = 0;
    private float mpCursorBlink = 0f;
    private boolean mpRegisterMode = false; // true = register form
    private int mpAccountField = 0;         // 0=email,1=password,2=confirm
    private String mpConfirmPassword = "";
    private String mpAccountNewEmail = "";
    private String mpAccountNewPassword = "";
    private String mpAccountConfirm = "";
    private int mpAccountTab = 0;           // 0=change email, 1=change pwd, 2=delete
    private boolean scoreCooldown = false;  // prevents double-scoring

    // === MENU DEMO GAME (animated background) ===
    private float demoPlayerY, demoCpuY, demoBallX, demoBallY, demoBallDX, demoBallDY;
    private int demoP1Score = 0, demoP2Score = 0;
    private boolean demoReady = false;

    public PongGame() {
        instance = this;
        setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        setBackground(BG_DARK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false); // Allow TAB to reach keyListener
        addKeyListener(this);
        setDoubleBuffered(true);

        for (int i = 0; i < 150; i++) {
            stars.add(new Star(rand.nextInt(GAME_WIDTH), rand.nextInt(GAME_HEIGHT), rand));
        }

        player = new Paddle(30, true);
        cpu = new Paddle(GAME_WIDTH - PADDLE_WIDTH - 30, false);
        ball = new Ball();
        ball.reset(rand.nextBoolean()); // FIXED: Initialize ball position
        score = new Score();

        timer = new Timer(16, this);
        timer.start();

        soundManager = new SoundManager();
        mpManager = new MultiplayerManager(this);
        initDemoGame();
    }

    @Override
    public void paint(Graphics g) {
        if (buffer == null || g2d == null) {
            buffer = createImage(GAME_WIDTH, GAME_HEIGHT);
            g2d = (Graphics2D) buffer.getGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        int shakeX = 0, shakeY = 0;
        if (screenShake > 0) {
            shakeX = rand.nextInt(screenShake * 2) - screenShake;
            shakeY = rand.nextInt(screenShake * 2) - screenShake;
            screenShake--;
        }

        g2d.translate(shakeX, shakeY);

        GradientPaint bgGradient = new GradientPaint(0, 0, BG_DARK, 0, GAME_HEIGHT, BG_LIGHT);
        g2d.setPaint(bgGradient);
        g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        for (Star star : stars) {
            star.update();
            star.draw(g2d);
        }

        draw(g2d);

        g2d.translate(-shakeX, -shakeY);
        g.drawImage(buffer, 0, 0, this);
    }

    private void draw(Graphics2D g) {
        drawArena(g);
        
        if (state != State.EXIT_CONFIRM && state != State.MP_LOGIN && state != State.MP_LOBBY) {
            drawSpeakerIcon(g);
        }

        switch (state) {
            case MENU:
                drawMenu(g);
                break;
            case DIFFICULTY:
                menuAnimationTimer += 0.016f;
                titleGlowPhase += 0.05f;
                drawMenu(g);
                drawDifficultySelect(g);
                drawFooter(g);
                break;
            case COUNTDOWN:
                updateParticles();
                drawParticles(g);
                player.draw(g);
                cpu.draw(g);
                ball.draw(g);
                score.draw(g);
                drawCountdown(g);
                break;
            case PLAYING:
            case PAUSED:
                updateParticles();
                drawParticles(g);
                player.draw(g);
                cpu.draw(g);
                ball.draw(g);
                score.draw(g);
                drawSpeedBar(g);
                if (state == State.PAUSED) drawPause(g);
                if (mpManager.isMultiplayerActive()) {
                    drawMultiplayerStatus(g);
                }
                break;
            case GAMEOVER:
                drawParticles(g);
                player.draw(g);
                cpu.draw(g);
                ball.draw(g);
                score.draw(g);
                drawGameOver(g);
                drawFooter(g);
                break;
            case EXIT_CONFIRM:
                drawExitConfirm(g);
                break;
            case MP_LOGIN:
                drawMultiplayerLogin(g);
                break;
            case MP_REGISTER:
                drawMultiplayerRegister(g);
                break;
            case MP_LOBBY:
                drawMultiplayerLobby(g);
                break;
            case MP_WAITING:
                drawMultiplayerWaiting(g);
                break;
            case MP_ACCOUNT:
                drawMultiplayerAccount(g);
                break;
        }
        // Help overlay drawn on top of any screen
        if (showHelp) drawHelpOverlay(g);
        // Persistent F1 hint in bottom-right corner (except when help is open)
        if (!showHelp && state != State.MENU) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 13));
            FontMetrics hfm = g.getFontMetrics();
            String helpLabel = "H  HELP";
            int hw = hfm.stringWidth(helpLabel) + 18;
            int hx = GAME_WIDTH - hw - 10;
            int hy = GAME_HEIGHT - 38;
            g.setColor(new Color(80, 20, 160, 200));
            g.fillRoundRect(hx, hy, hw, 22, 11, 11);
            g.setColor(new Color(180, 100, 255, 255));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(hx, hy, hw, 22, 11, 11);
            g.setColor(Color.WHITE);
            g.drawString(helpLabel, hx + 9, hy + 16);
        }
    }

    private void drawHelpOverlay(Graphics2D g) {
        // Dark translucent backdrop
        g.setColor(new Color(0, 0, 10, 210));
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        int panelW = 620; int panelH = 480;
        int px = (GAME_WIDTH - panelW) / 2;
        int py = (GAME_HEIGHT - panelH) / 2;

        // Panel glow + fill
        for (int i = 5; i > 0; i--) {
            g.setColor(new Color(80, 0, 200, i * 8));
            g.fillRoundRect(px - i*3, py - i*3, panelW + i*6, panelH + i*6, 22, 22);
        }
        GradientPaint pg = new GradientPaint(px, py, new Color(12, 8, 35, 252), px, py + panelH, new Color(6, 4, 20, 252));
        g.setPaint(pg);
        g.fillRoundRect(px, py, panelW, panelH, 18, 18);
        g.setColor(new Color(100, 0, 255, 180));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(px, py, panelW, panelH, 18, 18);

        // Title
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(180, 100, 255));
        String title = "KEYBOARD REFERENCE";
        g.drawString(title, px + panelW/2 - fm.stringWidth(title)/2, py + 38);
        g.setColor(new Color(60, 40, 100));
        g.setStroke(new BasicStroke(1));
        g.drawLine(px + 30, py + 50, px + panelW - 30, py + 50);

        // Two-column layout
        String[][] left = {
            {"GAMEPLAY", null},
            {"W / ↑",        "Move paddle up"},
            {"S / ↓",        "Move paddle down"},
            {"ESC",          "Pause game"},
            {"ESC (paused)", "Leave / forfeit"},
            {"ENTER (paused)","Resume"},
            {"M",            "Toggle sound"},
            {"", null},
            {"MENUS", null},
            {"ENTER",        "Confirm / select"},
            {"ESC",          "Back / cancel"},
            {"TAB / ↑↓",     "Switch fields"},
        };
        String[][] right = {
            {"MULTIPLAYER", null},
            {"O  (menu)",    "Open online lobby"},
            {"A  (lobby)",   "My account"},
            {"↑↓ (lobby)",   "Select room"},
            {"ENTER (lobby)","Join / create room"},
            {"ESC (lobby)",  "Back to main menu"},
            {"", null},
            {"ACCOUNT", null},
            {"←/→",          "Switch tabs"},
            {"TAB",          "Switch fields"},
            {"ENTER",        "Save changes"},
            {"H",            "Toggle this help"},
        };

        int colW = panelW / 2 - 20;
        int startY = py + 70;
        int rowH = 26;
        int col1X = px + 20;
        int col2X = px + panelW / 2 + 10;

        for (int col = 0; col < 2; col++) {
            String[][] rows = col == 0 ? left : right;
            int cx = col == 0 ? col1X : col2X;
            int y = startY;
            for (String[] row : rows) {
                if (row[1] == null) {
                    // Section header
                    if (!row[0].isEmpty()) {
                        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        g.setColor(new Color(140, 60, 255));
                        g.drawString(row[0], cx, y + 14);
                        g.setColor(new Color(50, 30, 80));
                        g.drawLine(cx, y + 17, cx + colW - 10, y + 17);
                    }
                    y += 22;
                } else {
                    g.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    g.setColor(new Color(0, 220, 140));
                    g.drawString(row[0], cx, y + 14);
                    g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    g.setColor(new Color(170, 165, 210));
                    g.drawString(row[1], cx + 140, y + 14);
                    y += rowH;
                }
            }
        }

        // Footer
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        g.setColor(new Color(80, 70, 120));
        String close = "Press H or ESC to close";
        g.drawString(close, px + panelW/2 - fm.stringWidth(close)/2, py + panelH - 14);
    }

    private void drawMultiplayerStatus(Graphics2D g) {
        g.setColor(new Color(0, 255, 150, 200));
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        String status = mpManager.isHost() ? "🟢 HOST" : "🔵 CLIENT";
        g.drawString(status, 20, 25);
        
        g.setColor(mpManager.isConnected() ? new Color(0, 255, 100) : new Color(255, 50, 50));
        g.fillOval(GAME_WIDTH - 30, 15, 12, 12);
    }

    private void drawInputBox(Graphics2D g, int x, int y, int w, int h, String label, String value, boolean active, boolean masked) {
        // Glow behind active box
        if (active) {
            for (int i = 4; i > 0; i--) {
                g.setColor(new Color(0, 255, 150, i * 18));
                g.fillRoundRect(x - i*2, y - i*2, w + i*4, h + i*4, 14, 14);
            }
        }
        // Box background
        GradientPaint bp = new GradientPaint(x, y, new Color(20, 25, 50), x, y+h, new Color(10, 15, 35));
        g.setPaint(bp);
        g.fillRoundRect(x, y, w, h, 10, 10);
        // Border
        g.setColor(active ? ACCENT_GREEN : new Color(70, 70, 110));
        g.setStroke(new BasicStroke(active ? 2.5f : 1.5f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        // Label above
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(active ? ACCENT_GREEN : TEXT_SECONDARY);
        g.drawString(label, x, y - 8);
        // Value text + blinking cursor
        g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        g.setColor(TEXT_PRIMARY);
        String display = masked ? "*".repeat(value.length()) : value;
        boolean showCursor = active && ((int)(mpCursorBlink * 2) % 2 == 0);
        String full = display + (showCursor ? "|" : "");
        g.drawString(full, x + 14, y + h/2 + 7);
    }

    private void drawMultiplayerLogin(Graphics2D g) {
        // Dark overlay background
        GradientPaint bg = new GradientPaint(0, 0, new Color(2, 4, 18), 0, GAME_HEIGHT, new Color(8, 12, 35));
        g.setPaint(bg);
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        int centerX = GAME_WIDTH / 2;
        int panelW = 500;
        int panelH = 420;
        int panelX = centerX - panelW / 2;
        int panelY = (GAME_HEIGHT - panelH) / 2 - 20;

        // Panel glow
        for (int i = 6; i > 0; i--) {
            g.setColor(new Color(0, 180, 255, i * 8));
            g.fillRoundRect(panelX - i*3, panelY - i*3, panelW + i*6, panelH + i*6, 22, 22);
        }
        // Panel body
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, new Color(15, 18, 45, 245),
            panelX + panelW, panelY + panelH, new Color(8, 10, 30, 245));
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        // Panel border
        g.setColor(new Color(0, 150, 255, 180));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);

        // Title
        g.setFont(new Font("Segoe UI", Font.BOLD, 30));
        FontMetrics fm = g.getFontMetrics();
        String title = "ONLINE MULTIPLAYER";
        // Glow
        g.setColor(new Color(0, 200, 255, 60));
        g.drawString(title, centerX - fm.stringWidth(title)/2 - 2, panelY + 52);
        g.setColor(new Color(0, 200, 255, 60));
        g.drawString(title, centerX - fm.stringWidth(title)/2 + 2, panelY + 52);
        g.setColor(new Color(0, 220, 255));
        g.drawString(title, centerX - fm.stringWidth(title)/2, panelY + 50);

        // Subtitle
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        String sub = "Sign in to your Neon Pong account";
        g.setColor(TEXT_SECONDARY);
        g.drawString(sub, centerX - fm.stringWidth(sub)/2, panelY + 76);

        // Divider
        g.setColor(new Color(50, 60, 100));
        g.setStroke(new BasicStroke(1));
        g.drawLine(panelX + 30, panelY + 90, panelX + panelW - 30, panelY + 90);

        // Advance cursor blink
        mpCursorBlink += 0.016f;

        int fieldX = panelX + 40;
        int fieldW = panelW - 80;
        int fieldH = 48;

        // Email field
        drawInputBox(g, fieldX, panelY + 115, fieldW, fieldH, "EMAIL ADDRESS", mpEmail, mpEmailMode, false);

        // Password field
        drawInputBox(g, fieldX, panelY + 210, fieldW, fieldH, "PASSWORD", mpPassword, !mpEmailMode, true);

        // Active field indicator arrow
        int arrowX = panelX + 18;
        int arrowY = mpEmailMode ? panelY + 115 + fieldH/2 : panelY + 210 + fieldH/2;
        g.setColor(ACCENT_GREEN);
        int[] ax = {arrowX, arrowX + 10, arrowX};
        int[] ay = {arrowY - 6, arrowY, arrowY + 6};
        g.fillPolygon(ax, ay, 3);

        // Error message
        if (!mpLoginError.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            fm = g.getFontMetrics();
            g.setColor(ACCENT_RED);
            g.drawString("⚠ " + mpLoginError, centerX - fm.stringWidth("⚠ " + mpLoginError)/2, panelY + 283);
        }

        // Loading state
        if (mpManager.isLoading()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            fm = g.getFontMetrics();
            g.setColor(ACCENT_YELLOW);
            String loadMsg = "Connecting...";
            g.drawString(loadMsg, centerX - fm.stringWidth(loadMsg)/2, panelY + 283);
        }

        // Login button
        int btnW = 220; int btnH = 44;
        int btnX = centerX - btnW/2; int btnY = panelY + 300;
        boolean canLogin = !mpEmail.isEmpty() && !mpPassword.isEmpty() && !mpManager.isLoading();
        Color btnColor = canLogin ? ACCENT_GREEN : new Color(40, 60, 40);
        // Button glow
        if (canLogin) {
            for (int i = 3; i > 0; i--) {
                g.setColor(new Color(0, 255, 150, i * 25));
                g.fillRoundRect(btnX - i*2, btnY - i*2, btnW + i*4, btnH + i*4, 14, 14);
            }
        }
        GradientPaint btnGrad = new GradientPaint(btnX, btnY,
            canLogin ? new Color(0, 180, 100) : new Color(25, 40, 25),
            btnX, btnY + btnH,
            canLogin ? new Color(0, 120, 70) : new Color(15, 25, 15));
        g.setPaint(btnGrad);
        g.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        g.setColor(btnColor);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        fm = g.getFontMetrics();
        String btnLabel = "SIGN IN  →";
        g.setColor(canLogin ? Color.WHITE : new Color(80, 100, 80));
        g.drawString(btnLabel, centerX - fm.stringWidth(btnLabel)/2, btnY + btnH/2 + 6);

        // Register link
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        String regLink = "No account?  Press R to Register";
        g.setColor(new Color(80, 160, 255));
        g.drawString(regLink, centerX - fm.stringWidth(regLink)/2, panelY + panelH - 42);
        // Help hints
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        String hint1 = "TAB/↓  switch field    ENTER  sign in    ESC  cancel";
        g.setColor(new Color(90, 90, 130));
        g.drawString(hint1, centerX - fm.stringWidth(hint1)/2, panelY + panelH - 18);
    }

    private void drawMultiplayerLobby(Graphics2D g) {
        // Periodically fetch open rooms every 3 seconds
        long now = System.currentTimeMillis();
        if (now - mpLastRoomFetch > 3000) {
            mpLastRoomFetch = now;
            mpManager.fetchOpenRooms();
        }

        // Full background
        GradientPaint bg = new GradientPaint(0, 0, new Color(2, 4, 18), 0, GAME_HEIGHT, new Color(8, 12, 35));
        g.setPaint(bg); g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        int W = GAME_WIDTH, H = GAME_HEIGHT, cx = W / 2;

        // Panel
        int panelW = 720; int panelH = 580;
        int panelX = cx - panelW/2; int panelY = (H - panelH)/2 - 10;
        for (int i = 6; i > 0; i--) { g.setColor(new Color(150, 0, 255, i * 7)); g.fillRoundRect(panelX-i*3, panelY-i*3, panelW+i*6, panelH+i*6, 22, 22); }
        GradientPaint pg = new GradientPaint(panelX, panelY, new Color(15, 10, 45, 248), panelX+panelW, panelY+panelH, new Color(8, 5, 30, 248));
        g.setPaint(pg); g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setColor(new Color(130, 0, 255, 180)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);

        FontMetrics fm;

        // ── HEADER ───────────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 26)); fm = g.getFontMetrics();
        g.setColor(new Color(200, 100, 255));
        g.drawString("GAME LOBBY", cx - fm.stringWidth("GAME LOBBY")/2, panelY + 42);
        // Account button
        renderButton(g, panelX + panelW - 170, panelY + 14, 155, 30, "MY ACCOUNT  ⚙", true, new Color(50, 80, 150));
        // Divider
        g.setColor(new Color(60, 40, 90)); g.setStroke(new BasicStroke(1));
        g.drawLine(panelX + 24, panelY + 56, panelX + panelW - 24, panelY + 56);

        // ── SECTION A: CREATE A ROOM ─────────────────────────────────────────
        int sectionX = panelX + 24; int sectionW = panelW - 48;

        // Section label
        g.setFont(new Font("Segoe UI", Font.BOLD, 13)); fm = g.getFontMetrics();
        g.setColor(new Color(180, 80, 255));
        g.drawString("① CREATE A NEW ROOM", sectionX, panelY + 80);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12)); fm = g.getFontMetrics();
        g.setColor(TEXT_SECONDARY);
        g.drawString("Pick a name, create the room and wait for someone to join.", sectionX, panelY + 95);

        // Create input + button inline  (label drawn 8px above box → box at +118 means label at +110, clear of description at +95)
        int createFieldW = sectionW - 170; int fieldH = 42;
        boolean createFocused = mpSelectedRoom == -2;
        drawInputBox(g, sectionX, panelY + 118, createFieldW, fieldH, "ROOM NAME", mpCreateRoomInput, createFocused, false);
        boolean canCreate = !mpCreateRoomInput.isEmpty() && !mpManager.isLoading();
        renderButton(g, sectionX + createFieldW + 14, panelY + 118, sectionW - createFieldW - 14, fieldH, "CREATE  →", canCreate, new Color(120, 0, 200));

        // ── DIVIDER ──────────────────────────────────────────────────────────
        int divY = panelY + 162;
        g.setColor(new Color(60, 40, 90)); g.setStroke(new BasicStroke(1));
        g.drawLine(sectionX, divY, sectionX + sectionW, divY);

        // ── SECTION B: QUICK JOIN ────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 13)); fm = g.getFontMetrics();
        g.setColor(new Color(0, 210, 100));
        g.drawString("② QUICK JOIN", sectionX, panelY + 180);
        String statsStr = "● " + mpOnlinePlayers + " online   ✕ " + mpActiveRooms + " playing";
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11)); fm = g.getFontMetrics();
        g.setColor(new Color(90, 85, 130));
        g.drawString(statsStr, sectionX + sectionW - fm.stringWidth(statsStr), panelY + 180);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11)); fm = g.getFontMetrics();
        g.setColor(TEXT_SECONDARY);
        g.drawString("Ranked rooms — always open.  ←→ pick, ENTER to join.", sectionX, panelY + 194);

        // ── 4 PERSISTENT ROOM CARDS ──────────────────────────────────────────
        int cardY = panelY + 202; int cardH = 64; int cardGap = 8;
        int cardW = (sectionW - cardGap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            String pName = PERSISTENT_ROOMS[i][0];
            Color rc = Color.decode(PERSISTENT_ROOMS[i][1]);
            String pStat = (i < mpPersistentRooms.size()) ? mpPersistentRooms.get(i)[2] : "...";
            boolean occupied = "occupied".equals(pStat);
            boolean busy = "playing".equals(pStat);
            boolean sel = (mpSelectedRoom == -10 - i);
            int cx2 = sectionX + i * (cardW + cardGap);
            if (sel) {
                for (int s = 4; s > 0; s--) {
                    g.setColor(new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), s * 20));
                    g.fillRoundRect(cx2 - s*2, cardY - s*2, cardW + s*4, cardH + s*4, 12, 12);
                }
            }
            Color bgDark = new Color(Math.max(0,rc.getRed()/6), Math.max(0,rc.getGreen()/6), Math.max(0,rc.getBlue()/6), 230);
            Color bgMid  = new Color(Math.max(0,rc.getRed()/3), Math.max(0,rc.getGreen()/3), Math.max(0,rc.getBlue()/3), 220);
            g.setPaint(new GradientPaint(cx2, cardY, sel ? bgMid : bgDark, cx2, cardY+cardH, bgDark));
            g.fillRoundRect(cx2, cardY, cardW, cardH, 8, 8);
            g.setColor(new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), sel ? 240 : 90));
            g.setStroke(new BasicStroke(sel ? 2f : 1f));
            g.drawRoundRect(cx2, cardY, cardW, cardH, 8, 8);
            // Name
            g.setFont(new Font("Segoe UI", Font.BOLD, 15)); fm = g.getFontMetrics();
            g.setColor(new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), sel ? 255 : 200));
            g.drawString(pName, cx2 + cardW/2 - fm.stringWidth(pName)/2, cardY + 25);
            // Status pill
            Color stCol = busy ? new Color(255, 150, 0) : occupied ? new Color(255, 200, 0) : new Color(0, 210, 100);
            String stTxt = busy ? "● PLAYING" : occupied ? "● 1 WAITING" : "● OPEN";
            g.setFont(new Font("Segoe UI", Font.BOLD, 10)); fm = g.getFontMetrics();
            int pillW = fm.stringWidth(stTxt) + 10;
            g.setColor(new Color(stCol.getRed(), stCol.getGreen(), stCol.getBlue(), 50));
            g.fillRoundRect(cx2 + cardW/2 - pillW/2, cardY + 33, pillW, 16, 8, 8);
            g.setColor(stCol);
            g.drawString(stTxt, cx2 + cardW/2 - fm.stringWidth(stTxt)/2, cardY + 45);
            if (sel) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 9)); fm = g.getFontMetrics();
                g.setColor(new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), 170));
                String et = "ENTER to join";
                g.drawString(et, cx2 + cardW/2 - fm.stringWidth(et)/2, cardY + cardH - 4);
            }
        }

        // ── CUSTOM ROOMS (if any) ─────────────────────────────────────────────
        int listY = cardY + cardH + 8;
        if (!mpRoomList.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 11)); fm = g.getFontMetrics();
            g.setColor(new Color(130, 110, 170));
            g.drawString("CUSTOM ROOMS", sectionX, listY + 12);
            listY += 16;
            int rowH = 36; int listW = sectionW;
            for (int i = 0; i < mpRoomList.size() && i < 2; i++) {
                String[] room = mpRoomList.get(i);
                boolean sel = (mpSelectedRoom == i);
                int ry = listY + i * (rowH + 4);
                if (sel) { for (int s = 2; s > 0; s--) { g.setColor(new Color(0, 190, 90, s*18)); g.fillRoundRect(sectionX-s*2, ry-s*2, listW+s*4, rowH+s*4, 8, 8); } }
                g.setPaint(new GradientPaint(sectionX, ry, sel ? new Color(0,45,22) : new Color(18,15,38), sectionX+listW, ry+rowH, new Color(10,8,24)));
                g.fillRoundRect(sectionX, ry, listW, rowH, 6, 6);
                g.setColor(sel ? new Color(0,200,90) : new Color(42,36,68)); g.setStroke(new BasicStroke(sel ? 1.5f : 1f));
                g.drawRoundRect(sectionX, ry, listW, rowH, 6, 6);
                g.setFont(new Font("Segoe UI", Font.BOLD, 14)); fm = g.getFontMetrics();
                g.setColor(sel ? Color.WHITE : new Color(185,180,225));
                g.drawString(room[0], sectionX + 14, ry + 23);
                g.setFont(new Font("Segoe UI", Font.BOLD, 10)); fm = g.getFontMetrics();
                g.setColor(new Color(0,200,90));
                g.drawString("● WAITING", sectionX + 14, ry + 33);
                String act = sel ? "ENTER ↵ to join" : "↑↓ to select";
                g.setFont(new Font("Segoe UI", Font.PLAIN, 11)); fm = g.getFontMetrics();
                g.setColor(sel ? new Color(0,220,110) : new Color(65,58,95));
                g.drawString(act, sectionX + listW - fm.stringWidth(act) - 12, ry + rowH/2 + 5);
            }
            listY += Math.min(mpRoomList.size(), 2) * (rowH + 4) + 4;
        }

        // ── DIRECT ID JOIN ───────────────────────────────────────────────────
        g.setColor(new Color(50, 40, 70)); g.setStroke(new BasicStroke(1));
        g.drawLine(sectionX, listY + 2, sectionX + sectionW, listY + 2);
        g.setFont(new Font("Segoe UI", Font.BOLD, 11)); fm = g.getFontMetrics();
        g.setColor(new Color(0, 150, 240));
        g.drawString("Or join directly by Room ID:", sectionX, listY + 17);
        int joinFieldW = sectionW - 130;
        drawInputBox(g, sectionX, listY + 22, joinFieldW, 32, "", mpRoomIdInput, mpSelectedRoom == -3, false);
        boolean canJoin = !mpRoomIdInput.isEmpty() && !mpManager.isLoading();
        renderButton(g, sectionX + joinFieldW + 10, listY + 22, sectionW - joinFieldW - 10, 32, "JOIN  →", canJoin, new Color(0, 110, 200));

        // ── STATUS / ERROR ───────────────────────────────────────────────────
        if (mpManager.isLoading()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 13)); fm = g.getFontMetrics();
            g.setColor(ACCENT_YELLOW);
            g.drawString("Connecting...", cx - fm.stringWidth("Connecting...")/2, panelY + panelH - 34);
        } else if (!mpLoginError.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 12)); fm = g.getFontMetrics();
            g.setColor(ACCENT_RED);
            g.drawString(mpLoginError, cx - fm.stringWidth(mpLoginError)/2, panelY + panelH - 34);
        }

        // ── FOOTER HINTS ─────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11)); fm = g.getFontMetrics();
        String hint = "←→  pick room    ENTER  join    A  account    ESC  main menu";
        g.setColor(new Color(60, 50, 90));
        g.drawString(hint, cx - fm.stringWidth(hint)/2, panelY + panelH - 14);
    }

    private void drawMultiplayerWaiting(Graphics2D g) {
        GradientPaint bg = new GradientPaint(0, 0, new Color(2, 4, 18), 0, GAME_HEIGHT, new Color(8, 12, 35));
        g.setPaint(bg);
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        int centerX = GAME_WIDTH / 2;
        int panelW = 500; int panelH = 300;
        int panelX = centerX - panelW/2;
        int panelY = (GAME_HEIGHT - panelH)/2;
        for (int i = 6; i > 0; i--) {
            g.setColor(new Color(255, 200, 0, i * 8));
            g.fillRoundRect(panelX-i*3, panelY-i*3, panelW+i*6, panelH+i*6, 22, 22);
        }
        GradientPaint pg = new GradientPaint(panelX, panelY, new Color(20, 18, 5, 245), panelX+panelW, panelY+panelH, new Color(12, 10, 2, 245));
        g.setPaint(pg);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setColor(new Color(255, 200, 0, 180)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        // Animated dots
        float dots = (mpCursorBlink % 3);
        mpCursorBlink += 0.02f;
        String dotStr = dots < 1 ? "." : dots < 2 ? ".." : "...";
        g.setFont(new Font("Segoe UI", Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();
        String title = "Waiting for opponent" + dotStr;
        g.setColor(ACCENT_YELLOW);
        g.drawString(title, centerX - fm.stringWidth(title)/2, panelY + 80);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 16)); fm = g.getFontMetrics();
        g.setColor(TEXT_SECONDARY);
        String msg = "Share this Room ID with your opponent:";
        g.drawString(msg, centerX - fm.stringWidth(msg)/2, panelY + 125);
        g.setFont(new Font("Segoe UI", Font.BOLD, 32)); fm = g.getFontMetrics();
        g.setColor(new Color(0, 255, 180));
        String rid = mpRoomIdInput;
        g.drawString(rid, centerX - fm.stringWidth(rid)/2, panelY + 175);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13)); fm = g.getFontMetrics();
        g.setColor(new Color(255, 100, 100));
        String cancel = "ESC to cancel and return to lobby";
        g.drawString(cancel, centerX - fm.stringWidth(cancel)/2, panelY + 240);
    }

    private void drawMultiplayerRegister(Graphics2D g) {
        GradientPaint bg = new GradientPaint(0, 0, new Color(2, 4, 18), 0, GAME_HEIGHT, new Color(8, 12, 35));
        g.setPaint(bg);
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        int centerX = GAME_WIDTH / 2;
        int panelW = 500; int panelH = 460;
        int panelX = centerX - panelW/2;
        int panelY = (GAME_HEIGHT - panelH)/2 - 20;
        for (int i = 6; i > 0; i--) {
            g.setColor(new Color(255, 100, 0, i * 7));
            g.fillRoundRect(panelX - i*3, panelY - i*3, panelW + i*6, panelH + i*6, 22, 22);
        }
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, new Color(18, 12, 5, 245), panelX + panelW, panelY + panelH, new Color(10, 6, 2, 245));
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setColor(new Color(255, 120, 0, 180));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setFont(new Font("Segoe UI", Font.BOLD, 30));
        FontMetrics fm = g.getFontMetrics();
        String title = "CREATE ACCOUNT";
        g.setColor(new Color(255, 160, 50));
        g.drawString(title, centerX - fm.stringWidth(title)/2, panelY + 50);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        String sub = "Register to play online multiplayer";
        g.setColor(TEXT_SECONDARY);
        g.drawString(sub, centerX - fm.stringWidth(sub)/2, panelY + 76);
        g.setColor(new Color(60, 40, 20));
        g.setStroke(new BasicStroke(1));
        g.drawLine(panelX + 30, panelY + 90, panelX + panelW - 30, panelY + 90);
        mpCursorBlink += 0.016f;
        int fieldX = panelX + 40; int fieldW = panelW - 80; int fieldH = 48;
        drawInputBox(g, fieldX, panelY + 110, fieldW, fieldH, "EMAIL ADDRESS", mpEmail, mpAccountField == 0, false);
        drawInputBox(g, fieldX, panelY + 205, fieldW, fieldH, "PASSWORD (min 8 chars)", mpPassword, mpAccountField == 1, true);
        drawInputBox(g, fieldX, panelY + 300, fieldW, fieldH, "CONFIRM PASSWORD", mpConfirmPassword, mpAccountField == 2, true);
        int arrowX = panelX + 18;
        int[] arrowYs = {panelY + 110 + fieldH/2, panelY + 205 + fieldH/2, panelY + 300 + fieldH/2};
        g.setColor(new Color(255, 160, 50));
        int ay = arrowYs[mpAccountField];
        g.fillPolygon(new int[]{arrowX, arrowX+10, arrowX}, new int[]{ay-6, ay, ay+6}, 3);
        if (!mpLoginError.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            fm = g.getFontMetrics();
            boolean isOk = mpLoginError.startsWith("Account created");
            g.setColor(isOk ? ACCENT_GREEN : ACCENT_RED);
            g.drawString(mpLoginError, centerX - fm.stringWidth(mpLoginError)/2, panelY + 378);
        }
        if (mpManager.isLoading()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 14)); fm = g.getFontMetrics();
            g.setColor(ACCENT_YELLOW);
            g.drawString("Creating account...", centerX - fm.stringWidth("Creating account...")/2, panelY + 378);
        }
        int btnW = 220; int btnH = 44;
        int btnX = centerX - btnW/2; int btnY = panelY + 395;
        boolean canReg = !mpEmail.isEmpty() && !mpPassword.isEmpty() && !mpConfirmPassword.isEmpty() && !mpManager.isLoading();
        if (canReg) { for (int i = 3; i > 0; i--) { g.setColor(new Color(255, 140, 0, i*20)); g.fillRoundRect(btnX-i*2, btnY-i*2, btnW+i*4, btnH+i*4, 14, 14); } }
        GradientPaint btnGrad = new GradientPaint(btnX, btnY, canReg ? new Color(200, 100, 0) : new Color(40, 25, 5), btnX, btnY+btnH, canReg ? new Color(140, 60, 0) : new Color(25, 15, 2));
        g.setPaint(btnGrad);
        g.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        g.setColor(canReg ? new Color(255, 160, 50) : new Color(80, 50, 20));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16)); fm = g.getFontMetrics();
        String btnLabel = "CREATE ACCOUNT  →";
        g.setColor(canReg ? Color.WHITE : new Color(80, 60, 30));
        g.drawString(btnLabel, centerX - fm.stringWidth(btnLabel)/2, btnY + btnH/2 + 6);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12)); fm = g.getFontMetrics();
        String hint2 = "TAB/↓  next field    ENTER  register    ESC  back to login";
        g.setColor(new Color(90, 70, 40));
        g.drawString(hint2, centerX - fm.stringWidth(hint2)/2, panelY + panelH - 18);
    }

    private void drawMultiplayerAccount(Graphics2D g) {
        GradientPaint bg = new GradientPaint(0, 0, new Color(2, 4, 18), 0, GAME_HEIGHT, new Color(8, 12, 35));
        g.setPaint(bg);
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        int centerX = GAME_WIDTH / 2;
        int panelW = 560; int panelH = 490;
        int panelX = centerX - panelW/2;
        int panelY = (GAME_HEIGHT - panelH)/2 - 20;
        for (int i = 6; i > 0; i--) { g.setColor(new Color(50, 150, 255, i*7)); g.fillRoundRect(panelX-i*3, panelY-i*3, panelW+i*6, panelH+i*6, 22, 22); }
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, new Color(8, 15, 35, 245), panelX+panelW, panelY+panelH, new Color(5, 10, 25, 245));
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setColor(new Color(50, 150, 255, 180)); g.setStroke(new BasicStroke(2));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setFont(new Font("Segoe UI", Font.BOLD, 28)); FontMetrics fm = g.getFontMetrics();
        String title = "MY ACCOUNT";
        g.setColor(new Color(100, 180, 255));
        g.drawString(title, centerX - fm.stringWidth(title)/2, panelY + 48);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13)); fm = g.getFontMetrics();
        g.setColor(TEXT_SECONDARY);
        String idStr = "ID: " + mpManager.getUserId();
        g.drawString(idStr, centerX - fm.stringWidth(idStr)/2, panelY + 72);
        g.setColor(new Color(40, 60, 100)); g.setStroke(new BasicStroke(1));
        g.drawLine(panelX + 30, panelY + 85, panelX + panelW - 30, panelY + 85);
        // Tab buttons
        String[] tabs = {"Change Email", "Change Password", "Delete Account"};
        Color[] tabColors = {new Color(0, 180, 255), new Color(0, 200, 100), new Color(255, 80, 80)};
        int tabW = (panelW - 80) / 3; int tabH = 36; int tabY = panelY + 100; int tabStartX = panelX + 40;
        for (int i = 0; i < tabs.length; i++) {
            int tx = tabStartX + i * (tabW + 10);
            boolean sel = mpAccountTab == i;
            g.setColor(sel ? tabColors[i] : new Color(30, 40, 60));
            g.fillRoundRect(tx, tabY, tabW, tabH, 8, 8);
            g.setColor(sel ? tabColors[i].brighter() : new Color(80, 100, 130));
            g.setStroke(new BasicStroke(sel ? 2 : 1));
            g.drawRoundRect(tx, tabY, tabW, tabH, 8, 8);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12)); fm = g.getFontMetrics();
            g.setColor(sel ? Color.WHITE : new Color(120, 140, 170));
            g.drawString(tabs[i], tx + tabW/2 - fm.stringWidth(tabs[i])/2, tabY + tabH/2 + 5);
        }
        mpCursorBlink += 0.016f;
        int fieldX = panelX + 40; int fieldW = panelW - 80; int fieldH = 46;
        int contentY = panelY + 175;
        if (mpAccountTab == 0) {
            drawInputBox(g, fieldX, contentY, fieldW, fieldH, "NEW EMAIL ADDRESS", mpAccountNewEmail, true, false);
            int btnW = 200; int btnH = 40; int btnX = centerX - btnW/2; int btnY = contentY + 70;
            boolean can = !mpAccountNewEmail.isEmpty() && !mpManager.isLoading();
            renderButton(g, btnX, btnY, btnW, btnH, "UPDATE EMAIL  →", can, new Color(0, 150, 220));
        } else if (mpAccountTab == 1) {
            drawInputBox(g, fieldX, contentY, fieldW, fieldH, "NEW PASSWORD", mpAccountNewPassword, mpAccountField == 0, true);
            drawInputBox(g, fieldX, contentY + 80, fieldW, fieldH, "CONFIRM PASSWORD", mpAccountConfirm, mpAccountField == 1, true);
            int btnW = 220; int btnH = 40; int btnX = centerX - btnW/2; int btnY = contentY + 160;
            boolean can = !mpAccountNewPassword.isEmpty() && !mpAccountConfirm.isEmpty() && !mpManager.isLoading();
            renderButton(g, btnX, btnY, btnW, btnH, "UPDATE PASSWORD  →", can, new Color(0, 180, 80));
        } else if (mpAccountTab == 2) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 18)); fm = g.getFontMetrics();
            g.setColor(new Color(255, 100, 100));
            String warn = "⚠ This will permanently delete your account";
            g.drawString(warn, centerX - fm.stringWidth(warn)/2, contentY + 40);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 14)); fm = g.getFontMetrics();
            g.setColor(TEXT_SECONDARY);
            String warn2 = "All your data will be lost. This cannot be undone.";
            g.drawString(warn2, centerX - fm.stringWidth(warn2)/2, contentY + 70);
            int btnW = 240; int btnH = 44; int btnX = centerX - btnW/2; int btnY = contentY + 100;
            renderButton(g, btnX, btnY, btnW, btnH, "DELETE MY ACCOUNT", !mpManager.isLoading(), new Color(200, 50, 50));
        }
        if (!mpLoginError.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 13)); fm = g.getFontMetrics();
            boolean isOk = mpLoginError.endsWith("!");
            g.setColor(isOk ? ACCENT_GREEN : ACCENT_RED);
            g.drawString(mpLoginError, centerX - fm.stringWidth(mpLoginError)/2, panelY + panelH - 88);
        }
        if (mpManager.isLoading()) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 13)); fm = g.getFontMetrics();
            g.setColor(ACCENT_YELLOW);
            g.drawString("Saving...", centerX - fm.stringWidth("Saving...")/2, panelY + panelH - 88);
        }
        // Hint line ABOVE the buttons
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12)); fm = g.getFontMetrics();
        String hint3 = "TAB  fields    ENTER  save    ←/→  tabs    ESC  lobby    S  sign out";
        g.setColor(new Color(60, 80, 110));
        g.drawString(hint3, centerX - fm.stringWidth(hint3)/2, panelY + panelH - 56);
        // Buttons at the very bottom
        int btnW2 = 160; int btnH2 = 36;
        int logoutX = panelX + 40; int backX = panelX + panelW - 40 - btnW2;
        int botBtnY = panelY + panelH - 44;
        renderButton(g, logoutX, botBtnY, btnW2, btnH2, "SIGN OUT  [S]", true, new Color(150, 80, 0));
        renderButton(g, backX, botBtnY, btnW2, btnH2, "BACK  [ESC]", true, new Color(60, 80, 120));
    }

    private void renderButton(Graphics2D g, int x, int y, int w, int h, String label, boolean active, Color col) {
        if (active) { for (int i = 3; i > 0; i--) { g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), i*18)); g.fillRoundRect(x-i*2, y-i*2, w+i*4, h+i*4, 12, 12); } }
        GradientPaint gp = new GradientPaint(x, y, active ? col : new Color(30,35,50), x, y+h, active ? col.darker() : new Color(20,25,40));
        g.setPaint(gp);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(active ? col.brighter() : new Color(60, 70, 90)); g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setFont(new Font("Segoe UI", Font.BOLD, 13)); FontMetrics fm = g.getFontMetrics();
        g.setColor(active ? Color.WHITE : new Color(80, 90, 110));
        g.drawString(label, x + w/2 - fm.stringWidth(label)/2, y + h/2 + 5);
    }

    private void drawFooter(Graphics2D g) {
        int centerX = GAME_WIDTH / 2; 
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(100, 100, 150, 150));
        String text = "© 2026 DeeWHY • Created by DeeWHY • All Rights Reserved";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, centerX - fm.stringWidth(text) / 2, GAME_HEIGHT - 15);
    }

    private void drawSpeakerIcon(Graphics2D g) {
        int centerX = GAME_WIDTH - 70;
        int centerY = 50;
        
        g.setFont(EMOJI_FONT_LARGE);
        String speakerIcon = soundEnabled ? "🔊" : "🔇";
        
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(speakerIcon);
        int textHeight = fm.getAscent();
        
        int emojiX = centerX - textWidth / 2;
        int emojiY = centerY + textHeight / 3;
        
        g.setColor(soundEnabled ? SPEAKER_ON : SPEAKER_OFF);
        g.drawString(speakerIcon, emojiX, emojiY);
        
        int circleRadius = 25;
        
        g.setColor(new Color(30, 30, 50, 230));
        g.fillOval(centerX - circleRadius, centerY - circleRadius, 
                   circleRadius * 2, circleRadius * 2);
        
        g.setColor(soundEnabled ? SPEAKER_ON : SPEAKER_OFF);
        g.drawString(speakerIcon, emojiX, emojiY);
        
        for (int i = 2; i > 0; i--) {
            float alpha = 0.15f - (i * 0.05f);
            Color glowColor = soundEnabled ? SPEAKER_ON : SPEAKER_OFF;
            g.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), (int)(alpha * 255)));
            g.fillOval(centerX - circleRadius - i, centerY - circleRadius - i, 
                       (circleRadius + i) * 2, (circleRadius + i) * 2);
        }
        
        g.setColor(soundEnabled ? SPEAKER_ON : SPEAKER_OFF);
        g.setStroke(new BasicStroke(2));
        g.drawOval(centerX - circleRadius, centerY - circleRadius, 
                   circleRadius * 2, circleRadius * 2);
    }

    private void drawArena(Graphics2D g) {
        for (int i = 0; i < 3; i++) {
            float alpha = 0.3f - (i * 0.1f);
            g.setColor(new Color(100, 100, 200, (int) (alpha * 255)));
            g.setStroke(new BasicStroke(6 + i * 2));
            g.drawRoundRect(10 - i * 3, 10 - i * 3, GAME_WIDTH - 20 + i * 6, GAME_HEIGHT - 20 + i * 6, 20, 20);
        }

        g.setColor(new Color(50, 50, 100));
        g.setStroke(new BasicStroke(4)); 
        g.drawRoundRect(10, 10, GAME_WIDTH - 20, GAME_HEIGHT - 20, 20, 20);

        g.setColor(new Color(80, 80, 150));
        g.setStroke(new BasicStroke(3));
        float dashPhase = (float) (menuAnimationTimer * 20) % 40;
        float[] dashPattern = {20, 20};
        Stroke dashed = new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dashPattern, dashPhase);
        g.setStroke(dashed);
        g.drawLine(GAME_WIDTH / 2, 30, GAME_WIDTH / 2, GAME_HEIGHT - 30);

        g.setColor(new Color(60, 60, 120, 100));
        g.fillOval(GAME_WIDTH / 2 - 60, GAME_HEIGHT / 2 - 60, 120, 120);
        g.setColor(new Color(100, 100, 180));
        g.drawOval(GAME_WIDTH / 2 - 60, GAME_HEIGHT / 2 - 60, 120, 120);
    }

    private void drawSpeedBar(Graphics2D g) {
        if (state != State.PLAYING && state != State.PAUSED) return;

        int currentSpeed = (int) Math.hypot(ball.dx, ball.dy);
        currentSpeedSmooth = currentSpeedSmooth + (currentSpeed - currentSpeedSmooth) * 0.15f;
        float speedRatio = currentSpeedSmooth / BALL_MAX_SPEED;
        if (speedRatio > 1.0f) speedRatio = 1.0f;
        if (speedRatio < 0.0f) speedRatio = 0.0f;

        int barWidth = 400;
        int barHeight = 24;
        int barX = GAME_WIDTH / 2 - barWidth / 2;
        int barY = GAME_HEIGHT - 55;

        for (int glow = 4; glow > 0; glow--) {
            float alpha = 0.25f - (glow * 0.05f);
            int glowSize = glow * 2;
            g.setColor(new Color(100, 150, 200, (int) (alpha * 255)));
            g.fillRoundRect(barX - glowSize, barY - glowSize, 
                          barWidth + glowSize * 2, barHeight + glowSize * 2, 
                          12 + glowSize, 12 + glowSize);
        }

        g.setColor(SPEED_BAR_BG);
        g.fillRoundRect(barX, barY, barWidth, barHeight, 12, 12);

        g.setColor(SPEED_BAR_BORDER);
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(barX, barY, barWidth, barHeight, 12, 12);

        Color barColor = getSpeedColor(speedRatio);
        
        if (speedRatio > 0.75f) {
            float pulse = (float) (Math.sin(menuAnimationTimer * 10) * 0.25 + 0.85);
            barColor = new Color(
                Math.min(255, (int) (barColor.getRed() / pulse)),
                Math.min(255, (int) (barColor.getGreen() / pulse)),
                Math.min(255, (int) (barColor.getBlue() / pulse))
            );
        }

        int fillWidth = (int) (barWidth * speedRatio);
        if (fillWidth > 4) {
            GradientPaint barGradient = new GradientPaint(
                barX + 3, barY + 3, barColor,
                barX + fillWidth - 3, barY + barHeight - 3, barColor.brighter()
            );
            g.setPaint(barGradient);
            g.fillRoundRect(barX + 3, barY + 3, Math.max(0, fillWidth - 6), barHeight - 6, 10, 10);
            
            g.setColor(new Color(255, 255, 255, 80));
            g.fillRoundRect(barX + 3, barY + 3, Math.max(0, fillWidth - 6), barHeight / 3 - 2, 10, 5);
        }

        if (speedRatio > 0.9f) {
            g.setColor(new Color(255, 50, 50, 150));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(barX, barY, barWidth, barHeight, 12, 12);
        }
    }

    private Color getSpeedColor(float ratio) {
        if (ratio < 0.3f) {
            return interpolateColor(ACCENT_GREEN, ACCENT_YELLOW, ratio / 0.3f);
        } else if (ratio < 0.6f) {
            return interpolateColor(ACCENT_YELLOW, ACCENT_ORANGE, (ratio - 0.3f) / 0.3f);
        } else if (ratio < 0.85f) {
            return interpolateColor(ACCENT_ORANGE, ACCENT_RED, (ratio - 0.6f) / 0.25f);
        } else {
            return ACCENT_RED;
        }
    }

    private Color interpolateColor(Color c1, Color c2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);
        return new Color(r, g, b);
    }

    private void drawCountdown(Graphics2D g) {
        int cx = GAME_WIDTH / 2;
        int cy = GAME_HEIGHT / 2;
        String label = countdownValue > 0 ? String.valueOf(countdownValue) : "GO!";
        Color col = countdownValue > 1 ? new Color(255, 200, 50) :
                    countdownValue == 1 ? new Color(255, 100, 50) : ACCENT_GREEN;

        // Progress arc (drains over 1 second)
        float elapsed = Math.min(1f, (System.currentTimeMillis() - countdownLastTick) / 1000f);
        int radius = 72;

        // Glow layers behind circle (max reach: radius + 6*4 = 96px from cy)
        for (int i = 6; i > 0; i--) {
            int a = i * 16;
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), a));
            int r = radius + i * 4;
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
        // Dark filled circle
        g.setColor(new Color(2, 5, 20, 225));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // Countdown progress ring (arc draining clockwise)
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Background ring (dim)
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 50));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        // Active arc
        g.setColor(col);
        int arcAngle = (int)((1f - elapsed) * 360);
        g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2, 90, arcAngle);

        // Number — use fixed font size, centre precisely using font metrics
        int fontSize = countdownValue == 0 ? 52 : 68;
        g.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int textX = cx - fm.stringWidth(label) / 2;
        // True vertical centre: cy + half ascent - half descent
        int textY = cy + (fm.getAscent() - fm.getDescent()) / 2;
        // Glow on text
        for (int i = 3; i > 0; i--) {
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), i * 40));
            g.drawString(label, textX + i, textY + i);
            g.drawString(label, textX - i, textY - i);
        }
        g.setColor(col);
        g.drawString(label, textX, textY);

        // Matchup names — above the entire glow area (max glow extends ~122px from cy)
        boolean isMP = mpManager.isMultiplayerActive();
        String leftName  = isMP ? mpManager.getDisplayName() : "PHANTOM";
        String rightName = isMP ? "OPPONENT" : "SPECTER";
        Color  leftCol   = PLAYER_GRADIENT_START;
        Color  rightCol  = CPU_GRADIENT_START;
        if (isMP && !mpManager.isHost()) { leftCol = CPU_GRADIENT_START; rightCol = PLAYER_GRADIENT_START; }

        // "GET READY" header — clear of glow
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fm = g.getFontMetrics();
        String header = countdownValue > 0 ? "GET READY" : "GOOD LUCK!";
        g.setColor(new Color(200, 200, 255, 190));
        g.drawString(header, cx - fm.stringWidth(header) / 2, cy - radius - 78);

        // Player names one line below header
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        fm = g.getFontMetrics();
        int nameY = cy - radius - 58;
        g.setColor(leftCol);
        g.drawString(leftName, cx - 18 - fm.stringWidth(leftName), nameY);
        g.setColor(new Color(100, 100, 160));
        g.drawString("vs", cx - fm.stringWidth("vs") / 2, nameY);
        g.setColor(rightCol);
        g.drawString(rightName, cx + 18, nameY);
    }

    // ─── MENU DEMO GAME ──────────────────────────────────────────────────────
    private void initDemoGame() {
        demoBallX = GAME_WIDTH / 2f;
        demoBallY = GAME_HEIGHT / 2f;
        float angle = (float)(Math.random() * Math.PI / 4 - Math.PI / 8);
        demoBallDX = (float)(Math.random() > 0.5 ? 5.5 : -5.5);
        demoBallDY = (float)(Math.sin(angle) * 4);
        demoPlayerY = GAME_HEIGHT / 2f - PADDLE_HEIGHT / 2f;
        demoCpuY    = GAME_HEIGHT / 2f - PADDLE_HEIGHT / 2f;
        demoReady = true;
    }

    private void updateDemoGame() {
        if (!demoReady) return;
        // Move ball
        demoBallX += demoBallDX;
        demoBallY += demoBallDY;
        // Bounce top/bottom
        if (demoBallY <= 0) { demoBallY = 0; demoBallDY = Math.abs(demoBallDY); }
        if (demoBallY >= GAME_HEIGHT - BALL_SIZE) { demoBallY = GAME_HEIGHT - BALL_SIZE; demoBallDY = -Math.abs(demoBallDY); }
        // Simple AI for both paddles
        float target = demoBallY - PADDLE_HEIGHT / 2f + BALL_SIZE / 2f;
        demoPlayerY += (target - demoPlayerY) * 0.09f;
        demoCpuY    += (target - demoCpuY)    * 0.085f;
        demoPlayerY = Math.max(0, Math.min(GAME_HEIGHT - PADDLE_HEIGHT, demoPlayerY));
        demoCpuY    = Math.max(0, Math.min(GAME_HEIGHT - PADDLE_HEIGHT, demoCpuY));
        // Bounce off paddles
        if (demoBallDX < 0 && demoBallX <= 30 + PADDLE_WIDTH &&
                demoBallY + BALL_SIZE >= demoPlayerY && demoBallY <= demoPlayerY + PADDLE_HEIGHT) {
            demoBallDX = Math.abs(demoBallDX) * 1.04f;
            float hit = (demoBallY + BALL_SIZE/2f - demoPlayerY - PADDLE_HEIGHT/2f) / (PADDLE_HEIGHT/2f);
            demoBallDY = hit * 6f;
        }
        if (demoBallDX > 0 && demoBallX + BALL_SIZE >= GAME_WIDTH - 30 - PADDLE_WIDTH &&
                demoBallY + BALL_SIZE >= demoCpuY && demoBallY <= demoCpuY + PADDLE_HEIGHT) {
            demoBallDX = -Math.abs(demoBallDX) * 1.04f;
            float hit = (demoBallY + BALL_SIZE/2f - demoCpuY - PADDLE_HEIGHT/2f) / (PADDLE_HEIGHT/2f);
            demoBallDY = hit * 6f;
        }
        // Cap speed
        float speed = (float)Math.sqrt(demoBallDX*demoBallDX + demoBallDY*demoBallDY);
        if (speed > 14) { demoBallDX = demoBallDX/speed*14; demoBallDY = demoBallDY/speed*14; }
        // Reset if ball exits
        if (demoBallX < -40 || demoBallX > GAME_WIDTH + 40) {
            if (demoBallX < 0) demoP2Score++; else demoP1Score++;
            if (demoP1Score > 9 || demoP2Score > 9) { demoP1Score = 0; demoP2Score = 0; }
            initDemoGame();
        }
    }

    private void drawDemoArenaElements(Graphics2D g) {
        // Dimmed paddles
        Color pCol = new Color(0, 200, 220, 60);
        Color cCol = new Color(220, 50, 130, 60);
        g.setColor(pCol);
        g.fillRoundRect(30, (int)demoPlayerY, PADDLE_WIDTH, PADDLE_HEIGHT, 10, 10);
        g.setColor(cCol);
        g.fillRoundRect(GAME_WIDTH - 30 - PADDLE_WIDTH, (int)demoCpuY, PADDLE_WIDTH, PADDLE_HEIGHT, 10, 10);
        // Dimmed ball with trail
        for (int t = 4; t > 0; t--) {
            int tx = (int)(demoBallX - demoBallDX * t * 0.7);
            int ty = (int)(demoBallY - demoBallDY * t * 0.7);
            g.setColor(new Color(100, 200, 255, t * 12));
            g.fillOval(tx, ty, BALL_SIZE, BALL_SIZE);
        }
        g.setColor(new Color(200, 240, 255, 55));
        g.fillOval((int)demoBallX, (int)demoBallY, BALL_SIZE, BALL_SIZE);
    }

    private void drawMenu(Graphics2D g) {
        // ── TICK DEMO ──────────────────────────────────────────────────────────
        menuAnimationTimer += 0.016f;
        titleGlowPhase     += 0.05f;
        updateDemoGame();

        int W = GAME_WIDTH, H = GAME_HEIGHT, cx = W / 2;

        // ── DARK CINEMATIC VIGNETTE over the court ─────────────────────────────
        // Horizontal gradient: dark sides fade to transparent middle
        GradientPaint leftFade = new GradientPaint(0, 0, new Color(4, 4, 14, 220), 220, 0, new Color(4, 4, 14, 0));
        g.setPaint(leftFade); g.fillRect(0, 0, 220, H);
        GradientPaint rightFade = new GradientPaint(W-220, 0, new Color(4, 4, 14, 0), W, 0, new Color(4, 4, 14, 220));
        g.setPaint(rightFade); g.fillRect(W-220, 0, 220, H);
        // Vertical vignette — darker top and bottom
        GradientPaint topFade = new GradientPaint(0, 0, new Color(4, 4, 14, 200), 0, 200, new Color(4, 4, 14, 0));
        g.setPaint(topFade); g.fillRect(0, 0, W, 200);
        GradientPaint botFade = new GradientPaint(0, H-160, new Color(4, 4, 14, 0), 0, H, new Color(4, 4, 14, 220));
        g.setPaint(botFade); g.fillRect(0, H-160, W, 160);

        // ── DEMO GAME ELEMENTS ─────────────────────────────────────────────────
        drawDemoArenaElements(g);

        // ── TITLE BLOCK ────────────────────────────────────────────────────────
        String title = "NEON PONG";
        g.setFont(new Font("Segoe UI", Font.BOLD, 100));
        FontMetrics fm = g.getFontMetrics();
        int tW = fm.stringWidth(title), tX = cx - tW/2, tY = 115;

        // Multi-layer chromatic glow
        float gPhase = (float)Math.sin(titleGlowPhase);
        for (int layer = 14; layer > 0; layer--) {
            float a = (float)(Math.sin(titleGlowPhase + layer * 0.2) * 0.14 + 0.20);
            g.setColor(new Color(0, 255, 255, (int)(a*255)));
            g.drawString(title, tX - layer, tY + layer);
        }
        // Red ghost offset for chromatic aberration effect
        g.setColor(new Color(255, 0, 100, 35));
        g.drawString(title, tX + 4, tY + 2);
        g.setColor(new Color(0, 255, 180, 35));
        g.drawString(title, tX - 4, tY - 2);
        // Main title — cyan-to-blue gradient
        GradientPaint tGrad = new GradientPaint(tX, tY-70, new Color(0,255,255), tX+tW, tY+20, new Color(80,80,255));
        g.setPaint(tGrad);
        g.drawString(title, tX, tY);

        // Subtitle
        g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        fm = g.getFontMetrics();
        String sub = "C Y B E R P U N K   A R C A D E   E D I T I O N";
        int sW = fm.stringWidth(sub);
        g.setColor(new Color(160, 160, 220, 160));
        g.drawString(sub, cx - sW/2, tY + 32);
        // Animated scanline under subtitle
        float ul = (float)(Math.sin(menuAnimationTimer * 1.6) * 30 + sW * 0.85f);
        GradientPaint ulGrad = new GradientPaint(cx-(int)(ul/2), tY+40, new Color(0,255,255,0), cx, tY+40, new Color(0,255,255,120));
        GradientPaint ulGrad2 = new GradientPaint(cx, tY+40, new Color(0,255,255,120), cx+(int)(ul/2), tY+40, new Color(0,255,255,0));
        g.setPaint(ulGrad);  g.setStroke(new BasicStroke(1.5f)); g.drawLine(cx-(int)(ul/2), tY+40, cx, tY+40);
        g.setPaint(ulGrad2); g.drawLine(cx, tY+40, cx+(int)(ul/2), tY+40);

        // ── FIGHTER CARDS ──────────────────────────────────────────────────────
        int cardW = 210, cardH = 260, cardY = 165;
        int leftCardX  = 28;
        int rightCardX = W - 28 - cardW;
        drawFighterCard(g, leftCardX,  cardY, cardW, cardH, "PHANTOM", "YOU",
            PLAYER_GRADIENT_START, PLAYER_GRADIENT_END, true);
        drawFighterCard(g, rightCardX, cardY, cardW, cardH, "SPECTER", "A.I.",
            CPU_GRADIENT_START, CPU_GRADIENT_END, false);

        // Animated demo score — small, muted, in centre gap above action panel
        g.setFont(new Font("Segoe UI", Font.BOLD, 32));
        fm = g.getFontMetrics();
        String dScore = demoP1Score + "   " + demoP2Score;
        g.setColor(new Color(60, 60, 100, 160));
        g.drawString(dScore, cx - fm.stringWidth(dScore)/2, cardY + 55);

        // ── CENTRE ACTION PANEL ────────────────────────────────────────────────
        int panelW = 330, panelH = 170, panelX = cx - panelW/2, panelY = H - panelH - 68;
        // Glow behind panel
        for (int i = 6; i > 0; i--) {
            g.setColor(new Color(0, 200, 255, i * 8));
            g.fillRoundRect(panelX-i*3, panelY-i*3, panelW+i*6, panelH+i*6, 22, 22);
        }
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, new Color(8,10,30,240),
            panelX+panelW, panelY+panelH, new Color(18,8,40,240));
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
        g.setColor(new Color(60, 80, 160, 200));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);

        // Pulsing ENTER button
        float btnPulse = (float)(Math.sin(menuAnimationTimer * 4) * 0.1 + 0.9);
        int btnW = 250, btnH = 46, btnX = cx - btnW/2, btnY = panelY + 18;
        for (int i = 4; i > 0; i--) {
            g.setColor(new Color(0, 200, 255, (int)(btnPulse * i * 22)));
            g.fillRoundRect(btnX-i*2, btnY-i*2, btnW+i*4, btnH+i*4, 14, 14);
        }
        GradientPaint btnGrad = new GradientPaint(btnX, btnY, new Color(0,130,200), btnX, btnY+btnH, new Color(0,70,160));
        g.setPaint(btnGrad);
        g.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        g.setColor(new Color(0, 220, 255, (int)(btnPulse * 220)));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(btnX, btnY, btnW, btnH, 12, 12);
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        fm = g.getFontMetrics();
        String enterLabel = "⚔  SOLO CHALLENGE";
        g.setColor(Color.WHITE);
        g.drawString(enterLabel, cx - fm.stringWidth(enterLabel)/2, btnY + btnH/2 + 7);

        // Online button
        int onlineY = btnY + btnH + 10;
        GradientPaint onGrad = new GradientPaint(btnX, onlineY, new Color(80,0,160), btnX, onlineY+btnH, new Color(50,0,120));
        g.setPaint(onGrad);
        g.fillRoundRect(btnX, onlineY, btnW, btnH, 12, 12);
        g.setColor(new Color(180, 80, 255, 200));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnX, onlineY, btnW, btnH, 12, 12);
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        fm = g.getFontMetrics();
        String onlineLabel = "⚡  ONLINE  [ O ]";
        g.setColor(new Color(230, 180, 255));
        g.drawString(onlineLabel, cx - fm.stringWidth(onlineLabel)/2, onlineY + btnH/2 + 7);

        // ── FOOTER HINTS ──────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        String hints = "W / S  or  ↑ / ↓   move     M   sound     H   help     ESC   quit     First to " + WIN_SCORE + " wins";
        g.setColor(new Color(70, 70, 110, 200));
        g.drawString(hints, cx - fm.stringWidth(hints)/2, H - 16);
    }

    private void drawFighterCard(Graphics2D g, int x, int y, int w, int h,
            String callsign, String role, Color col1, Color col2, boolean leftSide) {
        int cx = x + w/2;
        // Card glow
        for (int i = 5; i > 0; i--) {
            g.setColor(new Color(col1.getRed(), col1.getGreen(), col1.getBlue(), i * 12));
            g.fillRoundRect(x-i*2, y-i*2, w+i*4, h+i*4, 18, 18);
        }
        // Card body
        GradientPaint bg = leftSide
            ? new GradientPaint(x, y, new Color(0,30,50,230), x+w, y+h, new Color(0,15,30,230))
            : new GradientPaint(x, y, new Color(50,0,25,230), x+w, y+h, new Color(30,0,15,230));
        g.setPaint(bg);
        g.fillRoundRect(x, y, w, h, 15, 15);
        // Border
        GradientPaint border = new GradientPaint(x, y, col1, x, y+h, col2);
        g.setPaint(border);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 15, 15);

        // Decorative corner accent
        g.setColor(new Color(col1.getRed(), col1.getGreen(), col1.getBlue(), 150));
        g.setStroke(new BasicStroke(3f));
        if (leftSide) {
            g.drawLine(x, y+20, x, y); g.drawLine(x, y, x+20, y);
            g.drawLine(x, y+h-20, x, y+h); g.drawLine(x, y+h, x+20, y+h);
        } else {
            g.drawLine(x+w, y+20, x+w, y); g.drawLine(x+w, y, x+w-20, y);
            g.drawLine(x+w, y+h-20, x+w, y+h); g.drawLine(x+w, y+h, x+w-20, y+h);
        }

        // Paddle silhouette
        int padX = leftSide ? cx - 30 : cx + 6;
        GradientPaint padGrad = new GradientPaint(padX, y+30, col1, padX+18, y+130, col2);
        g.setPaint(padGrad);
        g.fillRoundRect(padX, y+28, 18, 100, 8, 8);
        // Inner highlight
        g.setColor(new Color(255,255,255,40));
        g.fillRoundRect(padX+3, y+32, 6, 92, 4, 4);

        // Role label (YOU / A.I.)
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int roleW = fm.stringWidth(role) + 16;
        int roleX = cx - roleW/2, roleY = y + 140;
        g.setColor(new Color(col1.getRed(), col1.getGreen(), col1.getBlue(), 80));
        g.fillRoundRect(roleX, roleY, roleW, 20, 8, 8);
        g.setColor(col1);
        g.drawString(role, cx - fm.stringWidth(role)/2, roleY + 14);

        // Callsign
        g.setFont(new Font("Segoe UI", Font.BOLD, 26));
        fm = g.getFontMetrics();
        // Glow
        for (int i = 3; i > 0; i--) {
            g.setColor(new Color(col1.getRed(), col1.getGreen(), col1.getBlue(), i * 35));
            g.drawString(callsign, cx - fm.stringWidth(callsign)/2, y + 196);
        }
        g.setColor(col1);
        g.drawString(callsign, cx - fm.stringWidth(callsign)/2, y + 196);

        // Divider
        g.setColor(new Color(col1.getRed(), col1.getGreen(), col1.getBlue(), 50));
        g.setStroke(new BasicStroke(1));
        g.drawLine(x+20, y+208, x+w-20, y+208);

        // Stat pills
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        fm = g.getFontMetrics();
        String[] stats = { "SPEED", "POWER", "SKILL" };
        int barY = y + 220, barH = 8, barTotalW = w - 40, barX = x + 20;
        for (int s = 0; s < stats.length; s++) {
            int sy = barY + s * 11;
            // Label
            g.setColor(new Color(col1.getRed(), col1.getGreen(), col1.getBlue(), 160));
            // Bar track
            g.setColor(new Color(30, 30, 60));
            g.fillRoundRect(barX, sy, barTotalW, barH, 4, 4);
            // Bar fill — vary per stat and side for personality
            float fill = leftSide
                ? (s == 0 ? 0.78f : s == 1 ? 0.65f : 0.82f)
                : (s == 0 ? 0.90f : s == 1 ? 0.72f : 0.58f);
            GradientPaint fill2 = new GradientPaint(barX, sy, col2, barX+(int)(barTotalW*fill), sy, col1);
            g.setPaint(fill2);
            g.fillRoundRect(barX, sy, (int)(barTotalW * fill), barH, 4, 4);
        }
    }




    private void drawDifficultySelect(Graphics2D g) {
        g.setColor(new Color(0, 5, 20, 230));
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        int centerX = GAME_WIDTH / 2;

        g.setFont(new Font("Segoe UI", Font.BOLD, 48));
        FontMetrics fm = g.getFontMetrics();
        String title = "SELECT DIFFICULTY";
        g.setColor(TEXT_PRIMARY);
        g.drawString(title, centerX - fm.stringWidth(title) / 2, 100);

        Difficulty[] difficulties = Difficulty.values();
        int cardWidth = 550;
        int cardHeight = 75;
        int spacing = 18;
        int totalHeight = difficulties.length * (cardHeight + spacing);
        int startY = (GAME_HEIGHT - totalHeight) / 2 + 10;
        int cardX = centerX - cardWidth / 2;

        for (int i = 0; i < difficulties.length; i++) {
            Difficulty d = difficulties[i];
            boolean selected = (i == difficultyIndex);
            int cardY = startY + i * (cardHeight + spacing);

            if (selected) {
                for (int glow = 4; glow > 0; glow--) {
                    float alpha = 0.5f - (glow * 0.1f);
                    g.setColor(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), (int)(alpha * 255)));
                    g.fillRoundRect(cardX - glow * 2, cardY - glow * 2, 
                                   cardWidth + glow * 4, cardHeight + glow * 4, 15, 15);
                }
                
                GradientPaint cardGrad = new GradientPaint(
                    cardX, cardY, d.color.brighter(),
                    cardX + cardWidth, cardY + cardHeight, d.color
                );
                g.setPaint(cardGrad);
            } else {
                GradientPaint cardGrad = new GradientPaint(
                    cardX, cardY, new Color(35, 35, 60),
                    cardX + cardWidth, cardY + cardHeight, new Color(25, 25, 50)
                );
                g.setPaint(cardGrad);
            }

            g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 12, 12);

            g.setColor(selected ? d.color : new Color(80, 80, 120));
            g.setStroke(new BasicStroke(selected ? 3 : 2));
            g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 12, 12);

            g.setFont(new Font("Segoe UI", Font.BOLD, selected ? 24 : 20));
            fm = g.getFontMetrics();
            g.setColor(selected ? Color.WHITE : d.color);
            g.drawString(d.name, cardX + 25, cardY + 38);

            g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            g.setColor(TEXT_SECONDARY);
            g.drawString(d.description, cardX + 25, cardY + 62);

            if (selected) {
                g.setColor(d.color);
                g.fillOval(cardX + cardWidth - 50, cardY + 25, 35, 35);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI", Font.BOLD, 16));
                fm = g.getFontMetrics();
                g.drawString("OK", cardX + cardWidth - 45, cardY + 47);
            }
        }

        g.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        fm = g.getFontMetrics();
        String[] instructions = {
            "Use UP/DOWN to select",
            "Press ENTER to confirm",
            "Press ESC to go back",
            "Press M to toggle sound"
        };
        int instY = startY + difficulties.length * (cardHeight + spacing) + 40;
        for (String inst : instructions) {
            g.setColor(TEXT_SECONDARY);
            g.drawString(inst, centerX - fm.stringWidth(inst) / 2, instY);
            instY += 26;
        }
    }

    private void drawPause(Graphics2D g) {
        g.setColor(new Color(0, 5, 20, 220));
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        int centerX = GAME_WIDTH / 2;

        g.setColor(PAUSE_COLOR);
        g.fillRect(centerX - 45, 170, 35, 110);
        g.fillRect(centerX + 10, 170, 35, 110);

        g.setFont(new Font("Segoe UI", Font.BOLD, 60));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(PAUSE_COLOR);
        g.drawString("PAUSED", centerX - fm.stringWidth("PAUSED") / 2, 330);

        int boxWidth = 350;
        int boxHeight = 140;
        int boxX = centerX - boxWidth / 2;
        int boxY = 370;

        GradientPaint boxGrad = new GradientPaint(
            boxX, boxY, new Color(40, 40, 70),
            boxX + boxWidth, boxY + boxHeight, new Color(30, 30, 60)
        );
        g.setPaint(boxGrad);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 15, 15);
        
        g.setColor(new Color(100, 100, 150));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 15, 15);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        fm = g.getFontMetrics();
        
        g.setColor(ACCENT_GREEN);
        g.drawString("Press ENTER to Resume", centerX - fm.stringWidth("Press ENTER to Resume") / 2, boxY + 50);
        
        g.setColor(TEXT_SECONDARY);
        String enterLabel = mpManager.isMultiplayerActive() ? "Press ESC to Forfeit & Return to Lobby" : "Press ESC for Main Menu";
        g.drawString(enterLabel, centerX - fm.stringWidth(enterLabel) / 2, boxY + 90);
    }

    private void drawGameOver(Graphics2D g) {
        GradientPaint overlayGrad = new GradientPaint(
            GAME_WIDTH / 2, GAME_HEIGHT / 2 - 100, new Color(0, 5, 20, 240),
            GAME_WIDTH / 2, GAME_HEIGHT, new Color(0, 0, 10, 255)
        );
        g.setPaint(overlayGrad);
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        int centerX = GAME_WIDTH / 2;
        // In MP, client is player2 — they win if p2 has the winning score
        boolean isClientSide = mpManager.isMultiplayerActive() && !mpManager.isHost();
        boolean won = isClientSide ? score.p2 >= WIN_SCORE : score.p1 >= WIN_SCORE;
        Color resultColor = won ? PLAYER_GRADIENT_START : CPU_GRADIENT_START;

        g.setFont(new Font("Segoe UI", Font.BOLD, 70));
        FontMetrics fm = g.getFontMetrics();
        String result = won ? "VICTORY!" : "DEFEAT";
        int resultWidth = fm.stringWidth(result);

        for (int i = 6; i > 0; i--) {
            g.setColor(new Color(resultColor.getRed(), resultColor.getGreen(), resultColor.getBlue(), 40));
            g.drawString(result, centerX - resultWidth / 2 - i, 120 - i);
            g.drawString(result, centerX - resultWidth / 2 + i, 120 + i);
        }
        
        g.setColor(resultColor);
        g.drawString(result, centerX - resultWidth / 2, 120);

        int scoreBoxWidth = 280;
        int scoreBoxHeight = 60;
        int scoreBoxX = centerX - scoreBoxWidth / 2;
        int scoreBoxY = 165;

        GradientPaint scoreBoxGrad = new GradientPaint(
            scoreBoxX, scoreBoxY, new Color(40, 40, 70),
            scoreBoxX + scoreBoxWidth, scoreBoxY + scoreBoxHeight, new Color(30, 30, 60)
        );
        g.setPaint(scoreBoxGrad);
        g.fillRoundRect(scoreBoxX, scoreBoxY, scoreBoxWidth, scoreBoxHeight, 15, 15);
        
        g.setColor(new Color(100, 100, 150));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(scoreBoxX, scoreBoxY, scoreBoxWidth, scoreBoxHeight, 15, 15);

        g.setFont(new Font("Segoe UI", Font.BOLD, 32));
        fm = g.getFontMetrics();
        // Build score string with player names
        boolean isMP2 = mpManager.isMultiplayerActive();
        String myName2 = isMP2 ? mpManager.getDisplayName() : "PHANTOM";
        String oppName2 = isMP2 ? "OPPONENT" : "SPECTER";
        boolean amHost = !isClientSide;
        String p1n = amHost ? myName2 : oppName2;
        String p2n = amHost ? oppName2 : myName2;
        String finalScore = p1n + "  " + score.p1 + " — " + score.p2 + "  " + p2n;
        // If too wide, fall back to digits only
        if (fm.stringWidth(finalScore) > scoreBoxWidth - 10) finalScore = score.p1 + " — " + score.p2;
        g.setColor(TEXT_PRIMARY);
        g.drawString(finalScore, centerX - fm.stringWidth(finalScore) / 2, scoreBoxY + 42);

        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g.setColor(selectedDifficulty.color);
        g.setStroke(new BasicStroke(2));
        String diffBadge = selectedDifficulty.name + " MODE";
        fm = g.getFontMetrics();
        int badgeWidth = fm.stringWidth(diffBadge) + 35;
        int badgeX = centerX - badgeWidth / 2;
        g.drawRoundRect(badgeX, scoreBoxY + 75, badgeWidth, 26, 13, 13);
        g.drawString(diffBadge, centerX - fm.stringWidth(diffBadge) / 2, scoreBoxY + 93);

        int statsPanelWidth = 550;
        int statsPanelHeight = 220;
        int statsPanelX = centerX - statsPanelWidth / 2;
        int statsPanelY = 270;

        GradientPaint statsGrad = new GradientPaint(
            statsPanelX, statsPanelY, new Color(35, 35, 65, 220),
            statsPanelX + statsPanelWidth, statsPanelY + statsPanelHeight, new Color(25, 25, 55, 220)
        );
        g.setPaint(statsGrad);
        g.fillRoundRect(statsPanelX, statsPanelY, statsPanelWidth, statsPanelHeight, 15, 15);
        
        g.setColor(new Color(100, 100, 180, 180));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(statsPanelX, statsPanelY, statsPanelWidth, statsPanelHeight, 15, 15);

        g.setFont(EMOJI_FONT_LARGE);
        g.setColor(ACCENT_GREEN);
        fm = g.getFontMetrics();
        
        String headerText = "📊 MATCH STATISTICS 📊";
        g.drawString(headerText, centerX - fm.stringWidth(headerText) / 2, statsPanelY + 40);

        g.setColor(new Color(100, 100, 180, 100));
        g.setStroke(new BasicStroke(1));
        g.drawLine(statsPanelX + 35, statsPanelY + 48, statsPanelX + statsPanelWidth - 35, statsPanelY + 48);

        int leftColX = statsPanelX + 45;
        int rightColX = statsPanelX + statsPanelWidth - 45;
        int statStartY = statsPanelY + 90;
        int statRowHeight = 38;
        
        String[] labels = {
            "🎮 Total Rallies:",
            "🔥 Longest Rally:",
            "👤 Your Hits:",
            "⚡ Max Speed:"
        };
        
        String[] values = {
            String.valueOf(score.rallies),
            String.valueOf(score.longestRally),
            String.valueOf(score.playerHits),
            String.valueOf(score.maxBallSpeed)
        };
        
        Color[] valueColors = {TEXT_PRIMARY, TEXT_PRIMARY, TEXT_PRIMARY, ACCENT_ORANGE};

        g.setFont(EMOJI_FONT);
        
        for (int i = 0; i < labels.length; i++) {
            int rowY = statStartY + (i * statRowHeight);
            
            g.setColor(TEXT_SECONDARY);
            g.drawString(labels[i], leftColX, rowY);
            
            g.setColor(valueColors[i]);
            fm = g.getFontMetrics();
            int valueWidth = fm.stringWidth(values[i]);
            g.drawString(values[i], rightColX - valueWidth, rowY);
        }

        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        fm = g.getFontMetrics();
        
        boolean isMPGame = mpManager.isConnected();
        String playAgain = isMPGame ? "► Press ENTER to Return to Lobby ◄" : "► Press ENTER to Play Again ◄";
        g.setColor(PLAYER_GRADIENT_START);
        g.drawString(playAgain, centerX - fm.stringWidth(playAgain) / 2, 575);

        g.setColor(TEXT_SECONDARY);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        String exitPrompt = "Press ESC to Exit";
        fm = g.getFontMetrics();
        g.drawString(exitPrompt, centerX - fm.stringWidth(exitPrompt) / 2, 620);
    }

    private void drawExitConfirm(Graphics2D g) {
        g.setColor(new Color(0, 5, 20, 240));
        g.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        int centerX = GAME_WIDTH / 2;

        g.setColor(new Color(255, 100, 100));
        g.setFont(new Font("Segoe UI", Font.BOLD, 60));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("!", centerX - fm.stringWidth("!") / 2, 180);

        g.setFont(new Font("Segoe UI", Font.BOLD, 45));
        fm = g.getFontMetrics();
        g.setColor(new Color(255, 100, 100));
        g.drawString("Exit Game?", centerX - fm.stringWidth("Exit Game?") / 2, 260);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        fm = g.getFontMetrics();
        
        g.setColor(ACCENT_GREEN);
        String confirmText = "Press Y to Confirm";
        g.drawString(confirmText, centerX - fm.stringWidth(confirmText) / 2, 330);
        
        g.setColor(TEXT_SECONDARY);
        String cancelText = "Press N to Cancel";
        g.drawString(cancelText, centerX - fm.stringWidth(cancelText) / 2, 370);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == State.COUNTDOWN) {
            long now = System.currentTimeMillis();
            if (now - countdownLastTick >= 1000) {
                countdownLastTick = now;
                countdownValue--;
                if (countdownValue < 0) {
                    state = State.PLAYING;
                    if (soundEnabled) soundManager.playGameStart();
                }
            }
        }
        if (state == State.PLAYING) {
            update();
        }
        repaint();
    }

    private void update() {
        boolean isMP = mpManager.isMultiplayerActive();
        // In MP: host=player1=LEFT paddle, client=player2=RIGHT paddle
        // player/cpu objects are always LEFT/RIGHT physically, but in MP the client
        // controls cpu (right) and sees host position in player (left).
        boolean clientSide = isMP && !mpManager.isHost();

        if (clientSide) {
            // Client moves the RIGHT paddle (cpu object)
            if (up) { cpu.y -= PLAYER_SPEED; cpu.y = Math.max(20, Math.min(GAME_HEIGHT - PADDLE_HEIGHT - 20, cpu.y)); }
            if (down) { cpu.y += PLAYER_SPEED; cpu.y = Math.max(20, Math.min(GAME_HEIGHT - PADDLE_HEIGHT - 20, cpu.y)); }
            // Remote (host) position goes into player (left)
            player.y = mpManager.getRemotePaddleY();
        } else {
            // Single player or host: control LEFT paddle
            if (up) { player.y -= PLAYER_SPEED; player.y = Math.max(20, Math.min(GAME_HEIGHT - PADDLE_HEIGHT - 20, player.y)); }
            if (down) { player.y += PLAYER_SPEED; player.y = Math.max(20, Math.min(GAME_HEIGHT - PADDLE_HEIGHT - 20, player.y)); }
            if (!isMP) {
                cpu.updateAI(ball, rand, selectedDifficulty);
            } else {
                // Host: remote (client) position goes into cpu (right)
                cpu.y = mpManager.getRemotePaddleY();
            }
        }

        // FIXED: Ball always moves in single-player, host controls in multiplayer
        if (!isMP || mpManager.isHost()) {
            ball.move();
        }

        int currentSpeed = (int) Math.hypot(ball.dx, ball.dy);
        if (currentSpeed > score.maxBallSpeed) {
            score.maxBallSpeed = currentSpeed;
        }

        if (ball.y <= 20) {
            ball.y = 20;
            ball.dy = -ball.dy;
            createParticles(ball.x + BALL_SIZE / 2, ball.y, 8, BALL_GLOW);
            if (soundEnabled) soundManager.playWallHit(currentSpeed);
        }
        if (ball.y + BALL_SIZE >= GAME_HEIGHT - 20) {
            ball.y = GAME_HEIGHT - BALL_SIZE - 20;
            ball.dy = -ball.dy;
            createParticles(ball.x + BALL_SIZE / 2, ball.y + BALL_SIZE, 8, BALL_GLOW); 
            if (soundEnabled) soundManager.playWallHit(currentSpeed);
        }

        if (ball.intersects(player)) {
            ball.x = player.x + PADDLE_WIDTH;
            ball.dx = Math.abs(ball.dx);
            int hitPos = (ball.y + BALL_SIZE / 2) - (player.y + PADDLE_HEIGHT / 2);
            ball.dy = hitPos / 4;
            ball.accelerate(selectedDifficulty.ballAccel);
            player.flash();
            score.currentRally++;
            score.playerHits++;
            screenShake = 5;
            createParticles(ball.x, ball.y + BALL_SIZE / 2, 15, PLAYER_GRADIENT_START);
            if (soundEnabled) soundManager.playPaddleHit(currentSpeed, true);
        }

        if (ball.intersects(cpu)) {
            ball.x = cpu.x - BALL_SIZE;
            ball.dx = -Math.abs(ball.dx);
            int hitPos = (ball.y + BALL_SIZE / 2) - (cpu.y + PADDLE_HEIGHT / 2);
            ball.dy = hitPos / 4;
            ball.accelerate(selectedDifficulty.ballAccel);
            cpu.flash();
            score.currentRally++;
            score.cpuHits++;
            screenShake = 5;
            createParticles(ball.x + BALL_SIZE, ball.y + BALL_SIZE / 2, 15, CPU_GRADIENT_START);
            if (soundEnabled) soundManager.playPaddleHit(currentSpeed, false);
        }

        // Only host (or single-player) scores — client gets scores from DB via pollServer
        if (!isMP || mpManager.isHost()) {
            if (!scoreCooldown && ball.x < 10) {
                scoreCooldown = true;
                score.p2++;
                score.endRally();
                screenShake = 10;
                if (soundEnabled) soundManager.playScore(false);
                if (score.p2 >= WIN_SCORE) {
                    state = State.GAMEOVER;
                    if (soundEnabled) soundManager.playGameOver(false);
                    if (isMP) mpManager.endGame(false);
                } else {
                    resetRound(false);
                }
            }
            if (!scoreCooldown && ball.x > GAME_WIDTH - BALL_SIZE - 10) {
                scoreCooldown = true;
                score.p1++;
                score.endRally();
                screenShake = 10;
                if (soundEnabled) soundManager.playScore(true);
                if (score.p1 >= WIN_SCORE) {
                    state = State.GAMEOVER;
                    if (soundEnabled) soundManager.playGameOver(true);
                    if (isMP) mpManager.endGame(true);
                } else {
                    resetRound(true);
                }
            }
        }
        
        if (isMP) {
            if (mpManager.isHost()) {
                mpManager.sendState(this);         // host pushes game state
                mpManager.sendPaddleMove(player.y); // host sends left paddle
            } else {
                mpManager.syncGame(this);           // client pulls ball from server
                mpManager.sendPaddleMove(cpu.y);    // client sends right paddle
            }
        }
    }
 
    private void createParticles(int x, int y, int count, Color color) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, color, rand));
        }
    }

    private void updateParticles() {
        particles.removeIf(Particle::isDead);
        for (Particle p : particles) {
            p.update();
        }
    }

    private void drawParticles(Graphics2D g) {
        for (Particle p : particles) {
            p.draw(g);
        }
    }

    private void resetRound(boolean playerScored) {
        ball.reset(rand.nextBoolean());
        player.reset();
        cpu.reset();
        particles.clear();
        scoreCooldown = false;
    }

    private void startCountdown() {
        countdownValue = 3;
        countdownLastTick = System.currentTimeMillis();
        state = State.COUNTDOWN;
    }

    private void resetGame() {
        score.reset();
        resetRound(true); 
        up = false;
        down = false;
        screenShake = 0;
        particles.clear();
        currentSpeedSmooth = 0;
        startCountdown();
    }

    private void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            if (soundEnabled) soundManager.playPause();
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
            if (soundEnabled) soundManager.playResume();
        }
    }

    private void toggleSound() {
        soundEnabled = !soundEnabled;
        if (soundEnabled) {
            soundManager.playTestSound();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (k == KeyEvent.VK_H) {
            showHelp = !showHelp;
            return;
        }

        // ESC closes help overlay if open
        if (showHelp) {
            showHelp = false;
            return;
        }

        if (k == KeyEvent.VK_M) {
            toggleSound();
            return;
        }

        if (k == KeyEvent.VK_ESCAPE) {
            if (state == State.PLAYING) {
                togglePause();
            } else if (state == State.COUNTDOWN) {
                if (mpManager.isMultiplayerActive()) {
                    mpManager.forfeitGame();
                    javax.swing.Timer delay = new javax.swing.Timer(300, ev -> {
                        mpManager.resetRoom();
                        state = State.MP_LOBBY;
                        mpRoomIdInput = ""; mpCreateRoomInput = ""; mpSelectedRoom = -10;
                    });
                    delay.setRepeats(false);
                    delay.start();
                } else {
                    state = State.DIFFICULTY;
                }
            } else if (state == State.PAUSED) {
                // ESC from pause: leave the game directly
                if (mpManager.isMultiplayerActive()) {
                    mpManager.forfeitGame();
                    javax.swing.Timer delay = new javax.swing.Timer(300, ev -> {
                        mpManager.resetRoom();
                        state = State.MP_LOBBY;
                        mpRoomIdInput = ""; mpCreateRoomInput = ""; mpSelectedRoom = -10;
                    });
                    delay.setRepeats(false);
                    delay.start();
                } else {
                    state = State.MENU;
                }
            } else if (state == State.MENU) {
                state = State.EXIT_CONFIRM;
            } else if (state == State.GAMEOVER) {
                if (mpManager.isConnected()) {
                    mpManager.resetRoom();
                    state = State.MP_LOBBY;
                    mpRoomIdInput = ""; mpCreateRoomInput = ""; mpSelectedRoom = -10;
                } else {
                    state = State.EXIT_CONFIRM;
                }
            } else if (state == State.EXIT_CONFIRM) {
                state = State.MENU;
            } else if (state == State.DIFFICULTY) {
                state = State.MENU;
            } else if (state == State.MP_LOGIN) {
                state = State.MENU;
                mpManager.disconnect();
            } else if (state == State.MP_REGISTER) {
                state = State.MP_LOGIN;
                mpLoginError = "";
            } else if (state == State.MP_LOBBY) {
                // Go back to main menu but stay logged in
                state = State.MENU;
            } else if (state == State.MP_WAITING) {
                mpManager.disconnect();
                state = State.MP_LOBBY;
            } else if (state == State.MP_ACCOUNT) {
                mpLoginError = "";
                state = State.MP_LOBBY;
            }
            return;
        }

        if (state == State.EXIT_CONFIRM) {
            if (k == KeyEvent.VK_Y) System.exit(0);
            else if (k == KeyEvent.VK_N) state = State.MENU;
            return;
        }

        if (state == State.DIFFICULTY) {
            if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) {
                difficultyIndex--;
                if (difficultyIndex < 0) difficultyIndex = Difficulty.values().length - 1;
                selectedDifficulty = Difficulty.values()[difficultyIndex];
                if (soundEnabled) soundManager.playMenuMove();
            }
            if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) {
                difficultyIndex++;
                if (difficultyIndex >= Difficulty.values().length) difficultyIndex = 0;
                selectedDifficulty = Difficulty.values()[difficultyIndex];
                if (soundEnabled) soundManager.playMenuMove();
            }
            if (k == KeyEvent.VK_ENTER) {
                resetGame();
            }
            return;
        }

        if (state == State.MENU) {
            if (k == KeyEvent.VK_ENTER) {
                state = State.DIFFICULTY;
                if (soundEnabled) soundManager.playMenuSelect();
            }
            if (k == KeyEvent.VK_O) {
                state = State.MP_LOGIN;
                mpEmail = "";
                mpPassword = "";
                mpEmailMode = true;
            }
            return;
        }

        if (state == State.MP_LOGIN) {
            if (k == KeyEvent.VK_R) {
                // Go to register screen
                mpEmail = ""; mpPassword = ""; mpConfirmPassword = "";
                mpAccountField = 0; mpLoginError = "";
                state = State.MP_REGISTER;
            } else if (k == KeyEvent.VK_ENTER) {
                if (!mpEmail.isEmpty() && !mpPassword.isEmpty() && !mpManager.isLoading()) {
                    mpLoginError = "";
                    mpManager.login(mpEmail, mpPassword);
                } else if (mpEmail.isEmpty()) {
                    mpLoginError = "Email is required"; mpEmailMode = true;
                } else if (mpPassword.isEmpty()) {
                    mpLoginError = "Password is required"; mpEmailMode = false;
                }
            } else if (k == KeyEvent.VK_TAB || k == KeyEvent.VK_DOWN || k == KeyEvent.VK_UP) {
                mpEmailMode = !mpEmailMode; mpLoginError = "";
            } else if (k == KeyEvent.VK_BACK_SPACE) {
                mpLoginError = "";
                if (mpEmailMode) { if (mpEmail.length() > 0) mpEmail = mpEmail.substring(0, mpEmail.length() - 1); }
                else { if (mpPassword.length() > 0) mpPassword = mpPassword.substring(0, mpPassword.length() - 1); }
            } else {
                char c = e.getKeyChar();
                if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c < 127) {
                    mpLoginError = "";
                    if (mpEmailMode) mpEmail += c; else mpPassword += c;
                }
            }
            return;
        }

        if (state == State.MP_REGISTER) {
            if (k == KeyEvent.VK_ENTER) {
                if (!mpEmail.isEmpty() && !mpPassword.isEmpty() && !mpConfirmPassword.isEmpty() && !mpManager.isLoading()) {
                    if (!mpPassword.equals(mpConfirmPassword)) { mpLoginError = "Passwords do not match"; }
                    else if (mpPassword.length() < 8) { mpLoginError = "Password must be at least 8 characters"; }
                    else { mpLoginError = ""; mpManager.register(mpEmail, mpPassword); }
                } else { mpLoginError = "Please fill all fields"; }
            } else if (k == KeyEvent.VK_TAB || k == KeyEvent.VK_DOWN) {
                mpAccountField = (mpAccountField + 1) % 3; mpLoginError = "";
            } else if (k == KeyEvent.VK_UP) {
                mpAccountField = (mpAccountField + 2) % 3; mpLoginError = "";
            } else if (k == KeyEvent.VK_BACK_SPACE) {
                mpLoginError = "";
                if (mpAccountField == 0 && mpEmail.length() > 0) mpEmail = mpEmail.substring(0, mpEmail.length() - 1);
                else if (mpAccountField == 1 && mpPassword.length() > 0) mpPassword = mpPassword.substring(0, mpPassword.length() - 1);
                else if (mpAccountField == 2 && mpConfirmPassword.length() > 0) mpConfirmPassword = mpConfirmPassword.substring(0, mpConfirmPassword.length() - 1);
            } else {
                char c = e.getKeyChar();
                if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c < 127) {
                    mpLoginError = "";
                    if (mpAccountField == 0) mpEmail += c;
                    else if (mpAccountField == 1) mpPassword += c;
                    else mpConfirmPassword += c;
                }
            }
            return;
        }

        if (state == State.MP_LOBBY) {
            if (k == KeyEvent.VK_A) {
                mpLoginError = ""; mpAccountTab = 0; mpAccountField = 0;
                mpAccountNewEmail = ""; mpAccountNewPassword = ""; mpAccountConfirm = "";
                state = State.MP_ACCOUNT;
            } else if (k == KeyEvent.VK_LEFT) {
                if (mpSelectedRoom <= -10) mpSelectedRoom = Math.min(-10, mpSelectedRoom + 1);
                else mpSelectedRoom = -10;
            } else if (k == KeyEvent.VK_RIGHT) {
                if (mpSelectedRoom <= -10) mpSelectedRoom = Math.max(-13, mpSelectedRoom - 1);
                else mpSelectedRoom = -10;
            } else if (k == KeyEvent.VK_UP) {
                if (mpSelectedRoom >= 0) mpSelectedRoom = -10;
            } else if (k == KeyEvent.VK_DOWN) {
                if (mpSelectedRoom <= -10 && !mpRoomList.isEmpty()) mpSelectedRoom = 0;
                else if (mpSelectedRoom >= 0) mpSelectedRoom = Math.min(Math.min(mpRoomList.size()-1, 1), mpSelectedRoom + 1);
            } else if (k == KeyEvent.VK_ENTER) {
                if (mpSelectedRoom <= -10) {
                    int pi = -10 - mpSelectedRoom;
                    if (pi < mpPersistentRooms.size()) {
                        mpLoginError = "";
                        mpManager.joinOrCreateRoom(mpPersistentRooms.get(pi)[0]);
                    }
                } else if (mpSelectedRoom >= 0 && mpSelectedRoom < mpRoomList.size()) {
                    mpManager.joinOrCreateRoom(mpRoomList.get(mpSelectedRoom)[0]);
                } else if (!mpRoomIdInput.isEmpty()) {
                    mpManager.joinOrCreateRoom(mpRoomIdInput);
                }
            } else if (k == KeyEvent.VK_BACK_SPACE) {
                if (!mpRoomIdInput.isEmpty()) {
                    mpRoomIdInput = mpRoomIdInput.substring(0, mpRoomIdInput.length() - 1);
                }
            } else {
                char c = e.getKeyChar();
                if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c < 127) {
                    mpRoomIdInput += c;
                }
            }
            return;
        }

        if (state == State.MP_ACCOUNT) {
            if (k == KeyEvent.VK_LEFT) { mpAccountTab = (mpAccountTab + 2) % 3; mpLoginError = ""; mpAccountField = 0; }
            else if (k == KeyEvent.VK_RIGHT) { mpAccountTab = (mpAccountTab + 1) % 3; mpLoginError = ""; mpAccountField = 0; }
            else if (k == KeyEvent.VK_TAB || k == KeyEvent.VK_DOWN) {
                if (mpAccountTab == 1) mpAccountField = (mpAccountField + 1) % 2; mpLoginError = "";
            } else if (k == KeyEvent.VK_UP) {
                if (mpAccountTab == 1) mpAccountField = (mpAccountField + 1) % 2; mpLoginError = "";
            } else if (k == KeyEvent.VK_ENTER) {
                mpLoginError = "";
                if (mpAccountTab == 0 && !mpAccountNewEmail.isEmpty()) mpManager.changeEmail(mpAccountNewEmail);
                else if (mpAccountTab == 1 && !mpAccountNewPassword.isEmpty()) mpManager.changePassword(mpAccountNewPassword, mpAccountConfirm);
                else if (mpAccountTab == 2) mpManager.deleteAccount();
            } else if (k == KeyEvent.VK_BACK_SPACE) {
                if (mpAccountTab == 0 && mpAccountNewEmail.length() > 0) mpAccountNewEmail = mpAccountNewEmail.substring(0, mpAccountNewEmail.length() - 1);
                else if (mpAccountTab == 1 && mpAccountField == 0 && mpAccountNewPassword.length() > 0) mpAccountNewPassword = mpAccountNewPassword.substring(0, mpAccountNewPassword.length() - 1);
                else if (mpAccountTab == 1 && mpAccountField == 1 && mpAccountConfirm.length() > 0) mpAccountConfirm = mpAccountConfirm.substring(0, mpAccountConfirm.length() - 1);
                mpLoginError = "";
            } else if (k == KeyEvent.VK_S && mpAccountTab != 1) {
                // S = sign out (only when not on password tab where S is typed into field)
                mpManager.disconnect();
                mpEmail = ""; mpPassword = ""; mpLoginError = "";
                state = State.MP_LOGIN;
            } else {
                char c = e.getKeyChar();
                if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c < 127) {
                    mpLoginError = "";
                    if (mpAccountTab == 0) mpAccountNewEmail += c;
                    else if (mpAccountTab == 1 && mpAccountField == 0) mpAccountNewPassword += c;
                    else if (mpAccountTab == 1 && mpAccountField == 1) mpAccountConfirm += c;
                }
            }
            return;
        }

        if (state == State.GAMEOVER) {
            if (k == KeyEvent.VK_ENTER) {
                if (soundEnabled) soundManager.playMenuSelect();
                if (mpManager.isConnected()) {
                    // MP: go back to lobby, still logged in, ready to play again
                    mpManager.resetRoom();
                    state = State.MP_LOBBY;
                    mpRoomIdInput = ""; mpCreateRoomInput = ""; mpSelectedRoom = -10;
                } else {
                    state = State.DIFFICULTY;
                }
            }
            return;
        }

        if (state == State.PAUSED) {
            if (k == KeyEvent.VK_ENTER) {
                togglePause(); // resume
                if (soundEnabled) soundManager.playMenuSelect();
            }
            return;
        }

        if (state == State.PLAYING) {
            if (k == KeyEvent.VK_W || k == KeyEvent.VK_UP) up = true;
            if (k == KeyEvent.VK_S || k == KeyEvent.VK_DOWN) down = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_W || k == KeyEvent.VK_UP) up = false;
        if (k == KeyEvent.VK_S || k == KeyEvent.VK_DOWN) down = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Neon Pong - Cyberpunk Edition");
            PongGame g = new PongGame();
            f.add(g);
            f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    int result = JOptionPane.showConfirmDialog(f,
                        "Are you sure you want to exit?", "Exit Game",
                        JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) System.exit(0);
                }
            });
            f.setResizable(false);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }

    // ==================== SOUND MANAGER ====================
    static class SoundManager {
        private static final int SAMPLE_RATE = 44100;
        private SourceDataLine line;
        private final Object lock = new Object();
        private Thread currentSoundThread;
        private boolean isPlaying = false;

        public SoundManager() {
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, true);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format);
            } catch (LineUnavailableException e) {
                System.err.println("Audio line unavailable: " + e.getMessage());
            }
        }

        private void playSound(int[] frequencies, int[] durations, int[] amplitudes, int[] waveTypes) {
            if (line == null) return;

            synchronized (lock) {
                if (isPlaying) {
                    line.stop();
                    line.flush();
                    if (currentSoundThread != null) {
                        currentSoundThread.interrupt();
                    }
                }
                
                isPlaying = true;
                currentSoundThread = new Thread(() -> {
                    try {
                        int totalSamples = 0;
                        for (int duration : durations) {
                            totalSamples += (duration * SAMPLE_RATE) / 1000;
                        }
                        totalSamples += 100;
                        byte[] soundBuffer = new byte[totalSamples];
                        int sampleIndex = 0;
                        
                        for (int soundIdx = 0; soundIdx < frequencies.length; soundIdx++) {
                            if (Thread.currentThread().isInterrupted()) return;
                            int freq = frequencies[soundIdx];
                            int duration = durations[soundIdx];
                            int amp = amplitudes[soundIdx];
                            int waveType = waveTypes[soundIdx];
                            int samples = (duration * SAMPLE_RATE) / 1000;
                            
                            for (int i = 0; i < samples; i++) {
                                if (Thread.currentThread().isInterrupted() || sampleIndex >= soundBuffer.length) return;
                                double time = i / (double) SAMPLE_RATE;
                                double value = 0;
                                switch (waveType) {
                                    case 0: value = Math.sin(2 * Math.PI * freq * time); break;
                                    case 1: value = Math.sin(2 * Math.PI * freq * time) > 0 ? 1 : -1; break;
                                    case 2: value = 2 * (freq * time - Math.floor(freq * time + 0.5)); break;
                                }
                                double envelope = 1.0;
                                if (i < 50) envelope = i / 50.0;
                                else if (i > samples - 50) envelope = (samples - i) / 50.0;
                                soundBuffer[sampleIndex++] = (byte) (value * amp * envelope);
                            }
                        }
                        
                        for (int i = 0; i < 100 && sampleIndex < soundBuffer.length; i++) {
                            soundBuffer[sampleIndex++] = 0;
                        }
                        
                        if (!Thread.currentThread().isInterrupted() && line != null) {
                            line.start();
                            line.write(soundBuffer, 0, sampleIndex);
                            line.drain();
                            line.stop();
                        }
                    } catch (Exception e) {} finally {
                        synchronized (lock) { isPlaying = false; }
                    }
                });
                currentSoundThread.setDaemon(true);
                currentSoundThread.start();
            }
        }

        public void playPaddleHit(int speed, boolean isPlayer) {
            int freq = 300 + (speed * 15);
            if (!isPlayer) freq -= 40;
            playSound(new int[]{freq, freq + 80}, new int[]{25, 15}, new int[]{40, 25}, new int[]{1, 0});
        }

        public void playWallHit(int speed) {
            int freq = 200 + (speed * 8);
            playSound(new int[]{freq}, new int[]{15}, new int[]{30}, new int[]{2});
        }

        public void playScore(boolean isPlayer) {
            if (isPlayer) {
                playSound(new int[]{523, 659, 784}, new int[]{80, 80, 150}, new int[]{40, 40, 50}, new int[]{0, 0, 0});
            } else {
                playSound(new int[]{784, 659, 523}, new int[]{80, 80, 150}, new int[]{35, 35, 40}, new int[]{0, 0, 0});
            }
        }

        public void playGameOver(boolean won) {
            if (won) {
                playSound(new int[]{523, 659, 784, 1046}, new int[]{120, 120, 120, 250}, new int[]{50, 50, 50, 70}, new int[]{0, 0, 0, 0});
            } else {
                playSound(new int[]{784, 659, 523, 392}, new int[]{120, 120, 120, 250}, new int[]{45, 45, 45, 35}, new int[]{2, 2, 2, 2});
            }
        }

        public void playGameStart() {
            playSound(new int[]{262, 330, 392, 523}, new int[]{70, 70, 70, 150}, new int[]{40, 40, 45, 60}, new int[]{0, 0, 0, 0});
        }

        public void playMenuMove() {
            playSound(new int[]{440}, new int[]{30}, new int[]{25}, new int[]{0});
        }

        public void playMenuSelect() {
            playSound(new int[]{523, 659}, new int[]{40, 80}, new int[]{30, 40}, new int[]{0, 0});
        }

        public void playPause() {
            playSound(new int[]{440, 330}, new int[]{40, 80}, new int[]{30, 25}, new int[]{0, 2});
        }

        public void playResume() {
            playSound(new int[]{330, 440}, new int[]{40, 80}, new int[]{25, 30}, new int[]{2, 0});
        }

        public void playTestSound() {
            playSound(new int[]{523}, new int[]{80}, new int[]{40}, new int[]{0});
        }
    }

    // ==================== PARTICLE CLASS ====================
    static class Particle {
        float x, y, vx, vy;
        Color color;
        float life = 1.0f;
        float decay;
        int size;

        Particle(int x, int y, Color color, Random rand) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.vx = (rand.nextFloat() - 0.5f) * 8;
            this.vy = (rand.nextFloat() - 0.5f) * 8;
            this.decay = 0.02f + rand.nextFloat() * 0.03f;
            this.size = 3 + rand.nextInt(5);
        }

        void update() {
            x += vx;
            y += vy;
            vy += 0.2f;
            life -= decay;
        }

        boolean isDead() { return life <= 0; }

        void draw(Graphics2D g) {
            int alpha = (int) (life * 255);
            if (alpha < 0) alpha = 0;
            if (alpha > 255) alpha = 255;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.fillOval((int) x - size / 2, (int) y - size / 2, size, size); 
        }
    }

    // ==================== STAR CLASS ====================
    static class Star {
        float x, y;
        float size;
        float brightness;
        float twinkleSpeed;

        Star(int x, int y, Random rand) {
            this.x = x;
            this.y = y;
            this.size = rand.nextFloat() * 2 + 1;
            this.brightness = rand.nextFloat();
            this.twinkleSpeed = 0.02f + rand.nextFloat() * 0.03f;
        }

        void update() {
            brightness += twinkleSpeed;
            if (brightness > 1 || brightness < 0.3) {
                twinkleSpeed = -twinkleSpeed;
            }
        }

        void draw(Graphics2D g) {
            g.setColor(new Color(200, 200, 255, (int) (brightness * 180)));
            g.fillOval((int) x, (int) y, (int) size, (int) size);
        }
    }

    // ==================== PADDLE CLASS ====================
    static class Paddle extends Rectangle {
        private final boolean isPlayer;
        private int flash = 0;
        private float targetY;

        Paddle(int x, boolean player) {
            super(x, (GAME_HEIGHT - PADDLE_HEIGHT) / 2, PADDLE_WIDTH, PADDLE_HEIGHT);
            this.isPlayer = player;
            this.targetY = y;
        }

        void updateAI(Ball b, Random r, Difficulty diff) {
            if (!isPlayer) {
                int paddleCenter = y + height / 2;
                int ballCenter = b.y + b.height / 2;
                int ballSpeed = (int) Math.hypot(b.dx, b.dy);
                int cpuSpeed = diff.cpuBaseSpeed + (ballSpeed / 4);
                cpuSpeed = Math.min(cpuSpeed, diff.cpuMaxSpeed);

                if (b.dx > 0) {
                    int error = r.nextInt(diff.cpuError * 2) - diff.cpuError;
                    int target = ballCenter + error;
                    if (target > paddleCenter + 14) {
                        targetY += cpuSpeed;
                    } else if (target < paddleCenter - 14) {
                        targetY -= cpuSpeed;
                    }
                } else {
                    int center = (GAME_HEIGHT - PADDLE_HEIGHT) / 2;
                    if (targetY < center - 20) {
                        targetY += cpuSpeed / 3;
                    } else if (targetY > center + 20) {
                        targetY -= cpuSpeed / 3;
                    }
                }
                targetY = Math.max(20, Math.min(GAME_HEIGHT - PADDLE_HEIGHT - 20, targetY));
                float interpFactor = diff == Difficulty.EXTREME ? 0.18f : 0.10f;
                y = y + (int) ((targetY - y) * interpFactor);
            }
        }

        void flash() { flash = 10; }

        void draw(Graphics2D g) {
            Color start = isPlayer ? PLAYER_GRADIENT_START : CPU_GRADIENT_START;
            Color end = isPlayer ? PLAYER_GRADIENT_END : CPU_GRADIENT_END;

            for (int i = 4; i > 0; i--) {
                float alpha = 0.4f - (i * 0.1f);
                if (flash > 0) alpha += 0.3f;
                g.setColor(new Color(start.getRed(), start.getGreen(), start.getBlue(), (int) (alpha * 255)));
                g.fillRoundRect(x - i * 2, y - i * 2, width + i * 4, height + i * 4, 15, 15);
            }

            GradientPaint grad = new GradientPaint(x, y, flash > 0 ? start.brighter() : start, x + width, y + height, end);
            g.setPaint(grad);
            g.fillRoundRect(x, y, width, height, 10, 10);

            g.setColor(flash > 0 ? Color.WHITE : new Color(255, 255, 255, 150));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y, width, height, 10, 10);

            g.setColor(new Color(255, 255, 255, 100));
            g.fillRoundRect(x + 5, y + 5, width - 10, height / 4, 5, 5);

            if (flash > 0) flash--;
        }

        void reset() {
            y = (GAME_HEIGHT - PADDLE_HEIGHT) / 2;
            targetY = y;
        }
    }

    // ==================== BALL CLASS ====================
    static class Ball extends Rectangle {
        int dx, dy;
        private static final Random RAND = new Random();

        Ball() {
            super(0, 0, BALL_SIZE, BALL_SIZE);
        }

        void reset(boolean serveRight) {
            x = (GAME_WIDTH - BALL_SIZE) / 2;
            y = (GAME_HEIGHT - BALL_SIZE) / 2;
            dx = (serveRight ? 1 : -1) * BALL_START_SPEED;
            dy = RAND.nextInt(9) - 4;
            if (Math.abs(dy) < 2) dy = dy >= 0 ? 3 : -3;
        }

        void move() {
            x += dx;
            y += dy;
        }

        void accelerate(int amount) {
            int speed = (int) Math.hypot(dx, dy);
            if (speed < BALL_MAX_SPEED) {
                double factor = 1.0 + (amount * 0.08);
                dx = (int) Math.round(dx * factor);
                dy = (int) Math.round(dy * factor);
                int newSpeed = (int) Math.hypot(dx, dy);
                if (newSpeed > BALL_MAX_SPEED) {
                    double scale = (double) BALL_MAX_SPEED / newSpeed;
                    dx = (int) Math.round(dx * scale);
                    dy = (int) Math.round(dy * scale);
                }
            }
        }

        void draw(Graphics2D g) {
            int speed = (int) Math.hypot(dx, dy);
            Color ballColor = speed > BALL_MAX_SPEED * 0.8 ? ACCENT_RED :
                             speed > BALL_MAX_SPEED * 0.6 ? ACCENT_ORANGE : BALL_GLOW;

            for (int i = 5; i > 0; i--) {
                float alpha = 0.3f - (i * 0.05f);
                g.setColor(new Color(ballColor.getRed(), ballColor.getGreen(), ballColor.getBlue(), (int) (alpha * 255)));
                g.fillOval(x - i * 2, y - i * 2, width + i * 4, height + i * 4);
            }

            GradientPaint grad = new GradientPaint(x, y, BALL_CORE, x + width, y + height, ballColor);
            g.setPaint(grad);
            g.fillOval(x, y, width, height);

            g.setColor(new Color(255, 255, 255, 200));
            g.fillOval(x + 5, y + 5, width / 3, height / 3);
        }
    }

    // ==================== SCORE CLASS ====================
    static class Score {
        int p1 = 0, p2 = 0;
        int rallies = 0, currentRally = 0, longestRally = 0;
        int maxBallSpeed = 0;
        int playerHits = 0, cpuHits = 0;

        void draw(Graphics2D g) {
            // Resolve names based on game mode
            boolean isMP = PongGame.instance != null && PongGame.instance.mpManager.isMultiplayerActive();
            boolean isHost = PongGame.instance != null && PongGame.instance.mpManager.isHost();
            String p1Name, p2Name;
            if (isMP) {
                String myName = PongGame.instance.mpManager.getDisplayName();
                p1Name = isHost ? myName : "OPPONENT";
                p2Name = isHost ? "OPPONENT" : myName;
            } else {
                p1Name = "PHANTOM";
                p2Name = "SPECTER";
            }

            // Score digits — anchored from top, contained within top 90px
            g.setFont(new Font("Segoe UI", Font.BOLD, 54));
            FontMetrics fm = g.getFontMetrics();
            int scoreY = 68; // digit baseline — ascent ~43px, so digits occupy y=25..68

            for (int i = 3; i > 0; i--) {
                g.setColor(new Color(0, 255, 255, 70));
                g.drawString(String.valueOf(p1), GAME_WIDTH/2 - 120 - fm.stringWidth(String.valueOf(p1)) - i, scoreY - i);
            }
            g.setColor(PLAYER_GRADIENT_START);
            g.drawString(String.valueOf(p1), GAME_WIDTH/2 - 120 - fm.stringWidth(String.valueOf(p1)), scoreY);

            for (int i = 3; i > 0; i--) {
                g.setColor(new Color(255, 50, 150, 70));
                g.drawString(String.valueOf(p2), GAME_WIDTH/2 + 120 - i, scoreY - i);
            }
            g.setColor(CPU_GRADIENT_START);
            g.drawString(String.valueOf(p2), GAME_WIDTH/2 + 120, scoreY);

            // Name labels — small pill badges below the scores (no overlap)
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics fmL = g.getFontMetrics();
            int nameY = scoreY + 14; // baseline just below score descent
            g.setColor(new Color(0, 200, 220, 150));
            g.drawString(p1Name, GAME_WIDTH/2 - 120 - fmL.stringWidth(p1Name), nameY);
            g.setColor(new Color(255, 50, 130, 150));
            g.drawString(p2Name, GAME_WIDTH/2 + 120, nameY);

            // Thin centre divider
            g.setColor(new Color(100, 100, 180, 40));
            g.setStroke(new BasicStroke(1));
            g.drawLine(GAME_WIDTH/2, 18, GAME_WIDTH/2, nameY + 4);
        }

        void endRally() {
            if (currentRally > 0) rallies++;
            if (currentRally > longestRally) longestRally = currentRally;
            currentRally = 0;
        }

        void reset() {
            p1 = p2 = rallies = currentRally = longestRally = maxBallSpeed = playerHits = cpuHits = 0;
        }
    }

    // ==================== MULTIPLAYER MANAGER ====================
    static class MultiplayerManager {
        private static final String BASE_URL = "https://POCKETBASE.DEEWHY.OVH";
        private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

        private PongGame game;
        private String token = "";
        private String userId = "";
        private String gameId = "";
        private boolean isHost = false;
        private boolean active = false;
        private boolean loading = false;
        private long lastSync = 0;
        private long lastPaddleSend = 0;
        private int remotePaddleY = (GAME_HEIGHT - PADDLE_HEIGHT) / 2;

        private float rBallX, rBallY, rBallDX, rBallDY;
        private int rScore1, rScore2;
        private boolean waitingForPlayer2 = false;

        public MultiplayerManager(PongGame game) {
            this.game = game;
        }

        public boolean isLoading() { return loading; }
        public boolean isMultiplayerActive() { return active && !gameId.isEmpty(); }
        public boolean isHost() { return isHost; }
        public void resetRoom() {
            // Keep token + userId (still logged in), just clear local game session state.
            // NOTE: Do NOT touch the DB here — room cleanup is handled by endGame/forfeitGame.
            active = false;
            gameId = "";
            isHost = false;
            waitingForPlayer2 = false;
            remotePaddleY = (GAME_HEIGHT - PADDLE_HEIGHT) / 2;
            rBallX = rBallY = rBallDX = rBallDY = 0;
        }

        public boolean isConnected() { return !token.isEmpty(); }
        public int getRemotePaddleY() { return remotePaddleY; }

        public void disconnect() {
            active = false;
            gameId = "";
            token = "";
            userId = "";
        }

        public void fetchOpenRooms() {
            if (token.isEmpty()) return;
            new Thread(() -> {
                try {
                    // ── 1. READ EACH PERSISTENT ROOM — derive 3-state status ──
                    java.util.List<String[]> persistent = new java.util.ArrayList<>();
                    for (String[] pr : PongGame.PERSISTENT_ROOMS) {
                        String pName = pr[0];
                        var req = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/collections/games/records?filter=(roomId%3D'" + pName + "')&perPage=1"))
                            .header("Authorization", token).build();
                        var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                        String arrKey = "\"items\":[";
                        int ai = res.indexOf(arrKey);
                        String item = (ai >= 0) ? firstArrayItem(res.substring(ai + arrKey.length())) : null;
                        if (item != null && item.contains("\"id\"")) {
                            String gid = JsonUtil.get(item, "id");
                            String p1  = JsonUtil.get(item, "player1Id");
                            String p2  = JsonUtil.get(item, "player2Id");
                            String st  = JsonUtil.get(item, "status");
                            boolean p1ok = (p1 != null && p1.length() >= 10 && !"null".equals(p1));
                            boolean p2ok = (p2 != null && p2.length() >= 10 && !"null".equals(p2));
                            // Derive display from actual slot occupancy (not just status field)
                            String disp;
                            if ("finished".equals(st)) {
                                // Finished but not yet cleaned up — show as open
                                disp = "waiting";
                            } else if (p1ok && p2ok) {
                                disp = "playing";
                            } else if (p1ok) {
                                disp = "occupied"; // 1 player waiting for opponent
                            } else {
                                disp = "waiting";
                            }
                            persistent.add(new String[]{pName, gid != null ? gid : "", disp});
                        } else {
                            // Create this persistent room for the first time
                            String payload = "{\"roomId\":\"" + pName + "\",\"player1Id\":\"\",\"player2Id\":\"\"" +
                                ",\"player1Score\":0,\"player2Score\":0,\"winner\":\"\"" +
                                ",\"ballX\":" + (PongGame.GAME_WIDTH/2) + ",\"ballY\":" + (PongGame.GAME_HEIGHT/2) +
                                ",\"ballDX\":8,\"ballDY\":0" +
                                ",\"p1PaddleY\":" + ((PongGame.GAME_HEIGHT - PongGame.PADDLE_HEIGHT)/2) +
                                ",\"p2PaddleY\":" + ((PongGame.GAME_HEIGHT - PongGame.PADDLE_HEIGHT)/2) +
                                ",\"status\":\"waiting\"}";
                            var cReq = HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/collections/games/records"))
                                .header("Content-Type", "application/json").header("Authorization", token)
                                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                            var cRes = HTTP.send(cReq, HttpResponse.BodyHandlers.ofString()).body();
                            String gid = JsonUtil.get(cRes, "id");
                            persistent.add(new String[]{pName, gid != null ? gid : "", "waiting"});
                        }
                    }

                    // ── 2. USER-CREATED WAITING ROOMS (skip persistent names) ─
                    var req2 = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/games/records?filter=(status%3D'waiting')&sort=-created&perPage=20"))
                        .header("Authorization", token).build();
                    var res2 = HTTP.send(req2, HttpResponse.BodyHandlers.ofString()).body();
                    java.util.List<String[]> rooms = new java.util.ArrayList<>();
                    String arrKey2 = "\"items\":[";
                    int arrStart = res2.indexOf(arrKey2);
                    if (arrStart >= 0) {
                        int pos = arrStart + arrKey2.length();
                        while (pos < res2.length()) {
                            while (pos < res2.length() && res2.charAt(pos) != '{' && res2.charAt(pos) != ']') pos++;
                            if (pos >= res2.length() || res2.charAt(pos) == ']') break;
                            int depth = 0, recStart = pos;
                            while (pos < res2.length()) {
                                char ch = res2.charAt(pos);
                                if (ch == '"') { pos++; while (pos < res2.length() && res2.charAt(pos) != '"') { if (res2.charAt(pos) == '\\') pos++; pos++; } }
                                else if (ch == '{') depth++;
                                else if (ch == '}') { depth--; if (depth == 0) { pos++; break; } }
                                pos++;
                            }
                            String rec = res2.substring(recStart, pos);
                            String rid = JsonUtil.get(rec, "roomId");
                            String gid2 = JsonUtil.get(rec, "id");
                            boolean isPersist = false;
                            for (String[] p : PongGame.PERSISTENT_ROOMS) if (p[0].equals(rid)) { isPersist = true; break; }
                            if (!isPersist && rid != null && !rid.isEmpty() && gid2 != null)
                                rooms.add(new String[]{rid, gid2, "waiting"});
                        }
                    }

                    // ── 3. STATS ─────────────────────────────────────────────
                    var sReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/games/records?filter=(status%3D'playing')&perPage=1"))
                        .header("Authorization", token).build();
                    var sRes = HTTP.send(sReq, HttpResponse.BodyHandlers.ofString()).body();
                    String ti = JsonUtil.get(sRes, "totalItems");
                    int activePlaying = ti != null ? Integer.parseInt(ti) : 0;
                    int onlinePlayers = rooms.size() + activePlaying * 2;

                    // ── 4. CLEANUP finished non-persistent rooms ──────────────
                    var cleanReq = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/games/records?filter=(status%3D'finished')&perPage=50"))
                        .header("Authorization", token).build();
                    var cleanRes = HTTP.send(cleanReq, HttpResponse.BodyHandlers.ofString()).body();
                    int ci = cleanRes.indexOf("\"items\":[");
                    if (ci >= 0) {
                        int pos = ci + "\"items\":[".length();
                        while (pos < cleanRes.length()) {
                            while (pos < cleanRes.length() && cleanRes.charAt(pos) != '{' && cleanRes.charAt(pos) != ']') pos++;
                            if (pos >= cleanRes.length() || cleanRes.charAt(pos) == ']') break;
                            int depth = 0, rs = pos;
                            while (pos < cleanRes.length()) {
                                char ch = cleanRes.charAt(pos);
                                if (ch == '"') { pos++; while (pos < cleanRes.length() && cleanRes.charAt(pos) != '"') { if (cleanRes.charAt(pos) == '\\') pos++; pos++; } }
                                else if (ch == '{') depth++;
                                else if (ch == '}') { depth--; if (depth == 0) { pos++; break; } }
                                pos++;
                            }
                            String rec = cleanRes.substring(rs, pos);
                            String fid = JsonUtil.get(rec, "id");
                            String fRoom = JsonUtil.get(rec, "roomId");
                            boolean isPersist = false;
                            for (String[] p : PongGame.PERSISTENT_ROOMS) if (p[0].equals(fRoom)) { isPersist = true; break; }
                            if (fid != null && !isPersist) {
                                var delReq = HttpRequest.newBuilder()
                                    .uri(URI.create(BASE_URL + "/api/collections/games/records/" + fid))
                                    .header("Authorization", token)
                                    .method("DELETE", HttpRequest.BodyPublishers.noBody()).build();
                                HTTP.send(delReq, HttpResponse.BodyHandlers.ofString());
                            }
                        }
                    }

                    final java.util.List<String[]> fp = persistent;
                    final java.util.List<String[]> fr = rooms;
                    final int fOnline = onlinePlayers, fActive = activePlaying;
                    SwingUtilities.invokeLater(() -> {
                        game.mpPersistentRooms = fp;
                        game.mpRoomList = fr;
                        game.mpOnlinePlayers = fOnline;
                        game.mpActiveRooms = fActive;
                    });
                } catch (Exception e) { System.err.println("[rooms] error: " + e); }
            }).start();
        }

        public void register(String email, String password) {
            loading = true;
            new Thread(() -> {
                try {
                    String payload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"passwordConfirm\":\"" + password + "\"}";
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/pong/records"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                    String id = JsonUtil.get(res, "id");
                    if (id != null) {
                        // Auto-login after register
                        SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Account created! Please sign in."; game.state = State.MP_LOGIN; });
                    } else {
                        String errMsg = JsonUtil.get(res, "message");
                        final String msg = errMsg != null ? errMsg : "Registration failed";
                        SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = msg; });
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Connection failed"; });
                }
            }).start();
        }

        public void changeEmail(String newEmail) {
            loading = true;
            new Thread(() -> {
                try {
                    String payload = "{\"email\":\"" + newEmail + "\"}";
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/pong/records/" + userId))
                        .header("Content-Type", "application/json")
                        .header("Authorization", token)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                    String id = JsonUtil.get(res, "id");
                    if (id != null) {
                        SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Email updated!"; });
                    } else {
                        SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Update failed"; });
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Connection failed"; });
                }
            }).start();
        }

        public void changePassword(String newPassword, String confirm) {
            if (!newPassword.equals(confirm)) {
                game.mpLoginError = "Passwords do not match";
                return;
            }
            loading = true;
            new Thread(() -> {
                try {
                    String payload = "{\"password\":\"" + newPassword + "\",\"passwordConfirm\":\"" + confirm + "\"}";
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/pong/records/" + userId))
                        .header("Content-Type", "application/json")
                        .header("Authorization", token)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                    String id = JsonUtil.get(res, "id");
                    if (id != null) {
                        SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Password updated!"; });
                    } else {
                        SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Update failed"; });
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Connection failed"; });
                }
            }).start();
        }

        public void deleteAccount() {
            loading = true;
            new Thread(() -> {
                try {
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/pong/records/" + userId))
                        .header("Authorization", token)
                        .DELETE()
                        .build();
                    HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    SwingUtilities.invokeLater(() -> {
                        loading = false;
                        disconnect();
                        game.mpEmail = "";
                        game.mpPassword = "";
                        game.mpLoginError = "Account deleted.";
                        game.state = State.MP_LOGIN;
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> { loading = false; game.mpLoginError = "Delete failed"; });
                }
            }).start();
        }

        public String getUserId() { return userId; }
        public String getEmail() { return ""; }
        public boolean isWaitingForPlayer2() { return waitingForPlayer2; }
        public String getDisplayName() {
            // Use email prefix as display name, uppercased
            String email = game.mpEmail;
            if (email == null || email.isEmpty()) return "PLAYER";
            int at = email.indexOf('@');
            String name = at > 0 ? email.substring(0, at) : email;
            return name.length() > 10 ? name.substring(0, 10).toUpperCase() : name.toUpperCase();
        }

        public void login(String email, String password) {
            loading = true;
            new Thread(() -> {
                try {
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/pong/auth-with-password"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"identity\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                        .build();
                    var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                    String id = JsonUtil.get(res, "record", "id");
                    String tok = JsonUtil.get(res, "token");
                    if (id != null && tok != null) {
                        userId = id;
                        token = "Bearer " + tok;
                        SwingUtilities.invokeLater(() -> {
                            loading = false;
                            game.mpLoginError = "";
                            game.mpRoomIdInput = ""; game.mpCreateRoomInput = "";
                            game.state = State.MP_LOBBY;
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            loading = false;
                            game.mpLoginError = "Invalid email or password";
                        });
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        loading = false;
                        game.mpLoginError = "Connection failed — check server";
                    });
                }
            }).start();
        }

        // Extract first object from a JSON array string (handles nesting correctly)
        private String firstArrayItem(String arrayStr) {
            if (arrayStr == null) return null;
            int start = arrayStr.indexOf('{');
            if (start < 0) return null;
            int depth = 0, pos = start;
            while (pos < arrayStr.length()) {
                char ch = arrayStr.charAt(pos);
                if (ch == '"') { pos++; while (pos < arrayStr.length() && arrayStr.charAt(pos) != '"') { if (arrayStr.charAt(pos) == '\\') pos++; pos++; } }
                else if (ch == '{') depth++;
                else if (ch == '}') { depth--; if (depth == 0) return arrayStr.substring(start, pos + 1); }
                pos++;
            }
            return null;
        }

        public void joinOrCreateRoom(String roomId) {
            loading = true;
            new Thread(() -> {
                try {
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/games/records?filter=(roomId%3D'" + roomId + "')"))
                        .header("Authorization", token)
                        .build();
                    var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                    System.out.println("[join] list response: " + res.substring(0, Math.min(400, res.length())));
                    String arrKey = "\"items\":[";
                    int ai = res.indexOf(arrKey);
                    String firstItem = ai >= 0 ? firstArrayItem(res.substring(ai + arrKey.length())) : null;

                    // Check if this is a persistent room
                    boolean isPersistent = false;
                    for (String[] pr : PongGame.PERSISTENT_ROOMS) {
                        if (pr[0].equals(roomId)) { isPersistent = true; break; }
                    }

                    if (firstItem != null && firstItem.contains("\"id\"")) {
                        String existingId = JsonUtil.get(firstItem, "id");
                        String p1 = JsonUtil.get(firstItem, "player1Id");
                        String p2 = JsonUtil.get(firstItem, "player2Id");
                        boolean p1Empty = (p1 == null || p1.isEmpty() || "null".equals(p1) || p1.length() < 10);
                        boolean p2Empty = (p2 == null || p2.isEmpty() || "null".equals(p2) || p2.length() < 10);

                        if (isPersistent) {
                            // Persistent room: slot-based assignment
                            if (p1Empty) {
                                // Claim player1 slot → become host, wait for player2
                                var patch = HttpRequest.newBuilder()
                                    .uri(URI.create(BASE_URL + "/api/collections/games/records/" + existingId))
                                    .header("Content-Type", "application/json").header("Authorization", token)
                                    .method("PATCH", HttpRequest.BodyPublishers.ofString(
                                        "{\"player1Id\":\"" + userId + "\",\"player2Id\":\"\",\"status\":\"waiting\"" +
                                        ",\"player1Score\":0,\"player2Score\":0,\"winner\":\"\"}"))
                                    .build();
                                HTTP.send(patch, HttpResponse.BodyHandlers.ofString());
                                gameId = existingId; isHost = true;
                            } else if (p2Empty) {
                                // Claim player2 slot → become client, start game
                                var patch = HttpRequest.newBuilder()
                                    .uri(URI.create(BASE_URL + "/api/collections/games/records/" + existingId))
                                    .header("Content-Type", "application/json").header("Authorization", token)
                                    .method("PATCH", HttpRequest.BodyPublishers.ofString(
                                        "{\"player2Id\":\"" + userId + "\",\"status\":\"playing\"}"))
                                    .build();
                                HTTP.send(patch, HttpResponse.BodyHandlers.ofString());
                                gameId = existingId; isHost = false;
                            } else {
                                SwingUtilities.invokeLater(() -> {
                                    loading = false;
                                    game.mpLoginError = "Room is full — try another";
                                    new javax.swing.Timer(2000, ev -> { game.mpLoginError = ""; ((javax.swing.Timer)ev.getSource()).stop(); }).start();
                                });
                                return;
                            }
                        } else {
                            // Custom room: first joiner created it as host; second joiner is client
                            if (!p2Empty) {
                                SwingUtilities.invokeLater(() -> {
                                    loading = false;
                                    game.mpLoginError = "Room is full — try a different Room ID";
                                    new javax.swing.Timer(2000, ev -> { game.mpLoginError = ""; ((javax.swing.Timer)ev.getSource()).stop(); }).start();
                                });
                                return;
                            }
                            var patch = HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/collections/games/records/" + existingId))
                                .header("Content-Type", "application/json").header("Authorization", token)
                                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                                    "{\"player2Id\":\"" + userId + "\",\"status\":\"playing\"}"))
                                .build();
                            HTTP.send(patch, HttpResponse.BodyHandlers.ofString());
                            gameId = existingId; isHost = false;
                        }
                    } else {
                        // No record found — create a new custom room (caller is host/player1)
                        var payload = "{\"roomId\":\"" + roomId + "\",\"player1Id\":\"" + userId +
                            "\",\"player2Id\":\"\",\"player1Score\":0,\"player2Score\":0,\"winner\":\"\"" +
                            ",\"ballX\":" + (GAME_WIDTH/2) + ",\"ballY\":" + (GAME_HEIGHT/2) +
                            ",\"ballDX\":8,\"ballDY\":0" +
                            ",\"p1PaddleY\":" + ((GAME_HEIGHT-PADDLE_HEIGHT)/2) +
                            ",\"p2PaddleY\":" + ((GAME_HEIGHT-PADDLE_HEIGHT)/2) +
                            ",\"status\":\"waiting\"}";
                        var createReq = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/collections/games/records"))
                            .header("Content-Type", "application/json").header("Authorization", token)
                            .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                        var createRes = HTTP.send(createReq, HttpResponse.BodyHandlers.ofString()).body();
                        gameId = JsonUtil.get(createRes, "id");
                        isHost = true;
                    }

                    active = true;
                    waitingForPlayer2 = isHost;
                    SwingUtilities.invokeLater(() -> {
                        loading = false;
                        if (isHost) {
                            game.state = State.MP_WAITING;
                        } else {
                            game.resetGame();
                        }
                    });

                    SCHEDULER.scheduleAtFixedRate(this::pollServer, 0, 100, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    e.printStackTrace();
                    final String msg = e.getMessage() != null ? e.getMessage() : "Network error";
                    SwingUtilities.invokeLater(() -> {
                        loading = false;
                        game.mpLoginError = "Room error: " + msg;
                    });
                }
            }).start();
        }

        private void pollServer() {
            if (!active || gameId.isEmpty()) return;
            try {
                var req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/collections/games/records/" + gameId))
                    .header("Authorization", token)
                    .build();
                var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
                String status = JsonUtil.get(res, "status");

                // Host waiting: check if player2 has joined and status is playing
                if (waitingForPlayer2 && isHost) {
                    String p2id = JsonUtil.get(res, "player2Id");
                    boolean p2joined = p2id != null && p2id.length() >= 10 && !"null".equals(p2id);
                    boolean statusPlaying = "playing".equals(status);
                    if (p2joined && statusPlaying) {
                        waitingForPlayer2 = false;
                        SwingUtilities.invokeLater(() -> game.resetGame());
                    }
                    return;
                }

                if ("playing".equals(status)) {
                    // Remote paddle: always safe to sync
                    String py = isHost ? JsonUtil.get(res, "p2PaddleY") : JsonUtil.get(res, "p1PaddleY");
                    if (py != null) remotePaddleY = (int) Float.parseFloat(py);

                    if (!isHost) {
                        // Client: take ball position from host
                        String bx = JsonUtil.get(res, "ballX");
                        String by = JsonUtil.get(res, "ballY");
                        String bdx = JsonUtil.get(res, "ballDX");
                        String bdy = JsonUtil.get(res, "ballDY");
                        if (bx != null) rBallX = Float.parseFloat(bx);
                        if (by != null) rBallY = Float.parseFloat(by);
                        if (bdx != null) rBallDX = Float.parseFloat(bdx);
                        if (bdy != null) rBallDY = Float.parseFloat(bdy);

                        // Client: take scores from DB (host is the authority)
                        String s1s = JsonUtil.get(res, "player1Score");
                        String s2s = JsonUtil.get(res, "player2Score");
                        if (s1s != null && s2s != null) {
                            final int s1 = Integer.parseInt(s1s);
                            final int s2 = Integer.parseInt(s2s);
                            SwingUtilities.invokeLater(() -> {
                                game.score.p1 = s1;
                                game.score.p2 = s2;
                            });
                        }
                    }
                    // Host: scores are local; DB is written by sendState
                } else if ("finished".equals(status)) {
                    // Either player: game ended (normal finish or forfeit)
                    String s1s = JsonUtil.get(res, "player1Score");
                    String s2s = JsonUtil.get(res, "player2Score");
                    String winner = JsonUtil.get(res, "winner");
                    final int s1 = s1s != null ? Integer.parseInt(s1s) : game.score.p1;
                    final int s2 = s2s != null ? Integer.parseInt(s2s) : game.score.p2;
                    // Only act if we didn't write this ourselves (client always, host only on forfeit)
                    boolean clientShouldAct = !isHost;
                    boolean hostShouldAct = isHost && "forfeit".equals(winner);
                    if (clientShouldAct || hostShouldAct) {
                        SwingUtilities.invokeLater(() -> {
                            game.score.p1 = s1;
                            game.score.p2 = s2;
                            if (game.state == State.PLAYING || game.state == State.COUNTDOWN || game.state == State.PAUSED) {
                                game.state = State.GAMEOVER;
                                if (game.soundEnabled) {
                                    boolean iWon = isHost ? s1 >= WIN_SCORE : s2 >= WIN_SCORE;
                                    game.soundManager.playGameOver(iWon);
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) { System.err.println("pollServer: " + e.getMessage()); }
        }

        public void sendPaddleMove(int paddleY) {
            if (!active || gameId.isEmpty()) return;
            if (System.currentTimeMillis() - lastPaddleSend < 50) return;
            lastPaddleSend = System.currentTimeMillis();
            String field = isHost ? "p1PaddleY" : "p2PaddleY";
            String payload = "{\"" + field + "\":" + paddleY + "}";
            String gId = gameId; String tok = token;
            new Thread(() -> {
                try {
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/games/records/" + gId))
                        .header("Content-Type", "application/json")
                        .header("Authorization", tok)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {}
            }).start();
        }

        public void sendState(PongGame g) {
            if (!active || gameId.isEmpty() || !isHost) return;
            if (System.currentTimeMillis() - lastSync < 50) return;
            lastSync = System.currentTimeMillis();
            String payload = String.format(
                "{\"ballX\":%d,\"ballY\":%d,\"ballDX\":%d,\"ballDY\":%d," +
                "\"p1PaddleY\":%d,\"p2PaddleY\":%d,\"player1Score\":%d,\"player2Score\":%d}",
                g.ball.x, g.ball.y, g.ball.dx, g.ball.dy,
                g.player.y, g.cpu.y, g.score.p1, g.score.p2
            );
            String gId = gameId; String tok = token;
            new Thread(() -> {
                try {
                    var req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/collections/games/records/" + gId))
                        .header("Content-Type", "application/json")
                        .header("Authorization", tok)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {}
            }).start();
        }

        public void syncGame(PongGame g) {
            if (isHost) return;
            g.ball.x = (int) rBallX;
            g.ball.y = (int) rBallY;
            g.ball.dx = (int) rBallDX;
            g.ball.dy = (int) rBallDY;
        }

        public void endGame(boolean won) {
            if (!active || gameId.isEmpty() || !isHost) return;
            try {
                // Clear both player slots so the persistent room is ready for the next game
                var payload = "{\"status\":\"finished\",\"winner\":\"" + (won ? userId : "opponent") + "\"" +
                    ",\"player1Id\":\"\",\"player2Id\":\"\"}";
                var req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/collections/games/records/" + gameId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", token)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {}
        }

        /** Called by either player when leaving mid-game. Marks the game finished
         *  with the leaver as the loser so the other player sees GAMEOVER. */
        public void forfeitGame() {
            if (!active || gameId.isEmpty()) return;
            try {
                String p1Score, p2Score;
                if (isHost) {
                    p1Score = "0"; p2Score = String.valueOf(WIN_SCORE);
                } else {
                    p1Score = String.valueOf(WIN_SCORE); p2Score = "0";
                }
                // Clear both player slots so the persistent room is immediately reusable
                var payload = "{\"status\":\"finished\",\"player1Score\":" + p1Score
                    + ",\"player2Score\":" + p2Score + ",\"winner\":\"forfeit\""
                    + ",\"player1Id\":\"\",\"player2Id\":\"\"}";
                var req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/collections/games/records/" + gameId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", token)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) { System.err.println("forfeit error: " + e); }
        }
    }

    // ==================== JSON UTIL ====================
    static class JsonUtil {
        public static String get(String json, String... keys) {
            if (json == null) return null;
            String current = json;
            for (String key : keys) {
                String search = "\"" + key + "\":";
                int idx = current.indexOf(search);
                if (idx == -1) return null;
                int start = idx + search.length();
                while (start < current.length() && Character.isWhitespace(current.charAt(start))) start++;
                if (start >= current.length()) return null;
                
                char c = current.charAt(start);
                if (c == '"') {
                    int end = current.indexOf('"', start + 1);
                    if (end == -1) return null;
                    current = current.substring(start + 1, end);
                } else if (c == '{') {
                    int depth = 1;
                    int end = start + 1;
                    while (end < current.length() && depth > 0) {
                        if (current.charAt(end) == '{') depth++;
                        if (current.charAt(end) == '}') depth--;
                        end++;
                    }
                    current = current.substring(start, end);
                } else {
                    int end = start;
                    while (end < current.length() && current.charAt(end) != ',' && current.charAt(end) != '}') end++;
                    current = current.substring(start, end).trim();
                }
            }
            return current;
        }
    }
}