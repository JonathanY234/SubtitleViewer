package com.thing.subtitleviewer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SubtitleDisplay extends AppCompatActivity {

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subtitle_display);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // get rid of annoying stuff
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        // Prevent the screen from turning off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        subtitleText = findViewById(R.id.SubtitleText);

        startSubtitleLoop();

        // Handle back button/ action
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                SubtitlePlaybackState.saveTimeProgressAsTimeOffset();
                finish();
            }
        });
        FrameLayout root = findViewById(R.id.rootFrame);
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                showControls();
            }

            return false;
        });

        // Handle SeekBar
        seekBar = findViewById(R.id.seekBar);
        seekBar.setMax((int) SubtitlesParser.trackLength);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;

                SubtitlePlaybackState.setTimeOffset(progress);

                SubtitlePlaybackState.setCurrentIndex(
                        SubtitlesParser.getIndexCorrespondingToTime(progress)
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        // handle forward / back button

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            adjustTime(-500);
            resetHideTimer();
        });
        ImageButton btnForward = findViewById(R.id.btnForward);
        btnForward.setOnClickListener(v -> {
            adjustTime(500);
            resetHideTimer();
        });
        ImageButton btnBackBig = findViewById(R.id.btnBackBig);
        btnBackBig.setOnClickListener(v -> {
            adjustTime(-5000);
            resetHideTimer();
        });
        ImageButton btnForwardBig = findViewById(R.id.btnForwardBig);
        btnForwardBig.setOnClickListener(v -> {
            adjustTime(5000);
            resetHideTimer();
        });

        // handle pause / resume button
        ImageButton btnPause = findViewById(R.id.btnPause);
        btnPause.setOnClickListener(v -> {

            if (SubtitlePlaybackState.playbackPaused) {
                SubtitlePlaybackState.playbackPaused = false;
                btnPause.setImageResource(R.drawable.ic_btn_pause);

                SubtitlePlaybackState.setResumeMode();

            } else { //not paused, pause now
                SubtitlePlaybackState.playbackPaused = true;
                btnPause.setImageResource(R.drawable.ic_btn_play);

                SubtitlePlaybackState.saveTimeProgressAsTimeOffset();
            }

            resetHideTimer();
        });
    }
    private void adjustTime(long delta) {
        long newOffset = SubtitlePlaybackState.getTimeOffset() + delta;

        SubtitlePlaybackState.setTimeOffset(newOffset);

        SubtitlePlaybackState.setCurrentIndex(
                SubtitlesParser.getIndexCorrespondingToTime(newOffset)
        );
    }

    private TextView subtitleText;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private void startSubtitleLoop() {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long elapsed;

                if (SubtitlePlaybackState.playbackPaused) {
                    elapsed = SubtitlePlaybackState.getTimeOffset();
                } else {
                    elapsed = System.currentTimeMillis()
                            - SubtitlePlaybackState.getStartTimeMs()
                            + SubtitlePlaybackState.getTimeOffset();
                }
                updateSubtitle(elapsed);
                seekBar.setProgress((int) elapsed);

                // repeat the loop every 50ms
                handler.postDelayed(this, 50);
            }
        }, 0);
    }

    private void updateSubtitle(long currentTimeMs) {
        SubtitlesParser.Subtitle currentSubtitle = SubtitlesParser.getSubtitleAtIndex(SubtitlePlaybackState.getIndex());

        // make sure we haven't reached the end
        if (currentSubtitle == null) {
            subtitleText.setText("");
            return;
        }

        if (currentTimeMs >= currentSubtitle.startTimeMs) {
            if (currentTimeMs <= currentSubtitle.endTimeMs) {
                // display current subtitle
                subtitleText.setText(Html.fromHtml(currentSubtitle.text, Html.FROM_HTML_MODE_LEGACY));
            } else {
                // subtitle has ended
                subtitleText.setText("");
                SubtitlePlaybackState.incrementCurrentIndex();  // move to the next subtitle

                updateSubtitle(currentTimeMs); // check next subtitle immediately
            }
        } else {
            // before the start of this subtitle
            subtitleText.setText("");
        }
    }
    // Subtitle Navigation code

    private SeekBar seekBar;
    private final Runnable hideRunnable = this::hideControls;

    private void showControls() {
        LinearLayout panel = findViewById(R.id.controlPanel);
        panel.setVisibility(View.VISIBLE);

        resetHideTimer();
    }
    private void resetHideTimer() {
        LinearLayout panel = findViewById(R.id.controlPanel);

        panel.removeCallbacks(hideRunnable);
        panel.postDelayed(hideRunnable, 6000);
    }
    private void hideControls() {
        LinearLayout panel = findViewById(R.id.controlPanel);
        panel.setVisibility(View.GONE);
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        resetHideTimer();
        return super.dispatchTouchEvent(ev);
    }

    // Handle Rotate Screen Button
    @SuppressLint("SourceLockedOrientationActivity")
    public void RotateScreenClicked(View view) {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }
}