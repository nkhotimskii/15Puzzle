package com.example.puzzlegame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.puzzlegame.game.Board;

import org.junit.Test;

public class BoardTest {

    @Test
    public void reset_isSolved() {
        Board board = new Board(4);
        assertTrue(board.isSolved());
    }

    @Test
    public void shuffle_isNotSolved_andHasAllValues() {
        for (int size = 3; size <= 5; size++) {
            Board board = new Board(size);
            board.shuffle();
            assertFalse("shuffle should not be solved", board.isSolved());

            boolean[] seen = new boolean[board.getCellCount()];
            for (int i = 0; i < board.getCellCount(); i++) {
                int v = board.get(i);
                assertTrue(v >= 0 && v < board.getCellCount());
                assertFalse(seen[v]);
                seen[v] = true;
            }
        }
    }

    @Test
    public void move_onlyAdjacentTileMoves() {
        Board board = new Board(4);
        assertEquals(15, board.getEmptyIndex());

        assertTrue(board.canMove(14));
        assertFalse(board.canMove(0));

        assertTrue(board.move(14));
        assertEquals(14, board.getEmptyIndex());
        assertEquals(15, board.get(15));
        assertEquals(Board.EMPTY, board.get(14));
    }

    @Test
    public void invalidMove_returnsFalse() {
        Board board = new Board(4);
        assertFalse(board.move(0));
        assertEquals(15, board.getEmptyIndex());
    }

    @Test
    public void solvingByReversingMoves() {
        Board board = new Board(4);
        assertTrue(board.move(14));
        assertFalse(board.isSolved());
        assertTrue(board.move(15));
        assertTrue(board.isSolved());
    }

    @Test
    public void setTiles_restoresEmptyIndex() {
        Board board = new Board(3);
        int[] layout = {1, 2, 3, 4, 0, 5, 6, 7, 8};
        board.setTiles(layout);
        assertEquals(4, board.getEmptyIndex());
        assertEquals(3, board.get(2));
    }
}
