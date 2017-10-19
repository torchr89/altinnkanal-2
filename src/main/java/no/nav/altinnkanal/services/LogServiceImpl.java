package no.nav.altinnkanal.services;

import no.nav.altinnkanal.entities.TopicMappingUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Service
public class LogServiceImpl implements LogService {
    JdbcTemplate jdbcTemplate;

    @Autowired
    public LogServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TopicMappingUpdate logChange(TopicMappingUpdate topicMappingUpdate) throws SQLException {
        /*GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update((con) -> {
            PreparedStatement preparedStatement = con.prepareStatement("INSERT INTO `topic_mapping_log` VALUES(?, ?, ?, ?, ?, ?, ?)");
            preparedStatement.setString(1, topicMappingUpdate.getServiceCode());
            preparedStatement.setString(2, topicMappingUpdate.getServiceEditionCode());
            preparedStatement.setString(3, topicMappingUpdate.getTopic());
            preparedStatement.setBoolean(4, topicMappingUpdate.isEnabled());
            preparedStatement.setString(5, topicMappingUpdate.getComment());
            preparedStatement.setTimestamp(6, Timestamp.valueOf(topicMappingUpdate.getUpdateDate()));
            preparedStatement.setString(7, topicMappingUpdate.getUpdatedBy());
            return preparedStatement;
        }, keyHolder);*/

        jdbcTemplate.update("INSERT INTO `topic_mapping_log`(`service_code`, `service_edition_code`, `topic`, `enabled`, `comment`, `updated_date`, `updated_by`) VALUES(?, ?, ?, ?, ?, ?, ?)",
                topicMappingUpdate.getServiceCode(), topicMappingUpdate.getServiceEditionCode(), topicMappingUpdate.getTopic(), topicMappingUpdate.isEnabled(),
                topicMappingUpdate.getComment(), Timestamp.valueOf(topicMappingUpdate.getUpdateDate()), topicMappingUpdate.getUpdatedBy());

        return getLastChangeFor(topicMappingUpdate.getServiceCode(), topicMappingUpdate.getServiceEditionCode());
    }

    @Override
    public List<TopicMappingUpdate> getChangelog() {
        return jdbcTemplate.query("SELECT * FROM `topic_mapping_log`;", (resultSet, rowCount) -> fromResultSet(resultSet));
    }

    @Override
    public List<TopicMappingUpdate> getUniqueChangelog(Boolean enabled) throws SQLException {
        return jdbcTemplate.query("SELECT * FROM `topic_mapping_log` `log` WHERE `enabled`=? AND `updated_date`=(SELECT MAX(`updated_date`) FROM `topic_mapping_log` WHERE service_code=log.service_code AND service_edition_code=log.service_edition_code);",
                new Object[] { enabled }, (resultSet, rowCount) -> fromResultSet(resultSet));
    }

    @Override
    public List<TopicMappingUpdate> getChangeLogFor(String serviceCode, String serviceEditionCode) throws SQLException {
        return jdbcTemplate.query("SELECT * FROM `topic_mapping_log` WHERE `service_code`=? AND `service_edition_code`=?;",
                new String[] { serviceCode, serviceEditionCode }, (resultSet, rowCount) -> fromResultSet(resultSet));
    }

    @Override
    public TopicMappingUpdate getLastChangeFor(String serviceCode, String serviceEditionCode) throws SQLException {
        return jdbcTemplate.query("SELECT * FROM `topic_mapping_log` WHERE `service_code`=? AND `service_edition_code`=? ORDER BY `updated_date` DESC LIMIT 1;",
                new String[]{ serviceCode, serviceEditionCode }, (resultSet, rowCount) -> fromResultSet(resultSet)).get(0);
    }

    private TopicMappingUpdate fromResultSet(ResultSet resultSet) throws SQLException {
        TopicMappingUpdate topicMappingUpdate = new TopicMappingUpdate(
                resultSet.getString("service_code"),
                resultSet.getString("service_edition_code"),
                resultSet.getString("topic"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("comment"),
                resultSet.getTimestamp("updated_date").toLocalDateTime(),
                resultSet.getString("updated_by")
        );

        topicMappingUpdate.setId(resultSet.getInt("id"));

        return topicMappingUpdate;
    }
}
