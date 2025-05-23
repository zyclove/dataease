package io.dataease.datasource.provider;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import io.dataease.extensions.datasource.dto.ApiDefinition;
import io.dataease.extensions.datasource.dto.ApiDefinitionRequest;
import io.dataease.exception.DEException;
import io.dataease.extensions.datasource.dto.DatasetTableDTO;
import io.dataease.extensions.datasource.dto.DatasourceRequest;
import io.dataease.extensions.datasource.dto.TableField;
import io.dataease.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApiUtils {
    private static Configuration jsonPathConf = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.ALWAYS_RETURN_LIST)
            .build();
    private static String path = "['%s']";
    public static ObjectMapper objectMapper = CommonBeanFactory.getBean(ObjectMapper.class);

    private static TypeReference<List<Object>> listTypeReference = new TypeReference<List<Object>>() {
    };
    private static TypeReference<List<Map<String, Object>>> listForMapTypeReference = new TypeReference<List<Map<String, Object>>>() {
    };

    public static List<DatasetTableDTO> getApiTables(DatasourceRequest datasourceRequest) throws DEException {
        List<DatasetTableDTO> tableDescs = new ArrayList<>();
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        for (ApiDefinition apiDefinition : apiDefinitionList) {
            if (apiDefinition == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(apiDefinition.getType()) && apiDefinition.getType().equalsIgnoreCase("params")) {
                continue;
            }
            DatasetTableDTO datasetTableDTO = new DatasetTableDTO();
            datasetTableDTO.setTableName(apiDefinition.getDeTableName());
            datasetTableDTO.setName(apiDefinition.getName());
            datasetTableDTO.setDatasourceId(datasourceRequest.getDatasource().getId());
            tableDescs.add(datasetTableDTO);
        }
        return tableDescs;
    }

    public static Map<String, String> getTableNamesMap(String configration) throws DEException {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode rootNode = objectMapper.readTree(configration);
            for (int i = 0; i < rootNode.size(); i++) {
                result.put(rootNode.get(i).get("name").asText(), rootNode.get(i).get("deTableName").asText());
            }
        } catch (Exception e) {
            DEException.throwException(e);
        }

        return result;
    }


    public static Map<String, Object> fetchApiResultField(DatasourceRequest datasourceRequest) throws DEException {
        Map<String, Object> result = new HashMap<>();
        List<String[]> dataList = new ArrayList<>();
        List<TableField> fieldList = new ArrayList<>();
        ApiDefinition apiDefinition = getApiDefinition(datasourceRequest);
        if (apiDefinition == null) {
            DEException.throwException("未找到");
        }
        if (apiDefinition.getRequest().getPage() != null && apiDefinition.getRequest().getPage().getPageType() != null && !apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("empty")) {
            String response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
            fieldList = getTableFields(apiDefinition);
            result.put("fieldList", fieldList);
            if (apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("pageNumber")) {
                int pageCount = Integer.valueOf(JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString());
                int beginPage = Integer.valueOf(apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue());
                if (apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPathType().equalsIgnoreCase("totalNumber")) {
                    pageCount = pageCount / Integer.valueOf(apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()) + 1;
                }
                for (int i = beginPage; i <= pageCount; i++) {
                    apiDefinition.getRequest().getPage().getRequestData().get(0).setParameterDefaultValue(String.valueOf(i));
                    response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
                    dataList.addAll(fetchResult(response, apiDefinition));
                }
            }
            if (apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("cursor")) {
                dataList.addAll(fetchResult(response, apiDefinition));
                String cursor = null;
                try {
                    cursor = JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString();
                } catch (Exception e) {
                }
                while (cursor != null) {
                    apiDefinition.getRequest().getPage().getRequestData().get(0).setParameterDefaultValue(cursor);
                    response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
                    dataList.addAll(fetchResult(response, apiDefinition));
                    try {
                        if (cursor.equalsIgnoreCase(JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString())) {
                            cursor = null;
                        } else {
                            cursor = JsonPath.read(response, apiDefinition.getRequest().getPage().getResponseData().get(0).getResolutionPath()).toString();
                        }
                    } catch (Exception e) {
                        cursor = null;
                    }
                }
            }
            result.put("dataList", dataList);
            return result;
        } else {
            String response = execHttpRequest(false, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
            fieldList = getTableFields(apiDefinition);
            result.put("fieldList", fieldList);
            dataList = fetchResult(response, apiDefinition);
            result.put("dataList", dataList);
            return result;
        }
    }


    private static List<TableField> getTableFields(ApiDefinition apiDefinition) throws DEException {
        return apiDefinition.getFields();
    }

    public static List<TableField> getTableFields(DatasourceRequest datasourceRequest) throws DEException {
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };

        List<TableField> tableFields = new ArrayList<>();
        try {
            List<ApiDefinition> lists = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
            for (ApiDefinition apiDefinition : lists) {
                if (datasourceRequest.getTable().equalsIgnoreCase(apiDefinition.getDeTableName())) {
                    tableFields = getTableFields(apiDefinition);
                }
            }
        } catch (Exception e) {

        }
        return tableFields;
    }

    public static String checkAPIStatus(DatasourceRequest datasourceRequest) throws Exception {
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        List<ObjectNode> status = new ArrayList();
        for (ApiDefinition apiDefinition : apiDefinitionList) {
            if (apiDefinition == null || (apiDefinition.getType() != null && apiDefinition.getType().equalsIgnoreCase("params"))) {
                continue;
            }
            datasourceRequest.setTable(apiDefinition.getName());
            ObjectNode apiItemStatuses = objectMapper.createObjectNode();
            try {
                getData(datasourceRequest);
                apiItemStatuses.put("name", apiDefinition.getName());
                apiItemStatuses.put("status", "Success");
            } catch (Exception e) {
                LogUtil.error("API status Error: " + datasourceRequest.getDatasource().getName() + "-" + apiDefinition.getName(), e);
                apiItemStatuses.put("name", apiDefinition.getName());
                apiItemStatuses.put("status", "Error");
            }
            status.add(apiItemStatuses);
        }
        return JsonUtil.toJSONString(status).toString();
    }

    private static List<String[]> getData(DatasourceRequest datasourceRequest) throws Exception {
        ApiDefinition apiDefinition = getApiDefinition(datasourceRequest);
        if (apiDefinition == null) {
            DEException.throwException("未找到");
        }
        String response = execHttpRequest(true, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
        return fetchResult(response, apiDefinition);
    }

    public static String execHttpRequest(boolean preview, ApiDefinition api, int socketTimeout, List<ApiDefinition> paramsList) {
        ApiDefinition apiDefinition = new ApiDefinition();
        BeanUtils.copyBean(apiDefinition, api);

        if (apiDefinition.getRequest().getPage() != null && apiDefinition.getRequest().getPage().getPageType() != null && apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("pageNumber")) {
            apiDefinition.setUrl(apiDefinition.getUrl().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue()).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()));
            apiDefinition.setRequest(JsonUtil.parseObject(JsonUtil.toJSONString(apiDefinition.getRequest()).toString().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue()).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()), ApiDefinitionRequest.class));
        }

        if (apiDefinition.getRequest().getPage() != null && apiDefinition.getRequest().getPage().getPageType() != null && apiDefinition.getRequest().getPage().getPageType().equalsIgnoreCase("cursor")) {
            apiDefinition.setUrl(apiDefinition.getUrl().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue()).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()));
            String defaultCursor = apiDefinition.getRequest().getPage().getRequestData().get(0).getParameterDefaultValue();
            apiDefinition.setRequest(JsonUtil.parseObject(JsonUtil.toJSONString(apiDefinition.getRequest()).toString().replace(apiDefinition.getRequest().getPage().getRequestData().get(0).getBuiltInParameterName(), StringUtils.isEmpty(defaultCursor) ? "" : defaultCursor).replace(apiDefinition.getRequest().getPage().getRequestData().get(1).getBuiltInParameterName(), apiDefinition.getRequest().getPage().getRequestData().get(1).getParameterDefaultValue()), ApiDefinitionRequest.class));
        }


        String response = "";
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        httpClientConfig.setSocketTimeout(socketTimeout * 1000);
        ApiDefinitionRequest apiDefinitionRequest = apiDefinition.getRequest();
        for (Map header : apiDefinitionRequest.getHeaders()) {
            if (header.get("name") != null && StringUtils.isNotEmpty(header.get("name").toString()) && header.get("value") != null && StringUtils.isNotEmpty(header.get("value").toString())) {
                if (header.get("nameType") != null && header.get("nameType").toString().equalsIgnoreCase("params")) {
                    String param = header.get("value").toString();
                    for (ApiDefinition definition : paramsList) {
                        for (int i = 0; i < definition.getFields().size(); i++) {
                            TableField field = definition.getFields().get(i);
                            if (field.getName().equalsIgnoreCase(param)) {
                                String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                List<String[]> dataList = fetchResult(resultStr, definition);
                                if (dataList.size() > 0) {
                                    httpClientConfig.addHeader(header.get("name").toString(), dataList.get(0)[i]);
                                }
                            }
                        }
                    }
                } else if (header.get("nameType") != null && header.get("nameType").toString().equalsIgnoreCase("custom")) {
                    List<String> params = new ArrayList<>();
                    String regex = "\\$\\{(.*?)\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(header.get("value").toString());
                    while (matcher.find()) {
                        params.add(matcher.group(1));
                    }
                    String result = header.get("value").toString();
                    for (String param : params) {
                        for (ApiDefinition definition : paramsList) {
                            for (int i = 0; i < definition.getFields().size(); i++) {
                                TableField field = definition.getFields().get(i);
                                if (field.getName().equalsIgnoreCase(param)) {
                                    String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                    List<String[]> dataList = fetchResult(resultStr, definition);
                                    if (dataList.size() > 0) {
                                        result = result.replace("${" + param + "}", dataList.get(0)[i]);
                                    }
                                }
                            }
                        }
                    }
                    httpClientConfig.addHeader(header.get("name").toString(), result);
                } else if (header.get("nameType") != null && header.get("nameType").toString().equalsIgnoreCase("timeFun")) {
                    String timeFormat = header.get("value").toString();
                    Calendar calendar = Calendar.getInstance();
                    Date date = calendar.getTime();
                    if (StringUtils.isNotEmpty(timeFormat) && timeFormat.split(" ")[0].equalsIgnoreCase("currentDay")) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(timeFormat.split(" ")[1]);
                        httpClientConfig.addHeader(header.get("name").toString(), simpleDateFormat.format(date));
                    }
                } else {
                    httpClientConfig.addHeader(header.get("name").toString(), header.get("value").toString());
                }

            }
        }
        if (apiDefinitionRequest.getAuthManager() != null
                && StringUtils.isNotBlank(apiDefinitionRequest.getAuthManager().getUsername())
                && StringUtils.isNotBlank(apiDefinitionRequest.getAuthManager().getPassword())
                && apiDefinitionRequest.getAuthManager().getVerification().equals("Basic Auth")) {
            String authValue = "Basic " + Base64.getUrlEncoder().encodeToString((apiDefinitionRequest.getAuthManager().getUsername()
                    + ":" + apiDefinitionRequest.getAuthManager().getPassword()).getBytes());
            httpClientConfig.addHeader("Authorization", authValue);
        }

        List<String> params = new ArrayList<>();
        for (Map<String, String> argument : apiDefinition.getRequest().getArguments()) {
            if (StringUtils.isNotEmpty(argument.get("name")) && StringUtils.isNotEmpty(argument.get("value"))) {
                if (argument.get("nameType") != null && argument.get("nameType").toString().equalsIgnoreCase("params")) {
                    String param = argument.get("value").toString();
                    for (ApiDefinition definition : paramsList) {
                        for (int i = 0; i < definition.getFields().size(); i++) {
                            TableField field = definition.getFields().get(i);
                            if (field.getOriginName().equalsIgnoreCase(param)) {
                                String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                List<String[]> dataList = fetchResult(resultStr, definition);
                                if (dataList.size() > 0) {
                                    params.add(argument.get("name") + "=" + dataList.get(0)[i]);
                                }
                            }
                        }
                    }
                } else if (argument.get("nameType") != null && argument.get("nameType").toString().equalsIgnoreCase("custom")) {
                    List<String> arrayList = new ArrayList<>();
                    String regex = "\\$\\{(.*?)\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(argument.get("value").toString());
                    while (matcher.find()) {
                        arrayList.add(matcher.group(1));
                    }
                    String result = argument.get("value").toString();
                    for (String param : arrayList) {
                        for (ApiDefinition definition : paramsList) {
                            for (int i = 0; i < definition.getFields().size(); i++) {
                                TableField field = definition.getFields().get(i);
                                if (field.getName().equalsIgnoreCase(param)) {
                                    String resultStr = execHttpRequest(true, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                    List<String[]> dataList = fetchResult(resultStr, definition);
                                    if (dataList.size() > 0) {
                                        result = result.replace("${" + param + "}", dataList.get(0)[i]);
                                    }
                                }
                            }
                        }
                    }
                    params.add(argument.get("name") + "=" + result);
                } else if (argument.get("nameType") != null && argument.get("nameType").toString().equalsIgnoreCase("timeFun")) {
                    String timeFormat = argument.get("value").toString();
                    Calendar calendar = Calendar.getInstance();
                    Date date = calendar.getTime();
                    if (StringUtils.isNotEmpty(timeFormat) && timeFormat.split(" ")[0].equalsIgnoreCase("currentDay")) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(timeFormat.split(" ")[1]);
                        params.add(argument.get("name") + "=" + simpleDateFormat.format(date));
                    }
                } else {
                    params.add(argument.get("name") + "=" + URLEncoder.encode(argument.get("value")));
                }
            }
        }
        if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(params)) {
            apiDefinition.setUrl(apiDefinition.getUrl() + "?" + StringUtils.join(params, "&"));
        }

        switch (apiDefinition.getMethod()) {
            case "GET":
                response = HttpClientUtil.get(apiDefinition.getUrl().trim(), httpClientConfig);
                break;
            case "POST":
                if (!apiDefinitionRequest.getBody().keySet().contains("type")) {
                    DEException.throwException("请求类型不能为空");
                }
                String type = apiDefinitionRequest.getBody().get("type").toString();
                if (StringUtils.equalsAny(type, "JSON", "XML", "Raw")) {
                    String raw = null;
                    if (apiDefinitionRequest.getBody().get("raw") != null) {
                        raw = apiDefinitionRequest.getBody().get("raw").toString();

                        List<String> bodYparams = new ArrayList<>();
                        String regex = "\\$\\{(.*?)\\}";
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(raw);
                        while (matcher.find()) {
                            bodYparams.add(matcher.group(1));
                        }
                        for (String param : bodYparams) {
                            for (ApiDefinition definition : paramsList) {
                                for (int i = 0; i < definition.getFields().size(); i++) {
                                    TableField field = definition.getFields().get(i);
                                    if (field.getOriginName().equalsIgnoreCase(param)) {
                                        String resultStr = execHttpRequest(false, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                        List<String[]> dataList = fetchResult(resultStr, definition);
                                        if (dataList.size() > 0) {
                                            raw = raw.replace("${" + param + "}", dataList.get(0)[i]);
                                        }
                                    }
                                }
                            }
                        }
                        response = HttpClientUtil.post(apiDefinition.getUrl(), raw, httpClientConfig);
                    }
                }
                if (StringUtils.equalsAny(type, "Form_Data", "WWW_FORM")) {
                    if (apiDefinitionRequest.getBody().get("kvs") != null) {
                        Map<String, String> body = new HashMap<>();
                        TypeReference<List<JsonNode>> listTypeReference = new TypeReference<List<JsonNode>>() {
                        };
                        List<JsonNode> rootNode = null;
                        try {
                            rootNode = objectMapper.readValue(JsonUtil.toJSONString(apiDefinition.getRequest().getBody().get("kvs")).toString(), listTypeReference);
                        } catch (Exception e) {
                            e.printStackTrace();
                            DEException.throwException(e);
                        }
                        for (JsonNode jsonNode : rootNode) {
                            if (jsonNode.has("name") && jsonNode.has("value")) {
                                if (jsonNode.get("value") != null && StringUtils.isNotEmpty(jsonNode.get("value").asText())) {
                                    if (jsonNode.get("nameType") != null && jsonNode.get("nameType").asText().equalsIgnoreCase("params")) {
                                        String param = jsonNode.get("value").asText();
                                        for (ApiDefinition definition : paramsList) {
                                            for (int i = 0; i < definition.getFields().size(); i++) {
                                                TableField field = definition.getFields().get(i);
                                                if (field.getOriginName().equalsIgnoreCase(param)) {
                                                    String resultStr = execHttpRequest(false, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                                    List<String[]> dataList = fetchResult(resultStr, definition);
                                                    if (dataList.size() > 0) {
                                                        body.put(jsonNode.get("name").asText(), dataList.get(0)[i]);
                                                    }
                                                }
                                            }
                                        }
                                    } else if (jsonNode.get("nameType") != null && jsonNode.get("nameType").asText().equalsIgnoreCase("custom")) {
                                        List<String> bodYparams = new ArrayList<>();
                                        String regex = "\\$\\{(.*?)\\}";
                                        Pattern pattern = Pattern.compile(regex);
                                        Matcher matcher = pattern.matcher(jsonNode.get("value").asText());
                                        while (matcher.find()) {
                                            bodYparams.add(matcher.group(1));
                                        }
                                        String result = jsonNode.get("value").asText();
                                        for (String param : bodYparams) {
                                            for (ApiDefinition definition : paramsList) {
                                                for (int i = 0; i < definition.getFields().size(); i++) {
                                                    TableField field = definition.getFields().get(i);
                                                    if (field.getOriginName().equalsIgnoreCase(param)) {
                                                        String resultStr = execHttpRequest(false, definition, definition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), null);
                                                        List<String[]> dataList = fetchResult(resultStr, definition);
                                                        if (dataList.size() > 0) {
                                                            result = result.replace("${" + param + "}", dataList.get(0)[i]);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        body.put(jsonNode.get("name").asText(), result);
                                    } else if (jsonNode.get("nameType") != null && jsonNode.get("nameType").asText().equalsIgnoreCase("timeFun")) {
                                        String timeFormat = jsonNode.get("value").asText();
                                        Calendar calendar = Calendar.getInstance();
                                        Date date = calendar.getTime();
                                        if (StringUtils.isNotEmpty(timeFormat) && timeFormat.split(" ")[0].equalsIgnoreCase("currentDay")) {
                                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(timeFormat.split(" ")[1]);
                                            body.put(jsonNode.get("name").toString(), simpleDateFormat.format(date));
                                        }
                                    } else {
                                        body.put(jsonNode.get("name").asText(), jsonNode.get("value").asText());
                                    }
                                }
                            }
                        }
                        response = HttpClientUtil.post(apiDefinition.getUrl(), body, httpClientConfig);
                    }
                }
                break;
            default:
                break;
        }
        return response;
    }

    private static void previewNum(List<Map<String, Object>> fields, String response) {
        int previewNum = 100;
        for (Map<String, Object> field : fields) {
            JSONArray newArray = new JSONArray();
            if (field.get("value") != null) {
                Object object = JsonPath.using(jsonPathConf).parse(response).read(field.get("jsonPath").toString());
                int i = 0;
                if (object instanceof List) {
                    for (Object o : (List<String>) object) {
                        if (Objects.isNull(o)) {
                            newArray.add("");
                        } else {
                            newArray.add(o.toString());
                        }
                        i++;
                        if (i >= previewNum) {
                            break;
                        }
                    }
                } else {
                    if (object != null) {
                        newArray.add(object.toString());
                    }
                }
                field.put("value", newArray);
            } else {
                List<Map<String, Object>> childrenFields = (List<Map<String, Object>>) field.get("children");
                previewNum(childrenFields, response);
            }
        }
    }

    public static ApiDefinition checkApiDefinition(DatasourceRequest datasourceRequest) throws DEException {
        ApiDefinition apiDefinition = new ApiDefinition();
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        if (!CollectionUtils.isEmpty(apiDefinitionList)) {
            for (ApiDefinition definition : apiDefinitionList) {
                if (definition != null && (definition.getType() == null || !definition.getType().equalsIgnoreCase("params"))) {
                    apiDefinition = definition;
                }
            }
        }
        String response = execHttpRequest(true, apiDefinition, apiDefinition.getApiQueryTimeout() == null || apiDefinition.getApiQueryTimeout() <= 0 ? 10 : apiDefinition.getApiQueryTimeout(), params(datasourceRequest));
        return checkApiDefinition(apiDefinition, response);
    }

    private static ApiDefinition checkApiDefinition(ApiDefinition apiDefinition, String response) throws DEException {
        if (StringUtils.isEmpty(response)) {
            DEException.throwException("该请求返回数据为空");
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        if (apiDefinition.isShowApiStructure() || !apiDefinition.isUseJsonPath()) {
            String rootPath;
            if (response.startsWith("[")) {
                rootPath = "$[*]";
                JsonNode jsonArray = null;
                try {
                    jsonArray = objectMapper.readTree(response);
                } catch (Exception e) {
                    DEException.throwException(e);
                }
                for (Object o : jsonArray) {
                    handleStr(apiDefinition, o.toString(), fields, rootPath);
                }
            } else {
                rootPath = "$";
                handleStr(apiDefinition, response, fields, rootPath);
            }
            previewNum(fields, response);
            apiDefinition.setJsonFields(fields);
            return apiDefinition;
        } else {
            List<LinkedHashMap> currentData = new ArrayList<>();
            try {
                Object object = JsonPath.read(response, apiDefinition.getJsonPath());
                if (object instanceof List) {
                    currentData = (List<LinkedHashMap>) object;
                } else {
                    currentData.add((LinkedHashMap) object);
                }
            } catch (Exception e) {
                DEException.throwException(e);
            }
            int i = 0;
            try {
                LinkedHashMap data = currentData.get(0);
            } catch (Exception e) {
                DEException.throwException("数据不符合规范, " + e.getMessage());
            }
            for (LinkedHashMap data : currentData) {
                if (i >= apiDefinition.getPreviewNum()) {
                    break;
                }
                if (i == 0) {
                    for (Object o : data.keySet()) {
                        Map<String, Object> field = new HashMap<>();
                        field.put("originName", o.toString());
                        field.put("name", o.toString());
                        field.put("type", "STRING");
                        field.put("checked", true);
                        field.put("size", 65535);
                        field.put("deExtractType", 0);
                        field.put("deType", 0);
                        field.put("extField", 0);
                        fields.add(field);
                    }
                }
                for (Map<String, Object> field : fields) {
                    JSONArray array = new JSONArray();
                    if (field.get("value") != null) {
                        try {
                            TypeReference<JSONArray> listTypeReference = new TypeReference<JSONArray>() {
                            };
                            array = objectMapper.readValue(field.get("value").toString(), listTypeReference);
                        } catch (Exception e) {
                            DEException.throwException(e);
                        }
                        array.add(Optional.ofNullable(data.get(field.get("originName"))).orElse("").toString().replaceAll("\n", " ").replaceAll("\r", " "));
                    } else {
                        array.add(Optional.ofNullable(data.get(field.get("originName"))).orElse("").toString().replaceAll("\n", " ").replaceAll("\r", " "));
                    }
                    field.put("value", array);
                }
                i++;
            }
            apiDefinition.setJsonFields(fields);
            return apiDefinition;
        }
    }


    private static void handleStr(ApiDefinition apiDefinition, String jsonStr, List<Map<String, Object>> fields, String rootPath) throws DEException {
        if (jsonStr.startsWith("[")) {
            TypeReference<List<Object>> listTypeReference = new TypeReference<List<Object>>() {
            };
            List<Object> jsonArray = null;

            try {
                jsonArray = objectMapper.readValue(jsonStr, listTypeReference);
            } catch (Exception e) {
                DEException.throwException(e);
            }
            for (Object o : jsonArray) {
                handleStr(apiDefinition, o.toString(), fields, rootPath);
            }
        } else {
            JsonNode jsonNode = null;
            try {
                jsonNode = objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                DEException.throwException(e);
            }
            Iterator<String> fieldNames = jsonNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String value = jsonNode.get(fieldName).toString();
                if (StringUtils.isNotEmpty(value) && !value.startsWith("[") && !value.startsWith("{")) {
                    value = jsonNode.get(fieldName).asText();
                }
                if (StringUtils.isNotEmpty(value) && value.startsWith("[")) {
                    Map<String, Object> o = new HashMap<>();
                    try {
                        JsonNode jsonArray = objectMapper.readTree(value);
                        List<Map<String, Object>> childrenField = new ArrayList<>();
                        for (JsonNode node : jsonArray) {
                            if (StringUtils.isNotEmpty(node.toString()) && !node.toString().startsWith("[") && !node.toString().startsWith("{")) {
                                throw new Exception(node + "is not json type");
                            }
                        }
                        for (JsonNode node : jsonArray) {
                            handleStr(apiDefinition, node.toString(), childrenField, rootPath + "." + String.format(path, fieldName) + "[*]");
                        }
                        o.put("children", childrenField);
                        o.put("childrenDataType", "LIST");
                    } catch (Exception e) {
                        JSONArray array = new JSONArray();
                        array.add(StringUtils.isNotEmpty(jsonNode.get(fieldName).toString()) ? jsonNode.get(fieldName).toString() : "");
                        o.put("value", array);
                    }
                    o.put("jsonPath", rootPath + "." + String.format(path, fieldName));
                    setProperty(apiDefinition, o, fieldName);
                    if (!hasItem(apiDefinition, fields, o)) {
                        fields.add(o);
                    }
                } else if (StringUtils.isNotEmpty(value) && value.startsWith("{")) {
                    try {
                        JsonNode jsonNode1 = objectMapper.readTree(value);
                        List<Map<String, Object>> children = new ArrayList<>();
                        handleStr(apiDefinition, value, children, rootPath + "." + String.format(path, fieldName));
                        Map<String, Object> o = new HashMap<>();
                        o.put("children", children);
                        o.put("childrenDataType", "OBJECT");
                        o.put("jsonPath", rootPath + "." + fieldName);
                        setProperty(apiDefinition, o, fieldName);
                        if (!hasItem(apiDefinition, fields, o)) {
                            fields.add(o);
                        }
                    } catch (Exception e) {
                        Map<String, Object> o = new HashMap<>();
                        o.put("jsonPath", rootPath + "." + String.format(path, fieldName));
                        setProperty(apiDefinition, o, fieldName);
                        JSONArray array = new JSONArray();
                        array.add(StringUtils.isNotEmpty(value) ? value : "");
                        o.put("value", array);
                        if (!hasItem(apiDefinition, fields, o)) {
                            fields.add(o);
                        }
                    }
                } else {
                    Map<String, Object> o = new HashMap<>();
                    o.put("jsonPath", rootPath + "." + String.format(path, fieldName));
                    setProperty(apiDefinition, o, fieldName);
                    JSONArray array = new JSONArray();
                    array.add(StringUtils.isNotEmpty(value) ? value : "");
                    o.put("value", array);
                    if (!hasItem(apiDefinition, fields, o)) {
                        fields.add(o);
                    }
                }

            }
        }
    }

    private static void setProperty(ApiDefinition apiDefinition, Map<String, Object> o, String s) {
        o.put("originName", s);
        o.put("name", s);
        o.put("type", "STRING");
        o.put("size", 65535);
        o.put("deExtractType", 0);
        o.put("deType", 0);
        o.put("checked", false);
        if (!apiDefinition.isUseJsonPath()) {
            for (TableField field : apiDefinition.getFields()) {
                if (!ObjectUtils.isEmpty(o.get("jsonPath")) && StringUtils.isNotEmpty(field.getJsonPath()) && field.getJsonPath().equals(o.get("jsonPath").toString())) {
                    o.put("checked", true);
                    o.put("name", field.getName());
                    o.put("primaryKey", field.isPrimaryKey());
                    o.put("length", field.getLength());
                    o.put("deExtractType", field.getDeExtractType());
                }
            }
        }
    }

    private static boolean hasItem(ApiDefinition apiDefinition, List<Map<String, Object>> fields, Map<String, Object> item) throws DEException {
        boolean has = false;
        for (Map<String, Object> field : fields) {
            if (field.get("jsonPath").equals(item.get("jsonPath"))) {
                has = true;
                mergeField(field, item);
                mergeValue(field, apiDefinition, item);
                break;
            }
        }

        return has;
    }


    private static void mergeField(Map<String, Object> field, Map<String, Object> item) throws DEException {
        if (item.get("children") != null) {
            List<Map<String, Object>> fieldChildren = null;
            List<Map<String, Object>> itemChildren = null;
            try {
                fieldChildren = objectMapper.readValue(JsonUtil.toJSONString(field.get("children")).toString(), listForMapTypeReference);
                itemChildren = objectMapper.readValue(JsonUtil.toJSONString(item.get("children")).toString(), listForMapTypeReference);
            } catch (Exception e) {
                DEException.throwException(e);
            }
            if (fieldChildren == null) {
                fieldChildren = new ArrayList<>();
            }
            for (Map<String, Object> itemChild : itemChildren) {
                boolean hasKey = false;
                for (Map<String, Object> fieldChild : fieldChildren) {
                    if (itemChild.get("jsonPath").toString().equals(fieldChild.get("jsonPath").toString())) {
                        mergeField(fieldChild, itemChild);
                        hasKey = true;
                    }
                }
                if (!hasKey) {
                    fieldChildren.add(itemChild);
                }
            }
            field.put("children", fieldChildren);
        }
    }

    private static void mergeValue(Map<String, Object> field, ApiDefinition apiDefinition, Map<String, Object> item) throws DEException {
        TypeReference<JSONArray> listTypeReference = new TypeReference<JSONArray>() {
        };
        try {
            if (!ObjectUtils.isEmpty(field.get("value")) && !ObjectUtils.isEmpty(item.get("value"))) {
                JSONArray array = objectMapper.readValue(JsonUtil.toJSONString(field.get("value")).toString(), listTypeReference);
                array.add(objectMapper.readValue(JsonUtil.toJSONString(item.get("value")).toString(), listTypeReference).get(0));
                field.put("value", array);
            }
            if (!ObjectUtils.isEmpty(field.get("children")) && !ObjectUtils.isEmpty(item.get("children"))) {
                List<Map<String, Object>> fieldChildren = objectMapper.readValue(JsonUtil.toJSONString(field.get("children")).toString(), listForMapTypeReference);
                List<Map<String, Object>> itemChildren = objectMapper.readValue(JsonUtil.toJSONString(item.get("children")).toString(), listForMapTypeReference);
                List<Map<String, Object>> fieldArrayChildren = new ArrayList<>();
                for (Map<String, Object> fieldChild : fieldChildren) {
                    Map<String, Object> find = null;
                    for (Map<String, Object> itemChild : itemChildren) {
                        if (fieldChild.get("jsonPath").toString().equals(itemChild.get("jsonPath").toString())) {
                            find = itemChild;
                        }
                    }
                    if (find != null) {
                        mergeValue(fieldChild, apiDefinition, find);
                    }
                    fieldArrayChildren.add(fieldChild);
                }
                field.put("children", fieldArrayChildren);
            }
        } catch (Exception e) {
            e.printStackTrace();
            DEException.throwException(e);
        }

    }

    private static List<String[]> fetchResult(String result, ApiDefinition apiDefinition) {
        List<String[]> dataList = new LinkedList<>();
        if (apiDefinition.isUseJsonPath()) {
            List<LinkedHashMap> currentData = new ArrayList<>();
            Object object = JsonPath.read(result, apiDefinition.getJsonPath());
            if (object instanceof List) {
                currentData = (List<LinkedHashMap>) object;
            } else {
                currentData.add((LinkedHashMap) object);
            }
            for (LinkedHashMap data : currentData) {
                String[] row = new String[apiDefinition.getFields().size()];
                int i = 0;
                for (TableField field : apiDefinition.getFields()) {
                    row[i] = Optional.ofNullable(data.get(field.getOriginName())).orElse("").toString().replaceAll("\n", " ").replaceAll("\r", " ");
                    i++;
                }
                dataList.add(row);
            }
        } else {
            List<String> jsonPaths = apiDefinition.getFields().stream().map(TableField::getJsonPath).collect(Collectors.toList());
            Long maxLength = 0l;
            List<List<String>> columnDataList = new ArrayList<>();
            for (int i = 0; i < jsonPaths.size(); i++) {
                List<String> data = new ArrayList<>();
                Object object = JsonPath.using(jsonPathConf).parse(result).read(jsonPaths.get(i));
                if (object instanceof List) {
                    for (Object o : (List<String>) object) {
                        if (Objects.isNull(o)) {
                            data.add("");
                        } else {
                            data.add(o.toString());
                        }
                    }
                } else {
                    if (object != null) {
                        data.add(object.toString());
                    }
                }
                maxLength = maxLength > data.size() ? maxLength : data.size();
                columnDataList.add(data);
            }
            for (int i = 0; i < maxLength; i++) {
                String[] row = new String[apiDefinition.getFields().size()];
                dataList.add(row);
            }
            for (int i = 0; i < columnDataList.size(); i++) {
                for (int j = 0; j < columnDataList.get(i).size(); j++) {
                    dataList.get(j)[i] = Optional.ofNullable(String.valueOf(columnDataList.get(i).get(j))).orElse("").replaceAll("\n", " ").replaceAll("\r", " ");
                }
            }
        }
        return dataList;
    }


    private static List<ApiDefinition> params(DatasourceRequest datasourceRequest) {
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionListTemp = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);
        return apiDefinitionListTemp.stream().filter(apiDefinition -> apiDefinition != null && apiDefinition.getType() != null && apiDefinition.getType().equalsIgnoreCase("params")).collect(Collectors.toList());
    }

    private static ApiDefinition getApiDefinition(DatasourceRequest datasourceRequest) throws DEException {
        List<ApiDefinition> apiDefinitionList = new ArrayList<>();
        TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
        };
        List<ApiDefinition> apiDefinitionListTemp = JsonUtil.parseList(datasourceRequest.getDatasource().getConfiguration(), listTypeReference);

        if (!CollectionUtils.isEmpty(apiDefinitionListTemp)) {
            for (ApiDefinition apiDefinition : apiDefinitionListTemp) {
                if (apiDefinition == null || apiDefinition.getType() == null || apiDefinition.getType().equalsIgnoreCase("params")) {
                    continue;
                }
                if (apiDefinition.getDeTableName().equalsIgnoreCase(datasourceRequest.getTable()) || apiDefinition.getName().equalsIgnoreCase(datasourceRequest.getTable())) {
                    apiDefinitionList.add(apiDefinition);
                }

            }
        }
        if (CollectionUtils.isEmpty(apiDefinitionList)) {
            DEException.throwException("未找到API数据表");
        }
        if (apiDefinitionList.size() > 1) {
            DEException.throwException("存在重名的API数据表");
        }
        ApiDefinition find = null;
        for (ApiDefinition apiDefinition : apiDefinitionList) {
            if (apiDefinition == null) {
                continue;
            }
            if (apiDefinition.getName().equalsIgnoreCase(datasourceRequest.getTable()) || apiDefinition.getDeTableName().equalsIgnoreCase(datasourceRequest.getTable())) {
                find = apiDefinition;
            }
        }
        return find;
    }

}
