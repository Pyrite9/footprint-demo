package com.footprint.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 동료평가를 제출할 때 보내는 요청 body의 모양입니다. 대상 조원 전원의 평가를 한 번에 보냅니다.
// @Valid: 목록 안의 평가 하나하나에도 아래 검사(@NotNull, @Min, @Max)를 적용하라는 뜻입니다. 빠지면 안쪽은 검사하지 않습니다.
// List<> 꺾쇠 안에 붙인 검사는 목록의 원소 하나하나에 붙습니다. @NotNull이 없으면 [null]이 검사를 통과해 500이 납니다.
public record EvaluationSubmitRequest(
    @NotEmpty List<@NotNull @Valid Item> evaluations
) {

    // 대상 조원 한 명에 대한 평가입니다. 6개 항목 점수는 모두 1~5로 채워야 합니다. int가 아니라 Integer라서 빠지면 null이 되어 @NotNull에 걸립니다.
    public record Item(
        @NotNull Long targetTeamMemberId,
        @NotNull @Min(1) @Max(5) Integer goalSharing,
        @NotNull @Min(1) @Max(5) Integer roleFulfillment,
        @NotNull @Min(1) @Max(5) Integer encouragement,
        @NotNull @Min(1) @Max(5) Integer onlineCommunication,
        @NotNull @Min(1) @Max(5) Integer offlineCommunication,
        @NotNull @Min(1) @Max(5) Integer progressCheck,
        @Size(max = 2000) String comment
    ) { }
}
