package com.thing.subtitleviewer;


import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

public class SubtitlesReader {

    private static boolean isHoldingValidSubtitles = false;
    public static boolean getIsHoldingValidSubtitles() {
        return isHoldingValidSubtitles;
    }
    public static void invalidateCurrentSubtitles() {
        isHoldingValidSubtitles = false;
    }

    public static long trackLength = 0;

    private final static ArrayList<Subtitle> AllSubtitles = new ArrayList<Subtitle>();

    public static boolean parseFile(InputStream inputStream) {
        AllSubtitles.clear(); // reset previous data

        try (Scanner myReader = new Scanner(inputStream)) {
            StringBuilder subtitleBlockStr = new StringBuilder();

            while (myReader.hasNextLine()) {
                String data = myReader.nextLine().trim() + "\n";

                if (data.equals("\n")) {
                    Subtitle currentSubtitle = new Subtitle();
                    currentSubtitle.parseSubtitleBlockStr(subtitleBlockStr.toString());
                    AllSubtitles.add(currentSubtitle);

                    //start the next subtitle block
                    subtitleBlockStr = new StringBuilder();
                } else {
                    subtitleBlockStr.append(data);
                }
            }

            // handle last block
            if (subtitleBlockStr.length() > 0) {
                Subtitle currentSubtitle = new Subtitle();
                currentSubtitle.parseSubtitleBlockStr(subtitleBlockStr.toString());
                AllSubtitles.add(currentSubtitle);
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
        private void parseSubtitleBlockStr(String subData) throws Exception {
            String[] subBlockArray = subData.split("\n");

            String timeData = subBlockArray[1];
            parseTimingLine(timeData);

            int n = subBlockArray[0].length() + subBlockArray[1].length() + 2; // extra 2 for \n chars

            text = subData.substring(n);
        }
        private void parseTimingLine(String line) throws Exception {
            String[] stringParts = line.split(" ");

            startTimeMs = parseTime(stringParts[0]);
            endTimeMs = parseTime(stringParts[2]);

        }
        private long parseTime(String timeStr) throws ParseException {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss,SSS");

            Date date = sdf.parse(timeStr); // parse the string
            if (date == null) return 0;

            // return milliseconds since start of day
            return date.getTime()
                    - sdf.parse("00:00:00,000").getTime();
        }
    }
    private static void getTrackLength() {

        Subtitle lastSub = AllSubtitles.get(AllSubtitles.size() - 1);

        trackLength = lastSub.endTimeMs;
    }

    public static Subtitle getSubtitleAtIndex(int idx) {
        ArrayList<Subtitle> subs = AllSubtitles;
        if (idx < 0 || idx >= subs.size()) {
            return null; // out of bounds
        }
        return subs.get(idx);
    }
    public static int getIndexCorrespondingToTime(long timeMs) {
        // binary search because fancy

        int left = 0, right = AllSubtitles.size() - 1;
        int result = AllSubtitles.size() - 1;

        while (left <= right) {
            int mid = (left + right) / 2;
            Subtitle sub = AllSubtitles.get(mid);

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