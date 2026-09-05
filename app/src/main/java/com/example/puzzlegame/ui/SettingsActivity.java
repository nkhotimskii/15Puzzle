package com.example.puzzlegame.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.puzzlegame.R;
import com.example.puzzlegame.data.GameRepository;
import com.example.puzzlegame.game.Difficulty;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private GameRepository repository;
    private TextView gamesPlayedText;
    private TextView gamesWonText;
    private TextView totalMovesText;
    private TextView bestTimesText;

    public static Intent newIntent(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        repository = new GameRepository(this);

        SwitchMaterial soundSwitch = findViewById(R.id.sound_switch);
        SwitchMaterial hapticsSwitch = findViewById(R.id.haptics_switch);
        gamesPlayedText = findViewById(R.id.games_played_text);
        gamesWonText = findViewById(R.id.games_won_text);
        totalMovesText = findViewById(R.id.total_moves_text);
        bestTimesText = findViewById(R.id.best_times_text);
        Button resetStatsButton = findViewById(R.id.reset_stats_button);

        soundSwitch.setChecked(repository.isSoundEnabled());
        hapticsSwitch.setChecked(repository.isHapticsEnabled());

        soundSwitch.setOnCheckedChangeListener((button, checked) -> repository.setSoundEnabled(checked));
        hapticsSwitch.setOnCheckedChangeListener((button, checked) -> repository.setHapticsEnabled(checked));

        resetStatsButton.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.reset_stats)
                        .setMessage(R.string.reset_stats_confirm)
                        .setPositiveButton(R.string.reset_stats, (dialog, which) -> {
                            repository.resetStatistics();
                            repository.resetBestTimes();
                            refreshStatistics();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());

        refreshStatistics();
    }

    private void refreshStatistics() {
        gamesPlayedText.setText(getString(R.string.games_played, repository.getGamesPlayed()));
        gamesWonText.setText(getString(R.string.games_won, repository.getGamesWon()));
        totalMovesText.setText(getString(R.string.total_moves, repository.getTotalMoves()));

        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Difficulty d : Difficulty.values()) {
            long numbers = repository.getBestTime(false, d);
            long image = repository.getBestTime(true, d);
            if (numbers > 0 || image > 0) {
                any = true;
            }
            sb.append(getString(d.getLabelRes()))
                    .append(": ")
                    .append(format(numbers))
                    .append(" / ")
                    .append(format(image))
                    .append('\n');
        }
        bestTimesText.setText(any ? sb.toString().trim() : getString(R.string.best_times_none));
    }

    private static String format(long ms) {
        if (ms == 0L) {
            return "--:--";
        }
        long totalSeconds = ms / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
