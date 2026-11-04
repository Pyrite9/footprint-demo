package com.footprint.backend.service;

import com.footprint.backend.entity.Evaluation;
import com.footprint.backend.entity.TeamMember;

import java.util.List;

// 제출 현황에 쓸 Entity 묶음입니다. 조원 전원과 그 조에서 제출한 평가입니다. Service는 Entity를 돌려주는 규칙이라 묶음으로 돌려줍니다.
public record TeamEvaluations(List<TeamMember> teamMembers, List<Evaluation> evaluations) { }
