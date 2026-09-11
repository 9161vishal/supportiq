package com.supportiq.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class CsvReader implements AutoCloseable, Iterator<TweetRecord> {
    private final BufferedReader reader;
    private TweetRecord nextRecord;
    
    public static CsvReader read(Path csvPath) throws IOException {
        return new CsvReader(csvPath);
    }
    
    private CsvReader(Path csvPath) throws IOException {
        this.reader = Files.newBufferedReader(csvPath);
        // Skip header
        readNextLine();
        advance();
    }
    
    private List<String> readNextLine() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        while (true) {
            int c = reader.read();
            if (c == -1) {
                if (!fields.isEmpty() || currentField.length() > 0) {
                    fields.add(currentField.toString());
                }
                return fields.isEmpty() ? null : fields;
            }
            char ch = (char) c;
            
            if (inQuotes) {
                if (ch == '"') {
                    // Peek next char to check if escaped quote
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        currentField.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) reader.reset();
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
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                    }
                    fields.add(currentField.toString());
                    return fields;
                } else {
                    currentField.append(ch);
                }
            }
        }
    }
    
    private void advance() {
        try {
            List<String> fields = readNextLine();
            if (fields == null) {
                nextRecord = null;
                return;
            }
            // tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id
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
            nextRecord = r;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return nextRecord != null;
    }

    @Override
    public TweetRecord next() {
        if (nextRecord == null) throw new NoSuchElementException();
        TweetRecord r = nextRecord;
        advance();
        return r;
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
}
