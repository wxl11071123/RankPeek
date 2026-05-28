package io.rankpeek.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesPasswordResetPageForTokenPath() throws Exception {
        mockMvc.perform(get("/password-reset/reset-token-value"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("RankPeek 密码重置")))
                .andExpect(content().string(containsString("id=\"confirmInput\"")))
                .andExpect(content().string(containsString("class=\"password-toggle\"")))
                .andExpect(content().string(containsString("/api/auth/password-reset/confirm")));
    }
}
