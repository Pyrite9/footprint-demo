package com.footprint.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 교수가 과제를 만들 때 보내는 요청 body의 모양입니다. 조 개수는 leaderEmails의 길이입니다.
public record AssignmentCreateRequest(
    // title 열은 varchar(255)입니다. 더 긴 값은 DB 저장에서 500이 되므로 여기서 400으로 막습니다.
    @NotBlank @Size(max = 255) String title,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    // @NotEmpty는 null과 빈 배열([])을 모두 거절합니다. 조가 하나도 없는 과제는 만들 수 없습니다.
    @NotEmpty List<String> leaderEmails
) { }
