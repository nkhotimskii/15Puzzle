package com.example.puzzlegame.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.res.ResourcesCompat;

import com.example.puzzlegame.R;
import com.example.puzzlegame.game.Direction;
import com.example.puzzlegame.game.Move;
import com.example.puzzlegame.game.TileTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A stateless view that renders the current board and animates tile slides.
 * All game state lives in {@code GameViewModel}; this view only receives
 * snapshots and forwards touch gestures through a listener.
 */
public class PuzzleView extends View {

    /** Forwards user gestures to the host for resolution against the board. */
    public interface Listener {
        void onTileTap(int index);
        void onSwipe(Direction direction);
    }

    private static final float PADDING_FACTOR = 0.03f;
    private static final float GAP_FACTOR = 0.05f;
    private static final float CORNER_FACTOR = 0.08f;
    private static final long SLIDE_DURATION_MS = 120L;
    private static final int TRAIL_COUNT = 3;
    private static final float TRAIL_STEP = 0.10f;
    private static final int TEXT_OUTLINE_COLOR = 0xFF0E1018;
    private static final int WIN_PARTICLE_COUNT = 64;
    private static final long WIN_EFFECT_DURATION_MS = 1400L;

    private int[] tiles;
    private int size;

    private int[] tileColors;
    private String[] tileLabels;
    private Typeface numberTypeface;
    private int accentColor = TileTheme.ORANGE_RED.getAccentColor();
    private TileTheme theme = TileTheme.ORANGE_RED;

    private float cellSize;
    private float boardLeft;
    private float boardTop;

    private final Paint backgroundPaint = new Paint();
    private final Paint tilePaint = new Paint();
    private final Paint tileBorderPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint emptyPaint = new Paint();
    private final Paint emptyBorderPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint winPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frameGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF scratch = new RectF();
    private final RectF frameRect = new RectF();

    private Listener listener;

    // Slide animation state.
    private int animValue = -1;
    private int animFrom = -1;
    private int animTo = -1;
    private float animProgress = 1f;
    private ValueAnimator animator;

    // Win effect state.
    private final List<Particle> winParticles = new ArrayList<>();
    private ValueAnimator winAnimator;
    private float winProgress;

    // Touch tracking for tap vs swipe.
    private float touchStartX;
    private float touchStartY;
    private int touchDownIndex = -1;
    private boolean swipeHandled;

    public PuzzleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Context context = getContext();
        backgroundPaint.setColor(context.getColor(R.color.board_background));
        emptyPaint.setColor(context.getColor(R.color.tile_empty));

        tileBorderPaint.setStyle(Paint.Style.STROKE);
        tileBorderPaint.setStrokeWidth(2f);

        emptyBorderPaint.setStyle(Paint.Style.STROKE);
        emptyBorderPaint.setStrokeWidth(1f);

        gridPaint.setStrokeWidth(1f);

        frameGlowPaint.setStyle(Paint.Style.STROKE);
        framePaint.setStyle(Paint.Style.STROKE);

        applyAccentColors();

        numberTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(numberTypeface);

        setFocusable(true);
        setContentDescription(context.getString(R.string.board_description));
    }

    /** Re-colors the tile border, empty-cell outline and grid from the accent. */
    public void setAccentColor(int accent) {
        if (accent == accentColor) {
            return;
        }
        accentColor = accent;
        applyAccentColors();
        invalidate();
    }

    private void applyAccentColors() {
        tileBorderPaint.setColor(withAlpha(accentColor, 0x4D));
        emptyBorderPaint.setColor(withAlpha(accentColor, 0x26));
        gridPaint.setColor(withAlpha(accentColor, 0x18));
        frameGlowPaint.setColor(withAlpha(accentColor, 0x26));
        framePaint.setColor(withAlpha(accentColor, 0xE6));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Updates the board snapshot; size is derived from the array length. */
    public void setBoardState(int[] board) {
        this.tiles = board;
        if (board != null) {
            this.size = (int) Math.round(Math.sqrt(board.length));
            if (tileLabels == null || tileLabels.length != board.length) {
                buildCaches(board.length);
            }
        }
        updateGeometry();
        invalidate();
    }

    /** Starts an animated slide of the given move. */
    public void animateMove(Move move) {
        if (move == null || tiles == null) {
            return;
        }

        // Cancel any in-flight animation before mutating state, so that the
        // previous animator's end callback cannot clobber the new animation.
        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        animValue = move.value;
        animFrom = move.fromIndex;
        animTo = move.toIndex;
        animProgress = 0f;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SLIDE_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animValue = -1;
                animFrom = -1;
                animTo = -1;
                animProgress = 1f;
                invalidate();
            }
        });
        animator.start();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        if (size <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGeometry();
    }

    private void updateGeometry() {
        if (size <= 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float available = Math.min(getWidth(), getHeight()) * (1f - 2f * PADDING_FACTOR);
        cellSize = available / size;
        boardLeft = (getWidth() - cellSize * size) / 2f;
        boardTop = (getHeight() - cellSize * size) / 2f;
    }

    private void buildCaches(int cellCount) {
        tileColors = new int[cellCount];
        tileLabels = new String[cellCount];
        for (int v = 1; v < cellCount; v++) {
            float t = (v - 1) / (float) (cellCount - 1);
            float hue = theme.getHueStart() + t * (theme.getHueEnd() - theme.getHueStart());
            float value = 0.84f + t * (0.58f - 0.84f);
            tileColors[v] = Color.HSVToColor(new float[]{hue, 0.68f, value});
            tileLabels[v] = String.valueOf(v);
        }
    }

    /** Switches the tile color theme and rebuilds the cached colors. */
    public void setTileTheme(TileTheme theme) {
        if (theme == null || theme == this.theme) {
            return;
        }
        this.theme = theme;
        if (tiles != null) {
            buildCaches(tiles.length);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        if (tiles == null) {
            return;
        }

        drawGrid(canvas);
        drawFrame(canvas);

        float gap = cellSize * GAP_FACTOR;
        float corner = cellSize * CORNER_FACTOR;
        float textSize = cellSize * 0.38f;
        textPaint.setTextSize(textSize);
        float fontOffset = (textPaint.descent() + textPaint.ascent()) / 2f;

        boolean animating = isAnimating();

        for (int i = 0; i < tiles.length; i++) {
            int value = tiles[i];

            if (value == 0) {
                drawEmptyCell(canvas, i, gap, corner);
                continue;
            }

            // The moving tile is drawn last, on top of everything, so it never
            // gets hidden behind the empty cell it just vacated.
            if (animating && value == animValue && i == animTo) {
                continue;
            }

            float x = boardLeft + (i % size) * cellSize;
            float y = boardTop + (i / size) * cellSize;
            drawTile(canvas, value, x, y, gap, corner, fontOffset, 255);
        }

        if (animating && animValue > 0) {
            float fromX = boardLeft + (animFrom % size) * cellSize;
            float fromY = boardTop + (animFrom / size) * cellSize;
            float toX = boardLeft + (animTo % size) * cellSize;
            float toY = boardTop + (animTo / size) * cellSize;

            // Fading trail behind the moving tile.
            for (int k = TRAIL_COUNT; k >= 1; k--) {
                float tp = animProgress - k * TRAIL_STEP;
                if (tp < 0f) {
                    continue;
                }
                int alpha = (int) (255f * (1f - k / (float) (TRAIL_COUNT + 1)));
                float tx = fromX + (toX - fromX) * tp;
                float ty = fromY + (toY - fromY) * tp;
                drawTile(canvas, animValue, tx, ty, gap, corner, fontOffset, alpha);
            }

            float x = fromX + (toX - fromX) * animProgress;
            float y = fromY + (toY - fromY) * animProgress;
            drawTile(canvas, animValue, x, y, gap, corner, fontOffset, 255);
        }

        drawWinEffect(canvas);
    }

    private void drawGrid(Canvas canvas) {
        float step = cellSize * 0.5f;
        float right = boardLeft + cellSize * size;
        float bottom = boardTop + cellSize * size;
        for (float x = boardLeft; x <= right; x += step) {
            canvas.drawLine(x, boardTop, x, bottom, gridPaint);
        }
        for (float y = boardTop; y <= bottom; y += step) {
            canvas.drawLine(boardLeft, y, right, y, gridPaint);
        }
    }

    private void drawFrame(Canvas canvas) {
        float m = cellSize * 0.07f;
        float radius = cellSize * 0.18f;
        frameRect.set(boardLeft - m, boardTop - m,
                boardLeft + cellSize * size + m, boardTop + cellSize * size + m);

        frameGlowPaint.setStrokeWidth(cellSize * 0.04f);
        canvas.drawRoundRect(frameRect, radius, radius, frameGlowPaint);

        framePaint.setStrokeWidth(cellSize * 0.015f);
        canvas.drawRoundRect(frameRect, radius, radius, framePaint);
    }

    private void drawEmptyCell(Canvas canvas, int index, float gap, float corner) {
        float x = boardLeft + (index % size) * cellSize;
        float y = boardTop + (index / size) * cellSize;
        scratch.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
        canvas.drawRoundRect(scratch, corner, corner, emptyPaint);
        canvas.drawRoundRect(scratch, corner, corner, emptyBorderPaint);
    }

    private void drawTile(Canvas canvas, int value, float x, float y,
                          float gap, float corner, float fontOffset, int alpha) {
        scratch.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);

        float af = alpha / 255f;
        int base = tileColors[value];
        int fill = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base));
        int glow = Color.argb((int) (0x66 * af), Color.red(base), Color.green(base), Color.blue(base));
        int border = Color.argb((int) (0x4D * af),
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));

        tilePaint.setShadowLayer(cellSize * 0.12f, 0f, 0f, glow);
        tilePaint.setColor(fill);
        canvas.drawRoundRect(scratch, corner, corner, tilePaint);
        tilePaint.clearShadowLayer();

        tileBorderPaint.setColor(border);
        canvas.drawRoundRect(scratch, corner, corner, tileBorderPaint);

        float textX = scratch.centerX();
        float textY = scratch.centerY() - fontOffset;

        // Dark outline around the number so it stays legible on any tile.
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(textPaint.getTextSize() * 0.10f);
        textPaint.setColor(TEXT_OUTLINE_COLOR);
        textPaint.setAlpha(alpha);
        canvas.drawText(tileLabels[value], textX, textY, textPaint);

        // White fill on top.
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(tileLabels[value], textX, textY, textPaint);

        textPaint.setAlpha(255);
    }

    /** Starts a particle burst + shockwave effect used when the puzzle is solved. */
    public void playWinEffect() {
        if (tiles == null) {
            return;
        }

        winParticles.clear();
        Random rnd = new Random();
        float cx = boardLeft + cellSize * size / 2f;
        float cy = boardTop + cellSize * size / 2f;
        int cellCount = tiles.length;

        for (int i = 0; i < WIN_PARTICLE_COUNT; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            float speed = cellSize * (0.5f + rnd.nextFloat() * 0.9f);
            float vx = (float) (Math.cos(ang) * speed);
            float vy = (float) (Math.sin(ang) * speed);
            float radius = cellSize * (0.02f + rnd.nextFloat() * 0.03f);
            int color = (i % 5 == 0)
                    ? accentColor
                    : tileColors[1 + rnd.nextInt(cellCount - 1)];
            winParticles.add(new Particle(cx, cy, vx, vy, radius, color));
        }

        if (winAnimator != null) {
            winAnimator.cancel();
        }
        winAnimator = ValueAnimator.ofFloat(0f, 1f);
        winAnimator.setDuration(WIN_EFFECT_DURATION_MS);
        winAnimator.addUpdateListener(a -> {
            winProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        winAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                winParticles.clear();
                winProgress = 0f;
                invalidate();
            }
        });
        winAnimator.start();
        invalidate();
    }

    private void drawWinEffect(Canvas canvas) {
        if (winParticles.isEmpty() || winAnimator == null || !winAnimator.isRunning()) {
            return;
        }
        float f = winProgress;
        float cx = boardLeft + cellSize * size / 2f;
        float cy = boardTop + cellSize * size / 2f;
        int ar = Color.red(accentColor);
        int ag = Color.green(accentColor);
        int ab = Color.blue(accentColor);

        // Expanding shockwave ring.
        int ringAlpha = (int) (0x80 * (1f - f));
        if (ringAlpha > 0) {
            float ringRadius = cellSize * size * 0.25f + f * cellSize * size * 0.9f;
            winPaint.setStyle(Paint.Style.STROKE);
            winPaint.setStrokeWidth(cellSize * 0.05f);
            winPaint.setColor(Color.argb(ringAlpha, ar, ag, ab));
            canvas.drawCircle(cx, cy, ringRadius, winPaint);
            winPaint.setStyle(Paint.Style.FILL);
        }

        // Particles with a little gravity.
        float gravity = cellSize * 0.5f;
        for (Particle p : winParticles) {
            float px = p.x0 + p.vx * f;
            float py = p.y0 + p.vy * f + gravity * f * f;
            float rad = Math.max(p.radius * (1f - 0.3f * f), 0.5f);
            int pa = (int) (255f * (1f - f));
            winPaint.setColor(Color.argb(pa, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            canvas.drawCircle(px, py, rad, winPaint);
        }
    }

    private static class Particle {
        final float x0;
        final float y0;
        final float vx;
        final float vy;
        final float radius;
        final int color;

        Particle(float x0, float y0, float vx, float vy, float radius, int color) {
            this.x0 = x0;
            this.y0 = y0;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.color = color;
        }
    }

    private boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                touchDownIndex = indexAt(event.getX(), event.getY());
                swipeHandled = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!swipeHandled && touchDownIndex != -1) {
                    float dx = event.getX() - touchStartX;
                    float dy = event.getY() - touchStartY;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) > cellSize * 0.30f) {
                        swipeHandled = true;
                        if (listener != null) {
                            listener.onSwipe(swipeDirection(dx, dy));
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!swipeHandled && touchDownIndex != -1) {
                    if (listener != null) {
                        listener.onTileTap(touchDownIndex);
                    }
                    performClick();
                }
                touchDownIndex = -1;
                return true;

            case MotionEvent.ACTION_CANCEL:
                touchDownIndex = -1;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private Direction swipeDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.RIGHT : Direction.LEFT;
        }
        return dy > 0 ? Direction.DOWN : Direction.UP;
    }

    private int indexAt(float x, float y) {
        if (size <= 0 || x < boardLeft || y < boardTop) {
            return -1;
        }
        int col = (int) ((x - boardLeft) / cellSize);
        int row = (int) ((y - boardTop) / cellSize);
        if (col < 0 || col >= size || row < 0 || row >= size) {
            return -1;
        }
        return row * size + col;
    }
}
