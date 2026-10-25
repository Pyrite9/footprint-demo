package com.footprint.backend.repository;

import com.footprint.backend.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// team_member 테이블을 저장·조회합니다.
// 메서드 이름의 TeamAssignmentId는 team.assignment.id, MemberId는 member.id처럼 연관을 따라간 필드를 뜻합니다.
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    // 한 회원의 조 참여 기록을 최신순으로 찾습니다. 학생의 과제 목록에 씁니다.
    List<TeamMember> findByMemberIdOrderByIdDesc(Long memberId);

    // 한 과제에서 한 회원의 조 참여 기록을 찾습니다. 한 학생은 한 과제에서 한 조에만 있으므로 0개 또는 1개입니다.
    Optional<TeamMember> findByTeamAssignmentIdAndMemberId(Long assignmentId, Long memberId);

    // 한 과제의 조장 기록을 조 id 순서로 찾습니다. 조마다 조장이 한 명이라 조 목록과 같은 순서·개수입니다.
    List<TeamMember> findByTeamAssignmentIdAndLeaderTrueOrderByTeamId(Long assignmentId);

    // 한 조의 조장 기록을 찾습니다.
    Optional<TeamMember> findByTeamIdAndLeaderTrue(Long teamId);

    // 한 조의 조원 기록을 합류한 순서(id 작은 순)로 찾습니다. 조장은 과제를 만들 때 함께 저장되어 맨 앞입니다.
    List<TeamMember> findByTeamIdOrderByIdAsc(Long teamId);

    // 한 조에서 한 회원의 조 참여 기록을 찾습니다. 조원이 아니면 빈 Optional입니다.
    Optional<TeamMember> findByTeamIdAndMemberId(Long teamId, Long memberId);
}
