package io.dataease.chart.manage;

import com.fasterxml.jackson.core.type.TypeReference;
import io.dataease.dataset.dao.auto.entity.CoreDatasetTableField;
import io.dataease.dataset.dao.auto.mapper.CoreDatasetTableFieldMapper;
import io.dataease.engine.utils.SQLUtils;
import io.dataease.extensions.datasource.dto.CalParam;
import io.dataease.extensions.datasource.dto.DatasetTableFieldDTO;
import io.dataease.extensions.datasource.dto.FieldGroupDTO;
import io.dataease.extensions.view.filter.FilterTreeItem;
import io.dataease.extensions.view.filter.FilterTreeObj;
import io.dataease.utils.BeanUtils;
import io.dataease.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author Junjun
 */
@Service
public class ChartFilterTreeService {
    @Resource
    private CoreDatasetTableFieldMapper coreDatasetTableFieldMapper;

    public void searchFieldAndSet(FilterTreeObj tree) {
        if (ObjectUtils.isNotEmpty(tree)) {
            if (ObjectUtils.isNotEmpty(tree.getItems())) {
                for (FilterTreeItem item : tree.getItems()) {
                    if (ObjectUtils.isNotEmpty(item)) {
                        if (StringUtils.equalsIgnoreCase(item.getType(), "item") || ObjectUtils.isEmpty(item.getSubTree())) {
                            CoreDatasetTableField coreDatasetTableField = coreDatasetTableFieldMapper.selectById(item.getFieldId());
                            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
                            BeanUtils.copyBean(dto, coreDatasetTableField);
                            if (StringUtils.isNotEmpty(coreDatasetTableField.getParams())) {
                                TypeReference<List<CalParam>> tokenType = new TypeReference<>() {
                                };
                                List<CalParam> calParams = JsonUtil.parseList(coreDatasetTableField.getParams(), tokenType);
                                dto.setParams(calParams);
                            }
                            if (StringUtils.isNotEmpty(coreDatasetTableField.getGroupList())) {
                                TypeReference<List<FieldGroupDTO>> groupTokenType = new TypeReference<>() {
                                };
                                List<FieldGroupDTO> fieldGroups = JsonUtil.parseList(coreDatasetTableField.getGroupList(), groupTokenType);
                                dto.setGroupList(fieldGroups);
                            }
                            item.setField(dto);
                        } else if (StringUtils.equalsIgnoreCase(item.getType(), "tree") || (ObjectUtils.isNotEmpty(item.getSubTree()) && StringUtils.isNotEmpty(item.getSubTree().getLogic()))) {
                            searchFieldAndSet(item.getSubTree());
                        }
                    }
                }
            }
        }
    }

    public FilterTreeObj charReplace(FilterTreeObj tree) {
        if (ObjectUtils.isNotEmpty(tree)) {
            if (ObjectUtils.isNotEmpty(tree.getItems())) {
                for (FilterTreeItem item : tree.getItems()) {
                    if (ObjectUtils.isNotEmpty(item)) {
                        if (StringUtils.equalsIgnoreCase(item.getType(), "item") || ObjectUtils.isEmpty(item.getSubTree())) {
                            if (CollectionUtils.isNotEmpty(item.getEnumValue())) {
                                List<String> collect = item.getEnumValue().stream().map(SQLUtils::transKeyword).collect(Collectors.toList());
                                item.setEnumValue(collect);
                            }
                            item.setValue(SQLUtils.transKeyword(item.getValue()));
                        } else if (StringUtils.equalsIgnoreCase(item.getType(), "tree") || (ObjectUtils.isNotEmpty(item.getSubTree()) && StringUtils.isNotEmpty(item.getSubTree().getLogic()))) {
                            charReplace(item.getSubTree());
                        }
                    }
                }
            }
        }
        return tree;
    }
}
