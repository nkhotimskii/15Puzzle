package com.example.puzzlegame.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

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

    private static final float PADDING_FACTOR = 0.04f;
    private static final float GAP_FACTOR = 0.045f;
    private static final float CORNER_FACTOR = 0.06f;
    private static final long SLIDE_DURATION_MS = 180L;

    private int[] tiles;
    private Bitmap[] tileImages;
    private int size;

    private int[] tileColors;
    private String[] tileLabels;

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
        backgroundPaint.setColor(Color.parseColor("#1B1B2F"));
        emptyPaint.setColor(Color.parseColor("#252540"));

        tileBorderPaint.setStyle(Paint.Style.STROKE);
        tileBorderPaint.setStrokeWidth(2f);
        tileBorderPaint.setColor(Color.parseColor("#33FFFFFF"));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

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

    /** Sets the tile bitmaps for image mode, or null for number mode. */
    public void setTileImages(Bitmap[] images) {
        this.tileImages = images;
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
            float hue = 190f + (v - 1) * (360f - 190f) / (cellCount - 1);
            tileColors[v] = Color.HSVToColor(new float[]{hue, 0.55f, 0.92f});
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

        boolean animating = isAnimating();

        for (int i = 0; i < tiles.length; i++) {
            int value = tiles[i];

            float x = boardLeft + (i % size) * cellSize;
            float y = boardTop + (i / size) * cellSize;

            if (value == 0) {
                scratch.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
                canvas.drawRoundRect(scratch, corner, corner, emptyPaint);
                continue;
            }

            if (animating && animValue == value && i == animTo) {
                float fromX = boardLeft + (animFrom % size) * cellSize;
                float fromY = boardTop + (animFrom / size) * cellSize;
                x = fromX + (x - fromX) * animProgress;
                y = fromY + (y - fromY) * animProgress;
            }

            scratch.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);

            if (tileImages != null && tileImages[value] != null) {
                canvas.drawBitmap(tileImages[value], null, scratch, null);
            } else {
                tilePaint.setColor(tileColors[value]);
                canvas.drawRoundRect(scratch, corner, corner, tilePaint);
                canvas.drawRoundRect(scratch, corner, corner, tileBorderPaint);
                canvas.drawText(tileLabels[value], scratch.centerX(), scratch.centerY() - fontOffset, textPaint);
            }
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
