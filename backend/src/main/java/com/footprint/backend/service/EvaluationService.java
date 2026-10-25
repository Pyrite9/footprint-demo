package com.footprint.backend.service;

import com.footprint.backend.dto.EvaluationSubmitRequest;
import com.footprint.backend.entity.Evaluation;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.DuplicateException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.EvaluationRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 동료평가 제출과 내 제출 여부 조회를 처리합니다. 점수를 합치거나 평균 내는 일은 하지 않습니다.
@Service
public class EvaluationService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EvaluationRepository evaluationRepository;
    private final TeamAccessChecker teamAccessChecker;

    public EvaluationService(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                             EvaluationRepository evaluationRepository, TeamAccessChecker teamAccessChecker) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.evaluationRepository = evaluationRepository;
        this.teamAccessChecker = teamAccessChecker;
    }

    // 내가 이 조에서 제출한 평가를 찾습니다. 제출 여부를 알려 줄 때만 씁니다. 그 조의 조원만 부를 수 있습니다.
    public List<Evaluation> findMine(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        TeamMember evaluator = teamAccessChecker.checkTeamMember(team, memberId);
        return evaluationRepository.findByEvaluatorId(evaluator.getId());
    }

    // 자신을 뺀 조원 전원의 평가를 한 번에 저장합니다. 한 명이라도 빠지거나 이미 제출했으면 아무것도 저장하지 않습니다.
    // @Transactional: 여러 행을 저장하다 중간에 실패하면 앞에서 저장한 행도 함께 취소됩니다.
    @Transactional
    public List<Evaluation> submit(Long teamId, List<EvaluationSubmitRequest.Item> items, Long memberId) {
        Team team = findTeam(teamId);
        TeamMember evaluator = teamAccessChecker.checkTeamMember(team, memberId);
        if (!evaluationRepository.findByEvaluatorId(evaluator.getId()).isEmpty()) {
            throw new DuplicateException("이미 동료평가를 제출했습니다");
        }

        // 평가 대상 = 이 조의 조원 중 나를 뺀 사람. 조원 번호(id)로 바로 찾을 수 있게 Map에 담습니다.
        Map<Long, TeamMember> targetsById = new HashMap<>();
        for (TeamMember teamMember : teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId())) {
            if (!teamMember.getId().equals(evaluator.getId())) {
                targetsById.put(teamMember.getId(), teamMember);
            }
        }

        // 보낸 대상이 "나를 뺀 조원 전원, 한 번씩"인지 확인합니다. Set.add는 이미 있는 값이면 false를 돌려줍니다.
        Set<Long> submittedIds = new HashSet<>();
        for (EvaluationSubmitRequest.Item item : items) {
            if (!submittedIds.add(item.targetTeamMemberId())) {
                throw new InvalidInputException("같은 조원을 두 번 평가했습니다. id=" + item.targetTeamMemberId());
            }
        }
        if (!submittedIds.equals(targetsById.keySet())) {
            throw new InvalidInputException("자신을 뺀 조원 전원을 한 번씩 평가해야 합니다");
        }

        // 한 번에 제출한 평가는 모두 같은 시각으로 기록합니다.
        LocalDateTime submittedAt = LocalDateTime.now();
        List<Evaluation> evaluations = new ArrayList<>();
        for (EvaluationSubmitRequest.Item item : items) {
            evaluations.add(new Evaluation(evaluator, targetsById.get(item.targetTeamMemberId()),
                    item.goalSharing(), item.roleFulfillment(), item.encouragement(),
                    item.onlineCommunication(), item.offlineCommunication(), item.progressCheck(),
                    toComment(item.comment()), submittedAt));
        }
        return evaluationRepository.saveAll(evaluations);
    }

    // 서술 의견의 앞뒤 공백을 지웁니다. 비었으면 null로 저장합니다(의견은 선택입니다).
    private String toComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
