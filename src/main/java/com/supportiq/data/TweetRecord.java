package com.supportiq.data;

public class TweetRecord {
    private String tweet_id;
    private String author_id;
    private boolean inbound;
    private String created_at;
    private String text;
    private String response_tweet_id;
    private String in_response_to_tweet_id;

    public TweetRecord() {}

    public String getTweet_id() { return tweet_id; }
    public void setTweet_id(String tweet_id) { this.tweet_id = tweet_id; }

    public String getAuthor_id() { return author_id; }
    public void setAuthor_id(String author_id) { this.author_id = author_id; }

    public boolean isInbound() { return inbound; }
    public void setInbound(boolean inbound) { this.inbound = inbound; }

    public String getCreated_at() { return created_at; }
    public void setCreated_at(String created_at) { this.created_at = created_at; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getResponse_tweet_id() { return response_tweet_id; }
    public void setResponse_tweet_id(String response_tweet_id) { this.response_tweet_id = response_tweet_id; }

    public String getIn_response_to_tweet_id() { return in_response_to_tweet_id; }
    public void setIn_response_to_tweet_id(String in_response_to_tweet_id) { this.in_response_to_tweet_id = in_response_to_tweet_id; }
}
