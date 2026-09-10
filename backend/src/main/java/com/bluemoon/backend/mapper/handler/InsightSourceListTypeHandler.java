package com.bluemoon.backend.mapper.handler;

import com.bluemoon.backend.domain.insight.AiInsight;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * AI_INSIGHTS.sources(CLOB, JSON) <-> List&lt;AiInsight.InsightSource&gt; 변환.
 * Oracle 네이티브 JSON 타입 대신 이식성이 높은 CLOB CHECK(sources IS JSON)을 사용하므로 문자열 직렬화로 처리한다.
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.CLOB)
public class InsightSourceListTypeHandler extends BaseTypeHandler<List<AiInsight.InsightSource>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final CollectionType SOURCE_LIST_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructCollectionType(List.class, AiInsight.InsightSource.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int columnIndex, List<AiInsight.InsightSource> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(columnIndex, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("sources JSON 직렬화 실패", e);
        }
    }

    @Override
    public List<AiInsight.InsightSource> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<AiInsight.InsightSource> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<AiInsight.InsightSource> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<AiInsight.InsightSource> parse(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, SOURCE_LIST_TYPE);
        } catch (Exception e) {
            throw new SQLException("sources JSON 역직렬화 실패", e);
        }
    }
}
