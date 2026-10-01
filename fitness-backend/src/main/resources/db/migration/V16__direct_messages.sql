-- 2026-10-01 角色间站内私信（真实投递：双方都能在自己的消息界面看到）
--   与 message_delivery（外部短信/邮件通道，当前为模拟）完全分开：
--   这张表是站内一对一的真实消息，不涉及任何外部供应商。
CREATE TABLE direct_message (
    id BIGSERIAL PRIMARY KEY,
    sender_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    recipient_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    -- 姓名快照：对方之后改名，历史消息仍显示当时的名字
    sender_name VARCHAR(50) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    -- 发送时发送方所属门店，便于排查跨店消息
    venue_id BIGINT REFERENCES venue(id),
    content VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_direct_message_recipient ON direct_message(recipient_user_id, id DESC);
CREATE INDEX idx_direct_message_sender ON direct_message(sender_user_id, id DESC);
