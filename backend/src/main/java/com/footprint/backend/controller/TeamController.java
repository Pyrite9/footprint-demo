package com.footprint.backend.controller;

import com.footprint.backend.dto.InviteCodeResponse;
import com.footprint.backend.dto.RoleAssignRequest;
import com.footprint.backend.dto.TeamJoinRequest;
import com.footprint.backend.dto.TeamMemberResponse;
import com.footprint.backend.dto.TeamResponse;
import com.footprint.backend.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 조 요청을 받아 Service를 호출하고, 결과를 응답 DTO로 바꿔 돌려줍니다. 권한 검사는 Service가 합니다.
@RestController
@RequestMapping("/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    // 조 정보를 돌려줍니다.
    @GetMapping("/{teamId}")
    public TeamResponse findById(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return TeamResponse.from(teamService.findById(teamId, memberId));
    }

    // 조원 목록을 돌려줍니다.
    @GetMapping("/{teamId}/members")
    public List<TeamMemberResponse> findMembers(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return teamService.findMembers(teamId, memberId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    // 지금 유효한 초대 코드를 돌려줍니다. 조장만 부를 수 있습니다.
    @GetMapping("/{teamId}/invite-code")
    public InviteCodeResponse findInviteCode(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return InviteCodeResponse.from(teamService.findInviteCode(teamId, memberId));
    }

    // 초대 코드를 새로 발급합니다. 조장만 부를 수 있습니다.
    @PostMapping("/{teamId}/invite-code")
    public InviteCodeResponse issueInviteCode(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return InviteCodeResponse.from(teamService.issueInviteCode(teamId, memberId));
    }

    // 조장이 조원에게 역할을 지정하고, 역할이 지정된 조원을 돌려줍니다.
    @PostMapping("/{teamId}/members/{teamMemberId}/role")
    public TeamMemberResponse assignRole(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                         @PathVariable Long teamMemberId, @Valid @RequestBody RoleAssignRequest request) {
        return TeamMemberResponse.from(teamService.assignRole(teamId, teamMemberId, request.roleName(), memberId));
    }

    // 초대 코드로 조에 합류하고 합류한 조를 돌려줍니다.
    // "/join"은 숫자가 아니라서 위의 "/{teamId}" 주소와 섞이지 않습니다(메서드도 POST로 다릅니다).
    @PostMapping("/join")
    public TeamResponse join(@AuthenticationPrincipal Long memberId, @Valid @RequestBody TeamJoinRequest request) {
        return TeamResponse.from(teamService.join(request.inviteCode(), memberId));
    }
}
