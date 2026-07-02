-- 사용자별 코인 거래 내역 커서 페이징(user_id 동등 + id 내림차순 keyset)을 받치는 인덱스를 추가한다.
-- 기존 idx_user_id_coin_get_type_occurred_at는 user_id 내부가 coin_get_type·occurred_at순이라 id 정렬을 커버하지 못한다.
CREATE INDEX idx_user_id_id ON coin_histories (user_id, id);
