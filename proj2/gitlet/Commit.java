package gitlet;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class Commit implements Serializable {

    private final String message;
    private final String preId;
    private final String preSubId;
    private final int depth;
    private final Date date;
    private Map<String, String> fileNameBlob;

    public Commit(String message, String preId, String preSubId, int depth, Date date) {
        this.message = message;
        this.preId = preId;
        this.preSubId = preSubId;
        this.date = date;
        this.depth = depth;
        this.fileNameBlob = new HashMap<>();
    }
    public void attachBlob(Map<String, String> blobs) {
        fileNameBlob.putAll(blobs);
    }

    public String getMessage() {
        return message;
    }

    public Date getDate() {
        return date;
    }

    public int getDepth() {
        return depth;
    }

    public String getPreId() {
        return preId;
    }
    public String getPreSubId() {
        return preSubId;
    }
    public Map<String, String> getFileNameBlob() {
        return fileNameBlob;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        if (preSubId != null) {
            result.append("Merge: ");
            result.append(preId.substring(0, 7));
            result.append(" ").append(preSubId.substring(0, 7));
            result.append("\n");
        }
        String pattern = "EEE MMM d HH:mm:ss yyyy Z";

        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT-8"));
        String formattedDate = sdf.format(this.date);
        result.append("Date: ").append(formattedDate).append("\n");
        result.append(message + "\n");
        return result.toString();
    }
}
