package com.thing.subtitleviewer;

//import static com.thing.subtitleviewer.SubtitlePlaybackState.getStartTimeMs;
//import static com.thing.subtitleviewer.SubtitlesReader.getSubtitleAtIndex;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SubtitleDisplay extends AppCompatActivity {

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

        // thing to detect taps
        View overlay = findViewById(R.id.overlayView);
        setupTapAdjustments(overlay);

        subtitleText = findViewById(R.id.SubtitleText);

        startSubtitleLoop();

        // Handle back presses (buttons, gestures, etc.)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                SubtitlePlaybackState.saveTimeProgressAsTimeOffset();
                finish();
            }
        });
    }

    private TextView subtitleText;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private void startSubtitleLoop() {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - SubtitlePlaybackState.getStartTimeMs() + SubtitlePlaybackState.getTimeOffset();
                updateSubtitle(elapsed);

                // repeat the loop every 50ms
                handler.postDelayed(this, 50);
            }
        }, 0);
    }

    private void updateSubtitle(long currentTimeMs) {
        SubtitlesReader.Subtitle currentSubtitle = SubtitlesReader.getSubtitleAtIndex(SubtitlePlaybackState.getIndex());

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
                SubtitlePlaybackState.incrIndex();  // move to the next subtitle

                updateSubtitle(currentTimeMs); // check next subtitle immediately
            }
        } else {
            // before the start of this subtitle
            subtitleText.setText("");
        }
    }
    // Detect and handle double tap to change position within subtitles
    private long lastTapTime = 0;
    private int tapCount = 0;
    private static final long TAP_INTERVAL_MS = 300; // max interval between taps

    private void setupTapAdjustments(View overlayView) {
        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

            long now = System.currentTimeMillis();

            if (now - lastTapTime > TAP_INTERVAL_MS) {
                // Too long since last tap → reset count
                tapCount = 1;
            } else {
                tapCount++;
            }

            lastTapTime = now;

            // Schedule evaluation after interval
            v.removeCallbacks(tapRunnable); // cancel any previous pending run
            v.postDelayed(tapRunnable = () -> {
                float x = lastTouchX;
                if (tapCount == 2) {
                    if (x < v.getWidth() / 2f) {
                        SubtitlePlaybackState.adjustOffsetForwardBack(-300); // 0.3s back
                        Toast.makeText(SubtitleDisplay.this, "-0.3", Toast.LENGTH_SHORT).show();
                    } else {
                        SubtitlePlaybackState.adjustOffsetForwardBack(300); // 0.3s forward
                        Toast.makeText(SubtitleDisplay.this, "+0.3", Toast.LENGTH_SHORT).show();
                    }
                } else if (tapCount >= 3) {
                    if (x < v.getWidth() / 2f) {
                        SubtitlePlaybackState.adjustOffsetForwardBack(-5000); // 5s back
                        Toast.makeText(SubtitleDisplay.this, "-5", Toast.LENGTH_SHORT).show();
                    } else {
                        SubtitlePlaybackState.adjustOffsetForwardBack(5000); // 5s forward
                        Toast.makeText(SubtitleDisplay.this, "+5", Toast.LENGTH_SHORT).show();
                    }
                }
                tapCount = 0; // reset
            }, TAP_INTERVAL_MS);

            lastTouchX = event.getX(); // save last touch position
            return true;
        });
    }

    // fields to track last touch
    private float lastTouchX = 0;
    private Runnable tapRunnable;
}