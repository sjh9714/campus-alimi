-- 사용자. 비밀번호는 없다. 이메일 매직링크로만 들어온다 (ADR 005).
create table app_user (
    id            bigserial primary key,
    email         varchar(255) not null unique,
    entry_year    integer,                   -- 입학년도. 학년 계산용. 선택 입력
    major         varchar(64),               -- 전공. 선택 입력
    second_major  varchar(64),               -- 복수·부전공. 선택 입력
    created_at    timestamptz  not null default now(),
    last_login_at timestamptz
);

-- 로그인 링크. 토큰은 해시로만 저장한다. 15분 유효, 한 번만.
create table login_token (
    id          bigserial primary key,
    email       varchar(255) not null,
    token_hash  varchar(64)  not null unique,
    expires_at  timestamptz  not null,
    used_at     timestamptz,
    created_at  timestamptz  not null default now()
);

-- 세션. 쿠키에는 토큰 원문, 여기에는 해시. 30일.
create table user_session (
    id          bigserial primary key,
    user_id     bigint       not null references app_user(id),
    token_hash  varchar(64)  not null unique,
    expires_at  timestamptz  not null,
    created_at  timestamptz  not null default now()
);

create index user_session_user_idx on user_session(user_id);

-- 저장한 날짜 항목. 마감 3일 전 알림의 대상.
create table saved_item (
    user_id       bigint      not null references app_user(id),
    date_item_id  bigint      not null references date_item(id) on delete cascade,
    created_at    timestamptz not null default now(),
    primary key (user_id, date_item_id)
);

-- 체크한 혜택. 만료 7일 전 알림의 대상.
create table user_benefit (
    user_id     bigint      not null references app_user(id),
    benefit_id  bigint      not null references benefit(id),
    checked_at  timestamptz not null default now(),
    primary key (user_id, benefit_id)
);

-- 검수에서 누가 언제 고쳤는지 남긴다.
alter table date_item add column reviewed_at timestamptz;
alter table date_item add column reviewed_by varchar(255);
alter table notice add column reviewed_by varchar(255);
