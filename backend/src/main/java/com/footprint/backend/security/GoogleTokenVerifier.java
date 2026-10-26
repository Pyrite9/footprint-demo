package com.footprint.backend.security;

import com.footprint.backend.exception.UnauthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

// 화면이 보낸 구글 ID token이 진짜인지 확인하고, 구글이 확인해 준 계정 정보를 꺼냅니다.
// 어느 도메인을 받을지, 교수인지 같은 우리 서비스의 규칙은 여기가 아니라 AuthService가 정합니다.
@Component
public class GoogleTokenVerifier {

    // 무엇이 틀렸는지 구분하지 않고 같은 메시지를 줍니다. 로그인 API와 같은 방식입니다.
    private static final String VERIFY_FAILED_MESSAGE = "구글 로그인을 확인하지 못했습니다";

    private final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    private final GoogleIdTokenVerifier verifier;

    // clientId는 Google Cloud에서 만든 OAuth Client ID입니다. 다른 앱용으로 발급된 token을 막는 데 씁니다.
    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        // 구글 공개키는 처음 검증할 때 인터넷으로 받아 오고, 구글이 정한 시간 동안 보관했다가 다시 씁니다.
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), jsonFactory)
                .setAudience(List.of(clientId))
                .build();
    }

    // ID token을 검증하고 계정 정보를 돌려줍니다. 진짜가 아니면 UnauthorizedException을 던집니다.
    public GoogleAccount verify(String credential) {
        GoogleIdToken idToken = parse(credential);
        if (!isValid(idToken)) {
            throw new UnauthorizedException(VERIFY_FAILED_MESSAGE);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        // 구글이 "이 이메일의 주인이 맞다"고 확인한 계정만 받습니다. 이메일로 회원을 찾기 때문입니다.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new UnauthorizedException(VERIFY_FAILED_MESSAGE);
        }
        // 이름을 공개하지 않은 계정이면 이메일을 이름 대신 씁니다. 회원의 name은 비울 수 없습니다.
        String name = (String) payload.get("name");
        if (name == null) {
            name = payload.getEmail();
        }
        return new GoogleAccount(payload.getEmail(), name, payload.getHostedDomain());
    }

    // token 문자열을 header·payload·서명으로 나눠 읽습니다. 모양이 깨졌으면 UnauthorizedException입니다.
    private GoogleIdToken parse(String credential) {
        try {
            return GoogleIdToken.parse(jsonFactory, credential);
        } catch (IOException | IllegalArgumentException e) {
            throw new UnauthorizedException(VERIFY_FAILED_MESSAGE);
        }
    }

    // 서명(구글 공개키), 만료 시각, 발급자(accounts.google.com), 받는 앱(aud = Client ID)을 확인합니다.
    private boolean isValid(GoogleIdToken idToken) {
        try {
            return verifier.verify(idToken);
        } catch (GeneralSecurityException e) {
            return false;
        } catch (IOException e) {
            // 구글 공개키를 받아 오지 못한 경우(서버의 인터넷 끊김)입니다. 사용자의 잘못이 아니라서 500으로 나가게 둡니다.
            throw new IllegalStateException("구글 공개키를 받아 오지 못했습니다", e);
        }
    }

    // 구글이 확인해 준 계정 정보입니다. hostedDomain(hd)은 학교·회사 계정에만 있고, 개인 Gmail 계정이면 null입니다.
    public record GoogleAccount(String email, String name, String hostedDomain) { }
}
