package com.suhasan.finance.transaction_service.health;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseHealthIndicatorTest {
    @Test
    void reportsUpAndMasksPassword() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:test?password=secret&mode=safe");
        when(metadata.getDatabaseProductVersion()).thenReturn("15");

        Health health = new DatabaseHealthIndicator(source).health();
        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails().get("url")).isEqualTo("jdbc:test?password=***&mode=safe");
    }

    @Test
    void reportsEachFailureMode() throws Exception {
        DataSource invalidSource = mock(DataSource.class);
        Connection invalid = mock(Connection.class);
        when(invalidSource.getConnection()).thenReturn(invalid);
        when(invalid.isValid(5)).thenReturn(false);
        assertThat(new DatabaseHealthIndicator(invalidSource).health().getStatus()).isEqualTo(Health.Status.DOWN);

        DataSource badQuerySource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(badQuerySource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);
        assertThat(new DatabaseHealthIndicator(badQuerySource).health().getStatus()).isEqualTo(Health.Status.DOWN);

        DataSource failed = mock(DataSource.class);
        when(failed.getConnection()).thenThrow(new IllegalStateException("offline"));
        Health health = new DatabaseHealthIndicator(failed).health();
        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails().get("error")).isEqualTo("offline");
    }
}
