package com.gym.fitness.module.message.channel;

/**
 * 消息通道抽象。
 *
 * <p>站内通知走既有的 notification 表；短信与邮件等外部通道实现本接口。
 * 未接入真实供应商时用 {@code provider=MOCK} 的模拟实现：**只记录、不真的发出去**，
 * 这样业务链路与投递审计可以先跑通，接供应商时只换实现。</p>
 */
public interface MessageChannel {
    /** 通道编码：IN_APP / SMS / EMAIL。 */
    String code();

    String name();

    /** 是否启用；关闭时投递记录为 SKIPPED。 */
    boolean enabled();

    boolean mock();

    String provider();

    /** 收件人展示地址（脱敏），无有效收件人时返回 null 表示跳过。 */
    String recipientOf(Recipient recipient);

    Result send(Recipient recipient, String subject, String content);

    record Recipient(Long userId, Long memberId, String phone, String email, String realName) {
    }

    record Result(boolean success, String providerMessageId, String error) {
    }

    /** 手机号脱敏：138****5678。 */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.trim();
        if (digits.length() < 7) return "***";
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /** 邮箱脱敏：a***@qq.com。 */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) return "***";
        return value.substring(0, 1) + "***" + value.substring(at);
    }
}
