-- 연고 지역. "전라남도 순천시"나 "전라남도". 지역 장학 공지를 위로 올리는 데만 쓴다. 선택 입력 (ADR 008, 009).
alter table app_user add column home_region varchar(64);

-- 운영자가 고친 대상 범위. 비어 있으면 제목과 본문으로 정한다 (ADR 009).
alter table notice add column audience_scope varchar(16);
alter table notice add column audience_label varchar(64);
