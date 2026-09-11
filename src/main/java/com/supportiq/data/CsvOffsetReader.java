package com.supportiq.data;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvOffsetReader implements AutoCloseable {
    private final Path csvPath;
    private RandomAccessFile randomAccessFile;

    public CsvOffsetReader(Path csvPath) throws IOException {
        this.csvPath = csvPath;
        this.randomAccessFile = new RandomAccessFile(csvPath.toFile(), "r");
    }

    /**
     * Builds an index mapping tweetId -> file byte offset.
     * Streams the file efficiently using BufferedInputStream.
     */
    public TweetOffsetIndex buildIndex() throws IOException {
        // Assume maximum 3M tweets
        TweetOffsetIndex index = new TweetOffsetIndex(3_000_000);
        
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(csvPath.toFile()))) {
            long currentOffset = 0;
            boolean inQuotes = false;
            
            // To parse the tweet ID without building strings
            StringBuilder idBuilder = new StringBuilder();
            boolean readingId = true;
            
            // Skip the first line (header)
            int c;
            while ((c = bis.read()) != -1) {
                currentOffset++;
                if (c == '\n') break;
            }
            
            long recordStartOffset = currentOffset;
            
            while ((c = bis.read()) != -1) {
                char ch = (char) c;
                
                if (ch == '"') {
                    inQuotes = !inQuotes;
                }
                
                if (!inQuotes && (ch == '\n' || ch == '\r')) {
                    if (ch == '\r') {
                        bis.mark(1);
                        int next = bis.read();
                        if (next == '\n') {
                            currentOffset++;
                        } else if (next != -1) {
                            bis.reset();
                        }
                    }
                    
                    if (idBuilder.length() > 0) {
                        try {
                            long tweetId = Long.parseLong(idBuilder.toString());
                            index.put(tweetId, recordStartOffset);
                        } catch (NumberFormatException e) {
                            // ignore malformed ID
                        }
                    }
                    
                    currentOffset++;
                    recordStartOffset = currentOffset;
                    idBuilder.setLength(0);
                    readingId = true;
                    continue;
                }
                
                if (readingId) {
                    if (ch == ',') {
                        readingId = false;
                    } else {
                        idBuilder.append(ch);
                    }
                }
                
                currentOffset++;
            }
            
            // Last line
            if (idBuilder.length() > 0) {
                try {
                    long tweetId = Long.parseLong(idBuilder.toString());
                    index.put(tweetId, recordStartOffset);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        
        return index;
    }

    public TweetRecord readRecordAt(long offset) throws IOException {
        randomAccessFile.seek(offset);
        
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        while (true) {
            int c = randomAccessFile.read();
            if (c == -1) {
                if (!fields.isEmpty() || currentField.length() > 0) {
                    fields.add(currentField.toString());
                }
                break;
            }
            char ch = (char) c;
            
            if (inQuotes) {
                if (ch == '"') {
                    long currentPos = randomAccessFile.getFilePointer();
                    int next = randomAccessFile.read();
                    if (next == '"') {
                        currentField.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) randomAccessFile.seek(currentPos);
                    }
                } else {
                    currentField.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(currentField.toString());
                    currentField.setLength(0);
                } else if (ch == '\n' || ch == '\r') {
                    if (ch == '\r') {
                        long currentPos = randomAccessFile.getFilePointer();
                        int next = randomAccessFile.read();
                        if (next != '\n' && next != -1) {
                            randomAccessFile.seek(currentPos);
                        }
                    }
                    fields.add(currentField.toString());
                    break;
                } else {
                    currentField.append(ch);
                }
            }
        }
        
        while (fields.size() < 7) {
            fields.add("");
        }
        
        TweetRecord r = new TweetRecord();
        r.setTweet_id(fields.get(0));
        r.setAuthor_id(fields.get(1));
        r.setInbound(Boolean.parseBoolean(fields.get(2)));
        r.setCreated_at(fields.get(3));
        r.setText(fields.get(4));
        r.setResponse_tweet_id(fields.get(5));
        r.setIn_response_to_tweet_id(fields.get(6));
        return r;
    }

    @Override
    public void close() throws IOException {
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }
}
