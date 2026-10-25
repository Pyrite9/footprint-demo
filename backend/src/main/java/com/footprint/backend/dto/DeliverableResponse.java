package com.footprint.backend.dto;

import com.footprint.backend.entity.Deliverable;

import java.time.LocalDateTime;

// 산출물 한 건의 모양입니다. 올린 조원의 이름을 펼쳐서 담습니다. 저장 위치(storageKey)는 내보내지 않습니다.
public record DeliverableResponse(Long id, String roleName, String fileName, long fileSize,
                                  String uploaderName, LocalDateTime uploadedAt) {

    // 산출물 Entity를 응답 DTO로 바꿉니다.
    public static DeliverableResponse from(Deliverable deliverable) {
        return new DeliverableResponse(deliverable.getId(), deliverable.getRoleName(), deliverable.getFileName(),
                deliverable.getFileSize(), deliverable.getTeamMember().getMember().getName(), deliverable.getUploadedAt());
    }
}
