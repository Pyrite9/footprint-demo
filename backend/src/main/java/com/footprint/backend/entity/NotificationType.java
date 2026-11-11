package com.footprint.backend.entity;

// 알림의 종류입니다. 알림이 생기는 사건 하나에 종류 하나입니다.
public enum NotificationType {
    // 교수가 과제의 평가를 시작했습니다. 그 과제의 모든 조원이 받습니다.
    EVALUATION_STARTED,
    // 조장이 기여 항목의 담당으로 정했습니다. 담당 조원이 받습니다.
    CONTRIBUTION_ASSIGNED,
    // 담당 조원이 기여 항목을 완료 보고했습니다. 같은 조의 다른 조원이 받습니다.
    CONTRIBUTION_REPORTED
}
