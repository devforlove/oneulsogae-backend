-- 모임 소개(description) 최대 길이를 1000자에서 4000자로 늘린다(Markdown 전환 대비).
ALTER TABLE gatherings MODIFY COLUMN description VARCHAR(4000);
