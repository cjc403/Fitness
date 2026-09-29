package com.gym.fitness.module.message.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 邮件通道（默认模拟：provider=MOCK，只记录不真发；接入供应商时替换 {@link #send}）。 */
@Slf4j
@Component
public class EmailMessageChannel implements MessageChannel {
    @Value("${fitness.messages.email.enabled:true}")
    private boolean enabled;
    @Value("${fitness.messages.email.provider:MOCK}")
    private String provider;
    @Value("${fitness.messages.email.from:no-reply@fitchain.local}")
    private String from;

    @Override
    public String code() {
        return "EMAIL";
    }

    @Override
    public String name() {
        return "邮件";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean mock() {
        return "MOCK".equalsIgnoreCase(provider);
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String recipientOf(MessageChannel.Recipient recipient) {
        return MessageChannel.maskEmail(recipient.email());
    }

    @Override
    public Result send(MessageChannel.Recipient recipient, String subject, String content) {
        if (mock()) {
            log.info("[模拟邮件] {} → {}：{}", from, MessageChannel.maskEmail(recipient.email()), subject);
            return new MessageChannel.Result(true, "MOCK-" + UUID.randomUUID().toString().substring(0, 12), null);
        }
        return new MessageChannel.Result(false, null, "未接入真实邮件服务");
    }
}
