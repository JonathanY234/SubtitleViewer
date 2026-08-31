package com.thing.subtitleviewer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class SubtitlesParser {

    private static boolean isHoldingValidSubtitles = false;
    public static boolean getHoldingInvalidSubtitles() {
        return !isHoldingValidSubtitles;
    }
    public static void invalidateCurrentSubtitles() {
        isHoldingValidSubtitles = false;
    }

    public static long trackLength = 0;

    private final static ArrayList<Subtitle> allSubtitles = new ArrayList<>();

    public static boolean parseFile(InputStream inputStream) {
        allSubtitles.clear(); // reset previous data

        try (BufferedReader myReader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder subtitleBlockStr = new StringBuilder(128);

            String data;
            while ((data = myReader.readLine()) != null) {
                data = data.trim();

                if (data.isEmpty()) {
                    if (subtitleBlockStr.length() > 0) {
                        Subtitle currentSubtitle = new Subtitle();
                        currentSubtitle.parseSubtitleBlockStr(subtitleBlockStr.toString());
                        allSubtitles.add(currentSubtitle);
                    }

                    //reset for the next subtitle block
                    subtitleBlockStr.setLength(0);
                } else {
                    subtitleBlockStr.append(data).append('\n');
                }
            }

            // handle last block
            if (subtitleBlockStr.length() > 0) {
                Subtitle currentSubtitle = new Subtitle();
                currentSubtitle.parseSubtitleBlockStr(subtitleBlockStr.toString());
                allSubtitles.add(currentSubtitle);
            }
            getTrackLength();
            isHoldingValidSubtitles = true;
            return true;

        } catch (Exception e) {
            //e.printStackTrace();
            isHoldingValidSubtitles = false;
            return false;
        }
    }

    public static class Subtitle {
        long startTimeMs;
        long endTimeMs;
        String text;
        private void parseSubtitleBlockStr(String subData) {
            String[] subBlockArray = subData.split("\n");

            String timeData = subBlockArray[1];
            parseTimingLine(timeData);

            int n = subBlockArray[0].length() + subBlockArray[1].length() + 2; // extra 2 for \n chars

            text = subData.substring(n);
        }
        private void parseTimingLine(String line) {
            startTimeMs = parseTime(line.substring(0, 12));
            endTimeMs = parseTime(line.substring(17, 29));

        }
        private long parseTime(String timeStr) {
            int hours = Integer.parseInt(timeStr.substring(0, 2));
            int minutes = Integer.parseInt(timeStr.substring(3, 5));
            int seconds = Integer.parseInt(timeStr.substring(6, 8));
            int milliseconds = Integer.parseInt(timeStr.substring(9, 12));

            return hours * 3_600_000L
                    + minutes * 60_000L
                    + seconds * 1_000L
                    + milliseconds;
        }
    }
    private static void getTrackLength() {

        Subtitle lastSub = allSubtitles.get(allSubtitles.size() - 1);

        trackLength = lastSub.endTimeMs;
    }

    public static Subtitle getSubtitleAtIndex(int idx) {
        if (idx < 0 || idx >= allSubtitles.size()) {
            return null; // out of bounds
        }
        return allSubtitles.get(idx);
    }
    public static int getIndexCorrespondingToTime(long timeMs) {
        // binary search because fancy

        int left = 0, right = allSubtitles.size() - 1;
        int result = allSubtitles.size() - 1;

        while (left <= right) {
            int mid = (left + right) / 2;
            Subtitle sub = allSubtitles.get(mid);

            if (timeMs < sub.startTimeMs) {
                result = mid;
                right = mid - 1;
            } else if (timeMs > sub.endTimeMs) {
                left = mid + 1;
            } else {
                return mid;
            }
        }
        return result;
    }
}