package com.supportiq.service;

/**
 * Abstraction for human support handoff.
 * Current implementation returns a configurable message.
 * Can be replaced with Twilio/Exotel/Amazon Connect integration later.
 */
public interface HumanSupportService {
    String getHandoffMessage();
}
