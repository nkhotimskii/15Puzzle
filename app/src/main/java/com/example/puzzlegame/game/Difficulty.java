package com.example.puzzlegame.game;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.puzzlegame.R;

/**
 * The board sizes the player can choose from, each representing a classic
 * sliding puzzle: 3x3 (8-puzzle), 4x4 (15-puzzle) and 5x5 (24-puzzle).
 */
public enum Difficulty {
    EASY(3, R.string.difficulty_easy),
    NORMAL(4, R.string.difficulty_normal),
    HARD(5, R.string.difficulty_hard);

    private final int size;
    private final int labelRes;

    Difficulty(int size, int labelRes) {
        this.size = size;
        this.labelRes = labelRes;
    }

    /** The number of tiles per row/column (grid is size x size). */
    public int getSize() {
        return size;
    }

    /** Total number of cells on the board (size * size). */
    public int getCellCount() {
        return size * size;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @NonNull
    public static Difficulty fromSize(int size) {
        for (Difficulty d : values()) {
            if (d.size == size) {
                return d;
            }
        }
        return NORMAL;
    }
}
