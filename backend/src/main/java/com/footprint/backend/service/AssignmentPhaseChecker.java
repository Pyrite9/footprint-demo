package com.footprint.backend.service;

import com.footprint.backend.entity.Assignment;
import com.footprint.backend.entity.AssignmentPhase;
import com.footprint.backend.exception.InvalidInputException;
import org.springframework.stereotype.Component;

// 지금 과제 단계에서 할 수 있는 요청인지 확인합니다. 단계 검사는 모두 이 클래스 한 곳에 모읍니다.
// 누가 요청했는지는 TeamAccessChecker가 봅니다. Service는 권한을 먼저 확인하고 그다음 이 클래스를 부릅니다.
@Component
public class AssignmentPhaseChecker {

    // 기록 단계인지 확인합니다. 조 운영과 과정 기록을 바꾸는 요청에 씁니다.
    public void checkRecording(Assignment assignment) {
        if (assignment.getPhase() != AssignmentPhase.RECORDING) {
            throw new InvalidInputException("평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다");
        }
    }

    // 평가 단계인지 확인합니다. 동료평가를 입력하는 요청에 씁니다.
    public void checkEvaluation(Assignment assignment) {
        if (assignment.getPhase() != AssignmentPhase.EVALUATION) {
            throw new InvalidInputException("동료평가는 평가 단계에서만 입력할 수 있습니다");
        }
    }

    // 평가 단계인지 확인합니다. 조원이 근거 패널을 볼 때 씁니다. 교수는 이 검사를 거치지 않습니다.
    public void checkEvidenceOpen(Assignment assignment) {
        if (assignment.getPhase() != AssignmentPhase.EVALUATION) {
            throw new InvalidInputException("근거 패널은 평가 단계에서 볼 수 있습니다");
        }
    }
}
