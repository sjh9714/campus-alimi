-- 설정 화면의 "이건 왜 없나요?" 한 줄. 탈퇴하면 같이 지운다.
create table feedback (
    id          bigserial    primary key,
    user_id     bigint       not null references app_user(id),
    message     varchar(300) not null,
    created_at  timestamptz  not null default now()
);
create index feedback_user_idx on feedback(user_id, created_at);
