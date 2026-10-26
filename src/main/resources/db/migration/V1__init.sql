-- 수집 요청 기록. 어떤 주소를 언제 읽었고 무엇을 받았는지 그대로 남긴다.
create table fetch_log (
    id          bigserial primary key,
    source      varchar(32)  not null,
    url         text         not null,
    http_status integer,
    error       text,
    html        text,
    fetched_at  timestamptz  not null default now()
);

-- 공지 원문. 파싱 규칙을 고치면 여기서 다시 처리한다.
create table notice_raw (
    article_no  bigint primary key,
    html        text        not null,
    fetched_at  timestamptz not null default now()
);

-- 공지 한 건. 항상 검수 대상으로 들어온다.
create table notice (
    id              bigserial primary key,
    article_no      bigint       not null unique references notice_raw(article_no),
    category        varchar(16)  not null,
    title           text         not null,
    posted_on       date         not null,
    writer          varchar(128),
    url             text         not null,
    body_text       text         not null,
    has_images      boolean      not null default false,
    attachments     text         not null default '',
    status          varchar(16)  not null default 'NEEDS_REVIEW',
    review_reason   varchar(16)  not null,
    first_seen_at   timestamptz  not null default now(),
    reviewed_at     timestamptz
);

create index notice_status_idx on notice(status, posted_on desc);

-- 날짜 항목. 학사일정 한 줄, 공지에서 뽑은 한 줄, 운영자가 손으로 넣은 한 줄이 모두 여기 쌓인다.
create table date_item (
    id            bigserial primary key,
    source_kind   varchar(16)  not null,        -- SCHEDULE / NOTICE / MANUAL
    notice_id     bigint references notice(id),
    natural_key   text         not null unique,
    title         text         not null,
    starts_on     date,
    ends_on       date         not null,
    starts_at     time,
    ends_at       time,
    campus        varchar(32),
    target_grade  varchar(8),                   -- ALL / 1 / 2 / 3 / 4, null = 미확인
    target_dept   varchar(64),                  -- ALL / 학과명, null = 미확인
    target_note   text,
    status        varchar(16)  not null default 'AUTO',   -- AUTO / REVIEWED
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create index date_item_ends_on_idx on date_item(ends_on);
create index date_item_notice_idx on date_item(notice_id);
