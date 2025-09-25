-- 더미 사용자 데이터
INSERT INTO users (email, name, phone_number, created_at, updated_at) VALUES
('test@example.com', '테스트 사용자', '010-1234-5678', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('admin@example.com', '관리자', '010-9876-5432', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('user1@example.com', '사용자1', '010-1111-2222', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 더미 적립금 데이터
INSERT INTO points (user_id, balance, created_at, updated_at) VALUES
(1, 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 50000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 5000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);