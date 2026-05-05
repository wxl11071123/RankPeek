package io.rankpeek.server.common;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;

public final class JdbcSupport {

    private JdbcSupport() {
    }

    public static Long requireGeneratedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return a generated id");
        }
        return key.longValue();
    }

    public static KeyHolder newKeyHolder() {
        return new GeneratedKeyHolder();
    }

    public static PreparedStatement prepareInsert(java.sql.Connection connection, String sql, Object... values)
            throws java.sql.SQLException {
        PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
        return statement;
    }
}
