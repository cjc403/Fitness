package com.gym.fitness.module.message.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 短信通道（默认模拟：provider=MOCK，只记录不真发；接入供应商时替换 {@link #send}）。 */
@Slf4j
@Component
public class SmsMessageChannel implements MessageChannel {
    @Value("${fitness.messages.sms.enabled:true}")
    private boolean enabled;
    @Value("${fitness.messages.sms.provider:MOCK}")
    private String provider;

    @Override
    public String code() {
        return "SMS";
    }

    @Override
    public String name() {
        return "短信";
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
        return MessageChannel.maskPhone(recipient.phone());
    }

    @Override
    public Result send(MessageChannel.Recipient recipient, String subject, String content) {
        if (mock()) {
            log.info("[模拟短信] 发给 {}：{}", MessageChannel.maskPhone(recipient.phone()), subject);
            return new MessageChannel.Result(true, "MOCK-" + UUID.randomUUID().toString().substring(0, 12), null);
        }
        // 真实供应商接入点：在此调用 SDK 并用返回值填充 providerMessageId / error
        return new MessageChannel.Result(false, null, "未接入真实短信供应商");
    }
}
