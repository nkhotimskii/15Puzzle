package com.example.puzzlegame.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.example.puzzlegame.R;

/**
 * Plays short sound effects for moves and wins. Wraps a {@link SoundPool} and
 * respects the global sound toggle.
 */
public class SoundManager {

    private SoundPool soundPool;
    private int clickId = -1;
    private int successId = -1;
    private boolean enabled = true;

    public SoundManager(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build();
        clickId = soundPool.load(context, R.raw.click, 1);
        successId = soundPool.load(context, R.raw.success, 1);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void playClick() {
        if (enabled && clickId != -1) {
            soundPool.play(clickId, 0.7f, 0.7f, 1, 0, 1f);
        }
    }

    public void playSuccess() {
        if (enabled && successId != -1) {
            soundPool.play(successId, 0.9f, 0.9f, 1, 0, 1f);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
