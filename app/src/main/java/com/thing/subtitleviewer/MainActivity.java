package com.thing.subtitleviewer;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static Uri lastSelectedFileUri = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        TextView fileText = findViewById(R.id.selectedFileText);

        // restore fileName if we have one saved
        if (lastSelectedFileUri != null) {
            fileText.setText(getFileName(lastSelectedFileUri));
        }
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        String fileName = getFileName(uri);

                        if (!isSrtFile(fileName)) {
                            Toast.makeText(this, "Invalid File. Must be .srt", Toast.LENGTH_SHORT).show();
                            resetFileText(fileText);
                            return;
                        }

                        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                            boolean success = SubtitlesReader.parseFile(inputStream);

                            if (success) {
                                lastSelectedFileUri = uri; // store URI for restore in future
                                fileText.setText(fileName);
                            } else {
                                Toast.makeText(this, "Error: Could not parse .srt file", Toast.LENGTH_SHORT).show();
                                resetFileText(fileText);
                            }
                        } catch (Exception e) {
                            // This should really not happen unless openInputStream fails
                            Toast.makeText(this, "Unexpected error reading file", Toast.LENGTH_SHORT).show();
                            resetFileText(fileText);
                        }
                    }
                }
        );


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateResumeStartButtonText();
    }

    private void resetFileText(TextView fileTextt) {
        fileTextt.setText("");
        SubtitlesReader.invalidateCurrentSubtitles();
    }
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
    private boolean isSrtFile(String fileName) {
        if (fileName.length() >= 3) {
            String extension = fileName.substring(fileName.length() - 3);
            return extension.equalsIgnoreCase("srt");
        } else {
            return false;
        }
    }
    private ActivityResultLauncher<Intent> filePickerLauncher;

//    public void SelectSubtitleFileClicked(View view) {
//        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
//        intent.setType("*/*");
//        intent.addCategory(Intent.CATEGORY_OPENABLE);
//
//        filePickerLauncher.launch(intent);
//    }
    public void SelectSubtitleFileClicked(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // show text only
        intent.setType("text/*");

        // Extra filters
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain",
                "application/x-subrip",
                "text/*"
        });

        filePickerLauncher.launch(intent);
    }
    public void StartSubtitleFirstLineClicked(View view) {
        if (!SubtitlesReader.getIsHoldingValidSubtitles()) {
            Toast.makeText(this, "Select Subtitle File First", Toast.LENGTH_SHORT).show();
            return;
        }
        SubtitlePlaybackState.setFirstSubtitleLineMode();
        Intent intent = new Intent(this, SubtitleDisplay.class);
        startActivity(intent);
    }
    public void StartSubtitleFromResume(View view) {
        if (!SubtitlesReader.getIsHoldingValidSubtitles()) {
            Toast.makeText(this, "Select Subtitle File First", Toast.LENGTH_SHORT).show();
            SubtitlePlaybackState.playbackPaused = false;
            return;
        }
        SubtitlePlaybackState.setResumeMode();
        Intent intent = new Intent(this, SubtitleDisplay.class);
        startActivity(intent);
    }
    private void updateResumeStartButtonText() {
        //Toast.makeText(this, "Debug says hi", Toast.LENGTH_SHORT).show();
        Button startButton = findViewById(R.id.StartSubtitleFromResume);

        if (SubtitlePlaybackState.getTimeOffset() == 0) {
            startButton.setText("Start from beginning");
        } else {
            long totalSeconds = SubtitlePlaybackState.getTimeOffset() / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                startButton.setText(String.format("Resume from %d:%02d:%02d", hours, minutes, seconds));
            } else {
                startButton.setText(String.format("Resume from %d:%02d", minutes, seconds));
            }
        }
    }
}