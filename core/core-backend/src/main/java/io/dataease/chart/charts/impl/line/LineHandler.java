package io.dataease.chart.charts.impl.line;

import io.dataease.api.dataset.union.DatasetGroupInfoDTO;
import io.dataease.chart.charts.impl.YoyChartHandler;
import io.dataease.chart.utils.ChartDataBuild;
import io.dataease.extensions.datasource.dto.DatasourceRequest;
import io.dataease.extensions.datasource.dto.DatasourceSchemaDTO;
import io.dataease.extensions.datasource.model.SQLMeta;
import io.dataease.extensions.datasource.provider.Provider;
import io.dataease.extensions.view.dto.*;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LineHandler extends YoyChartHandler {
    @Getter
    private String type = "line";

    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        var xAxis = result.getAxisMap().get(ChartAxis.xAxis);
        xAxis.addAll(view.getXAxisExt());
        var yAxis = result.getAxisMap().get(ChartAxis.yAxis);
        yAxis.addAll(view.getExtLabel());
        yAxis.addAll(view.getExtTooltip());
        result.getAxisMap().put(ChartAxis.xAxisExt, view.getXAxisExt());
        result.getAxisMap().put(ChartAxis.extLabel, view.getExtLabel());
        result.getAxisMap().put(ChartAxis.extTooltip, view.getExtTooltip());
        return result;
    }

    @Override
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var xAxisExt = formatResult.getAxisMap().get(ChartAxis.xAxisExt);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var drillAxis = xAxis.stream().filter(axis -> FieldSource.DRILL == axis.getSource()).toList();
        var xAxisBase = xAxis.subList(0, xAxis.size() - xAxisExt.size() - drillAxis.size());
        return ChartDataBuild.transBaseGroupDataAntV(xAxisBase, xAxis, xAxisExt, yAxis, view, data, isDrill);
    }

    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, Provider provider) {
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean crossDs = ((DatasetGroupInfoDTO) formatResult.getContext().get("dataset")).getIsCross();
        var result = (T) super.calcChartResult(view, formatResult, filterResult, sqlMap, sqlMeta, provider);
        try {
            //如果有同环比过滤,应该用原始sql
            var originSql = result.getQuerySql();
            var dynamicAssistFields = getDynamicAssistFields(view);
            var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
            var assistFields = getAssistFields(dynamicAssistFields, yAxis);
            if (CollectionUtils.isNotEmpty(assistFields)) {
                var req = new DatasourceRequest();
                req.setIsCross(crossDs);
                req.setDsList(dsMap);

                List<ChartSeniorAssistDTO> assists = dynamicAssistFields.stream().filter(ele -> !StringUtils.equalsIgnoreCase(ele.getSummary(), "last_item")).toList();
                if (ObjectUtils.isNotEmpty(assists)) {
                    var assistSql = assistSQL(originSql, assistFields, dsMap, crossDs);
                    req.setQuery(assistSql);
                    logger.debug("calcite assistSql sql: " + assistSql);
                    var assistData = (List<String[]>) provider.fetchResultField(req).get("data");
                    result.setAssistData(assistData);
                    result.setDynamicAssistFields(assists);
                }

                List<ChartSeniorAssistDTO> assistsOriginList = dynamicAssistFields.stream().filter(ele -> StringUtils.equalsIgnoreCase(ele.getSummary(), "last_item")).toList();
                if (ObjectUtils.isNotEmpty(assistsOriginList)) {
                    var assistSqlOriginList = assistSQLOriginList(originSql, assistFields, dsMap, crossDs);
                    req.setQuery(assistSqlOriginList);
                    logger.debug("calcite assistSql sql origin list: " + assistSqlOriginList);
                    var assistDataOriginList = (List<String[]>) provider.fetchResultField(req).get("data");
                    result.setAssistDataOriginList(assistDataOriginList);
                    result.setDynamicAssistFieldsOriginList(assistsOriginList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
