package com.footprint.backend.service;

import com.footprint.backend.dto.EvaluationDraftSaveRequest;
import com.footprint.backend.dto.EvaluationSubmitRequest;
import com.footprint.backend.entity.Evaluation;
import com.footprint.backend.entity.EvaluationDraft;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.DuplicateException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.EvaluationDraftRepository;
import com.footprint.backend.repository.EvaluationRepository;
import com.footprint.backend.repository.TeamMemberRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 동료평가 제출, 내 제출 여부 조회, 임시 저장, 교수의 평가 결과 조회를 처리합니다. 점수를 합치거나 평균 내는 일은 하지 않습니다.
@Service
public class EvaluationService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationDraftRepository evaluationDraftRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;

    public EvaluationService(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository,
                             EvaluationRepository evaluationRepository, EvaluationDraftRepository evaluationDraftRepository,
                             TeamAccessChecker teamAccessChecker, AssignmentPhaseChecker assignmentPhaseChecker) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.evaluationRepository = evaluationRepository;
        this.evaluationDraftRepository = evaluationDraftRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
    }

    // 내가 이 조에서 제출한 평가를 찾습니다. 제출 여부를 알려 줄 때만 씁니다. 그 조의 조원만 부를 수 있습니다.
    public List<Evaluation> findMine(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        TeamMember evaluator = teamAccessChecker.checkTeamMember(team, memberId);
        return evaluationRepository.findByEvaluatorId(evaluator.getId());
    }

    // 조원별 제출 여부에 쓸 조원과 제출된 평가를 찾습니다. 과제를 만든 교수만 부를 수 있습니다.
    public TeamEvaluations findSubmissions(Long teamId, Long memberId) {
        List<Evaluation> evaluations = findTeamEvaluations(teamId, memberId);
        return new TeamEvaluations(teamMemberRepository.findByTeamIdOrderByIdAsc(teamId), evaluations);
    }

    // 한 조에서 제출된 평가를 찾습니다. 임시 저장은 나오지 않습니다. 과제를 만든 교수만 부를 수 있습니다.
    public List<Evaluation> findTeamEvaluations(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkProfessor(team.getAssignment(), memberId);
        return evaluationRepository.findByEvaluatorTeamIdOrderByIdAsc(teamId);
    }

    // 내가 이 조에서 임시 저장한 내용을 찾습니다. 그 조의 조원만 부를 수 있어서 교수는 볼 수 없습니다.
    public List<EvaluationDraft> findMyDrafts(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        TeamMember evaluator = teamAccessChecker.checkTeamMember(team, memberId);
        return evaluationDraftRepository.findByEvaluatorIdOrderByIdAsc(evaluator.getId());
    }

    // 지금까지 입력한 내용을 임시 저장합니다. 이전 임시 저장을 모두 지우고 보낸 내용으로 바꿉니다. 조원 일부만, 점수 일부만 보내도 됩니다.
    @Transactional
    public List<EvaluationDraft> saveDrafts(Long teamId, List<EvaluationDraftSaveRequest.Item> items, Long memberId) {
        // 저장 요청끼리 또는 저장과 제출이 겹치면 unique 제약에 걸리거나 제출한 뒤에 임시 저장이 남을 수 있습니다. 조 행을 잠가 한 번에 하나씩 처리합니다.
        Team team = findTeamWithLock(teamId);
        TeamMember evaluator = teamAccessChecker.checkTeamMember(team, memberId);
        assignmentPhaseChecker.checkEvaluation(team.getAssignment());
        if (!evaluationRepository.findByEvaluatorId(evaluator.getId()).isEmpty()) {
            throw new DuplicateException("이미 동료평가를 제출했습니다");
        }

        Map<Long, TeamMember> targetsById = findTargetsById(team, evaluator);
        Set<Long> savedIds = new HashSet<>();
        for (EvaluationDraftSaveRequest.Item item : items) {
            if (!savedIds.add(item.targetTeamMemberId())) {
                throw new InvalidInputException("같은 조원을 두 번 평가했습니다. id=" + item.targetTeamMemberId());
            }
            if (!targetsById.containsKey(item.targetTeamMemberId())) {
                throw new InvalidInputException("평가 대상이 아닌 조원입니다. id=" + item.targetTeamMemberId());
            }
        }

        // JPA는 모아 둔 SQL을 보낼 때 INSERT를 DELETE보다 먼저 보내서, 그냥 지우면 아래 저장이 unique 제약에 걸립니다.
        // deleteAllInBatch는 DELETE를 지금 바로 보냅니다.
        evaluationDraftRepository.deleteAllInBatch(evaluationDraftRepository.findByEvaluatorIdOrderByIdAsc(evaluator.getId()));

        // 한 번에 저장한 내용은 모두 같은 시각으로 기록합니다.
        LocalDateTime savedAt = LocalDateTime.now();
        List<EvaluationDraft> drafts = new ArrayList<>();
        for (EvaluationDraftSaveRequest.Item item : items) {
            drafts.add(new EvaluationDraft(evaluator, targetsById.get(item.targetTeamMemberId()),
                    item.goalSharing(), item.roleFulfillment(), item.encouragement(),
                    item.onlineCommunication(), item.offlineCommunication(), item.progressCheck(),
                    toComment(item.comment()), savedAt));
        }
        return evaluationDraftRepository.saveAll(drafts);
    }

    // 자신을 뺀 조원 전원의 평가를 한 번에 저장합니다. 한 명이라도 빠지거나 이미 제출했으면 아무것도 저장하지 않습니다.
    // 대상 조원마다 값이 여러 개라서 요청 DTO의 Item 목록을 그대로 받습니다. 다른 Service는 Controller가 풀어 준 값을 받습니다.
    // @Transactional: 여러 행을 저장하다 중간에 실패하면 앞에서 저장한 행도 함께 취소됩니다.
    @Transactional
    public List<Evaluation> submit(Long teamId, List<EvaluationSubmitRequest.Item> items, Long memberId) {
        // saveDrafts와 같은 조 행을 잠급니다. 제출과 겹친 임시 저장은 제출이 끝난 뒤 "이미 제출" 검사에 걸립니다.
        Team team = findTeamWithLock(teamId);
        TeamMember evaluator = teamAccessChecker.checkTeamMember(team, memberId);
        assignmentPhaseChecker.checkEvaluation(team.getAssignment());
        if (!evaluationRepository.findByEvaluatorId(evaluator.getId()).isEmpty()) {
            throw new DuplicateException("이미 동료평가를 제출했습니다");
        }

        Map<Long, TeamMember> targetsById = findTargetsById(team, evaluator);
        // 조에 나 혼자면 평가할 사람이 없어서 제출할 것도 없습니다. 아래 "전원 한 번씩" 문구 대신 이유를 그대로 알려 줍니다.
        // 화면은 이때 입력 폼 대신 "평가할 조원이 없습니다."를 보여 주므로 이 요청을 보내지 않습니다.
        if (targetsById.isEmpty()) {
            throw new InvalidInputException("평가할 조원이 없습니다. 조에 자신만 있습니다");
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
        // 조 행을 잠가서 겹친 제출은 위의 "이미 제출했는지" 검사에 걸립니다. DB의 unique 제약은 그래도 빠져나간 경우의 마지막 안전장치입니다.
        // saveAllAndFlush는 INSERT를 지금 바로 보내서 그 오류를 여기서 잡아 409로 바꿀 수 있게 합니다.
        List<Evaluation> saved;
        try {
            saved = evaluationRepository.saveAllAndFlush(evaluations);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateException("이미 동료평가를 제출했습니다");
        }
        // 제출한 뒤에는 임시 저장이 필요 없습니다. 위 저장과 같은 트랜잭션이라 제출이 실패하면 지워지지 않습니다.
        evaluationDraftRepository.deleteAllInBatch(evaluationDraftRepository.findByEvaluatorIdOrderByIdAsc(evaluator.getId()));
        return saved;
    }

    // 평가 대상 = 이 조의 조원 중 나를 뺀 사람. 조원 번호(id)로 바로 찾을 수 있게 Map에 담습니다.
    private Map<Long, TeamMember> findTargetsById(Team team, TeamMember evaluator) {
        Map<Long, TeamMember> targetsById = new HashMap<>();
        for (TeamMember teamMember : teamMemberRepository.findByTeamIdOrderByIdAsc(team.getId())) {
            if (!teamMember.getId().equals(evaluator.getId())) {
                targetsById.put(teamMember.getId(), teamMember);
            }
        }
        return targetsById;
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

    // 조를 찾으면서 그 행을 잠급니다. 잠금은 트랜잭션이 끝나면 풀립니다. 없으면 NotFoundException을 던집니다.
    private Team findTeamWithLock(Long teamId) {
        return teamRepository.findWithLockById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
