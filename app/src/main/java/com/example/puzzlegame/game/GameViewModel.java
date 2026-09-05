package com.example.puzzlegame.game;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.puzzlegame.data.GameRepository;
import com.example.puzzlegame.data.SavedGame;
import com.example.puzzlegame.ui.SingleLiveEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds all game state and survives configuration changes. It owns the board,
 * timer, best-time recording, statistics and image splitting, and exposes the
 * results as {@link LiveData} for the UI to observe.
 */
public class GameViewModel extends AndroidViewModel {

    private static final int MAX_IMAGE_EDGE = 1024;
    private static final String IMAGE_FILE = "puzzle_image.png";

    private final GameRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private Board board;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean imageMode;
    private int moves;

    // Image-mode data.
    private Bitmap sourceImage;
    private Bitmap[] tileImages;

    // Timer state.
    private long elapsedMs;
    private long startedAt;
    private boolean running;
    private boolean solvedHandled;

    private final MutableLiveData<int[]> boardState = new MutableLiveData<>();
    private final MutableLiveData<Move> moveEvent = new MutableLiveData<>();
    private final MutableLiveData<Integer> movesLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> timeLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> bestLiveData = new MutableLiveData<>();
    private final MutableLiveData<Difficulty> difficultyLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> imageModeLiveData = new MutableLiveData<>();
    private final MutableLiveData<Bitmap[]> tileImagesLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Integer> solvedEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer> imageErrorEvent = new SingleLiveEvent<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (running) {
                long now = SystemClock.elapsedRealtime();
                long total = elapsedMs + (now - startedAt);
                timeLiveData.setValue(formatElapsed(total));
                mainHandler.postDelayed(this, 250L);
            }
        }
    };

    public GameViewModel(@NonNull Application application) {
        super(application);
        repository = new GameRepository(application);

        SavedGame saved = repository.loadGame();
        if (saved != null) {
            restoreGame(saved);
        } else {
            startNewGame(repository.getDifficulty(), false);
        }
    }

    // --- Public getters (LiveData) ---

    public LiveData<int[]> getBoardState() {
        return boardState;
    }

    public LiveData<Move> getMoveEvent() {
        return moveEvent;
    }

    public LiveData<Integer> getMoves() {
        return movesLiveData;
    }

    public LiveData<String> getTime() {
        return timeLiveData;
    }

    public LiveData<String> getBest() {
        return bestLiveData;
    }

    public LiveData<Difficulty> getDifficulty() {
        return difficultyLiveData;
    }

    public LiveData<Boolean> getImageMode() {
        return imageModeLiveData;
    }

    public LiveData<Bitmap[]> getTileImages() {
        return tileImagesLiveData;
    }

    public LiveData<Integer> getSolvedEvent() {
        return solvedEvent;
    }

    public LiveData<Integer> getImageErrorEvent() {
        return imageErrorEvent;
    }

    // --- Settings ---

    public boolean isSoundEnabled() {
        return repository.isSoundEnabled();
    }

    public void setSoundEnabled(boolean enabled) {
        repository.setSoundEnabled(enabled);
    }

    public boolean isHapticsEnabled() {
        return repository.isHapticsEnabled();
    }

    public void setHapticsEnabled(boolean enabled) {
        repository.setHapticsEnabled(enabled);
    }

    // --- Statistics (read-only, for the settings screen) ---

    public int getGamesPlayed() {
        return repository.getGamesPlayed();
    }

    public int getGamesWon() {
        return repository.getGamesWon();
    }

    public long getTotalMoves() {
        return repository.getTotalMoves();
    }

    public long getBestTime(boolean image, Difficulty d) {
        return repository.getBestTime(image, d);
    }

    public void resetStatistics() {
        repository.resetStatistics();
        repository.resetBestTimes();
        bestLiveData.setValue(bestTimeText());
    }

    /** Re-reads persisted values and refreshes the best-time display. */
    public void refreshBestTime() {
        bestLiveData.setValue(bestTimeText());
    }

    // --- Game control ---

    public void newGame() {
        startNewGame(difficulty, imageMode);
    }

    public void selectDifficulty(@NonNull Difficulty d) {
        startNewGame(d, imageMode);
    }

    public void playNumbers() {
        if (!imageMode) {
            return;
        }
        startNewGame(difficulty, false);
    }

    public void playImage(Uri uri) {
        ioExecutor.execute(() -> {
            Bitmap square = decodeSquareImage(uri);
            if (square == null) {
                imageErrorEvent.postValue(0);
                return;
            }
            saveImageFile(square);
            mainHandler.post(() -> {
                recycle(sourceImage);
                sourceImage = square;
                startNewGame(difficulty, true);
            });
        });
    }

    public void moveTile(int index) {
        if (board.canMove(index)) {
            applyMove(index);
        }
    }

    public void moveDirection(@NonNull Direction direction) {
        int target = tileToMove(direction);
        if (target != -1) {
            applyMove(target);
        }
    }

    /**
     * Resolves a swipe direction to the tile that should slide into the empty
     * slot, or -1 if no tile can move that way.
     */
    private int tileToMove(@NonNull Direction direction) {
        int empty = board.getEmptyIndex();
        int size = board.getSize();
        int col = empty % size;
        int row = empty / size;

        switch (direction) {
            case LEFT:
                return col < size - 1 ? empty + 1 : -1;
            case RIGHT:
                return col > 0 ? empty - 1 : -1;
            case UP:
                return row < size - 1 ? empty + size : -1;
            case DOWN:
                return row > 0 ? empty - size : -1;
            default:
                return -1;
        }
    }

    // --- Lifecycle hooks ---

    public void onAppForeground() {
        if (moves > 0 && !solvedHandled && !running) {
            startTimer();
        }
    }

    public void onAppBackground() {
        pauseTimer();
        saveGame();
    }

    @Override
    protected void onCleared() {
        mainHandler.removeCallbacks(tick);
        ioExecutor.shutdownNow();
        recycle(sourceImage);
        super.onCleared();
    }

    // --- Internals ---

    private void startNewGame(Difficulty d, boolean image) {
        difficulty = d;
        imageMode = image;
        repository.setDifficulty(d);

        board = new Board(d.getSize());
        board.shuffle();
        moves = 0;
        elapsedMs = 0;
        running = false;
        solvedHandled = false;
        mainHandler.removeCallbacks(tick);

        if (image && sourceImage != null) {
            tileImages = splitImage(sourceImage);
        } else {
            tileImages = null;
        }

        repository.incrementGamesPlayed();
        repository.clearGame();
        emitAll();
    }

    private void restoreGame(SavedGame saved) {
        difficulty = saved.difficulty;
        imageMode = saved.imageMode;
        moves = saved.moves;
        elapsedMs = saved.elapsedMs;
        running = false;
        solvedHandled = false;

        board = new Board(difficulty.getSize());
        board.setTiles(saved.tiles);

        if (imageMode) {
            sourceImage = loadImageFile();
            if (sourceImage != null) {
                tileImages = splitImage(sourceImage);
            } else {
                imageMode = false;
                board.reset();
                moves = 0;
                elapsedMs = 0;
            }
        } else {
            tileImages = null;
        }

        emitAll();
    }

    private void applyMove(int index) {
        int from = index;
        int to = board.getEmptyIndex();
        int value = board.get(from);

        if (moves == 0 && !running && !solvedHandled) {
            startTimer();
        }
        board.move(index);
        moves++;
        publishMove(value, from, to);
    }

    private void publishMove(int value, int from, int to) {
        boardState.setValue(board.toArray());
        movesLiveData.setValue(moves);
        moveEvent.setValue(new Move(value, from, to));

        if (board.isSolved() && !solvedHandled) {
            solvedHandled = true;
            pauseTimer();
            repository.setBestTime(imageMode, difficulty, elapsedMs);
            repository.incrementGamesWon();
            repository.addToTotalMoves(moves);
            repository.clearGame();
            solvedEvent.setValue(moves);
        }
    }

    private void emitAll() {
        boardState.setValue(board.toArray());
        movesLiveData.setValue(moves);
        timeLiveData.setValue(formatElapsed(elapsedMs));
        bestLiveData.setValue(bestTimeText());
        difficultyLiveData.setValue(difficulty);
        imageModeLiveData.setValue(imageMode);
        tileImagesLiveData.setValue(tileImages);
    }

    private String bestTimeText() {
        long best = repository.getBestTime(imageMode, difficulty);
        return best == 0L ? "--:--" : formatElapsed(best);
    }

    private void startTimer() {
        if (running) {
            return;
        }
        running = true;
        startedAt = SystemClock.elapsedRealtime();
        mainHandler.removeCallbacks(tick);
        mainHandler.post(tick);
    }

    private void pauseTimer() {
        if (!running) {
            return;
        }
        elapsedMs += SystemClock.elapsedRealtime() - startedAt;
        running = false;
        mainHandler.removeCallbacks(tick);
        timeLiveData.setValue(formatElapsed(elapsedMs));
    }

    private void saveGame() {
        if (board.isSolved() && solvedHandled) {
            repository.clearGame();
            return;
        }
        repository.saveGame(new SavedGame(
                difficulty, imageMode, board.toArray(), moves, elapsedMs));
    }

    private static String formatElapsed(long ms) {
        long totalSeconds = ms / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    // --- Image helpers ---

    private Bitmap decodeSquareImage(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sampleSize = 1;
            while (bounds.outWidth / sampleSize > MAX_IMAGE_EDGE * 2
                    || bounds.outHeight / sampleSize > MAX_IMAGE_EDGE * 2) {
                sampleSize *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            Bitmap decoded;
            try (InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) {
                return null;
            }
            return centerCropSquare(decoded);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap centerCropSquare(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, x, y, side, side);
        if (square != source) {
            source.recycle();
        }
        return square;
    }

    private Bitmap[] splitImage(Bitmap square) {
        int size = difficulty.getSize();
        int cellCount = difficulty.getCellCount();
        int cellPx = square.getWidth() / size;
        Bitmap[] result = new Bitmap[cellCount];
        for (int v = 1; v < cellCount; v++) {
            int row = (v - 1) / size;
            int col = (v - 1) % size;
            result[v] = Bitmap.createBitmap(square, col * cellPx, row * cellPx, cellPx, cellPx);
        }
        return result;
    }

    private void saveImageFile(Bitmap square) {
        File file = new File(getApplication().getFilesDir(), IMAGE_FILE);
        try (FileOutputStream out = new FileOutputStream(file)) {
            square.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
            // Best effort; the game still works without persistence.
        }
    }

    private Bitmap loadImageFile() {
        File file = new File(getApplication().getFilesDir(), IMAGE_FILE);
        if (!file.exists()) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            return null;
        }
        int maxEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (maxEdge > MAX_IMAGE_EDGE) {
            float scale = (float) MAX_IMAGE_EDGE / maxEdge;
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.round(bitmap.getWidth() * scale),
                    Math.round(bitmap.getHeight() * scale), true);
            bitmap.recycle();
            bitmap = scaled;
        }
        return centerCropSquare(bitmap);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
