package io.rankpeek.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.auth.initial-admin.enabled=true",
        "rankpeek.auth.initial-admin.email=admin@rankpeek.local",
        "rankpeek.auth.initial-admin.password=Secret123!",
        "rankpeek.auth.initial-admin.display-name=RankPeek Admin"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAdminBootstrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Test
    void configuredInitialAdminCanLoginAsAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ADMIN@RANKPEEK.LOCAL", "Secret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("admin@rankpeek.local"))
                .andExpect(jsonPath("$.data.user.displayName").value("RankPeek Admin"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())));
    }

    @Test
    void configuredInitialAdminUpdatesExistingAccountToAdmin() {
        AuthUser user = authRepository.findUserByEmail("admin@rankpeek.local").orElseThrow();

        org.assertj.core.api.Assertions.assertThat(user.role()).isEqualTo("ADMIN");
        org.assertj.core.api.Assertions.assertThat(user.status()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(user.displayName()).isEqualTo("RankPeek Admin");
        org.assertj.core.api.Assertions.assertThat(passwordService.matches("Secret123!", user.passwordHash())).isTrue();
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
