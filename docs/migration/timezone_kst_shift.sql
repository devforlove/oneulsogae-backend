-- 운영 JVM 타임존을 UTC → Asia/Seoul로 전환(Dockerfile -Duser.timezone)하면서,
-- 그동안 UTC 벽시계로 저장된 기존 시각 데이터를 KST로 보정한다(+9시간).
--
-- ⚠️ 실행 절차 (순서 엄수):
--   1. 구(UTC) 앱 컨테이너 중지 — 실행 중 새 UTC 행이 섞이면 이중 보정/누락이 생긴다.
--   2. 이 SQL 실행 (1회만! 재실행하면 +9시간 중복 적용된다).
--   3. 타임존 반영된 새 이미지로 기동.
--
-- 보정 대상: 서버가 now()로 기록한 모든 시각 컬럼 (BaseEntity의 created_at/updated_at/deleted_at 포함).
-- NULL은 + INTERVAL 연산 결과도 NULL이므로 그대로 유지된다.
--
-- 보정 제외 (어드민이 KST 의도로 입력한 벽시계 값 — 이미 KST):
--   popups.exposed_from / exposed_to
--   gathering_schedules.start_at / end_at

UPDATE alarms SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE chat_messages SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, sent_at = sent_at + INTERVAL 9 HOUR;
UPDATE chat_room_members SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, last_read_at = last_read_at + INTERVAL 9 HOUR, joined_at = joined_at + INTERVAL 9 HOUR, exited_at = exited_at + INTERVAL 9 HOUR;
UPDATE chat_rooms SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, expired_at = expired_at + INTERVAL 9 HOUR, last_message_at = last_message_at + INTERVAL 9 HOUR;
UPDATE coin_balances SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE coin_histories SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, occurred_at = occurred_at + INTERVAL 9 HOUR;
UPDATE coin_items SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE coin_payments SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE company_email_verifications SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, expires_at = expires_at + INTERVAL 9 HOUR, verified_at = verified_at + INTERVAL 9 HOUR;
UPDATE company_image_verifications SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE gathering_members SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE gathering_payments SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE gathering_products SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE gathering_profile SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
-- gathering_schedules: start_at/end_at은 어드민 입력(KST)이라 제외, 감사 컬럼만 보정.
UPDATE gathering_schedules SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE gatherings SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE identity_verifications SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, verified_at = verified_at + INTERVAL 9 HOUR;
UPDATE image_templates SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE inquiries SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, answered_at = answered_at + INTERVAL 9 HOUR;
UPDATE lounge_chat_requests SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE lounge_post_images SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE lounge_post_likes SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE lounge_posts SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE match_user SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, last_login_at = last_login_at + INTERVAL 9 HOUR;
UPDATE matched_teams SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE member_verifications SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE notices SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE notification_preferences SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE payment_methods SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
-- popups: exposed_from/exposed_to는 어드민 입력(KST)이라 제외, 감사 컬럼만 보정.
UPDATE popups SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE recommended_team_histories SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE recommended_teams SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE refresh_tokens SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, expires_at = expires_at + INTERVAL 9 HOUR;
UPDATE regions SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE reports SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE self_intro_posts SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE solo_match_members SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, checked_at = checked_at + INTERVAL 9 HOUR;
UPDATE solo_matches SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, expires_at = expires_at + INTERVAL 9 HOUR;
UPDATE team_matches SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, expires_at = expires_at + INTERVAL 9 HOUR;
UPDATE team_members SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE teams SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE university_email_verifications SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, expires_at = expires_at + INTERVAL 9 HOUR, verified_at = verified_at + INTERVAL 9 HOUR;
UPDATE user_companies SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE user_details SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE user_ideal_types SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE user_universities SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR;
UPDATE users SET created_at = created_at + INTERVAL 9 HOUR, updated_at = updated_at + INTERVAL 9 HOUR, deleted_at = deleted_at + INTERVAL 9 HOUR, last_login_at = last_login_at + INTERVAL 9 HOUR;
