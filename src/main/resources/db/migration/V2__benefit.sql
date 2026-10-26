-- 학생 혜택. 출처 페이지를 사람이 열어 확인한 것만 benefits.yml에 적고, 앱이 뜰 때 여기로 옮긴다.
create table benefit (
    id            bigserial primary key,
    slug          varchar(64)  not null unique,
    name          text         not null,
    category      varchar(16)  not null,     -- TOOL / SUBSCRIPTION / TRANSPORT / FINANCE / SCHOLARSHIP / SCHOOL / CULTURE
    summary       text         not null,     -- 한 줄. 무엇을 얼마나
    condition     text         not null,     -- 누가 받을 수 있나
    how_to        text         not null,     -- 어디서 어떻게
    price         text,                      -- 가격이 있으면 그대로
    source_name   text         not null,
    source_url    text         not null,
    verified_on   date         not null,     -- 출처를 연 날
    renewal_rule  text         not null,     -- 재인증 주기나 종료 조건
    ends_on       date,                      -- 날짜로 끝나는 혜택만
    active        boolean      not null default true,
    updated_at    timestamptz  not null default now()
);

create index benefit_category_idx on benefit(category, active);
