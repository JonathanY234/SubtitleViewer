package com.thing.subtitleviewer;

public class SubtitlePlaybackState {
    public static boolean playbackPaused = false;
    private static long timeOffset = 0;
    public static long getTimeOffset() {
        return timeOffset;
    }
    public static void setTimeOffset(long progress) {
        timeOffset = progress;
    }
    public static void adjustOffsetForwardBack(long change) { // used for fast-forward/ backwards
        timeOffset += change;
        if (change < 0) {
            currentIndex = SubtitlesParser.getIndexCorrespondingToTime(timeOffset);
        }
    }
    private static int currentIndex = 0;
    public static int getIndex() {
        return currentIndex;
    }
    public static void setCurrentIndex(int idx) {
        currentIndex = idx;
    }
    public static void incrCurrentIndex() {
        currentIndex++;
    }
    private static long startTimeMs;
    public static long getStartTimeMs() {
        return startTimeMs;
    }
    public static void setFirstSubtitleLineMode() {
        currentIndex = 0;

        // get the start time of the first subtitle
        SubtitlesParser.Subtitle firstSub = SubtitlesParser.getSubtitleAtIndex(0);
        long firstSubStartMs = (firstSub != null) ? firstSub.startTimeMs : 0;
        timeOffset = firstSubStartMs;

        startTimeMs = System.currentTimeMillis();
    }
    public static void setResumeMode() {
        // timeOffset unchanged
        startTimeMs = System.currentTimeMillis();
        currentIndex = SubtitlesParser.getIndexCorrespondingToTime(timeOffset);
    }
    public static void saveTimeProgressAsTimeOffset() {
        timeOffset = System.currentTimeMillis() - startTimeMs + timeOffset;
        currentIndex -= 1;
    }
}
