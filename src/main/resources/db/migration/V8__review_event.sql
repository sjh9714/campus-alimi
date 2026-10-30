-- 검수 화면에서 운영자가 한 일. 자동 추출이 맞았는지, 고쳤는지, 없는 날짜를 지웠는지 세는 재료다.
-- 항목이 지워져도 기록은 남아야 하므로 date_item에는 외래 키를 걸지 않는다.
create table review_event (
    id            bigserial    primary key,
    notice_id     bigint       references notice(id),
    date_item_id  bigint,
    action        varchar(16)  not null,   -- APPROVE / EDIT / DELETE / ADD
    before_json   text,
    after_json    text,
    reviewed_by   varchar(254) not null,
    created_at    timestamptz  not null default now()
);
create index review_event_notice_idx on review_event(notice_id, created_at);
