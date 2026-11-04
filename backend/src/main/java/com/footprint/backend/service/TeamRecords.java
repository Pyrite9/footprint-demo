package com.footprint.backend.service;

import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.LeaderHistory;
import com.footprint.backend.entity.RoleHistory;
import com.footprint.backend.entity.TeamMember;

import java.time.LocalDateTime;
import java.util.List;

// 한 조의 기록 열람에 쓸 Entity 묶음입니다. Service는 Entity를 돌려주는 규칙이라 묶음으로 돌려주고, 한 줄씩 펼치는 일은 RecordResponse가 합니다.
// 목록은 모두 id가 큰 것부터입니다. evaluationStartedAt은 평가를 시작하지 않았으면 null입니다.
public record TeamRecords(LocalDateTime evaluationStartedAt, List<LeaderHistory> leaderHistories,
                          List<Confirmation> confirmations, List<Contribution> contributions,
                          List<Deliverable> deliverables, List<TeamMember> teamMembers,
                          List<RoleHistory> roleHistories) { }
