// PingPong.java
// JavaFX Ping Pong: 2P controls, optional AI (toggle 'A'), Pause ('P'), Reset ('R')
import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class PingPong extends Application {

    // --- Config ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;

    private static final double PADDLE_W = 12;
    private static final double PADDLE_H = 90;
    private static final double PADDLE_SPEED = 7.0;

    private static final double BALL_SIZE = 12;
    private static final double BALL_SPEED_START = 5.0;
    private static final double BALL_SPEED_MAX = 13.0;
    private static final double BALL_ACCEL_ON_HIT = 0.35;  // speeds up each paddle hit
    private static final double SPIN_FACTOR = 4.0;          // adds angle based on where it hits the paddle

    // --- State ---
    private double leftY, rightY;
    private double ballX, ballY, ballVX, ballVY;
    private int scoreL = 0, scoreR = 0;

    private boolean paused = false;
    private boolean aiRight = false; // toggle with 'A'

    private final Set<KeyCode> pressed = new HashSet<>();

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext g = canvas.getGraphicsContext2D();

        Scene scene = new Scene(new javafx.scene.Group(canvas), WIDTH, HEIGHT, Color.BLACK);

        scene.setOnKeyPressed(e -> pressed.add(e.getCode()));
        scene.setOnKeyReleased(e -> pressed.remove(e.getCode()));

        resetPositions(true);

        stage.setTitle("JavaFX Ping Pong");
        stage.setScene(scene);
        stage.show();

        AnimationTimer loop = new AnimationTimer() {
            long last = 0;
            @Override
            public void handle(long now) {
                if (last == 0) { last = now; draw(g); return; }
                double dt = (now - last) / 1_000_000.0; // ms
                last = now;

                // input
                if (pressed.contains(KeyCode.P)) { paused = true; }
                if (pressed.contains(KeyCode.R)) { resetPositions(true); paused = false; }
                if (pressed.contains(KeyCode.A)) { aiRight = true; } // hold A for AI; release to return to 2P
                if (!pressed.contains(KeyCode.A)) { /* fall back to manual when A released */ }

                if (!paused) {
                    update(dt);
                }

                draw(g);
            }
        };
        loop.start();
    }

    private void update(double dtMs) {
        // --- Move paddles ---
        // Left paddle (W/S)
        if (pressed.contains(KeyCode.W)) leftY -= PADDLE_SPEED;
        if (pressed.contains(KeyCode.S)) leftY += PADDLE_SPEED;

        // Right paddle (AI or ↑/↓)
        if (aiRight) {
            double target = ballY - PADDLE_H / 2 + BALL_SIZE / 2;
            if (Math.abs(target - rightY) > PADDLE_SPEED) {
                rightY += Math.signum(target - rightY) * PADDLE_SPEED * 0.95;
            }
        } else {
            if (pressed.contains(KeyCode.UP)) rightY -= PADDLE_SPEED;
            if (pressed.contains(KeyCode.DOWN)) rightY += PADDLE_SPEED;
        }

        // Clamp paddles
        leftY = clamp(leftY, 0, HEIGHT - PADDLE_H);
        rightY = clamp(rightY, 0, HEIGHT - PADDLE_H);

        // --- Move ball ---
        ballX += ballVX;
        ballY += ballVY;

        // Top/bottom collisions
        if (ballY <= 0) { ballY = 0; ballVY = Math.abs(ballVY); }
        if (ballY + BALL_SIZE >= HEIGHT) { ballY = HEIGHT - BALL_SIZE; ballVY = -Math.abs(ballVY); }

        // Paddle rects
        double lpX = 30, rpX = WIDTH - 30 - PADDLE_W;

        // Left paddle collision
        if (ballX <= lpX + PADDLE_W && ballX >= lpX - BALL_SIZE &&
            ballY + BALL_SIZE >= leftY && ballY <= leftY + PADDLE_H && ballVX < 0) {
            collideWithPaddle(true, leftY);
        }

        // Right paddle collision
        if (ballX + BALL_SIZE >= rpX && ballX <= rpX + PADDLE_W &&
            ballY + BALL_SIZE >= rightY && ballY <= rightY + PADDLE_H && ballVX > 0) {
            collideWithPaddle(false, rightY);
        }

        // Scoring
        if (ballX + BALL_SIZE < 0) { // right scores
            scoreR++;
            resetPositions(false);
        } else if (ballX > WIDTH) { // left scores
            scoreL++;
            resetPositions(false);
        }
    }

    private void collideWithPaddle(boolean left, double paddleY) {
        // Position correction
        if (left) ballX = 30 + PADDLE_W;
        else ballX = WIDTH - 30 - PADDLE_W - BALL_SIZE;

        // Reflect & add spin based on impact point relative to paddle center
        double paddleCenter = paddleY + PADDLE_H / 2.0;
        double ballCenter = ballY + BALL_SIZE / 2.0;
        double offset = (ballCenter - paddleCenter) / (PADDLE_H / 2.0); // in [-1,1]

        // speed up a bit each hit
        double speed = Math.min(BALL_SPEED_MAX, Math.hypot(ballVX, ballVY) + BALL_ACCEL_ON_HIT);

        // new direction: flip X, tweak Y with spin
        double newVX = (left ? 1 : -1) * Math.max(2.5, speed * 0.7);
        double newVY = clamp(ballVY + offset * SPIN_FACTOR, -speed, speed);

        // re-normalize to keep total speed near target
        double scale = speed / Math.max(1e-6, Math.hypot(newVX, newVY));
        ballVX = newVX * scale;
        ballVY = newVY * scale;
    }

    private void resetPositions(boolean centerScore) {
        leftY = HEIGHT / 2.0 - PADDLE_H / 2.0;
        rightY = HEIGHT / 2.0 - PADDLE_H / 2.0;
        ballX = WIDTH / 2.0 - BALL_SIZE / 2.0;
        ballY = HEIGHT / 2.0 - BALL_SIZE / 2.0;

        double dir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        double angle = (ThreadLocalRandom.current().nextDouble() * 0.6 - 0.3); // small vertical component
        double speed = BALL_SPEED_START;
        ballVX = dir * speed * (0.85);
        ballVY = speed * angle;

        if (centerScore) { scoreL = 0; scoreR = 0; }
    }

    private void draw(GraphicsContext g) {
        // background
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // center line
        g.setStroke(Color.GRAY);
        g.setLineWidth(2);
        for (int y = 0; y < HEIGHT; y += 18) {
            g.strokeLine(WIDTH / 2.0, y, WIDTH / 2.0, y + 10);
        }

        // paddles & ball
        g.setFill(Color.WHITE);
        double lpX = 30, rpX = WIDTH - 30 - PADDLE_W;
        g.fillRect(lpX, leftY, PADDLE_W, PADDLE_H);
        g.fillRect(rpX, rightY, PADDLE_W, PADDLE_H);
        g.fillOval(ballX, ballY, BALL_SIZE, BALL_SIZE);

        // scores & HUD
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Monospaced", 28));
        g.fillText(String.valueOf(scoreL), WIDTH / 2.0 - 60, 40);
        g.fillText(String.valueOf(scoreR), WIDTH / 2.0 + 45, 40);

        g.setFont(Font.font("Monospaced", 14));
        g.fillText("W/S = Left   ↑/↓ = Right   A = Hold for AI Right   P = Pause   R = Reset",
                20, HEIGHT - 16);
        if (paused) {
            g.setFont(Font.font("Monospaced", 42));
            g.setFill(Color.color(1, 1, 1, 0.85));
            g.fillText("PAUSED", WIDTH / 2.0 - 95, HEIGHT / 2.0 - 120);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static void main(String[] args) {
        launch(args);
    }
}