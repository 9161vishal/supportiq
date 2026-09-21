package com.supportiq.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HumanSupportServiceImpl implements HumanSupportService {

    private final String handoffMessage;

    public HumanSupportServiceImpl(
            @Value("${supportiq.human-support.message:Human contact support is not integrated yet.}") String handoffMessage) {
        this.handoffMessage = handoffMessage;
    }

    @Override
    public String getHandoffMessage() {
        return handoffMessage;
    }
}
