package com.example.puzzlegame.game;

import android.app.Application;
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

import java.util.Locale;

/**
 * Holds all game state and survives configuration changes. It owns the board,
 * timer, best-time recording and statistics, and exposes the results as
 * {@link LiveData} for the UI to observe.
 */
public class GameViewModel extends AndroidViewModel {

    private final GameRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Board board;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int moves;

    // Timer state.
    private long elapsedMs;
    private long startedAt;
    private boolean running;
    private boolean solvedHandled;
    private boolean playedCounted;

    private final MutableLiveData<int[]> boardState = new MutableLiveData<>();
    private final MutableLiveData<Move> moveEvent = new MutableLiveData<>();
    private final MutableLiveData<Integer> movesLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> timeLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> bestLiveData = new MutableLiveData<>();
    private final MutableLiveData<Difficulty> difficultyLiveData = new MutableLiveData<>();
    private final MutableLiveData<TileTheme> themeLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Integer> solvedEvent = new SingleLiveEvent<>();

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
        themeLiveData.setValue(repository.getTileTheme());

        SavedGame saved = repository.loadGame();
        if (saved != null) {
            restoreGame(saved);
        } else {
            startNewGame(repository.getDifficulty());
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

    public LiveData<TileTheme> getTileTheme() {
        return themeLiveData;
    }

    public void setTileTheme(@NonNull TileTheme theme) {
        repository.setTileTheme(theme);
        themeLiveData.setValue(theme);
    }

    public LiveData<Integer> getSolvedEvent() {
        return solvedEvent;
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

    public long getBestTime(Difficulty d) {
        return repository.getBestTime(d);
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
        markPlayed();
        startNewGame(difficulty);
    }

    public void selectDifficulty(@NonNull Difficulty d) {
        markPlayed();
        startNewGame(d);
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
        super.onCleared();
    }

    // --- Internals ---

    private void startNewGame(Difficulty d) {
        difficulty = d;
        repository.setDifficulty(d);

        board = new Board(d.getSize());
        board.shuffle();
        moves = 0;
        elapsedMs = 0;
        running = false;
        solvedHandled = false;
        playedCounted = false;
        mainHandler.removeCallbacks(tick);

        repository.clearGame();
        emitAll();
    }

    private void restoreGame(SavedGame saved) {
        difficulty = saved.difficulty;
        moves = saved.moves;
        elapsedMs = saved.elapsedMs;
        running = false;
        solvedHandled = false;
        playedCounted = false;

        board = new Board(difficulty.getSize());
        board.setTiles(saved.tiles);

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
            markPlayed();
            repository.setBestTime(difficulty, elapsedMs);
            repository.incrementGamesWon();
            repository.addToTotalMoves(moves);
            repository.clearGame();
            solvedEvent.setValue(moves);
        }
    }

    /** Counts the current game as played exactly once. */
    private void markPlayed() {
        if (!playedCounted) {
            playedCounted = true;
            repository.incrementGamesPlayed();
        }
    }

    private void emitAll() {
        boardState.setValue(board.toArray());
        movesLiveData.setValue(moves);
        timeLiveData.setValue(formatElapsed(elapsedMs));
        bestLiveData.setValue(bestTimeText());
        difficultyLiveData.setValue(difficulty);
    }

    private String bestTimeText() {
        long best = repository.getBestTime(difficulty);
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
        // A game that has already been won must never be resumed, otherwise the
        // same game could be won twice and "Games Won" would outgrow "Games Played".
        if (solvedHandled) {
            repository.clearGame();
            return;
        }
        repository.saveGame(new SavedGame(difficulty, board.toArray(), moves, elapsedMs));
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
}
