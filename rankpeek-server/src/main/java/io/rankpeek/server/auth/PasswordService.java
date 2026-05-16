package io.rankpeek.server.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String password) {
        return encoder.encode(password);
    }

    public boolean matches(String password, String passwordHash) {
        return encoder.matches(password, passwordHash);
    }
}
