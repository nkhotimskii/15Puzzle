package com.example.puzzlegame.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.puzzlegame.game.Difficulty;

/**
 * Persists settings, statistics, best times and the in-progress game using
 * {@link SharedPreferences}. Keeps all storage access in one place so the rest
 * of the app never touches preferences directly.
 */
public class GameRepository {

    private static final String PREFS_NAME = "puzzle_prefs";

    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_HAPTICS = "haptics_enabled";

    private static final String KEY_GAMES_PLAYED = "stat_games_played";
    private static final String KEY_GAMES_WON = "stat_games_won";
    private static final String KEY_TOTAL_MOVES = "stat_total_moves";

    private static final String KEY_SAVED = "saved_game_exists";
    private static final String KEY_SAVED_DIFFICULTY = "saved_difficulty";
    private static final String KEY_SAVED_TILES = "saved_tiles";
    private static final String KEY_SAVED_MOVES = "saved_moves";
    private static final String KEY_SAVED_ELAPSED = "saved_elapsed_ms";

    private final SharedPreferences prefs;

    public GameRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Settings ---

    public Difficulty getDifficulty() {
        return Difficulty.fromSize(prefs.getInt(KEY_DIFFICULTY, Difficulty.NORMAL.getSize()));
    }

    public void setDifficulty(Difficulty difficulty) {
        prefs.edit().putInt(KEY_DIFFICULTY, difficulty.getSize()).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public boolean isHapticsEnabled() {
        return prefs.getBoolean(KEY_HAPTICS, true);
    }

    public void setHapticsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply();
    }

    // --- Best times ---

    /** Returns the best time in milliseconds for the given difficulty, or 0. */
    public long getBestTime(Difficulty difficulty) {
        return prefs.getLong(bestTimeKey(difficulty), 0L);
    }

    /** Records a new best time if it beats (or is the first) recorded best. */
    public void setBestTime(Difficulty difficulty, long millis) {
        long current = getBestTime(difficulty);
        if (current == 0L || millis < current) {
            prefs.edit().putLong(bestTimeKey(difficulty), millis).apply();
        }
    }

    // --- Statistics ---

    public int getGamesPlayed() {
        return prefs.getInt(KEY_GAMES_PLAYED, 0);
    }

    public int getGamesWon() {
        return prefs.getInt(KEY_GAMES_WON, 0);
    }

    public long getTotalMoves() {
        return prefs.getLong(KEY_TOTAL_MOVES, 0L);
    }

    public void incrementGamesPlayed() {
        prefs.edit().putInt(KEY_GAMES_PLAYED, getGamesPlayed() + 1).apply();
    }

    public void incrementGamesWon() {
        prefs.edit().putInt(KEY_GAMES_WON, getGamesWon() + 1).apply();
    }

    public void addToTotalMoves(long moves) {
        prefs.edit().putLong(KEY_TOTAL_MOVES, getTotalMoves() + moves).apply();
    }

    public void resetStatistics() {
        prefs.edit()
                .remove(KEY_GAMES_PLAYED)
                .remove(KEY_GAMES_WON)
                .remove(KEY_TOTAL_MOVES)
                .apply();
    }

    public void resetBestTimes() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int size : new int[]{3, 4, 5}) {
            editor.remove("best_" + size + "x" + size);
        }
        editor.apply();
    }

    // --- In-progress game ---

    public void saveGame(SavedGame game) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < game.tiles.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(game.tiles[i]);
        }
        prefs.edit()
                .putBoolean(KEY_SAVED, true)
                .putInt(KEY_SAVED_DIFFICULTY, game.difficulty.getSize())
                .putString(KEY_SAVED_TILES, sb.toString())
                .putInt(KEY_SAVED_MOVES, game.moves)
                .putLong(KEY_SAVED_ELAPSED, game.elapsedMs)
                .apply();
    }

    @Nullable
    public SavedGame loadGame() {
        if (!prefs.getBoolean(KEY_SAVED, false)) {
            return null;
        }
        Difficulty difficulty = Difficulty.fromSize(prefs.getInt(KEY_SAVED_DIFFICULTY, 4));
        int moves = prefs.getInt(KEY_SAVED_MOVES, 0);
        long elapsed = prefs.getLong(KEY_SAVED_ELAPSED, 0L);

        String csv = prefs.getString(KEY_SAVED_TILES, null);
        int cellCount = difficulty.getCellCount();
        int[] tiles = new int[cellCount];
        boolean ok = csv != null;
        if (ok) {
            String[] parts = csv.split(",");
            if (parts.length == cellCount) {
                for (int i = 0; i < cellCount; i++) {
                    try {
                        tiles[i] = Integer.parseInt(parts[i].trim());
                    } catch (NumberFormatException e) {
                        ok = false;
                        break;
                    }
                }
            } else {
                ok = false;
            }
        }

        if (!ok) {
            return null;
        }
        return new SavedGame(difficulty, tiles, moves, elapsed);
    }

    public void clearGame() {
        prefs.edit().remove(KEY_SAVED).apply();
    }

    private String bestTimeKey(Difficulty difficulty) {
        return "best_" + difficulty.getSize() + "x" + difficulty.getSize();
    }
}
