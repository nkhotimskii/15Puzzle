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
    private static final long SLIDE_DURATION_MS = 180L;

    private int[] tiles;
    private int size;

    private int[] tileColors;
    private String[] tileLabels;
    private Typeface numberTypeface;

    private float cellSize;
    private float boardLeft;
    private float boardTop;

    private final Paint backgroundPaint = new Paint();
    private final Paint tilePaint = new Paint();
    private final Paint tileBorderPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint emptyPaint = new Paint();

    private final RectF scratch = new RectF();

    private Listener listener;

    // Slide animation state.
    private int animValue = -1;
    private int animFrom = -1;
    private int animTo = -1;
    private float animProgress = 1f;
    private ValueAnimator animator;

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
        tileBorderPaint.setColor(context.getColor(R.color.tile_border));

        numberTypeface = ResourcesCompat.getFont(context, R.font.poppins_bold);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(numberTypeface);

        setFocusable(true);
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
            float hue = 185f + (v - 1) * (300f - 185f) / (cellCount - 1);
            tileColors[v] = Color.HSVToColor(new float[]{hue, 0.70f, 0.92f});
            tileLabels[v] = String.valueOf(v);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        if (tiles == null) {
            return;
        }

        float gap = cellSize * GAP_FACTOR;
        float corner = cellSize * CORNER_FACTOR;
        float textSize = cellSize * 0.42f;
        textPaint.setTextSize(textSize);
        float fontOffset = (textPaint.descent() + textPaint.ascent()) / 2f;

        // Soft drop shadow under the tiles for a bit of depth.
        tilePaint.setShadowLayer(cellSize * 0.05f, 0f, cellSize * 0.025f, 0x66000000);

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
            drawTile(canvas, value, x, y, gap, corner, fontOffset);
        }

        if (animating && animValue > 0) {
            float fromX = boardLeft + (animFrom % size) * cellSize;
            float fromY = boardTop + (animFrom / size) * cellSize;
            float toX = boardLeft + (animTo % size) * cellSize;
            float toY = boardTop + (animTo / size) * cellSize;
            float x = fromX + (toX - fromX) * animProgress;
            float y = fromY + (toY - fromY) * animProgress;
            drawTile(canvas, animValue, x, y, gap, corner, fontOffset);
        }
    }

    private void drawEmptyCell(Canvas canvas, int index, float gap, float corner) {
        float x = boardLeft + (index % size) * cellSize;
        float y = boardTop + (index / size) * cellSize;
        scratch.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
        canvas.drawRoundRect(scratch, corner, corner, emptyPaint);
    }

    private void drawTile(Canvas canvas, int value, float x, float y,
                          float gap, float corner, float fontOffset) {
        scratch.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
        tilePaint.setColor(tileColors[value]);
        canvas.drawRoundRect(scratch, corner, corner, tilePaint);
        canvas.drawRoundRect(scratch, corner, corner, tileBorderPaint);
        canvas.drawText(tileLabels[value], scratch.centerX(), scratch.centerY() - fontOffset, textPaint);
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
