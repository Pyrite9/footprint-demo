package com.footprint.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 동료평가를 임시 저장할 때 보내는 요청 body의 모양입니다. 지금까지 입력한 내용을 한 번에 보냅니다. 조원 일부만 보내도 됩니다.
// List<> 꺾쇠 안에 붙인 검사는 목록의 원소 하나하나에 붙습니다. @NotNull이 없으면 [null]이 검사를 통과해 500이 납니다.
public record EvaluationDraftSaveRequest(
    @NotNull List<@NotNull @Valid Item> evaluations
) {

    // 대상 조원 한 명의 임시 저장입니다. 제출과 달리 점수에 @NotNull이 없어서 빠져도 됩니다. @Min, @Max는 값이 null이면 통과합니다.
    public record Item(
        @NotNull Long targetTeamMemberId,
        @Min(1) @Max(5) Integer goalSharing,
        @Min(1) @Max(5) Integer roleFulfillment,
        @Min(1) @Max(5) Integer encouragement,
        @Min(1) @Max(5) Integer onlineCommunication,
        @Min(1) @Max(5) Integer offlineCommunication,
        @Min(1) @Max(5) Integer progressCheck,
        @Size(max = 2000) String comment
    ) { }
}
