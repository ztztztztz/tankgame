import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * 坦克大战 — 单文件 Java Swing 游戏，JDK 8+ 兼容
 * 编译: javac TankGame.java
 * 运行: java TankGame
 */
public class TankGame extends JFrame {

    public TankGame() {
        setTitle("坦克大战 - Tank Battle");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel panel = new GamePanel();
        add(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        panel.requestFocusInWindow();
        panel.startGame();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TankGame();
            }
        });
    }
}

/* ==================== 方向枚举 ==================== */
enum Direction {
    UP, DOWN, LEFT, RIGHT;

    public Direction opposite() {
        switch (this) {
            case UP:    return DOWN;
            case DOWN:  return UP;
            case LEFT:  return RIGHT;
            case RIGHT: return LEFT;
            default:    return UP;
        }
    }
}

/* ==================== 墙壁类 ==================== */
class Wall {
    public static final int BRICK = 0;
    public static final int STEEL = 1;

    int x, y, width, height, type;
    boolean alive;

    public Wall(int x, int y, int width, int height, int type) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
        this.alive = true;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public void draw(Graphics g) {
        if (!alive) return;
        if (type == BRICK) {
            g.setColor(new Color(180, 100, 40));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(140, 70, 20));
            g.drawRect(x, y, width, height);
            // 砖纹
            g.drawLine(x + width / 2, y, x + width / 2, y + height);
            g.drawLine(x, y + height / 2, x + width, y + height / 2);
        } else {
            g.setColor(new Color(160, 160, 160));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(80, 80, 80));
            g.drawRect(x, y, width, height);
            g.setColor(new Color(190, 190, 190));
            g.fillRect(x + 4, y + 4, width - 8, height - 8);
        }
    }
}

/* ==================== 子弹类 ==================== */
class Bullet {
    int x, y, speed;
    Direction direction;
    boolean fromPlayer;
    boolean alive;

    public static final int SIZE = 6;

    public Bullet(int x, int y, Direction direction, boolean fromPlayer) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.fromPlayer = fromPlayer;
        this.speed = 8;
        this.alive = true;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, SIZE, SIZE);
    }

    public void move() {
        switch (direction) {
            case UP:    y -= speed; break;
            case DOWN:  y += speed; break;
            case LEFT:  x -= speed; break;
            case RIGHT: x += speed; break;
        }
        // 飞出边界则销毁
        if (x < 0 || y < 0 || x > 800 || y > 600) {
            alive = false;
        }
    }

    public void draw(Graphics g) {
        if (!alive) return;
        g.setColor(fromPlayer ? Color.YELLOW : new Color(255, 80, 80));
        g.fillOval(x, y, SIZE, SIZE);
    }
}

/* ==================== 爆炸效果类 ==================== */
class Explosion {
    int x, y, radius, maxRadius;
    boolean alive;

    public Explosion(int x, int y, int maxRadius) {
        this.x = x;
        this.y = y;
        this.radius = 2;
        this.maxRadius = maxRadius;
        this.alive = true;
    }

    public void update() {
        radius += 3;
        if (radius > maxRadius) {
            alive = false;
        }
    }

    public void draw(Graphics g) {
        if (!alive) return;
        int alpha = (int)(255 * (1.0 - (double)radius / maxRadius));
        if (alpha < 0) alpha = 0;
        g.setColor(new Color(255, 200, 50, alpha));
        g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        g.setColor(new Color(255, 100, 30, alpha / 2));
        g.fillOval(x - radius / 2, y - radius / 2, radius, radius);
    }
}

/* ==================== 坦克类 ==================== */
class Tank {
    int x, y;
    int width = 36, height = 36;
    Direction direction;
    int speed;
    boolean alive;
    boolean isPlayer;
    long lastShotTime;
    int shotCooldown;
    Color bodyColor, treadColor;
    int invincibleTimer; // 无敌计时器（帧数）
    long aiLastChange;   // AI 上次换方向时间
    int aiChangeInterval; // AI 换方向间隔（帧数）

    public Tank(int x, int y, Direction direction, boolean isPlayer) {
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.isPlayer = isPlayer;
        this.alive = true;
        this.invincibleTimer = 0;

        if (isPlayer) {
            this.speed = 4;
            this.shotCooldown = 350;
            this.bodyColor = new Color(255, 220, 50);
            this.treadColor = new Color(180, 150, 20);
        } else {
            this.speed = 2;
            this.shotCooldown = 1200;
            this.bodyColor = new Color(200, 50, 50);
            this.treadColor = new Color(120, 30, 30);
            this.aiLastChange = System.currentTimeMillis();
            this.aiChangeInterval = 1000 + (int)(Math.random() * 2000);
        }
        this.lastShotTime = 0;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    /**
     * 尝试移动坦克。如果成功返回 true，被阻挡返回 false。
     */
    public boolean move(Direction dir, List<Wall> walls, List<Tank> tanks) {
        this.direction = dir;
        int newX = x, newY = y;

        switch (dir) {
            case UP:    newY -= speed; break;
            case DOWN:  newY += speed; break;
            case LEFT:  newX -= speed; break;
            case RIGHT: newX += speed; break;
        }

        // 边界检查
        if (newX < 0 || newY < 0 || newX + width > GamePanel.PANEL_WIDTH || newY + height > GamePanel.PANEL_HEIGHT) {
            return false;
        }

        Rectangle newBounds = new Rectangle(newX, newY, width, height);

        // 墙壁碰撞
        for (Wall wall : walls) {
            if (wall.alive && newBounds.intersects(wall.getBounds())) {
                return false;
            }
        }

        // 坦克间碰撞
        for (Tank tank : tanks) {
            if (tank != this && tank.alive && newBounds.intersects(tank.getBounds())) {
                return false;
            }
        }

        x = newX;
        y = newY;
        return true;
    }

    public Bullet shoot() {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < shotCooldown) return null;
        lastShotTime = now;

        int bx = 0, by = 0;
        switch (direction) {
            case UP:
                bx = x + width / 2 - Bullet.SIZE / 2;
                by = y - Bullet.SIZE;
                break;
            case DOWN:
                bx = x + width / 2 - Bullet.SIZE / 2;
                by = y + height;
                break;
            case LEFT:
                bx = x - Bullet.SIZE;
                by = y + height / 2 - Bullet.SIZE / 2;
                break;
            case RIGHT:
                bx = x + width;
                by = y + height / 2 - Bullet.SIZE / 2;
                break;
        }
        return new Bullet(bx, by, direction, isPlayer);
    }

    public void updateInvincible() {
        if (invincibleTimer > 0) {
            invincibleTimer--;
        }
    }

    public boolean isInvincible() {
        return invincibleTimer > 0;
    }

    public void draw(Graphics g) {
        if (!alive) return;

        int cx = x + width / 2;
        int cy = y + height / 2;

        // 无敌闪烁效果
        if (isInvincible() && (invincibleTimer / 5) % 2 == 0) {
            return; // 闪烁：每5帧切换显示/隐藏
        }

        Graphics2D g2 = (Graphics2D) g;

        // 履带
        g2.setColor(treadColor);
        int treadW = 8;
        switch (direction) {
            case UP: case DOWN:
                g2.fillRect(x, y, treadW, height);
                g2.fillRect(x + width - treadW, y, treadW, height);
                break;
            case LEFT: case RIGHT:
                g2.fillRect(x, y, width, treadW);
                g2.fillRect(x, y + height - treadW, width, treadW);
                break;
        }

        // 车身
        g2.setColor(bodyColor);
        int margin = 6;
        g2.fillRect(x + margin, y + margin, width - margin * 2, height - margin * 2);

        // 炮塔（圆形）
        g2.setColor(bodyColor.darker());
        int turretR = 7;
        g2.fillOval(cx - turretR, cy - turretR, turretR * 2, turretR * 2);

        // 炮管
        g2.setColor(bodyColor.darker());
        int barrelW = 6, barrelL = 16;
        switch (direction) {
            case UP:
                g2.fillRect(cx - barrelW / 2, cy - barrelL, barrelW, barrelL);
                break;
            case DOWN:
                g2.fillRect(cx - barrelW / 2, cy, barrelW, barrelL);
                break;
            case LEFT:
                g2.fillRect(cx - barrelL, cy - barrelW / 2, barrelL, barrelW);
                break;
            case RIGHT:
                g2.fillRect(cx, cy - barrelW / 2, barrelL, barrelW);
                break;
        }

        // 无敌光环
        if (isInvincible()) {
            g2.setColor(new Color(255, 255, 255, 150));
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx - 22, cy - 22, 44, 44);
            g2.setStroke(new BasicStroke(1));
        }
    }
}

/* ==================== 游戏面板 ==================== */
class GamePanel extends JPanel implements Runnable, KeyListener {

    public static final int PANEL_WIDTH = 800;
    public static final int PANEL_HEIGHT = 600;
    public static final int CELL = 40;
    public static final int COLS = 20;
    public static final int ROWS = 15;

    // 地图定义: 0=空地, 1=砖墙, 2=钢铁
    private static final int[][] MAP = {
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1},
        {0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1},
        {0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1},
        {0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {1,1,0,0,1,1,0,0,2,2,0,0,1,1,0,0,1,1,0,0},
        {1,1,0,0,1,1,0,0,2,2,0,0,1,1,0,0,1,1,0,0},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1},
        {0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    };

    // 游戏状态
    private static final int STATE_WAITING = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_PAUSED = 2;
    private static final int STATE_GAME_OVER = 3;

    private int gameState = STATE_WAITING;

    // 游戏对象
    private Tank player;
    private final List<Tank> enemies = Collections.synchronizedList(new ArrayList<Tank>());
    private final List<Bullet> bullets = Collections.synchronizedList(new ArrayList<Bullet>());
    private final List<Wall> walls = new ArrayList<Wall>();
    private final List<Explosion> explosions = Collections.synchronizedList(new ArrayList<Explosion>());

    // 输入状态
    private final Set<Integer> pressedKeys = Collections.synchronizedSet(new HashSet<Integer>());

    // 游戏数据
    private int score = 0;
    private int lives = 3;
    private int maxEnemies = 5;
    private int enemiesKilled = 0;

    // 游戏循环
    private Thread gameThread;
    private volatile boolean threadRunning = false;

    // AI 计时
    private long lastEnemySpawnTime = 0;

    public GamePanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusable(true);
        addKeyListener(this);
        initWalls();
    }

    private void initWalls() {
        walls.clear();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int type = MAP[row][col];
                if (type == 1) {
                    walls.add(new Wall(col * CELL, row * CELL, CELL, CELL, Wall.BRICK));
                } else if (type == 2) {
                    walls.add(new Wall(col * CELL, row * CELL, CELL, CELL, Wall.STEEL));
                }
            }
        }
    }

    public void startGame() {
        threadRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    private void initGame() {
        score = 0;
        lives = 3;
        enemiesKilled = 0;
        enemies.clear();
        bullets.clear();
        explosions.clear();
        initWalls();
        spawnPlayer();
        spawnEnemies(3);
        gameState = STATE_RUNNING;
    }

    private void spawnPlayer() {
        // 出生在底部中央
        int px = (COLS / 2) * CELL + (CELL - 36) / 2;
        int py = (ROWS - 1) * CELL + (CELL - 36) / 2;
        player = new Tank(px, py, Direction.UP, true);
        player.invincibleTimer = 90; // 1.5 秒出生无敌
    }

    private void spawnEnemies(int count) {
        for (int i = 0; i < count; i++) {
            if (enemies.size() >= maxEnemies) break;
            spawnOneEnemy();
        }
    }

    private void spawnOneEnemy() {
        // 随机出生点（顶部三行，避开墙壁）
        int col, row;
        boolean valid;
        int attempts = 0;
        do {
            col = (int)(Math.random() * COLS);
            row = (int)(Math.random() * 3); // 第 0~2 行
            valid = true;
            Rectangle spawnRect = new Rectangle(col * CELL + 2, row * CELL + 2, 36, 36);
            for (Wall w : walls) {
                if (w.alive && spawnRect.intersects(w.getBounds())) {
                    valid = false;
                    break;
                }
            }
            for (Tank t : enemies) {
                if (t.alive && spawnRect.intersects(t.getBounds())) {
                    valid = false;
                    break;
                }
            }
            if (player != null && player.alive && spawnRect.intersects(player.getBounds())) {
                valid = false;
            }
            attempts++;
        } while (!valid && attempts < 30);

        int ex = col * CELL + (CELL - 36) / 2;
        int ey = row * CELL + (CELL - 36) / 2;
        Tank enemy = new Tank(ex, ey, Direction.DOWN, false);
        enemies.add(enemy);
    }

    /* ========== 游戏循环 ========== */
    public void run() {
        while (threadRunning) {
            long startTime = System.currentTimeMillis();

            if (gameState == STATE_RUNNING) {
                update();
            }

            repaint();

            long elapsed = System.currentTimeMillis() - startTime;
            long sleepTime = 16 - elapsed; // 约 60 FPS
            if (sleepTime < 1) sleepTime = 1;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /* ========== 更新逻辑 ========== */
    private void update() {
        handleInput();
        updateTanks();
        updateBullets();
        updateExplosions();
        checkCollisions();
        updateEnemyAI();
        spawnCheck();
    }

    private void handleInput() {
        if (player == null || !player.alive) return;

        if (pressedKeys.contains(KeyEvent.VK_UP)) {
            player.move(Direction.UP, walls, enemies);
        }
        if (pressedKeys.contains(KeyEvent.VK_DOWN)) {
            player.move(Direction.DOWN, walls, enemies);
        }
        if (pressedKeys.contains(KeyEvent.VK_LEFT)) {
            player.move(Direction.LEFT, walls, enemies);
        }
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) {
            player.move(Direction.RIGHT, walls, enemies);
        }
        if (pressedKeys.contains(KeyEvent.VK_SPACE)) {
            Bullet b = player.shoot();
            if (b != null) {
                bullets.add(b);
            }
        }
    }

    private void updateTanks() {
        player.updateInvincible();

        synchronized (enemies) {
            Iterator<Tank> it = enemies.iterator();
            while (it.hasNext()) {
                Tank enemy = it.next();
                if (!enemy.alive) {
                    it.remove();
                } else {
                    enemy.updateInvincible();
                }
            }
        }
    }

    private void updateBullets() {
        synchronized (bullets) {
            Iterator<Bullet> it = bullets.iterator();
            while (it.hasNext()) {
                Bullet b = it.next();
                b.move();
                if (!b.alive) {
                    it.remove();
                }
            }
        }
    }

    private void updateExplosions() {
        synchronized (explosions) {
            Iterator<Explosion> it = explosions.iterator();
            while (it.hasNext()) {
                Explosion e = it.next();
                e.update();
                if (!e.alive) {
                    it.remove();
                }
            }
        }
    }

    private void checkCollisions() {
        // 子弹碰撞检测
        synchronized (bullets) {
            Iterator<Bullet> bit = bullets.iterator();
            while (bit.hasNext()) {
                Bullet b = bit.next();
                if (!b.alive) { bit.remove(); continue; }

                Rectangle bBounds = b.getBounds();
                boolean bulletRemoved = false;

                // 子弹 vs 墙壁
                for (Wall wall : walls) {
                    if (!wall.alive) continue;
                    if (bBounds.intersects(wall.getBounds())) {
                        bulletRemoved = true;
                        if (wall.type == Wall.BRICK) {
                            wall.alive = false;
                            addExplosion(wall.x + wall.width / 2, wall.y + wall.height / 2, 20);
                        } else {
                            addExplosion(b.x, b.y, 10);
                        }
                        break;
                    }
                }

                if (bulletRemoved) { bit.remove(); continue; }

                // 玩家子弹 vs 敌人
                if (b.fromPlayer) {
                    synchronized (enemies) {
                        for (Tank enemy : enemies) {
                            if (!enemy.alive) continue;
                            if (bBounds.intersects(enemy.getBounds())) {
                                enemy.alive = false;
                                bulletRemoved = true;
                                score += 100;
                                enemiesKilled++;
                                addExplosion(enemy.x + enemy.width / 2, enemy.y + enemy.height / 2, 30);
                                break;
                            }
                        }
                    }
                } else {
                    // 敌人子弹 vs 玩家
                    if (player != null && player.alive && !player.isInvincible()) {
                        if (bBounds.intersects(player.getBounds())) {
                            player.alive = false;
                            bulletRemoved = true;
                            addExplosion(player.x + player.width / 2, player.y + player.height / 2, 30);
                            lives--;
                        }
                    }
                }

                if (bulletRemoved) { bit.remove(); }
            }
        }
    }

    private void updateEnemyAI() {
        synchronized (enemies) {
            // 收集玩家和敌人列表用于碰撞检测
            List<Tank> allTanks = new ArrayList<Tank>();
            allTanks.addAll(enemies);
            if (player != null && player.alive) allTanks.add(player);

            for (Tank enemy : enemies) {
                if (!enemy.alive) continue;

                // 随机换方向
                long now = System.currentTimeMillis();
                if (now - enemy.aiLastChange > enemy.aiChangeInterval) {
                    enemy.aiLastChange = now;
                    enemy.aiChangeInterval = 1000 + (int)(Math.random() * 2000);
                    Direction newDir = randomDirection(enemy.direction);
                    enemy.direction = newDir;
                }

                // 沿当前方向移动
                boolean moved = enemy.move(enemy.direction, walls, allTanks);
                if (!moved) {
                    // 被挡住，换方向
                    Direction newDir = randomDirection(enemy.direction);
                    enemy.direction = newDir;
                    enemy.move(newDir, walls, allTanks);
                }

                // 随机射击
                if (Math.random() < 0.015) { // 每帧约 1.5% 概率
                    Bullet eb = enemy.shoot();
                    if (eb != null) {
                        bullets.add(eb);
                    }
                }
            }
        }
    }

    private Direction randomDirection(Direction exclude) {
        Direction[] values = Direction.values();
        Direction chosen;
        do {
            chosen = values[(int)(Math.random() * values.length)];
        } while (chosen == exclude || chosen == exclude.opposite());
        return chosen;
    }

    private void spawnCheck() {
        // 玩家死亡处理
        if (player == null || !player.alive) {
            if (lives > 0) {
                // 清除屏幕上所有敌人子弹（给玩家喘息空间）
                synchronized (bullets) {
                    Iterator<Bullet> it = bullets.iterator();
                    while (it.hasNext()) {
                        Bullet b = it.next();
                        if (!b.fromPlayer) it.remove();
                    }
                }
                spawnPlayer();
            } else if (lives <= 0) {
                gameState = STATE_GAME_OVER;
                return;
            }
        }

        // 补充敌人生成
        long now = System.currentTimeMillis();
        if (now - lastEnemySpawnTime > 2000) { // 每 2 秒检查
            lastEnemySpawnTime = now;
            if (enemies.size() < maxEnemies) {
                spawnOneEnemy();
            }
        }
    }

    private void addExplosion(int x, int y, int maxRadius) {
        explosions.add(new Explosion(x, y, maxRadius));
    }

    /* ========== 渲染 ========== */
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 绘制网格线（淡化）
        g2.setColor(new Color(40, 40, 40));
        for (int i = 0; i <= COLS; i++) g2.drawLine(i * CELL, 0, i * CELL, PANEL_HEIGHT);
        for (int i = 0; i <= ROWS; i++) g2.drawLine(0, i * CELL, PANEL_WIDTH, i * CELL);

        // 绘制墙壁
        for (Wall wall : walls) {
            wall.draw(g2);
        }

        // 绘制爆炸
        synchronized (explosions) {
            for (Explosion e : explosions) {
                e.draw(g2);
            }
        }

        // 绘制子弹
        synchronized (bullets) {
            for (Bullet b : bullets) {
                b.draw(g2);
            }
        }

        // 绘制敌人
        synchronized (enemies) {
            for (Tank enemy : enemies) {
                enemy.draw(g2);
            }
        }

        // 绘制玩家
        if (player != null) {
            player.draw(g2);
        }

        // 绘制 HUD
        drawHUD(g2);

        // 绘制状态覆盖层
        if (gameState == STATE_WAITING) {
            drawTitleScreen(g2);
        } else if (gameState == STATE_PAUSED) {
            drawPauseScreen(g2);
        } else if (gameState == STATE_GAME_OVER) {
            drawGameOverScreen(g2);
        }
    }

    private void drawHUD(Graphics2D g) {
        // 顶部信息栏半透明背景
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, PANEL_WIDTH, 30);

        g.setColor(Color.WHITE);
        g.setFont(new Font("微软雅黑", Font.BOLD, 16));
        g.drawString("分数: " + score, 15, 22);
        g.drawString("生命: " + lives, 200, 22);
        g.drawString("击杀: " + enemiesKilled, 380, 22);

        // 操作提示
        g.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        g.setColor(Color.LIGHT_GRAY);
        String help = "移动: ↑↓←→  射击: 空格  暂停: P  重新开始: R";
        g.drawString(help, 540, 22);
    }

    private void drawTitleScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        g.setColor(Color.YELLOW);
        g.setFont(new Font("微软雅黑", Font.BOLD, 56));
        String title = "坦克大战";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (PANEL_WIDTH - fm.stringWidth(title)) / 2, 200);

        g.setColor(Color.WHITE);
        g.setFont(new Font("微软雅黑", Font.BOLD, 22));
        String sub = "TANK BATTLE";
        fm = g.getFontMetrics();
        g.drawString(sub, (PANEL_WIDTH - fm.stringWidth(sub)) / 2, 245);

        g.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        g.setColor(Color.ORANGE);
        String start = "按 ENTER 开始游戏";
        fm = g.getFontMetrics();
        g.drawString(start, (PANEL_WIDTH - fm.stringWidth(start)) / 2, 330);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        String[] controls = {
            "↑ ↓ ← →  移动坦克",
            "空格键    发射子弹",
            "P 键      暂停游戏",
            "R 键      重新开始"
        };
        int cy = 400;
        for (String line : controls) {
            fm = g.getFontMetrics();
            g.drawString(line, (PANEL_WIDTH - fm.stringWidth(line)) / 2, cy);
            cy += 30;
        }
    }

    private void drawPauseScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("微软雅黑", Font.BOLD, 48));
        String pause = "暂停";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(pause, (PANEL_WIDTH - fm.stringWidth(pause)) / 2, PANEL_HEIGHT / 2 - 20);

        g.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        g.setColor(Color.LIGHT_GRAY);
        String resume = "按 P 继续游戏";
        fm = g.getFontMetrics();
        g.drawString(resume, (PANEL_WIDTH - fm.stringWidth(resume)) / 2, PANEL_HEIGHT / 2 + 30);
    }

    private void drawGameOverScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        g.setColor(Color.RED);
        g.setFont(new Font("微软雅黑", Font.BOLD, 52));
        String over = "游戏结束";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(over, (PANEL_WIDTH - fm.stringWidth(over)) / 2, 200);

        g.setColor(Color.WHITE);
        g.setFont(new Font("微软雅黑", Font.BOLD, 22));
        String sc = "最终分数: " + score;
        fm = g.getFontMetrics();
        g.drawString(sc, (PANEL_WIDTH - fm.stringWidth(sc)) / 2, 260);

        g.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        String killText = "消灭敌人: " + enemiesKilled;
        fm = g.getFontMetrics();
        g.drawString(killText, (PANEL_WIDTH - fm.stringWidth(killText)) / 2, 295);

        g.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        g.setColor(Color.ORANGE);
        String restart = "按 R 重新开始";
        fm = g.getFontMetrics();
        g.drawString(restart, (PANEL_WIDTH - fm.stringWidth(restart)) / 2, 370);
    }

    /* ========== 键盘事件 ========== */
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        pressedKeys.add(key);

        // 全局按键
        if (key == KeyEvent.VK_P) {
            if (gameState == STATE_RUNNING) {
                gameState = STATE_PAUSED;
            } else if (gameState == STATE_PAUSED) {
                gameState = STATE_RUNNING;
            }
        }

        if (key == KeyEvent.VK_R) {
            if (gameState == STATE_GAME_OVER) {
                initGame();
            }
        }

        if (key == KeyEvent.VK_ENTER) {
            if (gameState == STATE_WAITING) {
                initGame();
            }
        }
    }

    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    public void keyTyped(KeyEvent e) {
        // 不处理
    }
}
