package com.example.puzzlegame.data;

import com.example.puzzlegame.game.Difficulty;

/**
 * A snapshot of an in-progress game that can be persisted and restored.
 */
public class SavedGame {
    public Difficulty difficulty;
    public boolean imageMode;
    public int[] tiles;
    public int moves;
    public long elapsedMs;

    public SavedGame(Difficulty difficulty, boolean imageMode, int[] tiles, int moves, long elapsedMs) {
        this.difficulty = difficulty;
        this.imageMode = imageMode;
        this.tiles = tiles;
        this.moves = moves;
        this.elapsedMs = elapsedMs;
    }
}
