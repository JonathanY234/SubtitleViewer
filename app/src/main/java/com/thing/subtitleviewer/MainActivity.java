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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

                        if (!isSrtOrZipFile(fileName)) {
                            Toast.makeText(this, "Invalid File. Must be .srt or .zip", Toast.LENGTH_SHORT).show();
                            resetFileText(fileText);
                            return;
                        }

                        try (InputStream inputStream = getContentResolver().openInputStream(uri);
                             InputStream subtitleStream = getSrtIfInAZip(inputStream)) {

                            boolean success = SubtitlesParser.parseFile(subtitleStream);

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
        SubtitlesParser.invalidateCurrentSubtitles();
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
    private boolean isSrtOrZipFile(String fileName) {
        if (fileName.length() >= 3) {
            String extension = fileName.substring(fileName.length() - 3);
            return extension.equalsIgnoreCase("srt") || extension.equalsIgnoreCase("zip");
        } else {
            return false;
        }
    }
    private ActivityResultLauncher<Intent> filePickerLauncher;

    public void SelectSubtitleFileClicked(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }
    public void StartSubtitleFirstLineClicked(View view) {
        if (!SubtitlesParser.getIsHoldingValidSubtitles()) {
            Toast.makeText(this, "Select Subtitle File First", Toast.LENGTH_SHORT).show();
            return;
        }
        SubtitlePlaybackState.setFirstSubtitleLineMode();
        Intent intent = new Intent(this, SubtitleDisplay.class);
        startActivity(intent);
    }
    public void StartSubtitleFromResume(View view) {
        if (!SubtitlesParser.getIsHoldingValidSubtitles()) {
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
                startButton.setText(String.format(Locale.ENGLISH, "Resume from %d:%02d:%02d", hours, minutes, seconds));
            } else {
                startButton.setText(String.format(Locale.ENGLISH, "Resume from %d:%02d", minutes, seconds));
            }
        }
    }
    public void OpenHelpScreen(View view) {
        Intent intent = new Intent(this, HelpActivitiy.class);
        startActivity(intent);
    }

    private InputStream getSrtIfInAZip(InputStream input) throws IOException {
        BufferedInputStream bufferedStream = new BufferedInputStream(input);

        // check if it's a zip file
        bufferedStream.mark(4); // allow for rewinding
        int first = bufferedStream.read();
        int second = bufferedStream.read();
        int third = bufferedStream.read();
        int fourth = bufferedStream.read();
        bufferedStream.reset();
        if (first != 'P' || second != 'K' || third != 0x03 || fourth != 0x04) {
            return bufferedStream; // not a zip therefore treat as a srt
        }

        // open as zip and look for srt within
        ZipInputStream zipStream = new ZipInputStream(bufferedStream);
        ZipEntry entry;
        while ((entry = zipStream.getNextEntry()) != null) {
            if (!entry.isDirectory()
                    && entry.getName().toLowerCase().endsWith(".srt")) {
                // just use the first .srt we find
                return zipStream;
            }
        }

        Toast.makeText(this, "Zip file does not contain a SRT Subtitle file", Toast.LENGTH_SHORT).show();
        throw new IOException("Zip file does not contain a SRT Subtitle file"); // don't do this
    }
}