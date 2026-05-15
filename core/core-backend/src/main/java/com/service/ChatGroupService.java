package com.service;

import org.springframework.stereotype.Service;
import com.model.AcceptanceStatusData;
import com.model.ContractSubscription;
import com.model.CustomerData;
import com.model.ImplementationCreateContext;
import com.model.ImplementationProductOption;
import com.model.MaintenanceCreateContext;
import com.model.MaintenanceRecord;
import com.model.ProductVersionSnapshot;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.model.request.UpdateTicketRequest;
import com.model.request.CreateMaintenanceRecordRequest;
import com.model.request.CreateImplementationRecordRequest;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.util.HttpClientUtil;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import jakarta.annotation.Resource;

@Service
public class ChatGroupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGroupService.class);
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Value("${cscrm.base.url:http://democenter.fit2cloud.cn:24916}")
    private String cscrmBaseUrl;

    @Value("${cscrm.api.path:/api/v1/staff-admin}")
    private String cscrmApiPath;

    @Value("${cscrm.api.key:}")
    private String cscrmApiKey;

    // 区域部门ID映射
    private static final List<Long> EAST_REGION_DEPT_IDS = List.of(129L, 131L, 130L, 61L, 123L);
    private static final List<Long> NORTH_REGION_DEPT_IDS = List.of(127L, 145L, 146L, 60L);
    private static final List<Long> SOUTH_REGION_DEPT_IDS = List.of(125L, 126L, 62L);

    // 线上部门ID
    private static final List<Long> ONLINE_DEPT_IDS = List.of(123L, 127L, 125L);

    // 虚拟账号部门ID
    private static final List<Long> VIRTUAL_DEPT_IDS = List.of(61L, 60L, 62L);

    // 线下部门ID
    private static final List<Long> OFFLINE_DEPT_IDS = List.of(129L, 131L, 130L, 145L, 146L, 126L);

    // 版本聚合产品ID映射
    private static final List<Long> MAXKB_PRODUCT_IDS = Arrays.asList(2009L, 2012L, 2013L);
    private static final List<Long> DATAEASE_PRODUCT_IDS = Arrays.asList(2003L, 2008L);

    @Resource
    private FinanceLedgerService financeLedgerService;

    private static final String ACCEPTANCE_REPORT_REQUIRED_ID = "ID01lceprWXBrp";
    private static final String IMPLEMENTATION_DEFAULT_VALIDATION_VALUE = "正常，满足客户使用";
    private static final String IMPLEMENTATION_FORM_TYPE_JS = "JS";
    private static final String IMPLEMENTATION_FORM_TYPE_MK = "MK";
    private static final String IMPLEMENTATION_FORM_TYPE_DE = "DE";
    private static final String IMPLEMENTATION_FORM_TYPE_SQLBOT = "SQLBOT";
    private static final String IMPLEMENTATION_FORM_TYPE_GENERIC = "GENERIC";
    private static final List<ImplementationProductSpec> IMPLEMENTATION_PRODUCT_SPECS = List.of(
            new ImplementationProductSpec(2001L, "JumpServer", "JS", "JumpServer", IMPLEMENTATION_FORM_TYPE_JS,
                    List.of("JUMPSERVER"), List.of()),
            new ImplementationProductSpec(2003L, "DataEase", "DE", "DataEaseV2", IMPLEMENTATION_FORM_TYPE_DE,
                    List.of("DATAEASE"), List.of()),
            new ImplementationProductSpec(2013L, "MaxKB 专业版V2", "MK", "MaxKBV2_PRO", IMPLEMENTATION_FORM_TYPE_MK,
                    List.of("MAXKB", "V2"), List.of("专业", "PRO", "PROFESSIONAL")),
            new ImplementationProductSpec(2012L, "MaxKB 企业版V2", "MK", "MaxKBV2_EE", IMPLEMENTATION_FORM_TYPE_MK,
                    List.of("MAXKB", "V2"), List.of("企业", "EE", "ENTERPRISE")),
            new ImplementationProductSpec(2011L, "SQLBot 专业版", "SQLBOT", "SQLBot", IMPLEMENTATION_FORM_TYPE_SQLBOT,
                    List.of("SQLBOT"), List.of())
    );

    public CustomerData getCustomerData(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new CustomerData();
        }
        return queryCustomerData(normalizedExtChatId);
    }

    public StreamingResponseBody streamRealtimeAnalysis(String extChatId, String timeRange) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            throw new IllegalArgumentException("extChatId 不能为空");
        }
        String normalizedTimeRange = normalizeRealtimeAnalysisTimeRange(timeRange);

        return outputStream -> {
            String url = buildCscrmUrl("/maxkb-chat-analysis/realtime/stream")
                    + "?ext_chat_id=" + URLEncoder.encode(normalizedExtChatId, StandardCharsets.UTF_8)
                    + "&time_range=" + URLEncoder.encode(normalizedTimeRange, StandardCharsets.UTF_8);
            HttpGet get = new HttpGet(url);
            get.addHeader("Accept", "text/event-stream");
            if (trimToNull(cscrmApiKey) != null) {
                get.addHeader("Authorization", "Bearer " + cscrmApiKey);
                get.addHeader("X-API-Key", cscrmApiKey);
            }

            try (CloseableHttpClient client = HttpClientBuilder.create().build();
                 CloseableHttpResponse response = client.execute(get)) {
                int code = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                if (code >= 400) {
                    JSONObject error = new JSONObject(true);
                    error.put("code", code);
                    error.put("message", entity != null ? readEntityText(entity) : response.getStatusLine().getReasonPhrase());
                    writeSseError(outputStream, error);
                    return;
                }

                if (entity == null) {
                    JSONObject error = new JSONObject(true);
                    error.put("code", -1);
                    error.put("message", "实时分析接口返回为空");
                    writeSseError(outputStream, error);
                    return;
                }

                try (InputStream inputStream = entity.getContent()) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                        outputStream.flush();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("streamRealtimeAnalysis failed extChatId={}, err={}", normalizedExtChatId, e.getMessage(), e);
                JSONObject error = new JSONObject(true);
                error.put("code", -1);
                error.put("message", e.getMessage());
                writeSseError(outputStream, error);
            } finally {
                get.releaseConnection();
            }
        };
    }

    private String normalizeRealtimeAnalysisTimeRange(String timeRange) {
        String normalized = trimToNull(timeRange);
        if (normalized == null) {
            return "24";
        }
        return switch (normalized) {
            case "1", "3", "6", "24", "72", "168" -> normalized;
            default -> "24";
        };
    }

    private CustomerData queryCustomerData(String extChatId) {
        long totalStartNs = System.nanoTime();
        CustomerData data = new CustomerData();

        // 1. 通过 API 获取客户名称和订阅信息
        SubscriptionContext subscription = resolveProductAwareSubscriptionContextFromApi(extChatId);
        if (subscription != null) {
            data.setName(resolveCustomerName(subscription.clientName, extChatId));
            data.setClientId(subscription.clientId);
            data.setProductId(subscription.productId);
            data.setRegionId(subscription.regionId);
            data.setSubscriptionEndDate(subscription.supportEndDate);
        } else {
            data.setName("未知客户");
        }

        // 2. 查询工单计数（保留 JDBC 查库）
        com.util.JdbcUtils.setCscrmConfig();
        try {
            long ticketQueryStartNs = System.nanoTime();
            String ticketSql = "with ticket_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_ticket_count, " +
                    "            count(1) all_ticket_count, " +
                    "            sum(if(status = 3, 1, 0)) critical_ticket_count " +
                    "     from chat_analysis_tickets " +
                    "     where deleted_at IS NULL " +
                    "     group by room_id " +
                    " ), " +
                    " issue_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_issue_count, " +
                    "            count(1) all_issue_count " +
                    "     from chat_analysis_tickets " +
                    "     where issue_category = '功能需求' AND deleted_at IS NULL " +
                    "     group by room_id " +
                    " ), " +
                    " bug_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_bug_count, " +
                    "            count(1) all_bug_count " +
                    "     from chat_analysis_tickets " +
                    "     where issue_category = '产品缺陷' AND deleted_at IS NULL " +
                    "     group by room_id " +
                    " ) " +
                    " select ticket_count.not_resolved_ticket_count, " +
                    "        ticket_count.all_ticket_count, " +
                    "        ticket_count.critical_ticket_count, " +
                    "        issue_count.all_issue_count, " +
                    "        issue_count.not_resolved_issue_count, " +
                    "        bug_count.all_bug_count, " +
                    "        bug_count.not_resolved_bug_count " +
                    " from ticket_count " +
                    " left join issue_count on issue_count.room_id = ticket_count.room_id " +
                    " left join bug_count on bug_count.room_id = ticket_count.room_id " +
                    " where ticket_count.room_id = ?";
            var ticketResult = com.util.JdbcUtils.query(ticketSql, extChatId);
            long ticketQueryCostMs = (System.nanoTime() - ticketQueryStartNs) / 1_000_000;
            if (!ticketResult.isEmpty()) {
                Object[] row = ticketResult.get(0);
                data.setNotResolvedTicketCount(row[0] != null ? Integer.parseInt(row[0].toString()) : 0);
                data.setAllTicketCount(row[1] != null ? Integer.parseInt(row[1].toString()) : 0);
                data.setCriticalTicketCount(row[2] != null ? Integer.parseInt(row[2].toString()) : 0);
                data.setAllIssueCount(row[3] != null ? Integer.parseInt(row[3].toString()) : 0);
                data.setNotResolvedIssueCount(row[4] != null ? Integer.parseInt(row[4].toString()) : 0);
                data.setAllBugCount(row[5] != null ? Integer.parseInt(row[5].toString()) : 0);
                data.setNotResolvedBugCount(row[6] != null ? Integer.parseInt(row[6].toString()) : 0);
            }

            // 3. 补充产品元数据和服务状态
            long productMetaStartNs = System.nanoTime();
            fillCustomerProductMeta(extChatId, data);
            long productMetaCostMs = (System.nanoTime() - productMetaStartNs) / 1_000_000;
            long serviceStatusStartNs = System.nanoTime();
            fillCustomerServiceStatus(data);
            long serviceStatusCostMs = (System.nanoTime() - serviceStatusStartNs) / 1_000_000;

            LOGGER.info("getCustomerData timing extChatId={}, ticketQueryMs={}, productMetaMs={}, serviceStatusMs={}, totalMs={}",
                    extChatId, ticketQueryCostMs, productMetaCostMs, serviceStatusCostMs,
                    (System.nanoTime() - totalStartNs) / 1_000_000);
        } finally {
            com.util.JdbcUtils.clearConfig();
        }

        return data;
    }

    private String resolveCustomerName(String clientName, String extChatId) {
        String resolvedClientName = sanitizeCustomerName(clientName);
        if (resolvedClientName != null) {
            return resolvedClientName;
        }
        LOGGER.warn("getCustomerData customer name missing, extChatId={}", extChatId);
        return null;
    }

    private String sanitizeCustomerName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String value = rawName.trim();
        if (value.isEmpty() || "-".equals(value) || "--".equals(value) || "—".equals(value)) {
            return null;
        }
        return value;
    }

    private void fillAcceptanceStatus(String extChatId, CustomerData data) {
        if (data == null) {
            return;
        }
        long startNs = System.nanoTime();
        try {
            FinanceLedgerService.LedgerRecord ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
            if (ledgerRecord == null) {
                LOGGER.warn("fillAcceptanceStatus ledger not found extChatId={}, clientId={}", extChatId, data.getClientId());
                return;
            }
            AcceptanceStatusData acceptanceStatusData = buildAcceptanceStatusData(ledgerRecord);
            data.setNeedAcceptanceReport(acceptanceStatusData.getNeedAcceptanceReport());
            data.setAccepted(acceptanceStatusData.getAccepted());
            data.setAcceptanceStatusCode(acceptanceStatusData.getAcceptanceStatusCode());
            data.setIsAccepted(acceptanceStatusData.getIsAccepted());

            LOGGER.info("fillAcceptanceStatus success extChatId={}, clientId={}, contractCode={}, needAcceptanceReport={}, accepted={}, acceptanceStatusCode={}",
                    extChatId,
                    data.getClientId(),
                    ledgerRecord.getCode(),
                    acceptanceStatusData.getNeedAcceptanceReport(),
                    acceptanceStatusData.getAccepted(),
                    acceptanceStatusData.getAcceptanceStatusCode());
        } catch (Exception e) {
            LOGGER.warn("fillAcceptanceStatus failed extChatId={}, clientId={}, err={}",
                    extChatId, data.getClientId(), e.getMessage());
        } finally {
            LOGGER.info("fillAcceptanceStatus timing extChatId={}, costMs={}",
                    extChatId, (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    private void fillCustomerServiceStatus(CustomerData data) {
        if (data == null) {
            return;
        }
        LocalDate subscriptionEndDate = parseSubscriptionEndDate(data.getSubscriptionEndDate());
        boolean subscriptionExpired = subscriptionEndDate != null
                && subscriptionEndDate.isBefore(LocalDate.now(ZONE_SHANGHAI));
        if (subscriptionExpired) {
            data.setSupportExpired(true);
            data.setServiceStatus("已到期");
            return;
        }

        data.setSupportExpired(false);
        String clientName = trim(data.getName());
        if (clientName.isEmpty()) {
            return;
        }
        data.setServiceStatus("交付中");

        try {
            String url = cscrmBaseUrl + cscrmApiPath +
                    "/support-info/clients?name=" + URLEncoder.encode(clientName, StandardCharsets.UTF_8) +
                    "&customerChurnType=2&page=1&page_size=10";
            String response = requestWithDnsRetry(url);
            JSONObject responseJson = JSONObject.parseObject(response);
            Integer code = responseJson.getInteger("code");
            if (code != null && code != 0) {
                LOGGER.warn("fillCustomerServiceStatus non-zero code clientName={}, code={}, msg={}",
                        clientName, code, firstNonBlank(responseJson.getString("message"), responseJson.getString("msg")));
                return;
            }

            JSONObject item = selectClientItem(responseJson.getJSONObject("data"), clientName);
            if (item == null) {
                return;
            }
            Boolean supportExpired = item.getBoolean("supportExpired");
            if (Boolean.TRUE.equals(supportExpired) && subscriptionEndDate == null) {
                data.setSupportExpired(true);
                data.setServiceStatus("已到期");
                return;
            }
            Integer isCompleted = item.getInteger("isCompleted");
            data.setIsCompleted(isCompleted);
            if (isCompleted != null && isCompleted == 1) {
                data.setServiceStatus("服务中");
            } else {
                data.setServiceStatus("交付中");
            }
        } catch (Exception e) {
            LOGGER.warn("fillCustomerServiceStatus failed clientName={}, err={}", clientName, e.getMessage());
        }
    }

    private JSONObject selectClientItem(JSONObject dataJson, String clientName) {
        if (dataJson == null || !(dataJson.get("items") instanceof JSONArray items) || items.isEmpty()) {
            return null;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof JSONObject itemJson)) {
                continue;
            }
            if (clientName.equals(trim(itemJson.getString("name")))
                    || clientName.equals(trim(itemJson.getString("abbreviatedName")))) {
                return itemJson;
            }
        }
        Object first = items.get(0);
        return first instanceof JSONObject ? (JSONObject) first : null;
    }

    private String resolveGroupChatName(String extChatId) {
        try {
            String sql = "SELECT name FROM group_chat WHERE ext_chat_id = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, extChatId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return trimToNull(result.get(0)[0].toString());
            }
        } catch (Exception e) {
            LOGGER.warn("resolveGroupChatName failed extChatId={}, err={}", extChatId, e.getMessage());
        }
        return null;
    }

    private List<ContractSubscription> fetchContractSubscriptions(String clientName, String productAlias) throws Exception {
        List<ContractSubscription> subscriptions = new ArrayList<>();
        String normalizedClientName = trimToNull(clientName);
        if (normalizedClientName == null) {
            return subscriptions;
        }
        String normalizedProductAlias = normalizeImplementationFormType(productAlias);

        String url = cscrmBaseUrl + cscrmApiPath +
                "/support-info/subscriptions?" +
                "clientName=" + URLEncoder.encode(normalizedClientName, StandardCharsets.UTF_8) +
                "&sort_field=start_date&sort_type=desc";
        String response = requestWithDnsRetry(url);
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            throw new Exception(firstNonBlank(
                    responseJson.getString("message"),
                    responseJson.getString("msg"),
                    "查询合同信息失败"
            ));
        }

        JSONObject dataJson = responseJson.getJSONObject("data");
        JSONArray items = dataJson != null ? dataJson.getJSONArray("items") : null;
        if (items == null || items.isEmpty()) {
            return subscriptions;
        }

        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null
                    || !matchesContractSubscription(item, normalizedClientName)
                    || !matchesContractProductAlias(item, normalizedProductAlias)) {
                continue;
            }
            subscriptions.add(toContractSubscription(item));
        }
        sortContractSubscriptionsDesc(subscriptions);
        return subscriptions;
    }

    private boolean matchesContractSubscription(JSONObject item, String exactValue) {
        String normalizedExact = trimToNull(exactValue);
        if (normalizedExact == null) {
            return true;
        }
        JSONObject client = item.getJSONObject("client");
        String clientName = client != null ? trimToNull(client.getString("name")) : null;
        return normalizedExact.equals(clientName);
    }

    private boolean matchesContractProductAlias(JSONObject item, String productAlias) {
        if (!isSupportedImplementationAlias(productAlias)) {
            return true;
        }
        JSONObject productService = item.getJSONObject("productService");
        Long productId = productService != null ? productService.getLong("productId") : null;
        String productName = productService != null ? trimToNull(productService.getString("name")) : null;
        String category = trimToNull(item.getString("category"));
        String value = (firstNonBlank(productName, category, "")).toUpperCase(Locale.ROOT);
        String categoryValue = category == null ? "" : category.toUpperCase(Locale.ROOT);
        return switch (productAlias) {
            case "JS" -> Long.valueOf(2001L).equals(productId)
                    || "JS".equals(categoryValue)
                    || "JUMPSERVER".equals(categoryValue)
                    || value.contains("JUMPSERVER")
                    || value.contains("JS");
            case "MK" -> (productId != null && (MAXKB_PRODUCT_IDS.contains(productId) || Long.valueOf(2012L).equals(productId)))
                    || "MK".equals(categoryValue)
                    || "MAXKB".equals(categoryValue)
                    || value.contains("MAXKB")
                    || value.contains("MK");
            case "DE" -> (productId != null && DATAEASE_PRODUCT_IDS.contains(productId))
                    || "DE".equals(categoryValue)
                    || "DATAEASE".equals(categoryValue)
                    || value.contains("DATAEASE")
                    || value.contains("DE");
            case "SQLBOT" -> Long.valueOf(2011L).equals(productId)
                    || "SQLBOT".equals(categoryValue)
                    || value.contains("SQLBOT");
            default -> true;
        };
    }

    private void sortContractSubscriptionsDesc(List<ContractSubscription> subscriptions) {
        subscriptions.sort((left, right) -> {
            int startCompare = Long.compare(
                    toContractDateSortValue(right.getStartDate()),
                    toContractDateSortValue(left.getStartDate())
            );
            if (startCompare != 0) {
                return startCompare;
            }
            int endCompare = Long.compare(
                    toContractDateSortValue(right.getSupportEndDate()),
                    toContractDateSortValue(left.getSupportEndDate())
            );
            if (endCompare != 0) {
                return endCompare;
            }
            return Long.compare(
                    right.getId() != null ? right.getId() : 0L,
                    left.getId() != null ? left.getId() : 0L
            );
        });
    }

    private long toContractDateSortValue(String value) {
        LocalDate date = parseSubscriptionEndDate(value);
        return date != null ? date.toEpochDay() : 0L;
    }

    private ContractSubscription toContractSubscription(JSONObject item) {
        ContractSubscription subscription = new ContractSubscription();
        JSONObject client = item.getJSONObject("client");
        JSONObject productService = item.getJSONObject("productService");
        Boolean expired = item.getBoolean("expired");
        Boolean supportExpired = item.getBoolean("supportExpired");

        subscription.setId(item.getLong("id"));
        subscription.setClientId(item.getLong("clientId"));
        subscription.setClientName(client != null ? trimToNull(client.getString("name")) : null);
        subscription.setContractNumber(trimToNull(item.getString("contractNumber")));
        subscription.setProductId(productService != null ? productService.getLong("productId") : null);
        subscription.setCategory(trimToNull(item.getString("category")));
        subscription.setProductServiceName(productService != null ? trimToNull(productService.getString("name")) : null);
        subscription.setAmount(firstNonNullLong(item.getLong("amount"), productService != null ? productService.getLong("amount") : null));
        subscription.setAmountUnit(productService != null ? trimToNull(productService.getString("amountUnit")) : null);
        subscription.setServiceTypeName(trimToNull(item.getString("serviceTypeName")));
        subscription.setSupportUser(trimToNull(item.getString("supportUser")));
        subscription.setSalesUser(trimToNull(item.getString("salesUser")));
        subscription.setStartDate(formatEpochMillisDate(item.getString("start_date")));
        subscription.setEndDate(formatEpochMillisDate(item.getString("end_date")));
        subscription.setSupportEndDate(formatEpochMillisDate(item.getString("support_end_date")));
        subscription.setSubscriptionTypeName(trimToNull(item.getString("subscriptionTypeName")));
        subscription.setExpired(expired);
        subscription.setSupportExpired(supportExpired);
        subscription.setStatusText(Boolean.TRUE.equals(expired) || Boolean.TRUE.equals(supportExpired) ? "已到期" : "有效");
        subscription.setSerialNo(trimToNull(item.getString("serialNo")));
        subscription.setGroupChatName(trimToNull(item.getString("groupChatName")));
        return subscription;
    }

    private Long firstNonNullLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private boolean isSubscriptionExpired(String subscriptionEndDate) {
        LocalDate date = parseSubscriptionEndDate(subscriptionEndDate);
        return date != null && date.isBefore(LocalDate.now(ZONE_SHANGHAI));
    }

    private LocalDate parseSubscriptionEndDate(String subscriptionEndDate) {
        String value = trim(subscriptionEndDate);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public AcceptanceStatusData getAcceptanceStatus(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new AcceptanceStatusData();
        }
        return queryAcceptanceStatus(normalizedExtChatId);
    }

    private AcceptanceStatusData queryAcceptanceStatus(String extChatId) {
        long startNs = System.nanoTime();
        try {
            String resolveMode = "fast";
            FinanceLedgerService.LedgerRecord ledgerRecord;
            try {
                ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatIdFast(extChatId);
                if (ledgerRecord == null) {
                    resolveMode = "full_after_fast_miss";
                    ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
                }
            } catch (Exception fastLookupError) {
                resolveMode = "full_after_fast_error";
                LOGGER.warn("getAcceptanceStatus fast lookup failed extChatId={}, err={}",
                        extChatId,
                        fastLookupError.getMessage());
                ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
            }
            AcceptanceStatusData data = buildAcceptanceStatusData(ledgerRecord);
            LOGGER.info("getAcceptanceStatus timing extChatId={}, found={}, resolveMode={}, costMs={}",
                    extChatId,
                    ledgerRecord != null,
                    resolveMode,
                    (System.nanoTime() - startNs) / 1_000_000);
            return data;
        } catch (Exception e) {
            LOGGER.warn("getAcceptanceStatus failed extChatId={}, costMs={}, err={}",
                    extChatId,
                    (System.nanoTime() - startNs) / 1_000_000,
                    e.getMessage());
            return new AcceptanceStatusData();
        }
    }

    private AcceptanceStatusData buildAcceptanceStatusData(FinanceLedgerService.LedgerRecord ledgerRecord) {
        AcceptanceStatusData data = new AcceptanceStatusData();
        if (ledgerRecord == null) {
            return data;
        }
        String needAcceptanceReport = ACCEPTANCE_REPORT_REQUIRED_ID.equals(trim(ledgerRecord.getCheckAcceptReport())) ? "是" : "否";
        String accepted = "是".equals(trim(ledgerRecord.getCheckAccept())) ? "是" : "否";
        String acceptanceStatusCode;
        String acceptanceLabel;

        if ("否".equals(needAcceptanceReport)) {
            acceptanceStatusCode = "not_required";
            acceptanceLabel = "无需验收";
        } else if ("是".equals(accepted)) {
            acceptanceStatusCode = "accepted";
            acceptanceLabel = "已验收";
        } else {
            acceptanceStatusCode = "pending";
            acceptanceLabel = "待验收";
        }

        data.setNeedAcceptanceReport(needAcceptanceReport);
        data.setAccepted(accepted);
        data.setAcceptanceStatusCode(acceptanceStatusCode);
        data.setIsAccepted(acceptanceLabel);
        return data;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // 补齐客户产品/区域信息（优先订阅数据，次选群聊产品映射，再次选维护记录）
    private void fillCustomerProductMeta(String extChatId, CustomerData data) {
        if (data == null) {
            return;
        }
        long startNs = System.nanoTime();
        LOGGER.info("fillCustomerProductMeta start extChatId={}, clientId={}", extChatId, data.getClientId());

        // 1) 如果 productId 为空，按群聊名称映射产品再去 support_product_service 取 product_id
        if (data.getProductId() == null) {
            long stepStartNs = System.nanoTime();
            try {
                String productSql = "WITH chat_product AS ( " +
                        "    SELECT CASE " +
                        "        WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                        "        WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                        "        WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                        "        WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBot' " +
                        "        ELSE NULL END AS product " +
                        "    FROM group_chat gc WHERE gc.ext_chat_id = ? " +
                        ") " +
                        "SELECT sps.product_id " +
                        "FROM support_product_service sps " +
                        "CROSS JOIN chat_product cp " +
                        "WHERE cp.product IS NOT NULL " +
                        "AND (UPPER(sps.name) LIKE CONCAT('%', UPPER(cp.product), '%') " +
                        "OR UPPER(cp.product) LIKE CONCAT('%', UPPER(sps.name), '%')) " +
                        "ORDER BY sps.product_id LIMIT 1";
                var productResult = com.util.JdbcUtils.query(productSql, extChatId);
                if (!productResult.isEmpty() && productResult.get(0)[0] != null) {
                    data.setProductId(Long.parseLong(productResult.get(0)[0].toString()));
                    LOGGER.info("fillCustomerProductMeta by chatName success extChatId={}, productId={}", extChatId, data.getProductId());
                }
            } catch (Exception ignored) {
                // 忽略并继续兜底
            } finally {
                LOGGER.info("fillCustomerProductMeta timing extChatId={}, step=chatNameProduct, costMs={}",
                        extChatId, (System.nanoTime() - stepStartNs) / 1_000_000);
            }
        }

        // 2) 如果 productId 仍为空，从该群实时工单记录里的 product 名称反查 product_id
        if (data.getProductId() == null) {
            long stepStartNs = System.nanoTime();
            try {
                String ticketProductSql = "SELECT cat.product " +
                        "FROM chat_analysis_tickets cat " +
                        "WHERE cat.room_id = ? " +
                        "AND cat.product IS NOT NULL AND cat.product != '' " +
                        "ORDER BY cat.updated_at DESC LIMIT 1";
                var ticketProductResult = com.util.JdbcUtils.query(ticketProductSql, extChatId);
                if (!ticketProductResult.isEmpty() && ticketProductResult.get(0)[0] != null) {
                    String productName = ticketProductResult.get(0)[0].toString().trim();
                    if (!productName.isEmpty()) {
                        String findProductIdSql = "SELECT sps.product_id " +
                                "FROM support_product_service sps " +
                                "WHERE UPPER(sps.name) = UPPER(?) " +
                                "OR UPPER(sps.name) LIKE CONCAT('%', UPPER(?), '%') " +
                                "OR UPPER(?) LIKE CONCAT('%', UPPER(sps.name), '%') " +
                                "ORDER BY sps.product_id LIMIT 1";
                        var pidResult = com.util.JdbcUtils.query(findProductIdSql, productName, productName, productName);
                        if (!pidResult.isEmpty() && pidResult.get(0)[0] != null) {
                            data.setProductId(Long.parseLong(pidResult.get(0)[0].toString()));
                            LOGGER.info("fillCustomerProductMeta by ticketProduct success extChatId={}, ticketProduct={}, productId={}",
                                    extChatId, productName, data.getProductId());
                        }
                    }
                }
            } catch (Exception ignored) {
                // 忽略并继续兜底
            } finally {
                LOGGER.info("fillCustomerProductMeta timing extChatId={}, step=ticketProduct, costMs={}",
                        extChatId, (System.nanoTime() - stepStartNs) / 1_000_000);
            }
        }

        // 3) regionId 兜底取最近维护记录
        if ((data.getRegionId() == null || data.getRegionId().isEmpty()) && data.getClientId() != null) {
            long stepStartNs = System.nanoTime();
            try {
                String regionSql = "SELECT smr.region_id " +
                        "FROM support_maintenance_record smr " +
                        "WHERE smr.client_id = ? AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                        "ORDER BY smr.create_time DESC LIMIT 1";
                var regionResult = com.util.JdbcUtils.query(regionSql, data.getClientId());
                if (!regionResult.isEmpty() && regionResult.get(0)[0] != null) {
                    data.setRegionId(regionResult.get(0)[0].toString());
                    LOGGER.info("fillCustomerProductMeta region fallback success extChatId={}, regionId={}", extChatId, data.getRegionId());
                }
            } catch (Exception ignored) {
                // 忽略兜底失败
            } finally {
                LOGGER.info("fillCustomerProductMeta timing extChatId={}, step=regionFallback, costMs={}",
                        extChatId, (System.nanoTime() - stepStartNs) / 1_000_000);
            }
        }
        LOGGER.info("fillCustomerProductMeta done extChatId={}, productId={}, regionId={}, totalMs={}",
                extChatId, data.getProductId(), data.getRegionId(), (System.nanoTime() - startNs) / 1_000_000);
    }

    // 通用的获取工单方法
    private List<Ticket> queryTicketsByCategory(String extChatId, String issueCategory) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            StringBuilder sql = new StringBuilder("SELECT cat.id, " +
                    "       cat.title, " +
                    "       cat.description, " +
                    "       cat.product, " +
                    "       cat.status, " +
                    "       cat.urgent, " +
                    "       cat.resolved, " +
                    "       cat.owner_name, " +
                    "       cat.issue_category, " +
                    "       cat.customer_sentiment, " +
                    "       cat.tracking_links, " +
                    "       DATE_FORMAT(cat.created_at, '%Y-%m-%d %H:%i:%s') as created_at, " +
                    "       DATE_FORMAT(cat.updated_at, '%Y-%m-%d %H:%i:%s') as updated_at " +
                    "FROM chat_analysis_tickets cat " +
                    "WHERE cat.room_id = ? AND cat.deleted_at IS NULL ");

            List<Object> params = new ArrayList<>();
            params.add(extChatId);

            if (issueCategory != null) {
                sql.append("AND cat.issue_category = ? ");
                params.add(issueCategory);
            }

            sql.append("ORDER BY cat.created_at DESC");

            var result = com.util.JdbcUtils.query(sql.toString(), params.toArray());
            List<Ticket> tickets = new ArrayList<>();

            for (Object[] row : result) {
                Ticket ticket = new Ticket();
                ticket.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                ticket.setTitle(row[1] != null ? row[1].toString() : null);
                ticket.setDescription(row[2] != null ? row[2].toString() : null);
                ticket.setProduct(row[3] != null ? row[3].toString() : null);
                ticket.setStatus(row[4] != null ? Long.parseLong(row[4].toString()) : null);
                ticket.setUrgent(row[5] != null ? (Boolean) row[5] : null);
                ticket.setResolved(row[6] != null ? (Boolean) row[6] : null);
                ticket.setOwnerName(row[7] != null ? row[7].toString() : null);
                ticket.setIssueCategory(row[8] != null ? row[8].toString() : null);
                ticket.setCustomerSentiment(row[9] != null ? row[9].toString() : null);
                ticket.setTrackingLinks(row[10] != null ? row[10].toString() : null);
                ticket.setCreatedAt(row[11] != null ? row[11].toString() : null);
                ticket.setUpdatedAt(row[12] != null ? row[12].toString() : null);
                tickets.add(ticket);
            }

            return tickets;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public List<Ticket> getIssueTickets(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new ArrayList<>();
        }
        return queryTicketsByCategory(normalizedExtChatId, "功能需求");
    }

    public List<Ticket> getBugTickets(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new ArrayList<>();
        }
        return queryTicketsByCategory(normalizedExtChatId, "产品缺陷");
    }

    public List<MaintenanceRecord> getMaintenanceRecords(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new ArrayList<>();
        }
        return queryMaintenanceRecords(normalizedExtChatId);
    }

    private List<MaintenanceRecord> queryMaintenanceRecords(String extChatId) {
        try {
            SubscriptionContext subscription = resolveImplementationSubscriptionContext(extChatId);
            if (subscription != null && subscription.subscriptionId != null && subscription.subscriptionId > 0
                    && subscription.clientName != null) {
                List<MaintenanceRecord> records = fetchMaintenanceRecordsByClientName(subscription.clientName);
                Long subId = subscription.subscriptionId;
                List<MaintenanceRecord> filtered = new ArrayList<>();
                for (MaintenanceRecord r : records) {
                    if (subId.equals(r.getSubscriptionId())) {
                        filtered.add(r);
                    }
                }
                return filtered;
            }
            return fetchMaintenanceRecordsByGroupId(extChatId);
        } catch (Exception e) {
            LOGGER.warn("queryMaintenanceRecords failed extChatId={}, err={}", extChatId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<MaintenanceRecord> fetchMaintenanceRecordsByClientName(String clientName) throws Exception {
        String url = cscrmBaseUrl + cscrmApiPath +
                "/support-info/maintenances?clientName=" +
                URLEncoder.encode(clientName, StandardCharsets.UTF_8) +
                "&page=1&page_size=50";
        return parseMaintenanceRecordsFromResponse(requestWithDnsRetry(url), "clientName=" + clientName);
    }

    private List<MaintenanceRecord> fetchMaintenanceRecordsByGroupId(String extChatId) throws Exception {
        String url = cscrmBaseUrl + cscrmApiPath +
                "/support-info/maintenances?groupid=" +
                URLEncoder.encode(extChatId, StandardCharsets.UTF_8) +
                "&page=1&page_size=50";
        List<MaintenanceRecord> records = parseMaintenanceRecordsFromResponse(requestWithDnsRetry(url), "groupid=" + extChatId);
        if (!records.isEmpty()) {
            return records;
        }
        return fetchMaintenanceRecordsByGroupChatIdFromDb(extChatId);
    }

    private List<MaintenanceRecord> fetchMaintenanceRecordsByGroupChatIdFromDb(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT id, subscription_id, status, deployment_time, deployment_method, template, " +
                    "creator_name, created_by, create_time, version, content " +
                    "FROM support_maintenance " +
                    "WHERE group_chat_id = ? " +
                    "ORDER BY CASE WHEN create_time IS NULL OR create_time = 0 THEN id ELSE create_time END DESC, id DESC " +
                    "LIMIT 50";
            var result = com.util.JdbcUtils.query(sql, extChatId);
            List<MaintenanceRecord> records = new ArrayList<>();
            for (Object[] row : result) {
                JSONObject item = new JSONObject(true);
                item.put("id", row[0]);
                item.put("subscriptionId", row[1]);
                item.put("status", row[2]);
                item.put("deployment_time", row[3]);
                item.put("deploymentMethod", row[4]);
                item.put("template", row[5]);
                item.put("creatorName", row[6]);
                item.put("createdBy", row[7]);
                item.put("createTime", row[8]);
                item.put("version", row[9]);
                item.put("content", row[10]);
                records.add(toMaintenanceRecord(item));
            }
            return records;
        } catch (Exception e) {
            LOGGER.warn("fetchMaintenanceRecordsByGroupChatIdFromDb failed extChatId={}, err={}", extChatId, e.getMessage());
            return new ArrayList<>();
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private List<MaintenanceRecord> parseMaintenanceRecordsFromResponse(String response, String queryDesc) throws Exception {
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code == null || code != 0) {
            LOGGER.warn("fetchMaintenanceRecords non-zero code query={}, code={}", queryDesc, code);
            return new ArrayList<>();
        }
        JSONObject data = responseJson.getJSONObject("data");
        JSONArray items = data != null ? data.getJSONArray("items") : null;
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<MaintenanceRecord> records = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) continue;
            records.add(toMaintenanceRecord(item));
        }
        return records;
    }

    private List<MaintenanceRecord> fetchMaintenanceRecordsFromApi(String extChatId) throws Exception {
        String url = cscrmBaseUrl + cscrmApiPath +
                "/support-info/maintenances?groupid=" +
                URLEncoder.encode(extChatId, StandardCharsets.UTF_8) +
                "&page=1&page_size=50";
        String response = requestWithDnsRetry(url);
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code == null || code != 0) {
            LOGGER.warn("fetchMaintenanceRecordsFromApi non-zero code extChatId={}, code={}", extChatId, code);
            return new ArrayList<>();
        }
        JSONObject data = responseJson.getJSONObject("data");
        JSONArray items = data != null ? data.getJSONArray("items") : null;
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<MaintenanceRecord> records = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) continue;
            records.add(toMaintenanceRecord(item));
        }
        return records;
    }

    private MaintenanceRecord toMaintenanceRecord(JSONObject item) {
        String contentStr = item.getString("content");
        ImplementationContentSnapshot snapshot = parseImplementationContentSnapshot(contentStr);
        String resolvedTemplate = firstNonBlank(item.getString("template"), snapshot.template);
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(item.getLong("id"));
        record.setSubscriptionId(firstNonNullLong(item.getLong("subscriptionId"), item.getLong("subscription_id")));
        record.setStatus(item.getString("status"));
        record.setDeploymentTime(firstNonBlank(
                normalizeLegacyDeploymentTime(item.getString("deployment_time")),
                normalizeLegacyDeploymentTime(item.getString("deploymentTime")),
                snapshot.deploymentTime
        ));
        record.setDeploymentMethod(firstNonBlank(item.getString("deploymentMethod"), item.getString("deployment_method"), snapshot.deploymentMethod));
        record.setTemplate(resolvedTemplate);
        record.setCreatorName(firstNonBlank(
                item.getString("creatorName"),
                item.getString("creator_name"),
                resolveImplementationCreatorName(firstNonBlank(item.getString("createdBy"), item.getString("created_by")))
        ));
        record.setVersion(firstNonBlank(item.getString("version"), snapshot.version));
        record.setContent(buildImplementationDisplayContent(snapshot, contentStr));
        record.setCreateTime(firstNonBlank(
                normalizeLegacyDeploymentTime(item.getString("createTime")),
                normalizeLegacyDeploymentTime(item.getString("create_time")),
                snapshot.deploymentTime
        ));
        return record;
    }

    private String resolveImplementationCreatorName(String createdBy) {
        String normalizedCreatedBy = trimToNull(createdBy);
        if (normalizedCreatedBy == null) {
            return null;
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            String supportUserSql = "SELECT su.name, su.username, su.email " +
                    "FROM support_user su WHERE su.user_id = ? LIMIT 1";
            var supportUserResult = com.util.JdbcUtils.query(supportUserSql, normalizedCreatedBy);
            if (!supportUserResult.isEmpty()) {
                Object[] row = supportUserResult.get(0);
                String name = row[0] != null ? row[0].toString() : null;
                String username = row[1] != null ? row[1].toString() : null;
                String email = row[2] != null ? row[2].toString() : null;
                String staffName = resolveStaffNameByEmailOrName(email, name);
                return firstNonBlank(staffName, name, username, email);
            }

            String staffName = resolveStaffNameByExtId(normalizedCreatedBy);
            return firstNonBlank(staffName, normalizedCreatedBy);
        } catch (Exception e) {
            LOGGER.warn("resolveImplementationCreatorName failed createdBy={}, err={}", normalizedCreatedBy, e.getMessage());
            return normalizedCreatedBy;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public List<ServiceRecord> getServiceRecords(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new ArrayList<>();
        }
        return queryServiceRecords(normalizedExtChatId);
    }

    private List<ServiceRecord> queryServiceRecords(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "WITH chat_product AS ( " +
                    "    SELECT gc.name, " +
                    "           CASE " +
                    "               WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                    "               WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                    "               WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                    "               WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBot' " +
                    "               ELSE NULL " +
                    "           END AS product " +
                    "    FROM group_chat gc " +
                    "    WHERE gc.ext_chat_id = ? " +
                    ") " +
                    "SELECT DISTINCT smr.id, " +
                    "       smr.maintenance_types, " +
                    "       smr.maintenance_version, " +
                    "       smr.maintenance_title, " +
                    "       smr.maintenance_context, " +
                    "       smr.maintenance_time, " +
                    "       FROM_UNIXTIME(smr.maintenance_time/1000, '%Y-%m-%d') as maintenance_time_formatted, " +
                    "       smr.creator_name, " +
                    "       FROM_UNIXTIME(smr.create_time/1000, '%Y-%m-%d') as create_time " +
                    "FROM support_maintenance_record smr " +
                    "CROSS JOIN chat_product cp " +
                    "WHERE smr.client_id IN ( " +
                    "    SELECT DISTINCT ss.client_id " +
                    "    FROM group_chat gc " +
                    "    INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                    "    WHERE gc.ext_chat_id = ? " +
                    ") " +
                    "AND ( " +
                    "    (cp.product = 'MaxKB' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%MAXKB%' " +
                    "    )) " +
                    "    OR " +
                    "    (cp.product = 'JumpServer' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%JUMPSERVER%' " +
                    "    )) " +
                    "    OR " +
                    "    (cp.product = 'DataEase' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%DATAEASE%' " +
                    "    )) " +
                    "    OR " +
                    "    (cp.product = 'SQLBot' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%SQLBOT%' " +
                    "    )) " +
                    "    OR " +
                    "    smr.product_id IS NULL OR smr.product_id = 0 " +
                    ") " +
                    "ORDER BY smr.maintenance_time DESC";

            var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
            List<ServiceRecord> records = new ArrayList<>();

            for (Object[] row : result) {
                ServiceRecord record = new ServiceRecord();
                record.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                record.setMaintenanceTypes(row[1] != null ? row[1].toString() : null);
                record.setMaintenanceVersion(row[2] != null ? row[2].toString() : null);
                record.setMaintenanceTitle(row[3] != null ? row[3].toString() : null);
                record.setMaintenanceContext(row[4] != null ? row[4].toString() : null);
                record.setMaintenanceTime(row[6] != null ? row[6].toString() : null);
                record.setCreatorName(row[7] != null ? row[7].toString() : null);
                record.setCreateTime(row[8] != null ? row[8].toString() : null);
                records.add(record);
            }

            return records;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public List<Ticket> getTickets(String extChatId) {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            return new ArrayList<>();
        }
        return queryTicketsByCategory(normalizedExtChatId, null);
    }

    public List<TicketLog> getTicketLogs(Long ticketId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT catl.id, " +
                    "       catl.ticket_id, " +
                    "       catl.action, " +
                    "       catl.value, " +
                    "       catl.event_type, " +
                    "       catl.modified_by_name, " +
                    "       catl.comment, " +
                    "       DATE_FORMAT(catl.created_at, '%Y-%m-%d %H:%i:%s') as created_at " +
                    "FROM chat_analysis_ticket_logs catl " +
                    "WHERE catl.ticket_id = ? AND catl.deleted_at IS NULL " +
                    "ORDER BY catl.created_at ASC";

            var result = com.util.JdbcUtils.query(sql, ticketId);
            List<TicketLog> logs = new ArrayList<>();

            for (Object[] row : result) {
                TicketLog log = new TicketLog();
                log.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                log.setTicketId(row[1] != null ? Long.parseLong(row[1].toString()) : null);
                log.setAction(row[2] != null ? row[2].toString() : null);
                log.setValue(row[3] != null ? row[3].toString() : null);
                log.setEventType(row[4] != null ? row[4].toString() : null);
                log.setModifiedByName(row[5] != null ? row[5].toString() : null);
                log.setComment(row[6] != null ? row[6].toString() : null);
                log.setCreatedAt(row[7] != null ? row[7].toString() : null);
                logs.add(log);
            }

            return logs;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public void updateTicket(UpdateTicketRequest request, String modifiedById, String modifiedByName) throws Exception {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String extChatId = resolveExtChatIdByTicketId(request.getTicketId());
            // 打印接收到的参数
            LOGGER.info("========== Service 层接收到的参数 ==========");
            LOGGER.info("modifiedById (传入参数): {}", modifiedById);
            LOGGER.info("modifiedByName (传入参数): {}", modifiedByName);
            LOGGER.info("ownerName (处理人姓名): {}", request.getOwnerName());
            LOGGER.info("==========================================");

            String ownerId = trimToNull(request.getOwnerId());
            String requestedOwnerName = trimToNull(request.getOwnerName());
            if (ownerId == null && requestedOwnerName != null) {
                ownerId = resolveStaffExtIdByName(requestedOwnerName);
                if (ownerId == null) {
                    throw new Exception("未找到处理人: " + requestedOwnerName);
                }
            }
            if (ownerId == null) {
                ownerId = trimToNull(modifiedById);
            }
            if (ownerId == null) {
                throw new Exception("缺少处理人ID");
            }

            String resolvedOwnerName = firstNonBlank(
                    resolveStaffNameByExtId(ownerId),
                    requestedOwnerName,
                    trimToNull(modifiedByName),
                    trimToNull(modifiedById)
            );

            LOGGER.info("查询到的处理人ID (ownerId): {}", ownerId);

            // 构建请求体
            JSONObject payload = new JSONObject();
            payload.put("id", request.getTicketId());
            payload.put("urgent", request.getUrgent());
            payload.put("customer_sentiment", request.getCustomerSentiment());
            payload.put("owner_name", resolvedOwnerName);
            payload.put("owner_id", ownerId);  // 处理人ID
            payload.put("modified_by_id", modifiedById);  // 当前登录用户ID
            payload.put("modified_by_name", modifiedByName);  // 当前登录用户姓名
            payload.put("comment", request.getComment());
            payload.put("status", request.getStatus());
            payload.put("resolved", request.getResolved());
            if (request.getTrackingLinks() != null && !request.getTrackingLinks().isEmpty()) {
                payload.put("tracking_links", request.getTrackingLinks());
            }

            // 打印日志
            LOGGER.info("========== 更新工单请求信息 ==========");
            LOGGER.info("请求URL: {}", cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId());
            LOGGER.info("请求头 Authorization: Bearer {}", (cscrmApiKey != null ? cscrmApiKey.substring(0, Math.min(20, cscrmApiKey.length())) + "..." : "null"));
            LOGGER.info("请求头 X-API-Key: {}", (cscrmApiKey != null ? cscrmApiKey.substring(0, Math.min(20, cscrmApiKey.length())) + "..." : "null"));
            LOGGER.info("请求体 Payload: {}", payload.toJSONString());
            LOGGER.info("=====================================");

            // 调用 CSCRM API
            String url = cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId();
            String response = HttpClientUtil.putJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);

            LOGGER.info("响应内容: {}", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            if (responseJson.getInteger("code") != 0) {
                throw new Exception("更新工单失败: " + responseJson.getString("msg"));
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    // 更新需求/缺陷工单：按 api.md 中“更新需求工单/更新缺陷工单”字段组装
    public void updateIssueOrBugTicket(UpdateTicketRequest request, String loginUserId, String kind) throws Exception {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String extChatId = resolveExtChatIdByTicketId(request.getTicketId());
            String ownerId = trimToNull(request.getOwnerId());
            String requestedOwnerName = trimToNull(request.getOwnerName());
            if (ownerId == null && requestedOwnerName != null) {
                ownerId = resolveStaffExtIdByName(requestedOwnerName);
                if (ownerId == null) {
                    throw new Exception("未找到处理人: " + requestedOwnerName);
                }
            }
            if (ownerId == null) {
                ownerId = trimToNull(loginUserId);
            }
            if (ownerId == null) {
                throw new Exception("缺少负责人ID");
            }
            String ownerName = firstNonBlank(
                    resolveStaffNameByExtId(ownerId),
                    requestedOwnerName,
                    trimToNull(loginUserId)
            );
            String modifiedByName = resolveStaffNameByExtId(loginUserId);

            JSONObject payload = new JSONObject();
            payload.put("id", request.getTicketId());
            payload.put("owner_id", ownerId);
            if (ownerName != null && !ownerName.isEmpty()) {
                payload.put("owner_name", ownerName);
            }
            if (loginUserId != null && !loginUserId.isEmpty()) {
                payload.put("modified_by_id", loginUserId);
            }
            if (modifiedByName != null && !modifiedByName.isEmpty()) {
                payload.put("modified_by_name", modifiedByName);
            }
            payload.put("comment", request.getComment());
            payload.put("reminder_cycle", request.getReminderCycle() != null ? request.getReminderCycle() : 2);
            payload.put("urgent", request.getUrgent());
            payload.put("customer_sentiment", request.getCustomerSentiment());
            payload.put("tracking_links", request.getTrackingLinks() == null ? "" : request.getTrackingLinks());
            payload.put("status", request.getStatus());
            if (request.getResolved() != null) {
                payload.put("resolved", request.getResolved());
            }

            String url = cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId();
            LOGGER.info("update{}Ticket request url={}, payload={}",
                    "bug".equals(kind) ? "Bug" : "Issue", url, payload.toJSONString());

            String response = HttpClientUtil.putJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);
            LOGGER.info("update{}Ticket response={}", "bug".equals(kind) ? "Bug" : "Issue", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            Integer code = responseJson.getInteger("code");
            if (code != null && code != 0) {
                String msg = responseJson.getString("msg");
                if (msg == null || msg.isEmpty()) {
                    msg = responseJson.getString("message");
                }
                throw new Exception(msg != null ? msg : "更新失败");
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private String resolveStaffNameByExtId(String extId) {
        if (extId == null || extId.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT s.name FROM staff s WHERE s.ext_id = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, extId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private String resolveStaffExtIdByName(String staffName) {
        String normalizedStaffName = trimToNull(staffName);
        if (normalizedStaffName == null) {
            return null;
        }
        try {
            String sql = "SELECT s.ext_id FROM staff s WHERE s.name = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedStaffName);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return trimToNull(result.get(0)[0].toString());
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String resolveStaffNameByEmailOrName(String email, String name) {
        String normalizedEmail = trimToNull(email);
        String normalizedName = trimToNull(name);
        if (normalizedEmail == null && normalizedName == null) {
            return null;
        }
        try {
            String sql = "SELECT s.name FROM staff s " +
                    "WHERE (? IS NOT NULL AND s.email = ?) " +
                    "   OR (? IS NOT NULL AND s.name = ?) " +
                    "LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedEmail, normalizedEmail, normalizedName, normalizedName);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    public ImplementationCreateContext getImplementationCreateContext(String extChatId, String loginUserId) throws Exception {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            throw new Exception("缺少群聊ID");
        }
        String normalizedLoginUserId = nullToEmpty(trimToNull(loginUserId));
        return queryImplementationCreateContext(normalizedExtChatId, normalizedLoginUserId);
    }

    private ImplementationCreateContext queryImplementationCreateContext(String extChatId, String loginUserId) throws Exception {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            List<ImplementationProductOption> productOptions = queryImplementationProductOptions();
            SubscriptionContext subscription = resolveImplementationSubscriptionContext(extChatId);
            if (!hasSubscription(subscription)) {
                ImplementationCreateContext context = new ImplementationCreateContext();
                String draftRegionId = resolveRegionIdBySubmitter(loginUserId);
                context.setDefaultSubmitterUserId(loginUserId);
                context.setDefaultSubmitterName(resolveSubmitterName(loginUserId));
                context.setRegionId(draftRegionId);
                context.setRegionName(resolveRegionName(draftRegionId));
                context.setProductOptions(productOptions);
                context.setDraftMode(true);
                context.setSubscriptionDisplayText("客户信息待补全");
                return context;
            }
            return buildImplementationCreateContext(subscription, extChatId, loginUserId, productOptions);
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private ImplementationCreateContext buildImplementationCreateContext(
            SubscriptionContext subscription,
            String extChatId,
            String loginUserId,
            List<ImplementationProductOption> productOptions) {
        String implementationProductAlias = resolveImplementationProductAlias(subscription, extChatId);
        ImplementationProductOption productOption = resolveProductOption(productOptions, subscription.productId, subscription.productName);
        String template = resolveImplementationTemplate(implementationProductAlias, productOption, subscription.productName);
        String formType = resolveImplementationFormType(implementationProductAlias, productOption, null);

        ImplementationCreateContext context = new ImplementationCreateContext();
        context.setSubscriptionId(subscription.subscriptionId);
        context.setClientId(subscription.clientId);
        context.setClientName(subscription.clientName);
        context.setContractNumber(subscription.contractNumber);
        context.setProductId(subscription.productId);
        context.setProductName(subscription.productName);
        context.setServiceTypeName(subscription.serviceTypeName);
        context.setSalesName(resolveSalesName(subscription.clientId));
        context.setRegionId(subscription.regionId);
        context.setRegionName(resolveRegionName(subscription.regionId));
        context.setSubscriptionStartDate(subscription.subscriptionStartDate);
        context.setSupportEndDate(subscription.supportEndDate);
        context.setDefaultSubmitterUserId(loginUserId);
        context.setDefaultSubmitterName(resolveSubmitterName(loginUserId));
        context.setProductAlias(implementationProductAlias);
        context.setTemplate(template);
        context.setFormType(formType);
        context.setProductOptions(productOptions);
        context.setDraftMode(false);
        context.setSubscriptionDisplayText(buildSubscriptionDisplayText(subscription));
        return context;
    }

    public List<ImplementationProductOption> getImplementationProductOptions() {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            return queryImplementationProductOptions();
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private List<ImplementationProductOption> queryImplementationProductOptions() {
        List<ProductServiceRow> productRows = queryProductServiceRows();
        List<ImplementationProductOption> options = new ArrayList<>();
        for (ImplementationProductSpec spec : IMPLEMENTATION_PRODUCT_SPECS) {
            Long productId = resolveImplementationProductId(productRows, spec);
            options.add(new ImplementationProductOption(
                    productId,
                    spec.productName,
                    spec.productAlias,
                    spec.template,
                    spec.formType
            ));
        }
        return options;
    }

    private List<ProductServiceRow> queryProductServiceRows() {
        List<ProductServiceRow> rows = new ArrayList<>();
        try {
            String sql = "SELECT sps.product_id, sps.name " +
                    "FROM support_product_service sps " +
                    "WHERE sps.product_id IS NOT NULL " +
                    "  AND sps.name IS NOT NULL " +
                    "  AND sps.name != '' " +
                    "ORDER BY sps.product_id";
            var result = com.util.JdbcUtils.query(sql);
            for (Object[] row : result) {
                Long productId = toLong(row[0]);
                String productName = trimToNull(toStringValue(row[1]));
                if (productId != null && productName != null) {
                    rows.add(new ProductServiceRow(productId, productName));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("queryProductServiceRows failed, use fallback implementation product ids: {}", e.getMessage());
        }
        return rows;
    }

    private Long resolveImplementationProductId(List<ProductServiceRow> productRows, ImplementationProductSpec spec) {
        if (productRows != null && !productRows.isEmpty()) {
            for (ProductServiceRow row : productRows) {
                if (matchesImplementationProductSpec(row.productName, spec)) {
                    return row.productId;
                }
            }
        }
        return spec.fallbackProductId;
    }

    private boolean matchesImplementationProductSpec(String productName, ImplementationProductSpec spec) {
        String value = productName == null ? "" : productName.toUpperCase(Locale.ROOT);
        for (String keyword : spec.requiredKeywords) {
            if (!value.contains(keyword.toUpperCase(Locale.ROOT))) {
                return false;
            }
        }
        if (spec.preferredKeywords.isEmpty()) {
            return true;
        }
        for (String keyword : spec.preferredKeywords) {
            if (value.contains(keyword.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private ImplementationProductOption buildImplementationProductOption(Long productId, String productName, String latestTemplate) {
        String productAlias = inferImplementationProductAlias(productId, productName);
        String formType = switch (productAlias) {
            case "JS" -> IMPLEMENTATION_FORM_TYPE_JS;
            case "MK" -> IMPLEMENTATION_FORM_TYPE_MK;
            case "DE" -> IMPLEMENTATION_FORM_TYPE_DE;
            case "SQLBOT" -> IMPLEMENTATION_FORM_TYPE_SQLBOT;
            default -> IMPLEMENTATION_FORM_TYPE_GENERIC;
        };
        String template = switch (productAlias) {
            case "JS" -> "JumpServer";
            case "MK" -> resolveMaxKbImplementationTemplate(productId, productName, latestTemplate);
            case "DE" -> "DataEaseV2";
            case "SQLBOT" -> firstNonBlank(latestTemplate, "SQLBot");
            default -> firstNonBlank(latestTemplate, productName);
        };
        return new ImplementationProductOption(productId, productName, productAlias, template, formType);
    }

    private String resolveMaxKbImplementationTemplate(Long productId, String productName, String latestTemplate) {
        String value = productName == null ? "" : productName.toUpperCase(Locale.ROOT);
        if (value.contains("专业") ||
                value.contains("PRO") ||
                value.contains("PROFESSIONAL")) {
            return "MaxKBV2_PRO";
        }
        if (value.contains("企业") ||
                value.contains("EE") ||
                value.contains("ENTERPRISE")) {
            return "MaxKBV2_EE";
        }
        if (Long.valueOf(2009L).equals(productId) ||
                Long.valueOf(2013L).equals(productId)) {
            return "MaxKBV2_PRO";
        }
        if (Long.valueOf(2012L).equals(productId)) {
            return "MaxKBV2_EE";
        }
        return firstNonBlank(latestTemplate, "MaxKBV2_PRO");
    }

    private String inferImplementationProductAlias(Long productId, String productName) {
        if (productId != null) {
            if (productId == 2001L) {
                return "JS";
            }
            if (MAXKB_PRODUCT_IDS.contains(productId)) {
                return "MK";
            }
            if (DATAEASE_PRODUCT_IDS.contains(productId)) {
                return "DE";
            }
        }
        String value = productName == null ? "" : productName.toUpperCase(Locale.ROOT);
        if (value.contains("JUMPSERVER") || value.contains("JS")) {
            return "JS";
        }
        if (value.contains("MAXKB") || value.contains("MK")) {
            return "MK";
        }
        if (value.contains("DATAEASE") || value.contains("DE")) {
            return "DE";
        }
        if (value.contains("SQLBOT")) {
            return "SQLBOT";
        }
        return "OTHER";
    }

    private ImplementationProductOption resolveProductOption(List<ImplementationProductOption> productOptions, Long productId, String productName) {
        if (productOptions != null && productId != null) {
            for (ImplementationProductOption option : productOptions) {
                if (productId.equals(option.getProductId())) {
                    return option;
                }
            }
        }
        if (productId != null || trimToNull(productName) != null) {
            return buildImplementationProductOption(productId, productName, null);
        }
        return null;
    }

    private ImplementationProductOption resolveFixedImplementationProductOption(Long productId, String template, String formType) {
        String normalizedTemplate = trimToNull(template);
        String normalizedFormType = normalizeImplementationFormType(formType);
        for (ImplementationProductSpec spec : IMPLEMENTATION_PRODUCT_SPECS) {
            if (productId != null && productId.equals(spec.fallbackProductId)) {
                return new ImplementationProductOption(
                        spec.fallbackProductId,
                        spec.productName,
                        spec.productAlias,
                        spec.template,
                        spec.formType
                );
            }
            if (normalizedTemplate != null && normalizedTemplate.equals(spec.template)) {
                return new ImplementationProductOption(
                        spec.fallbackProductId,
                        spec.productName,
                        spec.productAlias,
                        spec.template,
                        spec.formType
                );
            }
            if (normalizedFormType != null && normalizedFormType.equals(spec.formType)) {
                return new ImplementationProductOption(
                        productId != null ? productId : spec.fallbackProductId,
                        spec.productName,
                        spec.productAlias,
                        spec.template,
                        spec.formType
                );
            }
        }
        return buildImplementationProductOption(productId, null, normalizedTemplate);
    }

    private String resolveImplementationTemplate(String implementationProductAlias, ImplementationProductOption productOption, String productName) {
        if (productOption != null && trimToNull(productOption.getTemplate()) != null) {
            return trimToNull(productOption.getTemplate());
        }
        return switch (nullToEmpty(implementationProductAlias)) {
            case "JS" -> "JumpServer";
            case "MK" -> "MaxKBV2_PRO";
            case "DE" -> "DataEaseV2";
            case "SQLBOT" -> "SQLBot";
            default -> firstNonBlank(productName, "Default");
        };
    }

    private String resolveImplementationFormType(String implementationProductAlias, ImplementationProductOption productOption, String requestFormType) {
        String normalizedRequestFormType = normalizeImplementationFormType(requestFormType);
        if (normalizedRequestFormType != null) {
            return normalizedRequestFormType;
        }
        if (productOption != null) {
            String normalizedOptionFormType = normalizeImplementationFormType(productOption.getFormType());
            if (normalizedOptionFormType != null) {
                return normalizedOptionFormType;
            }
        }
        String normalizedAlias = normalizeImplementationFormType(implementationProductAlias);
        return normalizedAlias != null ? normalizedAlias : IMPLEMENTATION_FORM_TYPE_GENERIC;
    }

    private String normalizeImplementationFormType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "JS", "JUMPSERVER" -> IMPLEMENTATION_FORM_TYPE_JS;
            case "MK", "MAXKB" -> IMPLEMENTATION_FORM_TYPE_MK;
            case "DE", "DATAEASE" -> IMPLEMENTATION_FORM_TYPE_DE;
            case "SQLBOT" -> IMPLEMENTATION_FORM_TYPE_SQLBOT;
            default -> IMPLEMENTATION_FORM_TYPE_GENERIC;
        };
    }

    public MaintenanceCreateContext getMaintenanceCreateContext(String extChatId, String loginUserId) throws Exception {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            throw new Exception("缺少群聊ID");
        }
        String normalizedLoginUserId = nullToEmpty(trimToNull(loginUserId));
        return queryMaintenanceCreateContext(normalizedExtChatId, normalizedLoginUserId);
    }

    private MaintenanceCreateContext queryMaintenanceCreateContext(String extChatId, String loginUserId) {
        MaintenanceCreateContext context = new MaintenanceCreateContext();
        context.setDefaultSubmitterName(resolveMaintenanceDefaultSubmitterName(loginUserId));
        return context;
    }

    public JSONObject createMaintenanceRecord(CreateMaintenanceRecordRequest request, String loginUserId) throws Exception {
        if (request.getClientId() == null) {
            throw new Exception("缺少客户ID");
        }
        if (request.getProductId() == null) {
            throw new Exception("缺少产品ID");
        }
        if (request.getMaintenanceTime() == null) {
            throw new Exception("缺少维护日期");
        }
        if (request.getMaintenanceTypes() == null || request.getMaintenanceTypes().isEmpty()) {
            throw new Exception("缺少维护类型");
        }
        if (request.getMaintenanceTitle() == null || request.getMaintenanceTitle().isEmpty()) {
            throw new Exception("缺少概述");
        }
        if (request.getMaintenanceVersion() == null || request.getMaintenanceVersion().isEmpty()) {
            throw new Exception("缺少版本");
        }
        if (request.getMaintenanceContext() == null || request.getMaintenanceContext().isEmpty()) {
            throw new Exception("缺少详细过程记录");
        }

        String regionId = request.getRegionId();
        if (regionId == null || regionId.isEmpty()) {
            regionId = resolveRegionId(request.getClientId(), request.getExtChatId());
        }
        if (regionId == null || regionId.isEmpty()) {
            throw new Exception("缺少区域ID，无法提交维护记录");
        }

        String submitterName = trimToNull(request.getSubmitterName());
        String ownerId;
        if (submitterName != null) {
            ownerId = resolveMaintenanceSubmitterUserId(submitterName);
        } else {
            String originalUserId = request.getOwnerId();
            if (originalUserId == null || originalUserId.isEmpty()) {
                originalUserId = request.getEditorUserId();
            }
            if (originalUserId == null || originalUserId.isEmpty()) {
                originalUserId = loginUserId;
            }
            if (originalUserId == null || originalUserId.isEmpty()) {
                throw new Exception("缺少提交人ID");
            }
            SupportUserResolution resolution = resolveSupportUserIdDetails(originalUserId);
            ownerId = resolution.resolvedUserId;
            LOGGER.info(
                    "createMaintenanceRecord owner resolution originalUserId={}, resolvedOwnerId={}, uuidCandidate={}, " +
                            "staffHit={}, staffName={}, staffEmail={}, directSupportUserHit={}, directSupportUserId={}, " +
                            "supportUserByStaffHit={}, supportUserByStaffId={}, fallbackToOriginal={}",
                    resolution.originalCandidate,
                    resolution.resolvedUserId,
                    resolution.uuidCandidate,
                    resolution.staffHit,
                    resolution.staffName,
                    resolution.staffEmail,
                    resolution.directSupportUserHit,
                    resolution.directSupportUserId,
                    resolution.supportUserByStaffHit,
                    resolution.supportUserByStaffId,
                    resolution.fallbackToOriginal
            );
        }

        JSONObject payload = new JSONObject();
        payload.put("clientId", request.getClientId());
        payload.put("editorUserId", ownerId);
        payload.put("creatorId", ownerId);
        payload.put("updaterId", ownerId);
        payload.put("maintenanceTypes", request.getMaintenanceTypes());
        payload.put("maintenanceTitle", request.getMaintenanceTitle());
        payload.put("maintenanceTime", request.getMaintenanceTime());
        payload.put("regionId", regionId);
        payload.put("maintenanceVersion", request.getMaintenanceVersion());
        payload.put("maintenanceContext", request.getMaintenanceContext());
        payload.put("productId", request.getProductId());

        String url = cscrmBaseUrl + cscrmApiPath + "/support-info/maintenance-records";
        LOGGER.info("createMaintenanceRecord request url={}, payload={}", url, payload.toJSONString());
        String response = HttpClientUtil.postJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);
        LOGGER.info("createMaintenanceRecord response={}", response);

        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            throw new Exception(responseJson.getString("msg"));
        }
        return responseJson.getJSONObject("data");
    }

    public JSONObject createImplementationRecord(CreateImplementationRecordRequest request, String loginUserId) throws Exception {
        String extChatId = trimToNull(request.getExtChatId());
        if (extChatId == null) {
            throw new Exception("缺少群聊ID");
        }

        SubscriptionContext subscription = resolveImplementationSubscriptionContext(extChatId);
        boolean draftMode = !hasSubscription(subscription);

        if (!draftMode && subscription.clientId == null && request.getClientId() == null) {
            throw new Exception("缺少客户ID");
        }
        Long selectedProductId = request.getSelectedProductId() != null ? request.getSelectedProductId() : request.getProductId();
        if (selectedProductId == null && !draftMode) {
            selectedProductId = subscription.productId;
        }
        if (selectedProductId == null) {
            throw new Exception("缺少产品ID");
        }
        if (request.getDeploymentDate() == null || request.getDeploymentDate().isEmpty()) {
            throw new Exception("缺少部署日期");
        }
        if (request.getDeploymentMethod() == null || request.getDeploymentMethod().isEmpty()) {
            throw new Exception("缺少部署方式");
        }
        if (request.getVersion() == null || request.getVersion().isEmpty()) {
            throw new Exception("缺少软件版本");
        }
        ImplementationProductOption productOption;
        String implementationProductAlias;
        if (draftMode) {
            productOption = resolveFixedImplementationProductOption(selectedProductId, request.getTemplate(), request.getFormType());
            implementationProductAlias = productOption != null
                    ? productOption.getProductAlias()
                    : normalizeImplementationFormType(request.getFormType());
        } else {
            List<ImplementationProductOption> productOptions = queryImplementationProductOptions();
            productOption = resolveProductOption(productOptions, selectedProductId, null);
            implementationProductAlias = resolveImplementationProductAlias(subscription, extChatId);
        }
        String formType = resolveImplementationFormType(implementationProductAlias, productOption, request.getFormType());
        String template = resolveImplementationTemplate(implementationProductAlias, productOption, request.getTemplate());
        validateImplementationRequestByProduct(formType, request);

        String regionId = trimToNull(request.getRegionId());
        String submitterName = trimToNull(request.getSubmitterName());
        SupportUserResolution resolution;
        String originalUserId;
        if (submitterName != null) {
            ImplementationSubmitterResolution submitterResolution = resolveImplementationSubmitter(submitterName);
            originalUserId = submitterResolution.staffExtId;
            resolution = submitterResolution.supportUserResolution;
            if (draftMode && regionId == null) {
                regionId = resolveRegionIdBySubmitter(submitterResolution.staffExtId);
            }
        } else {
            originalUserId = request.getEditorUserId();
            if (originalUserId == null || originalUserId.isEmpty()) {
                originalUserId = loginUserId;
            }
            if (originalUserId == null || originalUserId.isEmpty()) {
                throw new Exception("缺少提交人ID");
            }
            if (draftMode) {
                DraftSubmitterResolution submitterResolution = resolveDraftSubmitter(originalUserId);
                if (regionId == null) {
                    regionId = submitterResolution.regionId;
                }
                resolution = submitterResolution.supportUserResolution;
            } else {
                resolution = resolveSupportUserIdDetails(originalUserId);
            }
        }
        if (!draftMode && regionId == null) {
            Long clientId = subscription.clientId != null ? subscription.clientId : request.getClientId();
            regionId = resolveRegionId(clientId, extChatId);
        }
        if (regionId == null) {
            if (draftMode) {
                throw new Exception("缺少区域ID，无法根据提交人识别区域");
            }
            throw new Exception("缺少区域ID，无法提交实施记录");
        }

        if (!hasResolvedSupportUserId(resolution)) {
            if (submitterName != null) {
                throw new Exception("实施人未配置 support_user，请重新选择: " + submitterName);
            }
            throw new Exception("提交人未配置 support_user 或无法映射为 UUID: " + originalUserId);
        }
        String editorUserId = resolution.resolvedUserId;
        LOGGER.info(
                "createImplementationRecord owner resolution originalUserId={}, resolvedOwnerId={}, uuidCandidate={}, " +
                        "staffHit={}, staffName={}, staffEmail={}, directSupportUserHit={}, directSupportUserId={}, " +
                        "supportUserByStaffHit={}, supportUserByStaffId={}, fallbackToOriginal={}",
                resolution.originalCandidate,
                resolution.resolvedUserId,
                resolution.uuidCandidate,
                resolution.staffHit,
                resolution.staffName,
                resolution.staffEmail,
                resolution.directSupportUserHit,
                resolution.directSupportUserId,
                resolution.supportUserByStaffHit,
                resolution.supportUserByStaffId,
                resolution.fallbackToOriginal
        );

        JSONObject payload = new JSONObject(true);
        payload.put("subscriptionId", draftMode ? 0L : subscription.subscriptionId);
        payload.put("chat_id", extChatId);
        payload.put("groupChatId", extChatId);
        payload.put("group_chat_id", extChatId);
        payload.put("status", draftMode ? "DRAFT" : "DEPLOYED");
        payload.put("editorUserId", editorUserId);
        payload.put("regionId", regionId);
        payload.put("content", buildImplementationContent(formType, template, request));

        String url = cscrmBaseUrl + cscrmApiPath + "/support-info/maintenances";
        LOGGER.info("createImplementationRecord request url={}, payload={}", url, payload.toJSONString());
        String response = HttpClientUtil.postJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);
        LOGGER.info("createImplementationRecord response={}", response);

        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            String message = responseJson.getString("msg");
            if (message == null || message.isEmpty()) {
                message = responseJson.getString("message");
            }
            throw new Exception(message != null ? message : "新增实施记录失败");
        }
        JSONObject data = responseJson.getJSONObject("data");
        if (draftMode) {
            validateDraftCreateResponse(extChatId, regionId, data);
        }
        if (data != null && data.getLong("id") != null) {
            data.put("record", toMaintenanceRecord(data));
        }
        return data;
    }

    private void validateDraftCreateResponse(String extChatId, String expectedRegionId, JSONObject data) throws Exception {
        Long createdId = data != null ? data.getLong("id") : null;
        if (createdId == null || createdId <= 0) {
            throw new Exception("CSCRM接口未返回草稿记录ID，新增草稿未确认写入");
        }
        String status = trimToNull(data.getString("status"));
        if (!"DRAFT".equalsIgnoreCase(status)) {
            throw new Exception("CSCRM接口未返回草稿状态，新增草稿未确认写入");
        }
        String returnedGroupChatId = firstNonBlank(
                data.getString("group_chat_id"),
                data.getString("groupChatId"),
                data.getString("chat_id")
        );
        if (!extChatId.equals(returnedGroupChatId)) {
            throw new Exception("CSCRM接口返回的群聊ID不匹配，新增草稿未确认写入");
        }
        String returnedRegionId = firstNonBlank(data.getString("region_id"), data.getString("regionId"));
        if (returnedRegionId == null) {
            throw new Exception("CSCRM接口未返回区域ID，新增草稿未确认写入");
        }
        if (trimToNull(expectedRegionId) != null && !expectedRegionId.equals(returnedRegionId)) {
            throw new Exception("CSCRM接口返回的区域ID不匹配，新增草稿未确认写入");
        }
        if (trimToNull(data.getString("content")) == null) {
            throw new Exception("CSCRM接口未返回草稿内容，新增草稿未确认写入");
        }
    }

    private void validateImplementationRequestByProduct(String implementationProductAlias, CreateImplementationRecordRequest request) throws Exception {
        switch (implementationProductAlias) {
            case "JS" -> {
                if (request.getAssetTypes() == null || request.getAssetTypes().isEmpty()) {
                    throw new Exception("缺少纳管资产类型");
                }
                if (isBlank(request.getAssetCount())) {
                    throw new Exception("缺少管理资产数");
                }
                if (isBlank(request.getVirtualizationType())) {
                    throw new Exception("缺少虚拟化类型");
                }
                if (isBlank(request.getApplicationServer())) {
                    throw new Exception("缺少应用发布服务器");
                }
                if (isBlank(request.getDatabaseSync())) {
                    throw new Exception("缺少数据同步配置");
                }
                if (isBlank(request.getDatabaseExternal())) {
                    throw new Exception("缺少数据库外置配置");
                }
                if (isBlank(request.getRedisExternal())) {
                    throw new Exception("缺少 Redis 外置配置");
                }
                if (isBlank(request.getSharedNfs())) {
                    throw new Exception("缺少共享存储配置");
                }
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (isBlank(request.getDeploymentRecord())) {
                    throw new Exception("缺少记录内容");
                }
            }
            case "MK" -> {
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (request.getAuthMethods() == null || request.getAuthMethods().isEmpty()) {
                    throw new Exception("缺少认证方式");
                }
                if (request.getBusinessDirections() == null || request.getBusinessDirections().isEmpty()) {
                    throw new Exception("缺少应用方向");
                }
            }
            case "DE" -> {
                if (isBlank(request.getBackupMethod())) {
                    throw new Exception("缺少备份方式");
                }
                if (isBlank(request.getDataEaseDatabase())) {
                    throw new Exception("缺少数据库配置");
                }
                if (isBlank(request.getDorisUsage())) {
                    throw new Exception("缺少 Doris 配置");
                }
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (isBlank(request.getDataSourceType())) {
                    throw new Exception("缺少数据源类型");
                }
                if (isBlank(request.getDataScale())) {
                    throw new Exception("缺少数据量规模");
                }
                if (request.getAuthMethods() == null || request.getAuthMethods().isEmpty()) {
                    throw new Exception("缺少认证方式");
                }
                if (isBlank(request.getEmbeddedMode())) {
                    throw new Exception("缺少嵌入配置");
                }
                if (isBlank(request.getCustomerJoined())) {
                    throw new Exception("缺少客户接入状态");
                }
                if (isBlank(request.getAnalysisDirection())) {
                    throw new Exception("缺少分析及展示方向");
                }
                if (isBlank(request.getCustomerFocus())) {
                    throw new Exception("缺少客户核心关注点");
                }
            }
            case "SQLBOT" -> {
                if (isBlank(request.getBackupMethod())) {
                    throw new Exception("缺少备份方式");
                }
                if (isBlank(request.getDatabaseExternal())) {
                    throw new Exception("缺少 PostgreSQL 外置配置");
                }
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (isBlank(request.getDataSourceType())) {
                    throw new Exception("缺少数据源类型");
                }
                if (isBlank(request.getAiModelType())) {
                    throw new Exception("缺少 AI模型类型");
                }
                if (request.getAuthMethods() == null || request.getAuthMethods().isEmpty()) {
                    throw new Exception("缺少第三方平台对接");
                }
                if (isBlank(request.getEmbeddedMode())) {
                    throw new Exception("缺少嵌入集成配置");
                }
            }
            case "GENERIC" -> {
                // 通用产品只校验公共字段，公共字段已在入口统一校验。
            }
            default -> throw new Exception("当前群聊暂不支持新增实施记录");
        }
    }

    public List<ContractSubscription> getContractSubscriptions(String extChatId) throws Exception {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            throw new Exception("缺少群聊ID");
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            SubscriptionContext subscription = resolveImplementationSubscriptionContext(normalizedExtChatId);
            String clientName = subscription != null ? trimToNull(subscription.clientName) : null;
            String productAlias = resolveContractProductAlias(normalizedExtChatId, subscription);
            List<ContractSubscription> subscriptions = fetchContractSubscriptions(clientName, productAlias);
            LOGGER.info("getContractSubscriptions by clientName extChatId={}, clientName={}, productAlias={}, count={}",
                    normalizedExtChatId, clientName, productAlias, subscriptions.size());
            return subscriptions;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private String resolveContractProductAlias(String extChatId, SubscriptionContext subscription) {
        String groupAlias = getProductAliasByExtChatId(extChatId);
        if (isSupportedImplementationAlias(groupAlias)) {
            return groupAlias;
        }
        return resolveImplementationProductAlias(subscription, extChatId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean hasSubscription(SubscriptionContext subscription) {
        return subscription != null
                && subscription.subscriptionId != null
                && subscription.subscriptionId > 0;
    }

    private boolean hasResolvedSupportUserId(SupportUserResolution resolution) {
        return resolution != null
                && resolution.resolvedUserId != null
                && !resolution.resolvedUserId.isEmpty()
                && !resolution.fallbackToOriginal;
    }

    private DraftSubmitterResolution resolveDraftSubmitter(String submitterUserId) {
        DraftSubmitterResolution draftResolution = new DraftSubmitterResolution();
        SupportUserResolution userResolution = new SupportUserResolution(submitterUserId);
        draftResolution.supportUserResolution = userResolution;

        String normalizedSubmitterUserId = trimToNull(submitterUserId);
        if (normalizedSubmitterUserId == null) {
            userResolution.fallbackToOriginal = true;
            return draftResolution;
        }
        if (isUuid(normalizedSubmitterUserId)) {
            userResolution.uuidCandidate = true;
            userResolution.resolvedUserId = normalizedSubmitterUserId;
            draftResolution.regionId = resolveRegionIdBySubmitter(normalizedSubmitterUserId);
            return draftResolution;
        }

        Long departmentId = null;
        com.util.JdbcUtils.setCscrmConfig();
        try {
            try {
                String staffSql = "SELECT sd.ext_department_id, s.name, s.email " +
                        "FROM staff s " +
                        "LEFT JOIN staff_department sd ON s.id = sd.staff_id " +
                        "WHERE s.ext_id = ? LIMIT 1";
                var staffResult = com.util.JdbcUtils.query(staffSql, normalizedSubmitterUserId);
                if (!staffResult.isEmpty()) {
                    userResolution.staffHit = true;
                    departmentId = toLong(staffResult.get(0)[0]);
                    userResolution.staffName = staffResult.get(0)[1] != null ? staffResult.get(0)[1].toString() : null;
                    userResolution.staffEmail = staffResult.get(0)[2] != null ? staffResult.get(0)[2].toString() : null;
                }
            } catch (Exception ignored) {
                // ignore and try direct support_user match
            }

            String regionName = resolveRegionNameByDepartmentId(departmentId);
            if (regionName != null) {
                draftResolution.regionId = resolveRegionIdByName(regionName);
                LOGGER.info("resolveDraftSubmitter region submitterUserId={}, departmentId={}, regionName={}, regionId={}",
                        normalizedSubmitterUserId, departmentId, regionName, draftResolution.regionId);
            }

            String staffEmail = trimToNull(userResolution.staffEmail);
            String staffName = trimToNull(userResolution.staffName);
            if (staffEmail != null || staffName != null) {
                try {
                    String supportSql = "SELECT su.user_id FROM support_user su " +
                            "WHERE (? IS NOT NULL AND su.email = ?) " +
                            "   OR (? IS NOT NULL AND su.name = ?) " +
                            "LIMIT 1";
                    var supportResult = com.util.JdbcUtils.query(supportSql, staffEmail, staffEmail, staffName, staffName);
                    if (!supportResult.isEmpty() && supportResult.get(0)[0] != null) {
                        userResolution.supportUserByStaffHit = true;
                        userResolution.supportUserByStaffId = supportResult.get(0)[0].toString();
                        userResolution.resolvedUserId = userResolution.supportUserByStaffId;
                        return draftResolution;
                    }
                } catch (Exception ignored) {
                    // ignore and try direct support_user match
                }
            }

            try {
                String supportSql = "SELECT su.user_id FROM support_user su " +
                        "WHERE su.username = ? OR su.name = ? OR su.email = ? LIMIT 1";
                var supportResult = com.util.JdbcUtils.query(supportSql,
                        normalizedSubmitterUserId, normalizedSubmitterUserId, normalizedSubmitterUserId);
                if (!supportResult.isEmpty() && supportResult.get(0)[0] != null) {
                    userResolution.directSupportUserHit = true;
                    userResolution.directSupportUserId = supportResult.get(0)[0].toString();
                    userResolution.resolvedUserId = userResolution.directSupportUserId;
                    return draftResolution;
                }
            } catch (Exception ignored) {
                // ignore and fallback below
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }

        userResolution.resolvedUserId = normalizedSubmitterUserId;
        userResolution.fallbackToOriginal = true;
        return draftResolution;
    }

    private JSONObject buildImplementationContent(String formType, String template, CreateImplementationRecordRequest request) throws Exception {
        return switch (formType) {
            case "JS" -> buildJumpServerImplementationContent(template, request);
            case "MK" -> buildMaxKbImplementationContent(template, request);
            case "DE" -> buildDataEaseImplementationContent(template, request);
            case "SQLBOT" -> buildSqlBotImplementationContent(template, request);
            case "GENERIC" -> buildGenericImplementationContent(template, request);
            default -> throw new Exception("当前群聊暂不支持新增实施记录");
        };
    }

    // 维护记录接口需要 support_user.user_id（UUID）。当前登录态常见为企业微信 userid（如 QiJingYu），这里做自动映射。
    private SupportUserResolution resolveSupportUserIdDetails(String candidate) {
        SupportUserResolution resolution = new SupportUserResolution(candidate);
        if (candidate == null || candidate.isEmpty()) {
            resolution.resolvedUserId = candidate;
            resolution.fallbackToOriginal = true;
            return resolution;
        }
        if (isUuid(candidate)) {
            resolution.uuidCandidate = true;
            resolution.resolvedUserId = candidate;
            return resolution;
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            // 1) 直接按 support_user.username / name / email 兜底匹配
            try {
                String sql = "SELECT su.user_id FROM support_user su " +
                        "WHERE su.username = ? OR su.name = ? OR su.email = ? LIMIT 1";
                var result = com.util.JdbcUtils.query(sql, candidate, candidate, candidate);
                if (!result.isEmpty() && result.get(0)[0] != null) {
                    resolution.directSupportUserHit = true;
                    resolution.directSupportUserId = result.get(0)[0].toString();
                    resolution.resolvedUserId = resolution.directSupportUserId;
                    return resolution;
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }

            // 2) 先从 staff 按 ext_id 找到姓名/邮箱，再映射 support_user.user_id
            try {
                String staffSql = "SELECT s.name, s.email FROM staff s WHERE s.ext_id = ? LIMIT 1";
                var staffResult = com.util.JdbcUtils.query(staffSql, candidate);
                if (!staffResult.isEmpty()) {
                    resolution.staffHit = true;
                    String name = staffResult.get(0)[0] != null ? staffResult.get(0)[0].toString() : null;
                    String email = staffResult.get(0)[1] != null ? staffResult.get(0)[1].toString() : null;
                    resolution.staffName = name;
                    resolution.staffEmail = email;

                    String supportSql = "SELECT su.user_id FROM support_user su " +
                            "WHERE (? IS NOT NULL AND su.email = ?) " +
                            "   OR (? IS NOT NULL AND su.name = ?) " +
                            "LIMIT 1";
                    var supportResult = com.util.JdbcUtils.query(supportSql, email, email, name, name);
                    if (!supportResult.isEmpty() && supportResult.get(0)[0] != null) {
                        resolution.supportUserByStaffHit = true;
                        resolution.supportUserByStaffId = supportResult.get(0)[0].toString();
                        resolution.resolvedUserId = resolution.supportUserByStaffId;
                        return resolution;
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
        resolution.resolvedUserId = candidate;
        resolution.fallbackToOriginal = true;
        return resolution;
    }

    private String resolveMaintenanceDefaultSubmitterName(String loginUserId) {
        String normalizedLoginUserId = trimToNull(loginUserId);
        if (normalizedLoginUserId == null) {
            return null;
        }

        String staffName;
        com.util.JdbcUtils.setCscrmConfig();
        try {
            staffName = resolveStaffNameByExtId(normalizedLoginUserId);
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
        if (isBlank(staffName)) {
            return null;
        }

        SupportUserResolution resolution = resolveSupportUserIdDetails(normalizedLoginUserId);
        if (!hasResolvedSupportUserId(resolution)) {
            return null;
        }
        return staffName;
    }

    private String resolveMaintenanceSubmitterUserId(String submitterName) throws Exception {
        String normalizedSubmitterName = trimToNull(submitterName);
        if (normalizedSubmitterName == null) {
            throw new Exception("缺少提交人");
        }

        String staffExtId = null;
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT s.ext_id FROM staff s WHERE s.name = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedSubmitterName);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                staffExtId = result.get(0)[0].toString();
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }

        if (isBlank(staffExtId)) {
            throw new Exception("未找到提交人: " + normalizedSubmitterName);
        }

        SupportUserResolution resolution = resolveSupportUserIdDetails(staffExtId);
        LOGGER.info(
                "createMaintenanceRecord submitter resolution submitterName={}, staffExtId={}, resolvedOwnerId={}, uuidCandidate={}, " +
                        "staffHit={}, staffName={}, staffEmail={}, directSupportUserHit={}, directSupportUserId={}, " +
                        "supportUserByStaffHit={}, supportUserByStaffId={}, fallbackToOriginal={}",
                normalizedSubmitterName,
                staffExtId,
                resolution.resolvedUserId,
                resolution.uuidCandidate,
                resolution.staffHit,
                resolution.staffName,
                resolution.staffEmail,
                resolution.directSupportUserHit,
                resolution.directSupportUserId,
                resolution.supportUserByStaffHit,
                resolution.supportUserByStaffId,
                resolution.fallbackToOriginal
        );

        if (!hasResolvedSupportUserId(resolution)) {
            throw new Exception("提交人未配置 support_user，请重新选择: " + normalizedSubmitterName);
        }
        return resolution.resolvedUserId;
    }

    private ImplementationSubmitterResolution resolveImplementationSubmitter(String submitterName) throws Exception {
        String normalizedSubmitterName = trimToNull(submitterName);
        if (normalizedSubmitterName == null) {
            throw new Exception("缺少实施人");
        }

        String staffExtId = null;
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT s.ext_id FROM staff s WHERE s.name = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedSubmitterName);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                staffExtId = result.get(0)[0].toString();
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }

        if (isBlank(staffExtId)) {
            throw new Exception("未找到实施人: " + normalizedSubmitterName);
        }

        SupportUserResolution resolution = resolveSupportUserIdDetails(staffExtId);
        LOGGER.info(
                "createImplementationRecord submitter resolution submitterName={}, staffExtId={}, resolvedOwnerId={}, uuidCandidate={}, " +
                        "staffHit={}, staffName={}, staffEmail={}, directSupportUserHit={}, directSupportUserId={}, " +
                        "supportUserByStaffHit={}, supportUserByStaffId={}, fallbackToOriginal={}",
                normalizedSubmitterName,
                staffExtId,
                resolution.resolvedUserId,
                resolution.uuidCandidate,
                resolution.staffHit,
                resolution.staffName,
                resolution.staffEmail,
                resolution.directSupportUserHit,
                resolution.directSupportUserId,
                resolution.supportUserByStaffHit,
                resolution.supportUserByStaffId,
                resolution.fallbackToOriginal
        );

        ImplementationSubmitterResolution submitterResolution = new ImplementationSubmitterResolution();
        submitterResolution.staffExtId = staffExtId;
        submitterResolution.supportUserResolution = resolution;
        return submitterResolution;
    }

    private static final class SupportUserResolution {
        private final String originalCandidate;
        private String resolvedUserId;
        private boolean uuidCandidate;
        private boolean staffHit;
        private String staffName;
        private String staffEmail;
        private boolean directSupportUserHit;
        private String directSupportUserId;
        private boolean supportUserByStaffHit;
        private String supportUserByStaffId;
        private boolean fallbackToOriginal;

        private SupportUserResolution(String originalCandidate) {
            this.originalCandidate = originalCandidate;
        }
    }

    private static final class DraftSubmitterResolution {
        private String regionId;
        private SupportUserResolution supportUserResolution;
    }

    private static final class ImplementationSubmitterResolution {
        private String staffExtId;
        private SupportUserResolution supportUserResolution;
    }

    private SubscriptionContext fetchSubscriptionContextFromApi(String extChatId) {
        try {
            String url = cscrmBaseUrl + cscrmApiPath +
                    "/support-info/subscriptions/latest-by-group?groupid=" +
                    URLEncoder.encode(extChatId, StandardCharsets.UTF_8);
            String response = requestWithDnsRetry(url);
            JSONObject responseJson = JSONObject.parseObject(response);
            Integer code = responseJson.getInteger("code");
            if (code == null || code != 0) {
                LOGGER.warn("fetchSubscriptionContextFromApi non-zero code extChatId={}, code={}", extChatId, code);
                return null;
            }
            JSONObject data = responseJson.getJSONObject("data");
            if (data == null) {
                return null;
            }
            SubscriptionContext context = toSubscriptionContext(data);
            String source = data.getString("source");
            LOGGER.info("fetchSubscriptionContextFromApi success extChatId={}, subscriptionId={}, clientId={}, source={}",
                    extChatId, context.subscriptionId, context.clientId, source);
            return context;
        } catch (Exception e) {
            LOGGER.warn("fetchSubscriptionContextFromApi failed extChatId={}, err={}", extChatId, e.getMessage());
            return null;
        }
    }

    private SubscriptionContext fetchSubscriptionContextForImplementation(String extChatId) throws Exception {
        String url = cscrmBaseUrl + cscrmApiPath +
                "/support-info/subscriptions/latest-by-group?groupid=" +
                URLEncoder.encode(extChatId, StandardCharsets.UTF_8);
        String response = requestWithDnsRetry(url);
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code == null || code != 0) {
            throw new Exception(firstNonBlank(
                    responseJson.getString("message"),
                    responseJson.getString("msg"),
                    "查询群聊订阅失败"
            ));
        }
        JSONObject data = responseJson.getJSONObject("data");
        if (data == null) {
            return null;
        }
        SubscriptionContext context = toSubscriptionContext(data);
        String source = data.getString("source");
        LOGGER.info("fetchSubscriptionContextForImplementation success extChatId={}, subscriptionId={}, clientId={}, source={}",
                extChatId, context.subscriptionId, context.clientId, source);
        return context;
    }

    private SubscriptionContext resolveProductAwareSubscriptionContextFromApi(String extChatId) {
        return resolveProductAwareSubscriptionContext(extChatId, fetchSubscriptionContextFromApi(extChatId));
    }

    private SubscriptionContext resolveImplementationSubscriptionContext(String extChatId) throws Exception {
        return resolveProductAwareSubscriptionContext(extChatId, fetchSubscriptionContextForImplementation(extChatId));
    }

    private SubscriptionContext resolveProductAwareSubscriptionContext(String extChatId, SubscriptionContext latest) {
        String groupAlias = getProductAliasByExtChatId(extChatId);
        if (!isSupportedImplementationAlias(groupAlias)) {
            return latest;
        }

        if (hasSubscription(latest) && matchesProductAlias(groupAlias, latest.productName)) {
            return latest;
        }

        SubscriptionContext matched = queryImplementationSubscriptionByGroupProduct(extChatId, latest, groupAlias);
        if (hasSubscription(matched)) {
            LOGGER.info(
                    "resolveImplementationSubscriptionContext product matched extChatId={}, groupAlias={}, latestSubscriptionId={}, latestProduct={}, matchedSubscriptionId={}, matchedProduct={}",
                    extChatId,
                    groupAlias,
                    latest != null ? latest.subscriptionId : null,
                    latest != null ? latest.productName : null,
                    matched.subscriptionId,
                    matched.productName
            );
            return matched;
        }

        if (hasSubscription(latest)) {
            LOGGER.warn(
                    "resolveImplementationSubscriptionContext product mismatch but no matched subscription extChatId={}, groupAlias={}, latestSubscriptionId={}, latestProduct={}",
                    extChatId,
                    groupAlias,
                    latest.subscriptionId,
                    latest.productName
            );
        }
        return latest;
    }

    private SubscriptionContext queryImplementationSubscriptionByGroupProduct(
            String extChatId,
            SubscriptionContext latest,
            String productAlias) {
        try {
            List<Object> params = new ArrayList<>();
            params.add(extChatId);

            StringBuilder sql = new StringBuilder("SELECT ss.id, " +
                    "       ss.client_id, " +
                    "       sc.name, " +
                    "       ss.contract_number, " +
                    "       sps.product_id, " +
                    "       sps.name, " +
                    "       ss.region_id, " +
                    "       ss.start_date, " +
                    "       ss.support_end_date " +
                    "FROM group_chat gc " +
                    "INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                    "LEFT JOIN support_client sc ON sc.id = ss.client_id " +
                    "LEFT JOIN support_product_service sps ON sps.id = ss.product_service_id " +
                    "WHERE gc.ext_chat_id = ? ");
            if (latest != null && latest.clientId != null) {
                sql.append("AND ss.client_id = ? ");
                params.add(latest.clientId);
            } else if (latest != null && trimToNull(latest.clientName) != null) {
                sql.append("AND sc.name = ? ");
                params.add(trimToNull(latest.clientName));
            }
            sql.append("AND ").append(buildProductAliasSqlCondition(productAlias)).append(" ");
            sql.append("ORDER BY ss.support_end_date DESC, ss.id DESC LIMIT 1");

            var result = com.util.JdbcUtils.query(sql.toString(), params.toArray());
            if (result.isEmpty()) {
                return null;
            }
            SubscriptionContext context = toSubscriptionContextFromRow(result.get(0));
            if (latest != null) {
                context.clientName = firstNonBlank(context.clientName, latest.clientName);
                context.regionId = firstNonBlank(context.regionId, latest.regionId);
                context.serviceTypeName = firstNonBlank(context.serviceTypeName, latest.serviceTypeName, "授权服务");
            }
            return context;
        } catch (Exception e) {
            LOGGER.warn("queryImplementationSubscriptionByGroupProduct failed extChatId={}, productAlias={}, err={}",
                    extChatId, productAlias, e.getMessage());
            return null;
        }
    }

    private String buildProductAliasSqlCondition(String productAlias) {
        return switch (productAlias) {
            case "JS" -> "(sps.product_id = 2001 OR UPPER(COALESCE(sps.name, '')) LIKE '%JUMPSERVER%' OR UPPER(COALESCE(sps.name, '')) LIKE '%JS%')";
            case "MK" -> "(sps.product_id IN (2009, 2013) OR UPPER(COALESCE(sps.name, '')) LIKE '%MAXKB%' OR UPPER(COALESCE(sps.name, '')) LIKE '%MK%')";
            case "DE" -> "(sps.product_id IN (2003, 2008) OR UPPER(COALESCE(sps.name, '')) LIKE '%DATAEASE%' OR UPPER(COALESCE(sps.name, '')) LIKE '%DE%')";
            case "SQLBOT" -> "(sps.product_id = 2011 OR UPPER(COALESCE(sps.name, '')) LIKE '%SQLBOT%')";
            default -> "1 = 0";
        };
    }

    private SubscriptionContext toSubscriptionContextFromRow(Object[] row) {
        SubscriptionContext context = new SubscriptionContext();
        context.subscriptionId = toLong(row[0]);
        context.clientId = toLong(row[1]);
        context.clientName = toStringValue(row[2]);
        context.contractNumber = toStringValue(row[3]);
        context.productId = toLong(row[4]);
        context.productName = toStringValue(row[5]);
        context.regionId = toStringValue(row[6]);
        context.subscriptionStartDate = formatEpochMillisDate(toStringValue(row[7]));
        context.supportEndDate = formatEpochMillisDate(toStringValue(row[8]));
        context.serviceTypeName = "授权服务";
        return context;
    }

    private SubscriptionContext toSubscriptionContext(JSONObject data) {
        SubscriptionContext context = new SubscriptionContext();
        context.subscriptionId = data.getLong("id");
        context.clientId = data.getLong("client_id");
        context.clientName = data.getString("client_name");
        context.contractNumber = data.getString("contract_number");
        context.productId = data.getLong("product_id");
        context.productName = data.getString("product_name");
        context.regionId = data.getString("region_id");
        context.subscriptionStartDate = data.getString("subscription_start_date");
        context.supportEndDate = data.getString("support_end_date");
        context.serviceTypeName = "授权服务";
        return context;
    }

    private boolean isSupportedImplementationAlias(String productAlias) {
        return "JS".equals(productAlias)
                || "MK".equals(productAlias)
                || "DE".equals(productAlias)
                || "SQLBOT".equals(productAlias);
    }

    private boolean matchesProductAlias(String productAlias, String productName) {
        if (productAlias == null || productName == null) {
            return false;
        }
        String value = productName.toUpperCase(Locale.ROOT);
        return switch (productAlias) {
            case "JS" -> value.contains("JUMPSERVER") || value.contains("JS");
            case "MK" -> value.contains("MAXKB") || value.contains("MK");
            case "DE" -> value.contains("DATAEASE") || value.contains("DE");
            case "SQLBOT" -> value.contains("SQLBOT");
            default -> false;
        };
    }

    private boolean isJumpServerSubscription(SubscriptionContext subscription, String extChatId) {
        if (subscription == null) {
            return false;
        }
        if (matchesProductAlias("JS", subscription.productName)) {
            return true;
        }
        String groupAlias = getProductAliasByExtChatId(extChatId);
        return "JS".equals(groupAlias);
    }

    private String resolveImplementationProductAlias(SubscriptionContext subscription, String extChatId) {
        if (subscription != null) {
            if (matchesProductAlias("JS", subscription.productName)) {
                return "JS";
            }
            if (matchesProductAlias("MK", subscription.productName)) {
                return "MK";
            }
            if (matchesProductAlias("DE", subscription.productName)) {
                return "DE";
            }
            if (matchesProductAlias("SQLBOT", subscription.productName)) {
                return "SQLBOT";
            }
        }
        String groupAlias = getProductAliasByExtChatId(extChatId);
        if (isSupportedImplementationAlias(groupAlias)) {
            return groupAlias;
        }
        return null;
    }

    private String resolveSalesName(Long clientId) {
        if (clientId == null) {
            return null;
        }
        try {
            String sql = "SELECT su.name " +
                    "FROM client_sales_users csu " +
                    "INNER JOIN sales_user su ON su.user_id = csu.sales_user_user_id " +
                    "WHERE csu.support_client_id = ? " +
                    "AND su.name IS NOT NULL AND su.name != '' " +
                    "ORDER BY su.name LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, clientId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception e) {
            LOGGER.warn("resolveSalesName failed clientId={}, err={}", clientId, e.getMessage());
        }
        return null;
    }

    private String resolveSubmitterName(String loginUserId) {
        String staffName = resolveStaffNameByExtId(loginUserId);
        if (staffName != null && !staffName.isEmpty()) {
            return staffName;
        }
        return loginUserId;
    }

    private String resolveRegionName(String regionId) {
        if (regionId == null || regionId.isEmpty()) {
            return null;
        }
        String[] candidates = {
                "SELECT name FROM region WHERE id = ? LIMIT 1",
                "SELECT name FROM support_region WHERE id = ? LIMIT 1",
                "SELECT name FROM work_region WHERE id = ? LIMIT 1"
        };
        for (String sql : candidates) {
            try {
                var result = com.util.JdbcUtils.query(sql, regionId);
                if (!result.isEmpty() && result.get(0)[0] != null) {
                    return result.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore and continue fallback
            }
        }
        return null;
    }

    private String buildSubscriptionDisplayText(SubscriptionContext subscription) {
        List<String> parts = new ArrayList<>();
        if (subscription.clientName != null && !subscription.clientName.isEmpty()) {
            parts.add(subscription.clientName);
        }
        if (subscription.productName != null && !subscription.productName.isEmpty()) {
            parts.add(subscription.productName);
        }
        if (subscription.contractNumber != null && !subscription.contractNumber.isEmpty()) {
            parts.add(subscription.contractNumber);
        }
        return String.join(" - ", parts);
    }

    private JSONObject buildJumpServerImplementationContent(String template, CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", firstNonBlank(template, "JumpServer"));
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "form1prop4", request.getVirtualizationType());
        addImplementationElement(elements, "form1prop11", request.getDeploymentArchitecture());
        addImplementationElement(elements, "form1prop3", request.getAssetCount());
        addImplementationElement(elements, "form1prop8", request.getApplicationServer());
        addImplementationElement(elements, "form1prop5", request.getDatabaseExternal());
        addImplementationElement(elements, "form1prop6", request.getRedisExternal());
        addImplementationElement(elements, "form1prop9", request.getDatabaseSync());
        addImplementationElement(elements, "form1prop7", request.getSharedNfs());
        addImplementationMultiValueElement(elements, "form1prop1", request.getAssetTypes());
        addImplementationElement(elements, "form1prop10", nullToEmpty(request.getCustomerFocus()));
        addImplementationElement(elements, "deploymentRecord", nullToEmpty(trimToNull(request.getDeploymentRecord())));
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        for (int i = 1; i <= 9; i++) {
            addImplementationElement(elements, "form2prop" + i, IMPLEMENTATION_DEFAULT_VALIDATION_VALUE);
        }
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private JSONObject buildMaxKbImplementationContent(String template, CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", firstNonBlank(template, "MaxKBV2_PRO"));
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "form1prop1", request.getDeploymentArchitecture());
        addImplementationMultiValueElement(elements, "form4prop1", request.getAuthMethods());
        addImplementationMultiValueElement(elements, "form4prop3", request.getBusinessDirections());
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private JSONObject buildDataEaseImplementationContent(String template, CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", firstNonBlank(template, "DataEaseV2"));
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "backupMethod", request.getBackupMethod());
        addImplementationElement(elements, "form1prop1", request.getDataEaseDatabase());
        addImplementationElement(elements, "form1prop2", request.getDorisUsage());
        addImplementationElement(elements, "form1prop3", request.getDeploymentArchitecture());
        addImplementationElement(elements, "form4prop1", request.getDataSourceType());
        addImplementationElement(elements, "form4prop2", request.getDataScale());
        addImplementationMultiValueElement(elements, "form4prop5", request.getAuthMethods());
        addImplementationElement(elements, "form4prop6", request.getEmbeddedMode());
        addImplementationElement(elements, "form4prop7", request.getCustomerJoined());
        addImplementationElement(elements, "form4prop3", request.getAnalysisDirection());
        addImplementationElement(elements, "form4prop4", nullToEmpty(trimToNull(request.getCustomerFocus())));
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private JSONObject buildSqlBotImplementationContent(String template, CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", firstNonBlank(template, "SQLBot"));
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "form1prop3", request.getDeploymentArchitecture());
        addImplementationElement(elements, "backupMethod", request.getBackupMethod());
        addImplementationElement(elements, "form1prop1", request.getDatabaseExternal());
        addImplementationElement(elements, "form4prop1", request.getDataSourceType());
        addImplementationElement(elements, "form4prop2", request.getAiModelType());
        addImplementationElement(elements, "form4prop6", request.getEmbeddedMode());
        addImplementationMultiValueElement(elements, "form4prop5", request.getAuthMethods());
        for (int i = 1; i <= 9; i++) {
            addImplementationElement(elements, "form2prop" + i, IMPLEMENTATION_DEFAULT_VALIDATION_VALUE);
        }
        addImplementationElement(elements, "form2prop10", "已提醒客户，已修改");
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private JSONObject buildGenericImplementationContent(String template, CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", firstNonBlank(template, request.getTemplate(), "Default"));
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private void addImplementationElement(JSONArray elements, String title, String value) {
        JSONObject element = new JSONObject();
        element.put("title", title);
        JSONObject contentMap = new JSONObject();
        contentMap.put("value1", nullToEmpty(value));
        element.put("contentMap", contentMap);
        elements.add(element);
    }

    private void addImplementationMultiValueElement(JSONArray elements, String title, List<String> values) {
        JSONObject element = new JSONObject();
        element.put("title", title);
        JSONObject contentMap = new JSONObject();
        int index = 1;
        if (values != null) {
            for (String value : values) {
                String normalized = trimToNull(value);
                if (normalized == null) {
                    continue;
                }
                contentMap.put("value" + index, normalized);
                index++;
            }
        }
        if (contentMap.isEmpty()) {
            contentMap.put("value1", "");
        }
        element.put("contentMap", contentMap);
        elements.add(element);
    }

    private long parseDeploymentDateToEpochMillis(String deploymentDate) throws Exception {
        try {
            return LocalDate.parse(deploymentDate).atStartOfDay(ZONE_SHANGHAI).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new Exception("部署日期格式不正确");
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeLegacyDeploymentTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "1970-01-01".equals(normalized)) {
            return null;
        }
        return formatEpochMillisDate(normalized);
    }

    private String buildCscrmUrl(String endpoint) {
        String baseUrl = trimToNull(cscrmBaseUrl);
        String apiPath = trimToNull(cscrmApiPath);
        String normalizedEndpoint = endpoint == null ? "" : endpoint.trim();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (apiPath == null) {
            apiPath = "";
        } else if (!apiPath.startsWith("/")) {
            apiPath = "/" + apiPath;
        }
        if (apiPath.endsWith("/")) {
            apiPath = apiPath.substring(0, apiPath.length() - 1);
        }
        if (!normalizedEndpoint.startsWith("/")) {
            normalizedEndpoint = "/" + normalizedEndpoint;
        }
        return nullToEmpty(baseUrl) + apiPath + normalizedEndpoint;
    }

    private String readEntityText(HttpEntity entity) throws Exception {
        if (entity == null) {
            return "";
        }
        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
    }

    private void writeSseError(OutputStream outputStream, JSONObject error) throws IOException {
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer.write("event:error\n");
        writer.write("data:");
        writer.write(error.toJSONString());
        writer.write("\n\n");
        writer.flush();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveExtChatIdByTicketId(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return null;
        }
        try {
            String sql = "SELECT room_id FROM chat_analysis_tickets WHERE id = ? AND deleted_at IS NULL LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedTicketId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return trimToNull(result.get(0)[0].toString());
            }
        } catch (Exception e) {
            LOGGER.warn("resolveExtChatIdByTicketId failed ticketId={}, err={}", normalizedTicketId, e.getMessage());
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        String firstTrimmed = trimToNull(first);
        if (firstTrimmed != null) {
            return firstTrimmed;
        }
        return trimToNull(second);
    }

    private ImplementationContentSnapshot parseImplementationContentSnapshot(String rawContent) {
        ImplementationContentSnapshot snapshot = new ImplementationContentSnapshot();
        if (rawContent == null || rawContent.isBlank()) {
            return snapshot;
        }
        try {
            JSONObject contentJson = JSONObject.parseObject(rawContent);
            JSONArray elements = contentJson.getJSONArray("elements");
            if (elements == null) {
                return snapshot;
            }
            for (int i = 0; i < elements.size(); i++) {
                JSONObject element = elements.getJSONObject(i);
                if (element == null) {
                    continue;
                }
                String title = trimToNull(element.getString("title"));
                if (title == null) {
                    continue;
                }
                JSONObject contentMap = element.getJSONObject("contentMap");
                List<String> values = extractContentMapValues(contentMap);
                String value = values.isEmpty() ? null : values.get(0);
                if (value == null) {
                    continue;
                }
                switch (title) {
                    case "template" -> snapshot.template = value;
                    case "deploymentTime" -> snapshot.deploymentTime = formatEpochMillisDate(value);
                    case "deploymentMethod" -> snapshot.deploymentMethod = value;
                    case "version" -> snapshot.version = value;
                    case "backupMethod" -> snapshot.backupMethod = value;
                    case "virtualizationType", "form1prop4" -> snapshot.virtualizationType = value;
                    case "form1prop11" -> snapshot.deploymentArchitecture = value;
                    case "form1prop3" -> {
                        if (isTemplate(snapshot.template, "SQLBot")) {
                            snapshot.deploymentArchitecture = value;
                        } else {
                            snapshot.assetCount = value;
                        }
                    }
                    case "form1prop8" -> snapshot.applicationServer = value;
                    case "form1prop5" -> snapshot.databaseExternal = value;
                    case "form1prop6" -> snapshot.redisExternal = value;
                    case "form1prop9" -> snapshot.databaseSync = value;
                    case "form1prop7" -> snapshot.sharedNfs = value;
                    case "form1prop1", "assetTypes" -> {
                        if (isTemplate(snapshot.template, "JumpServer")) {
                            snapshot.assetTypes = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "MaxKB")) {
                            snapshot.deploymentArchitecture = value;
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.dataEaseDatabase = value;
                        } else if (isTemplate(snapshot.template, "SQLBot")) {
                            snapshot.databaseExternal = value;
                        }
                    }
                    case "form1prop2" -> {
                        if (isTemplate(snapshot.template, "JumpServer")) {
                            snapshot.assetTypes = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.dorisUsage = value;
                        }
                    }
                    case "form4prop1" -> {
                        if (isTemplate(snapshot.template, "MaxKB")) {
                            snapshot.authMethods = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.dataSourceType = value;
                        } else if (isTemplate(snapshot.template, "SQLBot")) {
                            snapshot.dataSourceType = value;
                        }
                    }
                    case "form4prop2" -> {
                        if (isTemplate(snapshot.template, "SQLBot")) {
                            snapshot.aiModelType = value;
                        } else {
                            snapshot.dataScale = value;
                        }
                    }
                    case "form4prop3" -> {
                        if (isTemplate(snapshot.template, "MaxKB")) {
                            snapshot.businessDirections = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.analysisDirection = value;
                        }
                    }
                    case "form4prop4" -> snapshot.customerFocus = value;
                    case "form4prop5" -> snapshot.authMethods = String.join("、", values);
                    case "form4prop6" -> snapshot.embeddedMode = value;
                    case "form4prop7" -> snapshot.customerJoined = value;
                    case "form1prop10", "customerFocus" -> snapshot.customerFocus = value;
                    case "deploymentRecord" -> snapshot.deploymentRecord = value;
                    case "remainingIssues", "form3prop1" -> snapshot.remainingIssues = value;
                    case "remark", "form3prop2" -> snapshot.remark = value;
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("parseImplementationContentSnapshot failed, err={}", e.getMessage());
        }
        splitLegacyDeploymentRecordSections(snapshot);
        return snapshot;
    }

    private void splitLegacyDeploymentRecordSections(ImplementationContentSnapshot snapshot) {
        String deploymentRecord = trimToNull(snapshot.deploymentRecord);
        if (deploymentRecord == null) {
            return;
        }

        int remainingIndex = deploymentRecord.indexOf("\n\n遗留问题：");
        int remarkIndex = deploymentRecord.indexOf("\n\n备注：");
        if (remainingIndex < 0 && remarkIndex < 0) {
            return;
        }

        int firstSectionIndex = remainingIndex >= 0 ? remainingIndex : remarkIndex;
        if (remainingIndex >= 0 && remarkIndex >= 0) {
            firstSectionIndex = Math.min(remainingIndex, remarkIndex);
        }
        snapshot.deploymentRecord = deploymentRecord.substring(0, firstSectionIndex).trim();

        if (remainingIndex >= 0) {
            int remainingValueStart = remainingIndex + "\n\n遗留问题：".length();
            int remainingValueEnd = remarkIndex > remainingIndex ? remarkIndex : deploymentRecord.length();
            if (snapshot.remainingIssues == null) {
                snapshot.remainingIssues = trimToNull(deploymentRecord.substring(remainingValueStart, remainingValueEnd));
            }
        }
        if (remarkIndex >= 0 && snapshot.remark == null) {
            int remarkValueStart = remarkIndex + "\n\n备注：".length();
            snapshot.remark = trimToNull(deploymentRecord.substring(remarkValueStart));
        }
    }

    private String formatEpochMillisDate(String value) {
        try {
            long epochMillis = Long.parseLong(value);
            if (epochMillis <= 0) {
                return null;
            }
            return Instant.ofEpochMilli(epochMillis).atZone(ZONE_SHANGHAI).toLocalDate().toString();
        } catch (Exception ignored) {
            return trimToNull(value);
        }
    }

    private List<String> extractContentMapValues(JSONObject contentMap) {
        List<String> values = new ArrayList<>();
        if (contentMap == null || contentMap.isEmpty()) {
            return values;
        }
        List<Map.Entry<String, Object>> entries = new ArrayList<>(contentMap.entrySet());
        entries.sort((left, right) -> extractContentMapOrder(left.getKey()) - extractContentMapOrder(right.getKey()));
        for (Map.Entry<String, Object> entry : entries) {
            String normalized = trimToNull(entry.getValue() == null ? null : entry.getValue().toString());
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    private int extractContentMapOrder(String key) {
        if (key == null) {
            return Integer.MAX_VALUE;
        }
        if (key.startsWith("value")) {
            try {
                return Integer.parseInt(key.substring(5));
            } catch (Exception ignored) {
                return Integer.MAX_VALUE - 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String buildImplementationDisplayContent(ImplementationContentSnapshot snapshot, String rawContent) {
        List<String> sections = new ArrayList<>();
        if (isTemplate(snapshot.template, "JumpServer")) {
            addImplementationDisplayLine(sections, "纳管资产类型", snapshot.assetTypes);
            addImplementationDisplayLine(sections, "管理资产数", snapshot.assetCount);
            addImplementationDisplayLine(sections, "虚拟化类型", snapshot.virtualizationType);
            addImplementationDisplayLine(sections, "应用发布服务器", snapshot.applicationServer);
            addImplementationDisplayLine(sections, "是否涉及到数据同步", snapshot.databaseSync);
            addImplementationDisplayLine(sections, "数据库是否外置", snapshot.databaseExternal);
            addImplementationDisplayLine(sections, "Redis是否外置部署", snapshot.redisExternal);
            addImplementationDisplayLine(sections, "共享存储NFS", snapshot.sharedNfs);
            addImplementationDisplayLine(sections, "客户核心关注点", snapshot.customerFocus);
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayBlock(sections, "记录内容", snapshot.deploymentRecord);
        } else if (isTemplate(snapshot.template, "MaxKB")) {
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayLine(sections, "认证方式", snapshot.authMethods);
            addImplementationDisplayLine(sections, "业务方向", snapshot.businessDirections);
        } else if (isTemplate(snapshot.template, "DataEase")) {
            addImplementationDisplayLine(sections, "备份方式", snapshot.backupMethod);
            addImplementationDisplayLine(sections, "数据库配置", snapshot.dataEaseDatabase);
            addImplementationDisplayLine(sections, "Doris配置", snapshot.dorisUsage);
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayLine(sections, "数据源类型", snapshot.dataSourceType);
            addImplementationDisplayLine(sections, "数据量规模", snapshot.dataScale);
            addImplementationDisplayLine(sections, "认证方式", snapshot.authMethods);
            addImplementationDisplayLine(sections, "嵌入方式", snapshot.embeddedMode);
            addImplementationDisplayLine(sections, "客户接入状态", snapshot.customerJoined);
            addImplementationDisplayLine(sections, "分析及展示方向", snapshot.analysisDirection);
            addImplementationDisplayLine(sections, "客户核心关注点", snapshot.customerFocus);
        } else if (isTemplate(snapshot.template, "SQLBot")) {
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayLine(sections, "备份方式", snapshot.backupMethod);
            addImplementationDisplayLine(sections, "PostgreSQL 是否外置", snapshot.databaseExternal);
            addImplementationDisplayLine(sections, "数据源类型", snapshot.dataSourceType);
            addImplementationDisplayLine(sections, "AI模型类型", snapshot.aiModelType);
            addImplementationDisplayLine(sections, "是否嵌入集成", snapshot.embeddedMode);
            addImplementationDisplayLine(sections, "第三方平台对接", snapshot.authMethods);
        }
        addImplementationDisplayBlock(sections, "遗留问题", snapshot.remainingIssues);
        addImplementationDisplayBlock(sections, "备注", snapshot.remark);

        if (sections.isEmpty()) {
            return rawContent;
        }
        return String.join("\n\n", sections);
    }

    private void addImplementationDisplayLine(List<String> sections, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        sections.add(label + "：" + normalized);
    }

    private void addImplementationDisplayBlock(List<String> sections, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        sections.add(label + "：\n" + normalized);
    }

    private static final class SubscriptionContext {
        private Long subscriptionId;
        private Long clientId;
        private String clientName;
        private String contractNumber;
        private Long productId;
        private String productName;
        private String serviceTypeName;
        private String regionId;
        private String subscriptionStartDate;
        private String supportEndDate;
    }

    private static final class ImplementationProductSpec {
        private final Long fallbackProductId;
        private final String productName;
        private final String productAlias;
        private final String template;
        private final String formType;
        private final List<String> requiredKeywords;
        private final List<String> preferredKeywords;

        private ImplementationProductSpec(Long fallbackProductId, String productName, String productAlias,
                                          String template, String formType, List<String> requiredKeywords,
                                          List<String> preferredKeywords) {
            this.fallbackProductId = fallbackProductId;
            this.productName = productName;
            this.productAlias = productAlias;
            this.template = template;
            this.formType = formType;
            this.requiredKeywords = requiredKeywords;
            this.preferredKeywords = preferredKeywords;
        }
    }

    private static final class ProductServiceRow {
        private final Long productId;
        private final String productName;

        private ProductServiceRow(Long productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }
    }

    private static final class ImplementationContentSnapshot {
        private String template;
        private String deploymentTime;
        private String deploymentMethod;
        private String version;
        private String backupMethod;
        private String assetTypes;
        private String assetCount;
        private String virtualizationType;
        private String applicationServer;
        private String databaseSync;
        private String databaseExternal;
        private String redisExternal;
        private String sharedNfs;
        private String customerFocus;
        private String deploymentArchitecture;
        private String deploymentRecord;
        private String authMethods;
        private String businessDirections;
        private String dataEaseDatabase;
        private String dorisUsage;
        private String dataSourceType;
        private String aiModelType;
        private String dataScale;
        private String embeddedMode;
        private String customerJoined;
        private String analysisDirection;
        private String remainingIssues;
        private String remark;
    }

    private boolean isTemplate(String template, String productKeyword) {
        if (template == null || productKeyword == null) {
            return false;
        }
        return template.toUpperCase(Locale.ROOT).contains(productKeyword.toUpperCase(Locale.ROOT));
    }

    private boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return v.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    private String resolveRegionId(Long clientId, String extChatId) {
        // 优先按订阅数据读取 region_id（你的库字段为 support_subscription.region_id）
        if (extChatId != null && !extChatId.isEmpty()) {
            try {
                String subRegionSql = "SELECT ss.region_id " +
                        "FROM support_subscription ss " +
                        "INNER JOIN group_chat gc ON gc.name = ss.group_chat_name " +
                        "WHERE gc.ext_chat_id = ? " +
                        "AND (? IS NULL OR ss.client_id = ?) " +
                        "AND ss.region_id IS NOT NULL " +
                        "ORDER BY ss.support_end_date DESC LIMIT 1";
                var subRegionResult = com.util.JdbcUtils.query(subRegionSql, extChatId, clientId, clientId);
                if (!subRegionResult.isEmpty() && subRegionResult.get(0)[0] != null) {
                    String regionId = subRegionResult.get(0)[0].toString();
                    LOGGER.info("resolveRegionId by subscription success extChatId={}, clientId={}, regionId={}",
                            extChatId, clientId, regionId);
                    return regionId;
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }
        }

        // 优先按客户历史维护记录
        if (clientId != null) {
            try {
                String regionSql = "SELECT smr.region_id " +
                        "FROM support_maintenance_record smr " +
                        "WHERE smr.client_id = ? AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                        "ORDER BY smr.create_time DESC LIMIT 1";
                var regionResult = com.util.JdbcUtils.query(regionSql, clientId);
                if (!regionResult.isEmpty() && regionResult.get(0)[0] != null) {
                    return regionResult.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }
        }

        // 次选按群聊对应的维护记录
        if (extChatId != null && !extChatId.isEmpty()) {
            try {
                String regionSqlByChat = "SELECT smr.region_id " +
                        "FROM support_maintenance_record smr " +
                        "WHERE smr.client_id IN ( " +
                        "    SELECT DISTINCT ss.client_id " +
                        "    FROM group_chat gc " +
                        "    INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                        "    WHERE gc.ext_chat_id = ? " +
                        ") " +
                        "AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                        "ORDER BY smr.create_time DESC LIMIT 1";
                var regionResultByChat = com.util.JdbcUtils.query(regionSqlByChat, extChatId);
                if (!regionResultByChat.isEmpty() && regionResultByChat.get(0)[0] != null) {
                    return regionResultByChat.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        return null;
    }

    private String resolveRegionIdBySubmitter(String submitterUserId) {
        String normalizedSubmitterUserId = trimToNull(submitterUserId);
        if (normalizedSubmitterUserId == null) {
            return null;
        }

        Long departmentId = resolveSubmitterDepartmentId(normalizedSubmitterUserId);
        String regionName = resolveRegionNameByDepartmentId(departmentId);
        if (regionName == null) {
            LOGGER.warn("resolveRegionIdBySubmitter no region mapping submitterUserId={}, departmentId={}",
                    normalizedSubmitterUserId, departmentId);
            return null;
        }

        String regionId = resolveRegionIdByName(regionName);
        LOGGER.info("resolveRegionIdBySubmitter submitterUserId={}, departmentId={}, regionName={}, regionId={}",
                normalizedSubmitterUserId, departmentId, regionName, regionId);
        return regionId;
    }

    private Long resolveSubmitterDepartmentId(String submitterUserId) {
        Long departmentId = resolveDepartmentIdByStaffExtId(submitterUserId);
        if (departmentId != null) {
            return departmentId;
        }

        try {
            String supportUserSql = "SELECT su.email, su.name " +
                    "FROM support_user su " +
                    "WHERE su.user_id = ? LIMIT 1";
            var supportUserResult = com.util.JdbcUtils.query(supportUserSql, submitterUserId);
            if (!supportUserResult.isEmpty()) {
                String email = supportUserResult.get(0)[0] != null ? supportUserResult.get(0)[0].toString() : null;
                String name = supportUserResult.get(0)[1] != null ? supportUserResult.get(0)[1].toString() : null;
                departmentId = resolveDepartmentIdByStaffEmailOrName(email, name);
                if (departmentId != null) {
                    return departmentId;
                }
            }
        } catch (Exception ignored) {
            // ignore and fallback
        }

        return resolveDepartmentIdByStaffEmailOrName(submitterUserId, submitterUserId);
    }

    private Long resolveDepartmentIdByStaffExtId(String extId) {
        String normalizedExtId = trimToNull(extId);
        if (normalizedExtId == null) {
            return null;
        }
        try {
            String sql = "SELECT sd.ext_department_id " +
                    "FROM staff s " +
                    "LEFT JOIN staff_department sd ON s.id = sd.staff_id " +
                    "WHERE s.ext_id = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedExtId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return toLong(result.get(0)[0]);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private Long resolveDepartmentIdByStaffEmailOrName(String email, String name) {
        String normalizedEmail = trimToNull(email);
        String normalizedName = trimToNull(name);
        if (normalizedEmail == null && normalizedName == null) {
            return null;
        }
        try {
            String sql = "SELECT sd.ext_department_id " +
                    "FROM staff s " +
                    "LEFT JOIN staff_department sd ON s.id = sd.staff_id " +
                    "WHERE (? IS NOT NULL AND s.email = ?) " +
                    "   OR (? IS NOT NULL AND s.name = ?) " +
                    "LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedEmail, normalizedEmail, normalizedName, normalizedName);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return toLong(result.get(0)[0]);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private String resolveRegionNameByDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        if (EAST_REGION_DEPT_IDS.contains(departmentId)) {
            return "东区";
        }
        if (NORTH_REGION_DEPT_IDS.contains(departmentId)) {
            return "北区";
        }
        if (SOUTH_REGION_DEPT_IDS.contains(departmentId)) {
            return "南区";
        }
        return null;
    }

    private String resolveRegionIdByName(String regionName) {
        String normalizedRegionName = trimToNull(regionName);
        if (normalizedRegionName == null) {
            return null;
        }
        try {
            String sql = "SELECT id " +
                    "FROM region " +
                    "WHERE name = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, normalizedRegionName);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    public List<String> getStaffList(String userId) {
        return queryRegionStaffList(userId, false);
    }

    public List<String> getImplementationStaffList(String userId) {
        return queryRegionStaffList(userId, true);
    }

    private List<String> queryRegionStaffList(String userId, boolean requireSupportUser) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            // 查询当前用户的部门ID
            String currentUserSql = "SELECT sd.ext_department_id FROM staff s " +
                    "LEFT JOIN staff_department sd ON s.id = sd.staff_id " +
                    "WHERE s.ext_id = ? LIMIT 1";
            var currentUserResult = com.util.JdbcUtils.query(currentUserSql, userId);

            Long currentUserDeptId = null;
            if (!currentUserResult.isEmpty() && currentUserResult.get(0)[0] != null) {
                currentUserDeptId = Long.parseLong(currentUserResult.get(0)[0].toString());
            }

            // 获取当前用户所属区域的所有部门ID
            List<Long> regionDeptIds = getRegionDeptIds(currentUserDeptId);
            if (regionDeptIds == null || regionDeptIds.isEmpty()) {
                // 如果找不到区域，返回空列表
                return new ArrayList<>();
            }

            // 构建 IN 子句
            String deptIdsStr = regionDeptIds.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            String supportUserFilter = requireSupportUser
                    ? "AND EXISTS (" +
                    "SELECT 1 FROM support_user su " +
                    "WHERE su.username = s.ext_id " +
                    "   OR (s.email IS NOT NULL AND s.email != '' AND su.email = s.email) " +
                    "   OR su.name = s.name" +
                    ") "
                    : "";

            // 查询同区域所有员工
            String sql = "SELECT DISTINCT s.name FROM staff s " +
                    "INNER JOIN staff_department sd ON s.id = sd.staff_id " +
                    "WHERE sd.ext_department_id IN (" + deptIdsStr + ") " +
                    "AND s.name IS NOT NULL AND s.name != '' " +
                    supportUserFilter +
                    "ORDER BY s.name";
            var result = com.util.JdbcUtils.query(sql);
            List<String> staffList = new ArrayList<>();

            for (Object[] row : result) {
                if (row[0] != null) {
                    staffList.add(row[0].toString());
                }
            }

            return staffList;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private List<String> fetchProductVersions(Long productId) throws Exception {
        List<String> versions = new ArrayList<>();
        List<ProductVersionInfo> versionInfos = fetchProductVersionInfos(productId);
        for (ProductVersionInfo versionInfo : versionInfos) {
            if (versionInfo.name != null && !versionInfo.name.isEmpty()) {
                versions.add(versionInfo.name);
            }
        }
        LOGGER.info("fetchProductVersions done productId={}, count={}", productId, versions.size());
        return versions;
    }

    private List<ProductVersionInfo> fetchProductVersionInfos(Long productId) throws Exception {
        List<ProductVersionInfo> versions = new ArrayList<>();
        String url = cscrmBaseUrl + cscrmApiPath + "/support-info/products/" + productId + "/versions";
        LOGGER.info("fetchProductVersionInfos start productId={}, url={}", productId, url);
        String response = requestWithDnsRetry(url);
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            LOGGER.warn("fetchProductVersionInfos non-zero code productId={}, code={}, msg={}",
                    productId, code, responseJson.getString("msg"));
            return versions;
        }

        Object dataObj = responseJson.get("data");
        JSONArray list = null;
        if (dataObj instanceof JSONArray) {
            list = (JSONArray) dataObj;
        } else if (dataObj instanceof JSONObject) {
            JSONObject dataJson = (JSONObject) dataObj;
            if (dataJson.get("items") instanceof JSONArray) {
                list = dataJson.getJSONArray("items");
            }
        }
        if (list == null) {
            return versions;
        }

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof String) {
                versions.add(new ProductVersionInfo((String) item, null));
            } else if (item instanceof JSONObject) {
                JSONObject itemJson = (JSONObject) item;
                String version = itemJson.getString("version");
                if (version == null || version.isEmpty()) {
                    version = itemJson.getString("name");
                }
                if (version != null && !version.isEmpty()) {
                    versions.add(new ProductVersionInfo(version, trimToNull(itemJson.getString("installation"))));
                }
            }
        }
        LOGGER.info("fetchProductVersionInfos done productId={}, count={}", productId, versions.size());
        return versions;
    }

    private String requestWithDnsRetry(String url) throws Exception {
        Exception last = null;
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return HttpClientUtil.getRequestWithApiKey(url, cscrmApiKey);
            } catch (Exception e) {
                last = e;
                if (!containsUnknownHost(e) || attempt >= maxAttempts) {
                    throw e;
                }
                LOGGER.warn("requestWithDnsRetry unknown host, retrying attempt={}/{}, url={}", attempt, maxAttempts, url);
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private boolean containsUnknownHost(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof UnknownHostException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    public List<Long> resolveVersionProductIds(Long productId, String extChatId) {
        String alias = getProductAliasByExtChatId(extChatId);
        LOGGER.info("resolveVersionProductIds input productId={}, extChatId={}, alias={}", productId, extChatId, alias);

        if (productId != null) {
            return new ArrayList<>(List.of(productId));
        }
        if ("MK".equals(alias)) {
            return new ArrayList<>(MAXKB_PRODUCT_IDS);
        }
        if ("DE".equals(alias)) {
            return new ArrayList<>(DATAEASE_PRODUCT_IDS);
        }
        return new ArrayList<>();
    }

    public List<String> getProductVersions(Long productId, String extChatId) throws Exception {
        String normalizedExtChatId = trimToNull(extChatId);
        if (normalizedExtChatId == null) {
            throw new Exception("extChatId 不能为空");
        }
        return queryProductVersions(productId, normalizedExtChatId);
    }

    public Map<String, Object> getProductDownloadUrl(String extChatId, String version) throws Exception {
        String normalizedExtChatId = trimToNull(extChatId);
        String normalizedVersion = trimToNull(version);
        if (normalizedExtChatId == null) {
            throw new Exception("extChatId 不能为空");
        }
        if (normalizedVersion == null) {
            throw new Exception("version 不能为空");
        }

        String productKey = getProductDownloadKeyByExtChatId(normalizedExtChatId);
        if (productKey == null || productKey.isEmpty()) {
            throw new Exception("无法识别当前群聊产品");
        }
        String key = resolveProductDownloadInstallation(normalizedExtChatId, normalizedVersion);
        String installationProductKey = extractProductKeyFromInstallation(key);
        if (installationProductKey != null && !installationProductKey.equalsIgnoreCase(productKey)) {
            throw new Exception("下载文件产品与当前群聊产品不一致");
        }
        String url = cscrmBaseUrl + cscrmApiPath +
                "/support-info/products/download-url?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        LOGGER.info("getProductDownloadUrl request extChatId={}, productKey={}, key={}", normalizedExtChatId, productKey, key);

        String response = requestWithDnsRetry(url);
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            throw new Exception(firstNonBlank(responseJson.getString("message"), responseJson.getString("msg"), "获取下载链接失败"));
        }
        JSONObject data = responseJson.getJSONObject("data");
        String downloadUrl = data != null ? trimToNull(data.getString("url")) : null;
        if (downloadUrl == null) {
            throw new Exception("下载链接为空");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("product", productKey);
        result.put("version", normalizedVersion);
        result.put("key", key);
        result.put("url", downloadUrl);
        return result;
    }

    private String resolveProductDownloadInstallation(String extChatId, String version) throws Exception {
        String normalizedVersion = trim(version);
        Long productId = getProductIdByExtChatId(extChatId);
        List<Long> productIds = resolveVersionProductIds(productId, extChatId);
        for (Long pid : productIds) {
            for (ProductVersionInfo versionInfo : fetchProductVersionInfos(pid)) {
                if (matchesSelectedVersion(versionInfo, normalizedVersion)) {
                    if (versionInfo.installation == null || versionInfo.installation.isEmpty()) {
                        throw new Exception("当前版本未配置 installation: " + normalizedVersion);
                    }
                    return versionInfo.installation;
                }
            }
        }
        throw new Exception("未找到版本 installation: " + normalizedVersion);
    }

    private String extractProductKeyFromInstallation(String installation) {
        String normalized = trim(installation);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        return normalized.substring(0, slashIndex);
    }

    private boolean matchesSelectedVersion(ProductVersionInfo versionInfo, String selectedVersion) {
        if (versionInfo == null) {
            return false;
        }
        String selected = trim(selectedVersion);
        return selected.equals(trim(versionInfo.name)) || selected.equals(trim(versionInfo.installation));
    }

    private static class ProductVersionInfo {
        private final String name;
        private final String installation;

        private ProductVersionInfo(String name, String installation) {
            this.name = name;
            this.installation = installation;
        }
    }

    private List<String> queryProductVersions(Long productId, String extChatId) throws Exception {
        List<Long> productIds = resolveVersionProductIds(productId, extChatId);
        if (productIds.isEmpty()) {
            throw new Exception("productId 不能为空");
        }
        LOGGER.info("getProductVersions aggregate start extChatId={}, baseProductId={}, productIds={}", extChatId, productId, productIds);

        Set<String> merged = new LinkedHashSet<>();
        for (Long pid : productIds) {
            merged.addAll(fetchProductVersions(pid));
        }

        List<String> sorted = new ArrayList<>(merged);
        sorted.sort(new VersionDescComparator());
        LOGGER.info("getProductVersions aggregate done extChatId={}, productIds={}, mergedCount={}", extChatId, productIds, sorted.size());
        return sorted;
    }

    public Long getProductIdByExtChatId(String extChatId) {
        if (extChatId == null || extChatId.isEmpty()) {
            return null;
        }
        try {
            CustomerData customerData = getCustomerData(extChatId);
            Long productId = customerData != null ? customerData.getProductId() : null;
            LOGGER.info("getProductIdByExtChatId extChatId={}, productId={}", extChatId, productId);
            return productId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getProductAliasByExtChatId(String extChatId) {
        if (extChatId == null || extChatId.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT name FROM group_chat WHERE ext_chat_id = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, extChatId);
            if (result.isEmpty() || result.get(0)[0] == null) {
                return null;
            }
            String chatName = result.get(0)[0].toString().toUpperCase();
            if (chatName.contains("MK") || chatName.contains("MAXKB")) {
                return "MK";
            }
            if (chatName.contains("DE") || chatName.contains("DATAEASE")) {
                return "DE";
            }
            if (chatName.contains("JS") || chatName.contains("JUMPSERVER")) {
                return "JS";
            }
            if (chatName.contains("SQLBOT")) {
                return "SQLBOT";
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getProductDownloadKeyByExtChatId(String extChatId) {
        String alias = getProductAliasByExtChatId(extChatId);
        String key = toProductDownloadKey(alias);
        if (key != null) {
            return key;
        }

        Long productId = getProductIdByExtChatId(extChatId);
        if (productId == null) {
            return null;
        }
        if (MAXKB_PRODUCT_IDS.contains(productId)) {
            return "maxkb";
        }
        if (DATAEASE_PRODUCT_IDS.contains(productId)) {
            return "dataease";
        }
        if (productId == 2001L) {
            return "jumpserver";
        }
        return null;
    }

    private String toProductDownloadKey(String alias) {
        if (alias == null || alias.isEmpty()) {
            return null;
        }
        return switch (alias) {
            case "JS" -> "jumpserver";
            case "MK" -> "maxkb";
            case "DE" -> "dataease";
            case "SQLBOT" -> "sqlbot";
            default -> null;
        };
    }

    public ProductVersionSnapshot getProductVersionSnapshot(String extChatId) {
        ProductVersionSnapshot snapshot = new ProductVersionSnapshot();
        if (extChatId == null || extChatId.isEmpty()) {
            return snapshot;
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            String aliasCode = getProductAliasByExtChatId(extChatId);
            String productAlias = toStandardProductAlias(aliasCode);
            snapshot.setProductAlias(productAlias);

            VersionCandidate implementationCandidate = getLatestImplementationVersion(extChatId);
            if (implementationCandidate != null) {
                snapshot.setVersion(implementationCandidate.version);
                snapshot.setVersionTs(implementationCandidate.versionTs);
                snapshot.setSource("implementation");
            }
            return snapshot;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private String toStandardProductAlias(String aliasCode) {
        if (aliasCode == null || aliasCode.isEmpty()) {
            return null;
        }
        return switch (aliasCode) {
            case "JS" -> "JumpServer";
            case "MK" -> "MaxKB";
            case "DE" -> "DataEase";
            case "SQLBOT" -> "SQLBot";
            default -> null;
        };
    }

    private VersionCandidate pickVersionCandidate(VersionCandidate serviceCandidate, VersionCandidate implementationCandidate) {
        if (serviceCandidate == null || serviceCandidate.versionTs == null) {
            return implementationCandidate;
        }
        if (implementationCandidate == null || implementationCandidate.versionTs == null) {
            return serviceCandidate;
        }
        if (isSameDay(serviceCandidate.versionTs, implementationCandidate.versionTs)) {
            return serviceCandidate;
        }
        return implementationCandidate.versionTs > serviceCandidate.versionTs ? implementationCandidate : serviceCandidate;
    }

    private boolean isSameDay(Long a, Long b) {
        if (a == null || b == null) {
            return false;
        }
        LocalDate ad = Instant.ofEpochMilli(a).atZone(ZONE_SHANGHAI).toLocalDate();
        LocalDate bd = Instant.ofEpochMilli(b).atZone(ZONE_SHANGHAI).toLocalDate();
        return ad.equals(bd);
    }

    private VersionCandidate getLatestImplementationVersion(String extChatId) {
        String sql = "WITH chat_product AS ( " +
                "    SELECT gc.name, " +
                "           CASE " +
                "               WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                "               WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                "               WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                "               WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBOT' " +
                "               ELSE NULL " +
                "           END AS product " +
                "    FROM group_chat gc " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "SELECT sm.version, COALESCE(sm.deployment_time, sm.create_time) AS version_ts " +
                "FROM support_maintenance sm " +
                "INNER JOIN support_subscription ss ON sm.subscription_id = ss.id " +
                "CROSS JOIN chat_product cp " +
                "WHERE ss.client_id IN ( " +
                "    SELECT DISTINCT ss2.client_id " +
                "    FROM group_chat gc " +
                "    INNER JOIN support_subscription ss2 ON gc.name = ss2.group_chat_name " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "AND (cp.product IS NULL OR UPPER(sm.template) LIKE CONCAT('%', UPPER(cp.product), '%')) " +
                "AND sm.version IS NOT NULL AND sm.version != '' " +
                "ORDER BY version_ts DESC, sm.create_time DESC LIMIT 1";

        var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
        if (result.isEmpty()) {
            return null;
        }
        Object[] row = result.get(0);
        if (row[0] == null || row[1] == null) {
            return null;
        }
        VersionCandidate candidate = new VersionCandidate();
        candidate.version = row[0].toString();
        candidate.versionTs = toLong(row[1]);
        if (candidate.versionTs == null) {
            return null;
        }
        return candidate;
    }

    private VersionCandidate getLatestServiceVersion(String extChatId) {
        String sql = "WITH chat_product AS ( " +
                "    SELECT gc.name, " +
                "           CASE " +
                "               WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                "               WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                "               WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                "               WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBot' " +
                "               ELSE NULL " +
                "           END AS product " +
                "    FROM group_chat gc " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "SELECT smr.maintenance_version, smr.maintenance_time " +
                "FROM support_maintenance_record smr " +
                "CROSS JOIN chat_product cp " +
                "WHERE smr.client_id IN ( " +
                "    SELECT DISTINCT ss.client_id " +
                "    FROM group_chat gc " +
                "    INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "AND ( " +
                "    (cp.product = 'MaxKB' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%MAXKB%' " +
                "    )) " +
                "    OR " +
                "    (cp.product = 'JumpServer' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%JUMPSERVER%' " +
                "    )) " +
                "    OR " +
                "    (cp.product = 'DataEase' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%DATAEASE%' " +
                "    )) " +
                "    OR " +
                "    (cp.product = 'SQLBot' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%SQLBOT%' " +
                "    )) " +
                "    OR " +
                "    smr.product_id IS NULL OR smr.product_id = 0 " +
                ") " +
                "AND smr.maintenance_version IS NOT NULL AND smr.maintenance_version != '' " +
                "ORDER BY smr.maintenance_time DESC, smr.create_time DESC LIMIT 1";

        var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
        if (result.isEmpty()) {
            return null;
        }
        Object[] row = result.get(0);
        if (row[0] == null || row[1] == null) {
            return null;
        }
        VersionCandidate candidate = new VersionCandidate();
        candidate.version = row[0].toString();
        candidate.versionTs = toLong(row[1]);
        if (candidate.versionTs == null) {
            return null;
        }
        return candidate;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class VersionCandidate {
        private String version;
        private Long versionTs;
    }

    private static class VersionDescComparator implements Comparator<String> {
        private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

        @Override
        public int compare(String a, String b) {
            List<Integer> av = extractNumbers(a);
            List<Integer> bv = extractNumbers(b);
            int size = Math.max(av.size(), bv.size());
            for (int i = 0; i < size; i++) {
                int ai = i < av.size() ? av.get(i) : 0;
                int bi = i < bv.size() ? bv.get(i) : 0;
                if (ai != bi) {
                    return Integer.compare(bi, ai);
                }
            }
            return b.compareToIgnoreCase(a);
        }

        private List<Integer> extractNumbers(String version) {
            List<Integer> numbers = new ArrayList<>();
            if (version == null) {
                return numbers;
            }
            Matcher matcher = NUMBER_PATTERN.matcher(version);
            while (matcher.find()) {
                try {
                    numbers.add(Integer.parseInt(matcher.group()));
                } catch (NumberFormatException ignored) {
                    // ignore malformed part
                }
            }
            return numbers;
        }
    }

    // 判断是否跨团队
    private boolean isCrossTeam(Long deptId1, Long deptId2) {
        if (deptId1 == null || deptId2 == null) {
            return false;
        }

        // 首先检查是否在同一区域
        if (!isSameRegion(deptId1, deptId2)) {
            // 不在同一区域，不属于跨团队（因为只有同区才有跨团队的概念）
            return false;
        }

        // 在同一区域内，检查特殊规则
        boolean dept1IsVirtual = VIRTUAL_DEPT_IDS.contains(deptId1);
        boolean dept2IsVirtual = VIRTUAL_DEPT_IDS.contains(deptId2);
        boolean dept1IsOnline = ONLINE_DEPT_IDS.contains(deptId1);
        boolean dept2IsOnline = ONLINE_DEPT_IDS.contains(deptId2);

        // 虚拟账号和线上不跨团队
        if ((dept1IsVirtual && dept2IsOnline) || (dept2IsVirtual && dept1IsOnline)) {
            return false;
        }

        // 虚拟账号和虚拟账号不跨团队
        if (dept1IsVirtual && dept2IsVirtual) {
            return false;
        }

        // 线上和线上不跨团队
        if (dept1IsOnline && dept2IsOnline) {
            return false;
        }

        // 其他情况（包括虚拟账号和线下、线上和线下、线下和线下）都是跨团队
        return true;
    }

    // 判断两个部门是否在同一区域
    private boolean isSameRegion(Long deptId1, Long deptId2) {
        if (deptId1 == null || deptId2 == null) {
            return false;
        }

        // 检查是否都在东区
        if (EAST_REGION_DEPT_IDS.contains(deptId1) && EAST_REGION_DEPT_IDS.contains(deptId2)) {
            return true;
        }
        // 检查是否都在北区
        if (NORTH_REGION_DEPT_IDS.contains(deptId1) && NORTH_REGION_DEPT_IDS.contains(deptId2)) {
            return true;
        }
        // 检查是否都在南区
        if (SOUTH_REGION_DEPT_IDS.contains(deptId1) && SOUTH_REGION_DEPT_IDS.contains(deptId2)) {
            return true;
        }
        return false;
    }

    // 获取部门所属区域的所有部门ID
    private List<Long> getRegionDeptIds(Long deptId) {
        if (deptId == null) {
            return new ArrayList<>();
        }

        if (EAST_REGION_DEPT_IDS.contains(deptId)) {
            return EAST_REGION_DEPT_IDS;
        }
        if (NORTH_REGION_DEPT_IDS.contains(deptId)) {
            return NORTH_REGION_DEPT_IDS;
        }
        if (SOUTH_REGION_DEPT_IDS.contains(deptId)) {
            return SOUTH_REGION_DEPT_IDS;
        }
        return new ArrayList<>();
    }
}
