package com.supportiq.data;

import java.util.Iterator;

public interface CsvReader {
    Iterator<TweetRecord> read(String filePath);
}
