package com.footprint.backend.dto;

import com.footprint.backend.entity.Evaluation;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 평가 결과를 CSV 파일 내용으로 바꿉니다. 합계, 평균, 순위 열은 두지 않습니다.
public final class EvaluationCsv {

    // Excel은 이 글자(BOM)가 맨 앞에 있어야 파일을 UTF-8로 읽어서 한글이 깨지지 않습니다.
    private static final String BOM = "﻿";
    private static final String LINE_END = "\r\n";
    private static final String HEADER = "평가자,대상,목표 공유·협업,역할 수행,격려·도움,온라인 소통,대면 소통,진행 점검·보충,의견,제출 시각";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private EvaluationCsv() {
    }

    // 평가 목록을 제목 줄과 함께 CSV 내용으로 바꿉니다. 평가가 없으면 제목 줄만 있습니다.
    public static byte[] from(List<Evaluation> evaluations) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(LINE_END);
        for (Evaluation evaluation : evaluations) {
            csv.append(String.join(",",
                    textCell(evaluation.getEvaluator().getMember().getName()),
                    textCell(evaluation.getTarget().getMember().getName()),
                    String.valueOf(evaluation.getGoalSharing()),
                    String.valueOf(evaluation.getRoleFulfillment()),
                    String.valueOf(evaluation.getEncouragement()),
                    String.valueOf(evaluation.getOnlineCommunication()),
                    String.valueOf(evaluation.getOfflineCommunication()),
                    String.valueOf(evaluation.getProgressCheck()),
                    textCell(evaluation.getComment()),
                    evaluation.getSubmittedAt().format(TIME_FORMAT)));
            csv.append(LINE_END);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // 사람이 쓴 글자를 칸 하나로 바꿉니다. 빈 의견은 빈 칸입니다.
    private static String textCell(String text) {
        if (text == null) {
            return "";
        }
        // =, +, -, @로 시작하면 Excel이 수식으로 실행합니다. 앞에 '를 붙이면 글자로 읽습니다.
        String cell = text;
        if (text.startsWith("=") || text.startsWith("+") || text.startsWith("-") || text.startsWith("@")) {
            cell = "'" + text;
        }
        // 쉼표, 큰따옴표, 줄바꿈이 있으면 칸이 나뉘지 않게 큰따옴표로 감쌉니다. 안의 큰따옴표는 두 번 씁니다.
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
