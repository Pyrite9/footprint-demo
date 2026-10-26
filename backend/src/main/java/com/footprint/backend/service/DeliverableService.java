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

// 산출물을 올리고, 목록을 보고, 파일 본문을 꺼냅니다. 파일 본문은 StorageService(MinIO)가 맡습니다.
@Service
public class DeliverableService {

    private final DeliverableRepository deliverableRepository;
    private final TeamRepository teamRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final StorageService storageService;

    public DeliverableService(DeliverableRepository deliverableRepository, TeamRepository teamRepository,
                              TeamAccessChecker teamAccessChecker, StorageService storageService) {
        this.deliverableRepository = deliverableRepository;
        this.teamRepository = teamRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.storageService = storageService;
    }

    // 역할이 배정된 조원이 파일을 올립니다. 올린 사람의 지금 역할명을 산출물에 복사해서 저장합니다.
    // 순서: 파일 본문을 MinIO에 먼저 저장하고, 성공하면 데이터베이스에 저장합니다.
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

        String storageKey = storageService.save(file);
        // 브라우저가 파일 이름을 보내지 않는 경우는 거의 없지만, 비어 있으면 "file"로 둡니다.
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "file" : file.getOriginalFilename();
        return deliverableRepository.save(new Deliverable(uploader, uploader.getRoleName(), fileName, file.getSize(), storageKey));
    }

    // 조의 산출물을 최신순으로 찾습니다. 그 조의 조원과 그 과제를 만든 교수가 볼 수 있습니다.
    public List<Deliverable> findAll(Long teamId, Long memberId) {
        Team team = findTeam(teamId);
        teamAccessChecker.checkTeamAccess(team, memberId);
        return deliverableRepository.findByTeamMemberTeamIdOrderByIdDesc(teamId);
    }

    // 다운로드할 산출물을 찾습니다. 권한은 findAll과 같습니다.
    public Deliverable findForDownload(Long deliverableId, Long memberId) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new NotFoundException("산출물이 없습니다. id=" + deliverableId));
        teamAccessChecker.checkTeamAccess(deliverable.getTeamMember().getTeam(), memberId);
        return deliverable;
    }

    // 산출물의 파일 본문을 MinIO에서 꺼냅니다. MinIO에 본문이 없으면 NotFoundException입니다.
    public Resource loadFile(Deliverable deliverable) {
        return storageService.load(deliverable.getStorageKey());
    }

    // 조를 찾습니다. 없으면 NotFoundException을 던집니다.
    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("조가 없습니다. id=" + teamId));
    }
}
