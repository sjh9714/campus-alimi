-- 아침 알림 메일 (ADR 004). 기본은 받음. 메일 안의 링크 하나로 끈다.
alter table app_user add column digest_enabled boolean not null default true;
alter table app_user add column unsubscribe_token varchar(64);
update app_user set unsubscribe_token = md5(random()::text || clock_timestamp()::text || id::text) where unsubscribe_token is null;
alter table app_user alter column unsubscribe_token set not null;
create unique index app_user_unsubscribe_idx on app_user(unsubscribe_token);

-- 보낸 알림. 사건마다 키 하나(새 공지, 저장한 마감, 체크한 혜택). 같은 키는 두 번 보내지 않는다.
create table sent_notification (
    user_id    bigint       not null references app_user(id),
    event_key  varchar(64)  not null,
    sent_at    timestamptz  not null default now(),
    primary key (user_id, event_key)
);
