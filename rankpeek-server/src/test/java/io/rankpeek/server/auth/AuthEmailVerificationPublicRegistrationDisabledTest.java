package io.rankpeek.server.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.auth.public-registration-enabled=false",
        "rankpeek.auth.email-verification.required=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthEmailVerificationPublicRegistrationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void emailCodeRequestIsRejectedWhenPublicRegistrationIsDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"closed@example.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PUBLIC_REGISTRATION_DISABLED"));
    }
}
