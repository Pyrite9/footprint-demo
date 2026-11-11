-- 업무 규칙 두 개를 데이터베이스도 지키게 하는 부분 unique 인덱스입니다(2026.11.12 O-1).
-- 부분 인덱스는 where 조건에 맞는 행만 담습니다. 그래서 취소된 행, 조장이 아닌 행은 몇 개가 있어도 걸리지 않습니다.
-- Hibernate의 @UniqueConstraint로는 where 조건을 적을 수 없어서 migration에만 있습니다(Entity에는 없음).
-- Service의 행 잠금과 검사가 먼저 409 DUPLICATE 등으로 막습니다. 이 인덱스는 그 검사를 지나친 저장을 막는 마지막 장치입니다.

-- 한 조원의 유효한(취소되지 않은) 완료확인은 산출물마다 하나입니다. 취소한 행은 남지만 이 인덱스에 들어가지 않습니다.
create unique index confirmation_active_deliverable_uk
    on confirmation (deliverable_id, confirmer_id)
    where canceled_at is null and deliverable_id is not null;

-- 같은 규칙을 기여 항목에도 겁니다.
create unique index confirmation_active_contribution_uk
    on confirmation (contribution_id, confirmer_id)
    where canceled_at is null and contribution_id is not null;

-- 조장은 조마다 한 명입니다. leader가 true인 행만 담아서, 같은 team_id가 두 번 들어오면 거절합니다.
create unique index team_member_one_leader_uk
    on team_member (team_id)
    where leader;
