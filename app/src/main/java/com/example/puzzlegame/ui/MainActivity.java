package com.example.puzzlegame.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.puzzlegame.R;
import com.example.puzzlegame.game.Direction;
import com.example.puzzlegame.game.Difficulty;
import com.example.puzzlegame.game.GameViewModel;
import com.example.puzzlegame.game.Move;
import com.example.puzzlegame.game.SoundManager;
import com.example.puzzlegame.game.TileTheme;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity implements PuzzleView.Listener {

    private static final long WIN_DIALOG_DELAY_MS = 600L;

    private GameViewModel viewModel;
    private PuzzleView puzzleView;
    private SoundManager soundManager;

    private Toolbar toolbar;
    private Button newGameButton;
    private View divider;

    private TextView movesText;
    private TextView timeText;
    private TextView bestText;

    private String currentTime = "0:00";
    private int currentAccent = TileTheme.YELLOW_GREEN.getAccentColor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        applyWindowInsets(root);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        puzzleView = findViewById(R.id.puzzle_view);
        movesText = findViewById(R.id.moves_text);
        timeText = findViewById(R.id.time_text);
        bestText = findViewById(R.id.best_text);
        newGameButton = findViewById(R.id.new_game_button);
        divider = findViewById(R.id.accent_divider);

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);
        soundManager = new SoundManager(this);

        puzzleView.setListener(this);
        newGameButton.setOnClickListener(v -> viewModel.newGame());

        observeViewModel();
    }

    private void applyWindowInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void observeViewModel() {
        viewModel.getBoardState().observe(this, puzzleView::setBoardState);

        viewModel.getMoves().observe(this, moves -> {
            if (moves != null) {
                movesText.setText(getString(R.string.moves, moves));
            }
        });

        viewModel.getTime().observe(this, time -> {
            if (time != null) {
                currentTime = time;
                timeText.setText(getString(R.string.time, time));
            }
        });

        viewModel.getBest().observe(this, best -> {
            if (best != null) {
                bestText.setText(getString(R.string.best, best));
            }
        });

        viewModel.getDifficulty().observe(this, difficulty -> invalidateOptionsMenu());

        viewModel.getTileTheme().observe(this, theme -> {
            if (theme == null) {
                return;
            }
            puzzleView.setTileTheme(theme);
            puzzleView.setAccentColor(theme.getAccentColor());
            applyChromeAccent(theme.getAccentColor());
        });

        viewModel.getMoveEvent().observe(this, move -> {
            puzzleView.animateMove(move);
            onMoveFeedback(move);
        });

        viewModel.getSolvedEvent().observe(this, this::showSolvedDialog);
    }

    private void applyChromeAccent(int accent) {
        currentAccent = accent;
        toolbar.setTitleTextColor(accent);

        newGameButton.setBackgroundTintList(ColorStateList.valueOf(accent));
        newGameButton.setTextColor(getColor(R.color.on_accent));

        divider.setBackgroundColor(Color.argb(0x40,
                Color.red(accent), Color.green(accent), Color.blue(accent)));

        int glow = Color.argb(0x80, Color.red(accent), Color.green(accent), Color.blue(accent));
        movesText.setShadowLayer(8f, 0f, 0f, glow);
        timeText.setShadowLayer(8f, 0f, 0f, glow);
        bestText.setShadowLayer(8f, 0f, 0f, glow);
    }

    private void onMoveFeedback(Move move) {
        if (viewModel.isHapticsEnabled()) {
            puzzleView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        soundManager.playClick();
    }

    private void showSolvedDialog(int moves) {
        if (viewModel.isHapticsEnabled()) {
            puzzleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        soundManager.playSuccess();

        puzzleView.playWinEffect();

        puzzleView.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.solved_title)
                    .setMessage(getString(R.string.solved_message, moves, currentTime))
                    .setPositiveButton(R.string.ok, (d, which) -> d.dismiss())
                    .show();
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(currentAccent);
        }, WIN_DIALOG_DELAY_MS);
    }

    @Override
    public void onTileTap(int index) {
        viewModel.moveTile(index);
    }

    @Override
    public void onSwipe(Direction direction) {
        viewModel.moveDirection(direction);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Difficulty difficulty = viewModel.getDifficulty().getValue();
        if (difficulty == Difficulty.EASY) {
            menu.findItem(R.id.menu_easy).setChecked(true);
        } else if (difficulty == Difficulty.HARD) {
            menu.findItem(R.id.menu_hard).setChecked(true);
        } else {
            menu.findItem(R.id.menu_normal).setChecked(true);
        }

        TileTheme theme = viewModel.getTileTheme().getValue();
        if (theme == TileTheme.ORANGE_RED) {
            menu.findItem(R.id.theme_orange_red).setChecked(true);
        } else if (theme == TileTheme.ROSE_PURPLE) {
            menu.findItem(R.id.theme_rose_purple).setChecked(true);
        } else {
            menu.findItem(R.id.theme_yellow_green).setChecked(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_easy) {
            viewModel.selectDifficulty(Difficulty.EASY);
            return true;
        } else if (id == R.id.menu_normal) {
            viewModel.selectDifficulty(Difficulty.NORMAL);
            return true;
        } else if (id == R.id.menu_hard) {
            viewModel.selectDifficulty(Difficulty.HARD);
            return true;
        } else if (id == R.id.theme_yellow_green) {
            viewModel.setTileTheme(TileTheme.YELLOW_GREEN);
            return true;
        } else if (id == R.id.theme_orange_red) {
            viewModel.setTileTheme(TileTheme.ORANGE_RED);
            return true;
        } else if (id == R.id.theme_rose_purple) {
            viewModel.setTileTheme(TileTheme.ROSE_PURPLE);
            return true;
        } else if (id == R.id.menu_settings) {
            startActivity(SettingsActivity.newIntent(this));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.onAppForeground();
    }

    @Override
    protected void onStop() {
        viewModel.onAppBackground();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        soundManager.setEnabled(viewModel.isSoundEnabled());
        viewModel.refreshBestTime();
    }

    @Override
    protected void onDestroy() {
        soundManager.release();
        super.onDestroy();
    }
}
