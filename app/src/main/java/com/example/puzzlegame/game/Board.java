package com.example.puzzlegame.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Core game logic for a sliding-tile puzzle of arbitrary size.
 *
 * The board is a size x size grid stored in a single int array (row-major).
 * Values 1..(size*size-1) represent tiles and 0 represents the empty slot.
 * A tile can only move into the empty slot, so a move is valid when the
 * tapped tile is orthogonally adjacent to the empty cell.
 */
public class Board {

    public static final int EMPTY = 0;

    private final int size;
    private final int cellCount;
    private final int[] tiles;
    private int emptyIndex;
    private final Random random = new Random();
    private final Deque<Integer> history = new ArrayDeque<>();

    public Board(int size) {
        if (size < 2) {
            throw new IllegalArgumentException("Board size must be at least 2");
        }
        this.size = size;
        this.cellCount = size * size;
        this.tiles = new int[cellCount];
        reset();
    }

    public int getSize() {
        return size;
    }

    public int getCellCount() {
        return cellCount;
    }

    /** Resets the board to the solved state (1..N in order, empty last). */
    public void reset() {
        for (int i = 0; i < cellCount - 1; i++) {
            tiles[i] = i + 1;
        }
        tiles[cellCount - 1] = EMPTY;
        emptyIndex = cellCount - 1;
        history.clear();
    }

    /**
     * Shuffles the board by performing many random valid moves from the solved
     * state, which guarantees the resulting position is solvable.
     */
    public void shuffle() {
        reset();
        int iterations = cellCount * cellCount + random.nextInt(cellCount * cellCount);
        int previous = -1;
        for (int i = 0; i < iterations; i++) {
            int[] neighbors = neighborsOf(emptyIndex);
            int next;
            do {
                next = neighbors[random.nextInt(neighbors.length)];
            } while (neighbors.length > 1 && next == previous);
            previous = emptyIndex;
            move(next);
        }
        if (isSolved()) {
            shuffle();
        }
    }

    /** Returns the value (1..cellCount-1, or EMPTY) at the given linear index. */
    public int get(int index) {
        return tiles[index];
    }

    public int get(int row, int col) {
        return tiles[row * size + col];
    }

    public int getEmptyIndex() {
        return emptyIndex;
    }

    public boolean canMove(int index) {
        return index >= 0 && index < cellCount && isAdjacent(index, emptyIndex);
    }

    /**
     * Moves the tile at {@code index} into the empty slot and records the move
     * so it can be undone later.
     *
     * @return true if the move was valid and applied.
     */
    public boolean move(int index) {
        if (!canMove(index)) {
            return false;
        }
        history.push(emptyIndex);
        performMove(index);
        return true;
    }

    /**
     * Reverses the most recent move.
     *
     * @return the index of the tile that moved back, or -1 if there was no move
     *         to undo.
     */
    public int undo() {
        if (history.isEmpty()) {
            return -1;
        }
        int prevEmpty = history.pop();
        performMove(prevEmpty);
        return prevEmpty;
    }

    /** True when every tile is in its home position. */
    public boolean isSolved() {
        for (int i = 0; i < cellCount - 1; i++) {
            if (tiles[i] != i + 1) {
                return false;
            }
        }
        return tiles[cellCount - 1] == EMPTY;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    /** Returns a copy of the current tile layout. */
    public int[] toArray() {
        return tiles.clone();
    }

    /** Restores a tile layout previously returned by {@link #toArray()}. */
    public void setTiles(int[] layout) {
        if (layout == null || layout.length != cellCount) {
            reset();
            return;
        }
        System.arraycopy(layout, 0, tiles, 0, cellCount);
        emptyIndex = 0;
        for (int i = 0; i < cellCount; i++) {
            if (tiles[i] == EMPTY) {
                emptyIndex = i;
                break;
            }
        }
    }

    private void performMove(int index) {
        tiles[emptyIndex] = tiles[index];
        tiles[index] = EMPTY;
        emptyIndex = index;
    }

    private boolean isAdjacent(int a, int b) {
        int ar = a / size;
        int ac = a % size;
        int br = b / size;
        int bc = b % size;
        return Math.abs(ar - br) + Math.abs(ac - bc) == 1;
    }

    private int[] neighborsOf(int index) {
        int row = index / size;
        int col = index % size;
        int count = 0;
        if (row > 0) count++;
        if (row < size - 1) count++;
        if (col > 0) count++;
        if (col < size - 1) count++;

        int[] result = new int[count];
        int i = 0;
        if (row > 0) result[i++] = index - size;
        if (row < size - 1) result[i++] = index + size;
        if (col > 0) result[i++] = index - 1;
        if (col < size - 1) result[i++] = index + 1;
        return result;
    }
}
