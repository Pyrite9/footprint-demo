package com.footprint.backend.service;

import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.Team;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.exception.ForbiddenException;
import com.footprint.backend.exception.InvalidInputException;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.TeamRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 산출물을 올리고, 목록을 보고, 파일 본문을 꺼내고, 삭제합니다. 파일 본문은 StorageService(MinIO)가 맡습니다.
@Service
public class DeliverableService {

    private final DeliverableRepository deliverableRepository;
    private final TeamRepository teamRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;
    private final StorageService storageService;

    public DeliverableService(DeliverableRepository deliverableRepository, TeamRepository teamRepository,
                              TeamAccessChecker teamAccessChecker, AssignmentPhaseChecker assignmentPhaseChecker,
                              StorageService storageService) {
        this.deliverableRepository = deliverableRepository;
        this.teamRepository = teamRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
        this.storageService = storageService;
    }

    // 역할이 배정된 조원이 파일을 올립니다. 올린 사람의 지금 역할명을 산출물에 복사해서 저장합니다.
    // 순서: 파일 본문을 MinIO에 먼저 저장하고, 성공하면 데이터베이스에 저장합니다. 데이터베이스 저장이 실패하면 올린 파일을 지웁니다.
    @Transactional
    public Deliverable upload(Long teamId, MultipartFile file, Long memberId) {
        // 빈 파일 검사를 권한 검사보다 먼저 합니다. 파일이 없거나 20MB를 넘는 요청도 Spring이 Service 전에 400으로 막기 때문입니다.
        if (file.isEmpty()) {
            throw new InvalidInputException("빈 파일은 올릴 수 없습니다");
        }
        Team team = findTeam(teamId);
        // 조원만 올립니다. 교수는 이 조의 조원이 아니라서 여기서 막힙니다.
        TeamMember uploader = teamAccessChecker.checkTeamMember(team, memberId);
        if (uploader.getRoleName() == null) {
            throw new ForbiddenException("역할이 배정된 조원만 올릴 수 있습니다");
        }
        assignmentPhaseChecker.checkRecording(team.getAssignment());

        String storageKey = storageService.save(file);
        // 브라우저가 파일 이름을 보내지 않는 경우는 거의 없지만, 비어 있으면 "file"로 둡니다.
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "file" : file.getOriginalFilename();
        try {
            // saveAndFlush: 저장 오류가 여기서 바로 나도록 합니다. 그래야 아래 catch가 받습니다.
            return deliverableRepository.saveAndFlush(new Deliverable(uploader, uploader.getRoleName(), fileName, file.getSize(), storageKey));
        } catch (RuntimeException e) {
            deleteOrphanFile(storageKey, e);
            throw e;
        }
    }

    // 올린 조원이 산출물을 삭제합니다. MinIO의 파일 본문만 지우고 데이터베이스 행은 삭제 시각과 함께 남깁니다. 그래서 순번이 끊기지 않습니다.
    @Transactional
    public Deliverable delete(Long deliverableId, Long memberId) {
        Deliverable deliverable = findDeliverable(deliverableId);
        teamAccessChecker.checkUploader(deliverable, memberId);
        assignmentPhaseChecker.checkRecording(deliverable.getTeamMember().getTeam().getAssignment());
        if (deliverable.isDeleted()) {
            throw new InvalidInputException("이미 삭제된 산출물입니다");
        }

        deliverable.markDeleted();
        // 파일을 지우면 되돌릴 수 없어서 데이터베이스에 먼저 반영합니다. 파일 지우기가 실패하면 예외로 트랜잭션이 되돌아가 삭제 표시도 사라집니다.
        deliverableRepository.flush();
        storageService.delete(deliverable.getStorageKey());
        return deliverable;
    }

    // 조의 산출물을 최신순으로 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<Deliverable> findAll(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkTeamAccess(team, memberId);
        return deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(teamId);
    }

    // 다운로드할 산출물을 찾습니다. 권한은 findAll과 같습니다. 삭제된 산출물은 파일 본문이 없어서 NotFoundException을 던집니다.
    public Deliverable findForDownload(Long deliverableId, Long memberId) {
        Deliverable deliverable = findDeliverable(deliverableId);
        teamAccessChecker.checkTeamAccess(deliverable.getTeamMember().getTeam(), memberId);
        if (deliverable.isDeleted()) {
            throw new NotFoundException("삭제된 산출물입니다. id=" + deliverableId);
        }
        return deliverable;
    }

    // 산출물의 파일 본문을 MinIO에서 꺼냅니다. MinIO에 본문이 없으면 NotFoundException입니다.
    public Resource loadFile(Deliverable deliverable) {
        return storageService.load(deliverable.getStorageKey());
    }

    // 데이터베이스에 저장하지 못한 파일을 지웁니다. 지우기도 실패하면 원래 오류(cause)에 함께 담아 서버 로그에 남깁니다.
    private void deleteOrphanFile(String storageKey, RuntimeException cause) {
        try {
            storageService.delete(storageKey);
        } catch (RuntimeException deleteFailure) {
            cause.addSuppressed(deleteFailure);
        }
    }

    // 산출물을 찾습니다. 없으면 NotFoundException을 던집니다.
    private Deliverable findDeliverable(Long deliverableId) {
        return deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new NotFoundException("산출물이 없습니다. id=" + deliverableId));
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
