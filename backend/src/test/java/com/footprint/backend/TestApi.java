package com.footprint.backend;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 통합 테스트에서 로그인과 요청 보내기를 짧게 쓰게 해 주는 도우미입니다.
// 공통 부모 클래스를 쓰지 않는 규칙이라, 테스트 클래스가 필드로 갖고 호출합니다.
class TestApi {

    // 시작 데이터의 계정 비밀번호입니다(config/DataInitializer).
    private static final String DEV_PASSWORD = "pass1234";

    private final MockMvc mockMvc;

    TestApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // 로그인하고 token을 돌려줍니다.
    String login(String email) throws Exception {
        String body = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, DEV_PASSWORD);
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    // token을 붙여 GET 요청을 보냅니다.
    ResultActions get(String path, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + token));
    }

    // token 없이 GET 요청을 보냅니다.
    ResultActions getWithoutToken(String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path));
    }

    // token을 붙여 JSON body가 있는 POST 요청을 보냅니다. body가 없는 요청은 "{}"가 아니라 빈 문자열을 넘깁니다.
    ResultActions post(String path, String token, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // token을 붙여 JSON body가 있는 PUT 요청을 보냅니다.
    ResultActions put(String path, String token, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // token을 붙여 DELETE 요청을 보냅니다.
    ResultActions delete(String path, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete(path)
                .header("Authorization", "Bearer " + token));
    }

    // token을 붙여 파일 하나를 multipart/form-data로 올립니다. file이 null이면 파일 칸 없이 보냅니다.
    ResultActions upload(String path, String token, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart(path);
        if (file != null) {
            request.file(file);
        }
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    // 오류 응답의 상태 코드와 code를 함께 확인합니다.
    static void expectError(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }
}
