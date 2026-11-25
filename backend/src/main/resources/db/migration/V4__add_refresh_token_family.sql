-- refresh token에 묶음 id와 쓴 시각을 더합니다(2026.11.24 인증-3 재사용 감지).
-- 묶음은 한 번의 로그인에서 재발급으로 이어진 refresh token들입니다. 재발급하면 쓴 행을 지우지 않고 쓴 시각을 적습니다.
-- 이미 쓴 값이 다시 오면 그 묶음의 행을 모두 지웁니다.

-- 처음에는 두 칸 모두 null을 허용해서 더합니다. 이미 있는 행에는 아직 값이 없기 때문입니다.
alter table refresh_token add column family_id varchar(36);
alter table refresh_token add column used_at timestamp(6);

-- 이미 있는 행은 어느 로그인에서 이어졌는지 알 수 없어서, 행마다 다른 묶음 id(UUID)를 넣습니다.
-- gen_random_uuid()는 PostgreSQL 13부터 기본으로 들어 있는 UUID 함수입니다. ::text로 36글자 문자열로 바꿉니다.
-- used_at은 null로 둡니다. 이전 방식은 쓴 행을 지웠으므로 남은 행은 모두 아직 쓰지 않은 값입니다.
update refresh_token set family_id = gen_random_uuid()::text;

-- 값을 모두 채운 뒤에 not null로 바꿉니다. Entity의 @Column(nullable = false)와 맞춥니다.
alter table refresh_token alter column family_id set not null;
