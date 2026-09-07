package com.example.puzzlegame.game;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.example.puzzlegame.R;

/**
 * Selectable color themes for the puzzle tiles. Each theme defines the hue
 * range the tile gradient sweeps across and a matching accent color that
 * harmonizes with the tiles and is used for buttons, titles and the board's
 * borders/grid. Lightness ramps down over the tile numbers so neighbouring
 * tiles stay distinguishable even on large boards.
 */
public enum TileTheme {
    YELLOW_GREEN(55f, 150f, R.string.theme_yellow_green, 0xFF00E676, R.style.Theme_PuzzleGame_YellowGreen),
    ORANGE_RED(28f, 0f, R.string.theme_orange_red, 0xFFFF6D00, R.style.Theme_PuzzleGame_OrangeRed),
    ROSE_PURPLE(348f, 265f, R.string.theme_rose_purple, 0xFFA78BFA, R.style.Theme_PuzzleGame_RosePurple);

    private final float hueStart;
    private final float hueEnd;
    private final int labelRes;
    private final int accentColor;
    private final int themeRes;

    TileTheme(float hueStart, float hueEnd, int labelRes, int accentColor, int themeRes) {
        this.hueStart = hueStart;
        this.hueEnd = hueEnd;
        this.labelRes = labelRes;
        this.accentColor = accentColor;
        this.themeRes = themeRes;
    }

    public float getHueStart() {
        return hueStart;
    }

    public float getHueEnd() {
        return hueEnd;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    public int getAccentColor() {
        return accentColor;
    }

    @StyleRes
    public int getThemeRes() {
        return themeRes;
    }
}
