-- 그림 공지의 그림을 AI가 받아 적은 글 (ADR 010). 그림 주소마다 한 번만 받아 적고, 하루 상한은 이 표로 센다.
create table image_transcript (
    image_url      varchar(1000) primary key,
    article_no     bigint        not null references notice_raw(article_no),
    text           text          not null,
    model          varchar(60)   not null,
    input_tokens   bigint        not null,
    output_tokens  bigint        not null,
    created_at     timestamptz   not null default now()
);
create index image_transcript_created_idx on image_transcript(created_at);
