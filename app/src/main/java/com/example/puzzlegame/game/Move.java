package com.example.puzzlegame.game;

/**
 * Describes a single tile slide so the view can animate it.
 */
public class Move {
    /** The value of the tile that moved (1..cellCount-1). */
    public final int value;
    /** Index the tile started at. */
    public final int fromIndex;
    /** Index the tile ended at. */
    public final int toIndex;

    public Move(int value, int fromIndex, int toIndex) {
        this.value = value;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }
}
