package com.footprint.backend.dto;

import com.footprint.backend.entity.Confirmation;
import com.footprint.backend.entity.Contribution;
import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.entity.LeaderHistory;
import com.footprint.backend.entity.RoleHistory;
import com.footprint.backend.entity.TeamMember;
import com.footprint.backend.service.TeamRecords;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 기록 열람의 한 줄입니다. 값이 없는 칸은 null입니다. 건수나 점수는 담지 않습니다.
// teamMemberId: 이 일을 한(맡은) 조원입니다. target: 확인받은 기록의 주인 또는 이전 조장입니다.
public record RecordResponse(String type, LocalDateTime occurredAt, Long teamMemberId, String memberName,
                             Long targetTeamMemberId, String targetMemberName, String title, String roleName,
                             String previousRoleName, Long deliverableId, Long contributionId) {

    // 조의 기록을 한 목록으로 펼쳐 최근 것부터 놓습니다. 시각이 같은 줄은 나중에 일어났을 일이 위에 오도록 넣는 순서를 정했습니다.
    public static List<RecordResponse> listFrom(TeamRecords records) {
        List<RecordResponse> rows = new ArrayList<>();
        if (records.evaluationStartedAt() != null) {
            rows.add(new RecordResponse("EVALUATION_STARTED", records.evaluationStartedAt(), null, null, null, null, null, null, null, null, null));
        }
        for (LeaderHistory history : records.leaderHistories()) {
            rows.add(leaderRow(history));
        }
        for (Confirmation confirmation : records.confirmations()) {
            addConfirmationRows(rows, confirmation);
        }
        for (Contribution contribution : records.contributions()) {
            addContributionRows(rows, contribution);
        }
        for (Deliverable deliverable : records.deliverables()) {
            addDeliverableRows(rows, deliverable);
        }
        for (TeamMember teamMember : records.teamMembers()) {
            addRoleRows(rows, teamMember, records.roleHistories());
        }
        // sort는 시각이 같은 줄의 순서를 바꾸지 않습니다.
        rows.sort(Comparator.comparing(RecordResponse::occurredAt).reversed());
        return rows;
    }

    // 조장 교체 한 건입니다. 새 조장을 teamMemberId에, 이전 조장을 target에 담습니다.
    private static RecordResponse leaderRow(LeaderHistory history) {
        TeamMember newLeader = history.getNewLeader();
        TeamMember previousLeader = history.getPreviousLeader();
        return new RecordResponse("LEADER_CHANGED", history.getChangedAt(), newLeader.getId(), newLeader.getMember().getName(),
                previousLeader.getId(), previousLeader.getMember().getName(), null, null, null, null, null);
    }

    // 완료확인 한 건을 줄로 바꿉니다. 취소했으면 취소 줄도 더합니다.
    private static void addConfirmationRows(List<RecordResponse> rows, Confirmation confirmation) {
        if (confirmation.getCanceledAt() != null) {
            rows.add(confirmationRow("CONFIRMATION_CANCELED", confirmation.getCanceledAt(), confirmation));
        }
        rows.add(confirmationRow("CONFIRMATION_CREATED", confirmation.getConfirmedAt(), confirmation));
    }

    // 완료확인 줄입니다. 확인한 조원을 teamMemberId에, 산출물을 올린 조원이나 항목의 담당 조원을 target에 담습니다.
    private static RecordResponse confirmationRow(String type, LocalDateTime occurredAt, Confirmation confirmation) {
        TeamMember confirmer = confirmation.getConfirmer();
        Deliverable deliverable = confirmation.getDeliverable();
        Contribution contribution = confirmation.getContribution();
        TeamMember target = deliverable != null ? deliverable.getTeamMember() : contribution.getAssignee();
        String title = deliverable != null ? deliverable.getFileName() : contribution.getTitle();
        Long deliverableId = deliverable != null ? deliverable.getId() : null;
        Long contributionId = contribution != null ? contribution.getId() : null;
        return new RecordResponse(type, occurredAt, confirmer.getId(), confirmer.getMember().getName(),
                target.getId(), target.getMember().getName(), title, null, null, deliverableId, contributionId);
    }

    // 기여 항목 한 건을 줄로 바꿉니다. 취소와 보고가 있으면 그 줄도 더합니다.
    private static void addContributionRows(List<RecordResponse> rows, Contribution contribution) {
        if (contribution.getCanceledAt() != null) {
            rows.add(contributionRow("CONTRIBUTION_CANCELED", contribution.getCanceledAt(), contribution));
        }
        if (contribution.getReportedAt() != null) {
            rows.add(contributionRow("CONTRIBUTION_REPORTED", contribution.getReportedAt(), contribution));
        }
        rows.add(contributionRow("CONTRIBUTION_CREATED", contribution.getCreatedAt(), contribution));
    }

    // 기여 항목 줄입니다. 등록과 취소는 조장이 하지만 누가 했는지 저장하지 않으므로, teamMemberId는 언제나 담당 조원입니다.
    private static RecordResponse contributionRow(String type, LocalDateTime occurredAt, Contribution contribution) {
        TeamMember assignee = contribution.getAssignee();
        return new RecordResponse(type, occurredAt, assignee.getId(), assignee.getMember().getName(),
                null, null, contribution.getTitle(), null, null, null, contribution.getId());
    }

    // 산출물 한 건을 줄로 바꿉니다. 삭제했으면 삭제 줄도 더합니다.
    private static void addDeliverableRows(List<RecordResponse> rows, Deliverable deliverable) {
        if (deliverable.getDeletedAt() != null) {
            rows.add(deliverableRow("DELIVERABLE_DELETED", deliverable.getDeletedAt(), deliverable));
        }
        rows.add(deliverableRow("DELIVERABLE_UPLOADED", deliverable.getUploadedAt(), deliverable));
    }

    // 산출물 줄입니다. roleName은 올릴 때 복사해 둔 역할명입니다.
    private static RecordResponse deliverableRow(String type, LocalDateTime occurredAt, Deliverable deliverable) {
        TeamMember uploader = deliverable.getTeamMember();
        return new RecordResponse(type, occurredAt, uploader.getId(), uploader.getMember().getName(),
                null, null, deliverable.getFileName(), deliverable.getRoleName(), null, deliverable.getId(), null);
    }

    // 한 조원의 역할 변경과 첫 지정을 줄로 바꿉니다. 이력은 최근 것부터이므로, 지금 역할에서 시작해 한 칸씩 과거로 갑니다.
    private static void addRoleRows(List<RecordResponse> rows, TeamMember teamMember, List<RoleHistory> roleHistories) {
        String roleName = teamMember.getRoleName();
        // 역할을 바꾸면 TeamMember의 지정 시각이 새 값으로 바뀌므로, 첫 지정 시각은 가장 오래된 이력에서 읽습니다.
        LocalDateTime assignedAt = teamMember.getRoleAssignedAt();
        for (RoleHistory history : roleHistories) {
            if (history.getTeamMember().getId().equals(teamMember.getId())) {
                rows.add(roleRow("ROLE_CHANGED", history.getChangedAt(), teamMember, roleName, history.getPreviousRoleName()));
                roleName = history.getPreviousRoleName();
                assignedAt = history.getPreviousRoleAssignedAt();
            }
        }
        if (roleName != null) {
            rows.add(roleRow("ROLE_ASSIGNED", assignedAt, teamMember, roleName, null));
        }
    }

    // 역할 줄입니다.
    private static RecordResponse roleRow(String type, LocalDateTime occurredAt, TeamMember teamMember, String roleName, String previousRoleName) {
        return new RecordResponse(type, occurredAt, teamMember.getId(), teamMember.getMember().getName(),
                null, null, null, roleName, previousRoleName, null, null);
    }
}
