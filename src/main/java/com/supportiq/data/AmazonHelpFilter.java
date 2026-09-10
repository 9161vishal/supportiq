package com.supportiq.data;

public class AmazonHelpFilter {
    public static final String AMAZON_HELP_AUTHOR_ID = "AmazonHelp";

    public boolean isAmazonHelp(TweetRecord record) {
        if (record == null || record.getAuthor_id() == null) {
            return false;
        }
        return record.getAuthor_id().equalsIgnoreCase(AMAZON_HELP_AUTHOR_ID);
    }
}
