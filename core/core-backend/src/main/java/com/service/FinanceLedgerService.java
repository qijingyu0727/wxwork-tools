package com.service;

import com.util.JdbcUtils;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FinanceLedgerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinanceLedgerService.class);
    private static final Map<String, String> REGION_NAME_TO_FINANCE_CODE = new HashMap<>();
    private static final List<String> EXCLUDED_CONTRACT_NAMES = Arrays.asList(
            "东区未签单客户",
            "南区未签单客户",
            "公司内部会议",
            "市场部专用",
            "综合部专用",
            "东区交付框架协议（交付多个项目-交付合同专用）",
            "南区交付框架协议（交付多个项目-交付合同专用）",
            "北区交付框架协议（交付多个项目-交付合同专用）",
            "东区服务费多项目协议",
            "北区服务费多项目协议",
            "南区服务费多项目协议",
            "研发中心专用",
            "北区云服务",
            "南区云服务",
            "东区云服务",
            "全公司使用",
            "公司福利事项",
            "北区预算类合同",
            "南区团建2022年",
            "北区未签单客户",
            "CEO专用",
            "员工述职培训",
            "高校合作计划",
            "部门内部会议-部门单独会议（招聘）",
            "测试*11111234567890-*11",
            "公司团建-车票、住宿（个人支付）"
    );

    static {
        REGION_NAME_TO_FINANCE_CODE.put("东区", "CODE2");
        REGION_NAME_TO_FINANCE_CODE.put("北区", "CODE1");
        REGION_NAME_TO_FINANCE_CODE.put("南区", "CODE3");
    }
    private final ConcurrentHashMap<String, FreshCacheEntry> freshCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LastKnownGoodCacheEntry> lastKnownGoodCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<LookupExecutionResult>> inFlightLookups = new ConcurrentHashMap<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, new RefreshThreadFactory());

    @Value("${cordyscrm.lookup.cache-ttl-sec:300}")
    private long ledgerCacheTtlSec;

    @Value("${cordyscrm.lookup.negative-cache-ttl-sec:30}")
    private long negativeLedgerCacheTtlSec;

    @Value("${cordyscrm.lookup.last-known-good-ttl-sec:1800}")
    private long lastKnownGoodTtlSec;

    public LedgerRecord resolveLedgerRecordByExtChatId(String extChatId) {
        return resolveLedgerRecordByExtChatId(extChatId, LookupMode.FULL);
    }

    public LedgerRecord resolveLedgerRecordByExtChatIdFast(String extChatId) {
        return resolveLedgerRecordByExtChatId(extChatId, LookupMode.FAST);
    }

    private LedgerRecord resolveLedgerRecordByExtChatId(String extChatId, LookupMode lookupMode) {
        long startNs = System.nanoTime();
        String cacheKey = trim(extChatId);
        FreshCacheEntry freshEntry = getFreshCacheEntry(cacheKey);
        LastKnownGoodCacheEntry lastKnownGoodEntry = lookupMode == LookupMode.FAST ? getLastKnownGoodCacheEntry(cacheKey) : null;

        if (freshEntry != null && freshEntry.record != null && freshEntry.canServe(lookupMode)) {
            logLookupInfo("cache_hit", cacheKey, lookupMode, startNs, freshEntry.record != null);
            return copyLedgerRecord(freshEntry.record);
        }

        if (lookupMode == LookupMode.FAST && lastKnownGoodEntry != null) {
            logLookupInfo("cache_stale_served", cacheKey, lookupMode, startNs, true);
            if (freshEntry == null || freshEntry.record == null) {
                refreshFastLookupInBackground(cacheKey, extChatId);
            }
            return copyLedgerRecord(lastKnownGoodEntry.record);
        }

        if (freshEntry != null && freshEntry.canServe(lookupMode)) {
            logLookupInfo("cache_hit", cacheKey, lookupMode, startNs, false);
            return null;
        }

        try {
            LookupExecutionResult result = executeLookupWithSingleFlight(cacheKey, extChatId, lookupMode);
            if (lookupMode == LookupMode.FAST && result.record == null) {
                refreshFastLookupInBackground(cacheKey, extChatId);
                logLookupInfo("fast_lookup_miss", cacheKey, lookupMode, startNs, false);
            } else {
                logLookupInfo("lookup_done", cacheKey, lookupMode, startNs, result.record != null);
            }
            return copyLedgerRecord(result.record);
        } catch (RuntimeException e) {
            if (lookupMode == LookupMode.FAST && lastKnownGoodEntry != null) {
                logLookupWarn("cache_stale_served", cacheKey, lookupMode, startNs, e);
                return copyLedgerRecord(lastKnownGoodEntry.record);
            }
            logLookupWarn(classifyLookupFailure(e, lookupMode), cacheKey, lookupMode, startNs, e);
            throw e;
        }
    }

    private FreshCacheEntry getFreshCacheEntry(String extChatId) {
        if (extChatId.isEmpty()) {
            return null;
        }
        FreshCacheEntry entry = freshCache.get(extChatId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            freshCache.remove(extChatId, entry);
            return null;
        }
        return entry;
    }

    private LastKnownGoodCacheEntry getLastKnownGoodCacheEntry(String extChatId) {
        if (extChatId.isEmpty()) {
            return null;
        }
        LastKnownGoodCacheEntry entry = lastKnownGoodCache.get(extChatId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            lastKnownGoodCache.remove(extChatId, entry);
            return null;
        }
        return entry;
    }

    private void putFreshCacheEntry(String extChatId, LedgerRecord record, LookupMode lookupMode) {
        if (extChatId.isEmpty()) {
            return;
        }
        if (record == null && lookupMode == LookupMode.FULL) {
            return;
        }
        long ttlSec = record == null ? negativeLedgerCacheTtlSec : ledgerCacheTtlSec;
        long ttlMs = Math.max(ttlSec, 0) * 1000L;
        if (ttlMs <= 0) {
            freshCache.remove(extChatId);
            return;
        }
        freshCache.put(extChatId, new FreshCacheEntry(copyLedgerRecord(record), System.currentTimeMillis() + ttlMs, lookupMode));
    }

    private void putLastKnownGoodCacheEntry(String extChatId, LedgerRecord record) {
        if (extChatId.isEmpty() || record == null) {
            return;
        }
        long ttlMs = Math.max(lastKnownGoodTtlSec, 0) * 1000L;
        if (ttlMs <= 0) {
            lastKnownGoodCache.remove(extChatId);
            return;
        }
        lastKnownGoodCache.put(extChatId, new LastKnownGoodCacheEntry(copyLedgerRecord(record), System.currentTimeMillis() + ttlMs));
    }

    private LookupExecutionResult executeLookupWithSingleFlight(String cacheKey, String extChatId, LookupMode lookupMode) {
        String inFlightKey = cacheKey + ":" + lookupMode.name();
        CompletableFuture<LookupExecutionResult> created = new CompletableFuture<>();
        CompletableFuture<LookupExecutionResult> existing = inFlightLookups.putIfAbsent(inFlightKey, created);
        if (existing != null) {
            return joinLookupFuture(existing);
        }

        try {
            LookupExecutionResult result = executeLookup(extChatId, lookupMode);
            created.complete(result);
            return result;
        } catch (Throwable t) {
            created.completeExceptionally(t);
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("查询失败", t);
        } finally {
            inFlightLookups.remove(inFlightKey, created);
        }
    }

    private LookupExecutionResult joinLookupFuture(CompletableFuture<LookupExecutionResult> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("查询失败", cause != null ? cause : e);
        }
    }

    private LookupExecutionResult executeLookup(String extChatId, LookupMode lookupMode) {
        ChatFinanceContext context = resolveChatFinanceContext(extChatId);
        LedgerRecord record = resolveLedgerRecord(context, lookupMode);
        putFreshCacheEntry(trim(extChatId), record, lookupMode);
        if (record != null) {
            putLastKnownGoodCacheEntry(trim(extChatId), record);
        }
        return new LookupExecutionResult(copyLedgerRecord(record));
    }

    private void refreshFastLookupInBackground(String cacheKey, String extChatId) {
        String inFlightKey = cacheKey + ":" + LookupMode.FAST.name();
        if (inFlightLookups.containsKey(inFlightKey)) {
            return;
        }
        refreshExecutor.execute(() -> {
            try {
                LookupExecutionResult result = executeLookupWithSingleFlight(cacheKey, extChatId, LookupMode.FAST);
                LOGGER.info("ledger_lookup event=refresh_done extChatId={}, mode={}, found={}",
                        cacheKey,
                        LookupMode.FAST.name().toLowerCase(),
                        result.record != null);
            } catch (RuntimeException e) {
                LOGGER.warn("ledger_lookup event=ledger_query_failed extChatId={}, mode={}, err={}",
                        cacheKey,
                        LookupMode.FAST.name().toLowerCase(),
                        e.getMessage());
            }
        });
    }

    private LedgerRecord copyLedgerRecord(LedgerRecord source) {
        if (source == null) {
            return null;
        }
        LedgerRecord copy = new LedgerRecord();
        copy.code = source.code;
        copy.contractedCustomer = source.contractedCustomer;
        copy.endCustomer = source.endCustomer;
        copy.salfesforceName = source.salfesforceName;
        copy.sku = source.sku;
        copy.startDate = source.startDate;
        copy.checkAccept = source.checkAccept;
        copy.checkAcceptDate = source.checkAcceptDate;
        copy.checkAcceptReport = source.checkAcceptReport;
        copy.active = source.active;
        copy.contractName = source.contractName;
        copy.region = source.region;
        copy.regionName = source.regionName;
        copy.regionCode = source.regionCode;
        return copy;
    }

    private ChatFinanceContext resolveChatFinanceContext(String extChatId) {
        long startNs = System.nanoTime();
        JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT gc.name AS chat_name, " +
                    "       ss.contract_number, " +
                    "       ss.client_id, " +
                    "       ss.region_id, " +
                    "       rg.name AS region_name, " +
                    "       rg.code AS region_code, " +
                    "       sc.name AS client_name, " +
                    "       sc.abbreviated_name AS client_abbr, " +
                    "       sps.name AS product_service_name " +
                    "FROM group_chat gc " +
                    "LEFT JOIN support_subscription ss ON ss.group_chat_name = gc.name " +
                    "LEFT JOIN region rg ON rg.id = ss.region_id " +
                    "LEFT JOIN support_client sc ON sc.id = ss.client_id " +
                    "LEFT JOIN support_product_service sps ON sps.id = ss.product_service_id " +
                    "WHERE gc.ext_chat_id = ? " +
                    "ORDER BY ss.support_end_date DESC, ss.id DESC";
            List<Object[]> rows = JdbcUtils.query(sql, extChatId);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("未找到群聊信息: " + extChatId);
            }

            ChatFinanceContext context = new ChatFinanceContext();
            context.chatName = trim(rows.get(0)[0] != null ? rows.get(0)[0].toString() : "");
            context.contractNumbers = new ArrayList<>();
            context.customerNames = new ArrayList<>();
            context.regionId = "";
            context.regionName = "";
            context.regionCode = "";
            context.financeRegionCode = "";

            Set<String> contractSet = new LinkedHashSet<>();
            Set<String> nameSet = new LinkedHashSet<>();
            String productAliasCode = "";
            Long primaryClientId = null;

            for (Object[] row : rows) {
                Long rowClientId = null;
                if (row[2] != null && !trim(String.valueOf(row[2])).isEmpty()) {
                    rowClientId = Long.parseLong(String.valueOf(row[2]));
                }
                if (primaryClientId == null && rowClientId != null) {
                    primaryClientId = rowClientId;
                    context.clientId = rowClientId;
                }
                if (context.regionId.isEmpty()) {
                    context.regionId = trim(row[3] != null ? row[3].toString() : "");
                }
                if (context.regionName.isEmpty()) {
                    context.regionName = trim(row[4] != null ? row[4].toString() : "");
                }
                if (context.regionCode.isEmpty()) {
                    context.regionCode = trim(row[5] != null ? row[5].toString() : "");
                }
                if (productAliasCode.isEmpty()) {
                    productAliasCode = inferProductAliasCode(row[8] != null ? row[8].toString() : "");
                }
                if (primaryClientId != null && rowClientId != null && !primaryClientId.equals(rowClientId)) {
                    continue;
                }
                String contractNumber = trim(row[1] != null ? row[1].toString() : "");
                if (!contractNumber.isEmpty()) {
                    contractSet.add(contractNumber);
                }
                String clientName = trim(row[6] != null ? row[6].toString() : "");
                if (!clientName.isEmpty()) {
                    nameSet.add(clientName);
                }
                String clientAbbr = trim(row[7] != null ? row[7].toString() : "");
                if (!clientAbbr.isEmpty()) {
                    nameSet.add(clientAbbr);
                }
            }

            nameSet.addAll(deriveNamesFromChatName(context.chatName));
            context.contractNumbers.addAll(contractSet);
            context.customerNames.addAll(nameSet);
            context.productAliasCode = productAliasCode.isEmpty() ? inferProductAliasCode(context.chatName) : productAliasCode;
            if (context.regionName.isEmpty() && !context.regionId.isEmpty()) {
                RegionInfo regionInfo = resolveRegionInfo(context.regionId);
                context.regionName = regionInfo.name;
                context.regionCode = regionInfo.code;
            }
            if (context.regionId.isEmpty()) {
                String fallbackRegionId = resolveFallbackRegionId(extChatId, context.clientId);
                if (!fallbackRegionId.isEmpty()) {
                    context.regionId = fallbackRegionId;
                    RegionInfo regionInfo = resolveRegionInfo(fallbackRegionId);
                    context.regionName = regionInfo.name;
                    context.regionCode = regionInfo.code;
                }
            }
            context.financeRegionCode = resolveFinanceRegionCode(context.regionName, context.regionCode);
            LOGGER.info("resolveChatFinanceContext timing extChatId={}, rowCount={}, contractCount={}, customerNameCount={}, productAliasCode={}, regionId={}, regionName={}, financeRegionCode={}, totalMs={}",
                    extChatId,
                    rows.size(),
                    context.contractNumbers.size(),
                    context.customerNames.size(),
                    context.productAliasCode,
                    context.regionId,
                    context.regionName,
                    context.financeRegionCode,
                    (System.nanoTime() - startNs) / 1_000_000);
            return context;
        } finally {
            JdbcUtils.clearConfig();
        }
    }

    private List<String> deriveNamesFromChatName(String chatName) {
        Set<String> names = new LinkedHashSet<>();
        String original = trim(chatName);
        if (original.isEmpty()) {
            return new ArrayList<>();
        }

        names.add(original);

        int rightBracket = original.lastIndexOf('】');
        if (rightBracket >= 0 && rightBracket < original.length() - 1) {
            String tail = trim(original.substring(rightBracket + 1));
            if (!tail.isEmpty()) {
                names.add(tail);
            }
        }

        String noBracket = trim(original.replaceAll("【[^】]*】", ""));
        if (!noBracket.isEmpty()) {
            names.add(noBracket);
        }

        for (String suffix : new String[]{"实施售后群", "支持群", "实施群", "售后群"}) {
            if (noBracket.endsWith(suffix)) {
                String stripped = trim(noBracket.substring(0, noBracket.length() - suffix.length()));
                if (!stripped.isEmpty()) {
                    names.add(stripped);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private String resolveFallbackRegionId(String extChatId, Long clientId) {
        if (clientId != null) {
            String regionSql = "SELECT smr.region_id " +
                    "FROM support_maintenance_record smr " +
                    "WHERE smr.client_id = ? AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                    "ORDER BY smr.create_time DESC LIMIT 1";
            List<Object[]> regionResult = JdbcUtils.query(regionSql, clientId);
            if (!regionResult.isEmpty() && regionResult.get(0)[0] != null) {
                return trim(regionResult.get(0)[0].toString());
            }
        }
        if (!trim(extChatId).isEmpty()) {
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
            List<Object[]> regionResultByChat = JdbcUtils.query(regionSqlByChat, extChatId);
            if (!regionResultByChat.isEmpty() && regionResultByChat.get(0)[0] != null) {
                return trim(regionResultByChat.get(0)[0].toString());
            }
        }
        return "";
    }

    private RegionInfo resolveRegionInfo(String regionId) {
        String normalizedRegionId = trim(regionId);
        if (normalizedRegionId.isEmpty()) {
            return RegionInfo.empty();
        }
        String sql = "SELECT name, code FROM region WHERE id = ? LIMIT 1";
        List<Object[]> rows = JdbcUtils.query(sql, normalizedRegionId);
        if (rows.isEmpty()) {
            return RegionInfo.empty();
        }
        return new RegionInfo(
                trim(rows.get(0)[0] != null ? rows.get(0)[0].toString() : ""),
                trim(rows.get(0)[1] != null ? rows.get(0)[1].toString() : "")
        );
    }

    private String resolveFinanceRegionCode(String regionName, String regionCode) {
        String normalizedName = trim(regionName);
        if (!normalizedName.isEmpty()) {
            String mapped = REGION_NAME_TO_FINANCE_CODE.get(normalizedName);
            if (mapped != null) {
                return mapped;
            }
        }
        String normalizedCode = trim(regionCode).toLowerCase(Locale.ROOT);
        return switch (normalizedCode) {
            case "eastern" -> "CODE2";
            case "northern" -> "CODE1";
            case "southern" -> "CODE3";
            default -> "";
        };
    }

    private LedgerRecord resolveLedgerRecord(ChatFinanceContext context, LookupMode lookupMode) {
        long startNs = System.nanoTime();
        boolean allowFuzzy = lookupMode == LookupMode.FULL;
        JdbcUtils.setConfig(lookupMode == LookupMode.FAST
                ? JdbcUtils.getCordyscrmFastLookupDatabaseConfig()
                : JdbcUtils.getCordyscrmDatabaseConfig());
        try {
            for (String contractNumber : context.contractNumbers) {
                LedgerRecord byContract = queryLedgerByContractNumber(contractNumber, context);
                if (byContract != null && isLedgerConsistentWithContext(byContract, context)) {
                    LOGGER.info("resolveLedgerRecord stage=contractNumber hit lookupMode={}, contractNumber={}, contractCode={}, totalMs={}",
                            lookupMode.name().toLowerCase(),
                            contractNumber,
                            byContract.getCode(),
                            (System.nanoTime() - startNs) / 1_000_000);
                    return byContract;
                } else if (byContract != null) {
                    LOGGER.warn("resolveLedgerRecord stage=contractNumber_mismatch skip lookupMode={}, contractNumber={}, contractCode={}, customers={}",
                            lookupMode.name().toLowerCase(),
                            contractNumber,
                            byContract.getCode(),
                            context.customerNames);
                }
            }

            if (!trim(context.productAliasCode).isEmpty()) {
                LedgerMatch exactWithProduct = queryLedgerByCustomerNames(context, context.customerNames, false, context.productAliasCode);
                if (exactWithProduct != null) {
                    LOGGER.info("resolveLedgerRecord stage=customerExactWithProduct hit lookupMode={}, customerName={}, productAliasCode={}, contractCode={}, totalMs={}",
                            lookupMode.name().toLowerCase(),
                            exactWithProduct.matchedCustomerName,
                            context.productAliasCode,
                            exactWithProduct.record.getCode(),
                            (System.nanoTime() - startNs) / 1_000_000);
                    return exactWithProduct.record;
                }

                if (allowFuzzy) {
                    LedgerMatch fuzzyWithProduct = queryLedgerByCustomerNames(context, context.customerNames, true, context.productAliasCode);
                    if (fuzzyWithProduct != null) {
                        LOGGER.info("resolveLedgerRecord stage=customerFuzzyWithProduct hit lookupMode={}, customerName={}, productAliasCode={}, contractCode={}, totalMs={}",
                                lookupMode.name().toLowerCase(),
                                fuzzyWithProduct.matchedCustomerName,
                                context.productAliasCode,
                                fuzzyWithProduct.record.getCode(),
                                (System.nanoTime() - startNs) / 1_000_000);
                        return fuzzyWithProduct.record;
                    }
                }
            }

            LedgerMatch exactFallback = queryLedgerByCustomerNames(context, context.customerNames, false, "");
            if (exactFallback != null) {
                LOGGER.info("resolveLedgerRecord stage=customerExactFallback hit lookupMode={}, customerName={}, contractCode={}, totalMs={}",
                        lookupMode.name().toLowerCase(),
                        exactFallback.matchedCustomerName,
                        exactFallback.record.getCode(),
                        (System.nanoTime() - startNs) / 1_000_000);
                return exactFallback.record;
            }

            if (allowFuzzy) {
                LedgerMatch fuzzyFallback = queryLedgerByCustomerNames(context, context.customerNames, true, "");
                if (fuzzyFallback != null) {
                    LOGGER.info("resolveLedgerRecord stage=customerFuzzyFallback hit lookupMode={}, customerName={}, contractCode={}, totalMs={}",
                            lookupMode.name().toLowerCase(),
                            fuzzyFallback.matchedCustomerName,
                            fuzzyFallback.record.getCode(),
                            (System.nanoTime() - startNs) / 1_000_000);
                    return fuzzyFallback.record;
                }
            }
            LOGGER.warn("resolveLedgerRecord stage=miss chatName={}, lookupMode={}, contractCount={}, customerNameCount={}, productAliasCode={}, allowFuzzy={}, totalMs={}",
                    context.chatName,
                    lookupMode.name().toLowerCase(),
                    context.contractNumbers.size(),
                    context.customerNames.size(),
                    context.productAliasCode,
                    allowFuzzy,
                    (System.nanoTime() - startNs) / 1_000_000);
            return null;
        } finally {
            JdbcUtils.clearConfig();
        }
    }

    private LedgerRecord queryLedgerByContractNumber(String contractNumber, ChatFinanceContext context) {
        long startNs = System.nanoTime();
        String normalized = trim(contractNumber);
        if (normalized.isEmpty()) {
            return null;
        }
        List<Object> params = new ArrayList<>();
        String sql = buildLedgerSelectSql() +
                "WHERE ecl.code = ? ";
        params.add(normalized);
        sql += appendCommonLedgerFilters(context, params, !trim(context.productAliasCode).isEmpty() ? context.productAliasCode : "");
        sql += "ORDER BY ecl.check_accept_date DESC, ecl.id DESC LIMIT 1";
        List<Object[]> rows = JdbcUtils.query(sql, params.toArray());
        LOGGER.info("queryLedgerByContractNumber contractNumber={}, rowCount={}, costMs={}",
                normalized, rows.size(), (System.nanoTime() - startNs) / 1_000_000);
        if (rows.isEmpty()) {
            return null;
        }
        return toLedgerRecord(rows.get(0));
    }

    private LedgerMatch queryLedgerByCustomerNames(ChatFinanceContext context, List<String> customerNames, boolean fuzzy, String productAliasCode) {
        long startNs = System.nanoTime();
        List<String> normalizedNames = normalizeCustomerNames(customerNames);
        if (normalizedNames.isEmpty()) {
            return null;
        }

        String sql = buildLedgerSelectSql() + "WHERE ";
        List<Object> params = new ArrayList<>();

        if (fuzzy) {
            StringJoiner orJoiner = new StringJoiner(" OR ");
            for (String normalized : normalizedNames) {
                orJoiner.add("(contracted_customer LIKE CONCAT('%', ?, '%') OR end_customer LIKE CONCAT('%', ?, '%') OR salfesforce_name LIKE CONCAT('%', ?, '%'))");
                params.add(normalized);
                params.add(normalized);
                params.add(normalized);
            }
            sql += "(" + orJoiner + ") ";
        } else {
            String placeholders = String.join(", ", Collections.nCopies(normalizedNames.size(), "?"));
            sql += "(contracted_customer IN (" + placeholders + ") " +
                    " OR end_customer IN (" + placeholders + ") " +
                    " OR salfesforce_name IN (" + placeholders + ")) ";
            params.addAll(normalizedNames);
            params.addAll(normalizedNames);
            params.addAll(normalizedNames);
        }

        sql += appendCommonLedgerFilters(context, params, productAliasCode);
        sql += "ORDER BY ecl.check_accept_date DESC, ecl.id DESC LIMIT 1";

        List<Object[]> rows = JdbcUtils.query(sql, params.toArray());
        LOGGER.info("queryLedgerByCustomerNames customerNameCount={}, fuzzy={}, productAliasCode={}, rowCount={}, costMs={}, candidates={}",
                normalizedNames.size(),
                fuzzy,
                trim(productAliasCode),
                rows.size(),
                (System.nanoTime() - startNs) / 1_000_000,
                normalizedNames);
        if (rows.isEmpty()) {
            return null;
        }
        LedgerRecord record = toLedgerRecord(rows.get(0));
        return new LedgerMatch(record, resolveMatchedCustomerName(record, normalizedNames));
    }

    private String buildLedgerSelectSql() {
        return "SELECT ecl.code, " +
                "       ecl.contracted_customer, " +
                "       ecl.end_customer, " +
                "       ecl.salfesforce_name, " +
                "       ecl.sku, " +
                "       ecl.start_date, " +
                "       ed_ca.name AS check_accept_name, " +
                "       ecl.check_accept_date, " +
                "       ecl.check_accept_report, " +
                "       ecl.active, " +
                "       ecl.name AS contract_name, " +
                "       ecl.region, " +
                "       ed_region.name AS region_name, " +
                "       ed_region.code AS region_code " +
                "FROM ekuaibao_contract_ledger ecl " +
                "LEFT JOIN ekuaibao_dimensions ed_ca ON ed_ca.id = ecl.check_accept AND ed_ca.active = 1 " +
                "LEFT JOIN ekuaibao_dimensions ed_region ON ed_region.id = ecl.region AND ed_region.active = 1 ";
    }

    private String appendCommonLedgerFilters(ChatFinanceContext context, List<Object> params, String productAliasCode) {
        StringBuilder sql = new StringBuilder();
        sql.append("AND ecl.active = true ");
        if (!EXCLUDED_CONTRACT_NAMES.isEmpty()) {
            sql.append("AND ecl.name NOT IN (")
                    .append(String.join(", ", Collections.nCopies(EXCLUDED_CONTRACT_NAMES.size(), "?")))
                    .append(") ");
            params.addAll(EXCLUDED_CONTRACT_NAMES);
        }
        String financeRegionCode = context != null ? trim(context.financeRegionCode) : "";
        if (!financeRegionCode.isEmpty()) {
            sql.append("AND ed_region.code = ? ");
            params.add(financeRegionCode);
        }
        sql.append(buildProductFilterSql(productAliasCode));
        return sql.toString();
    }

    private List<String> normalizeCustomerNames(List<String> customerNames) {
        if (customerNames == null || customerNames.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String customerName : customerNames) {
            String value = trim(customerName);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String resolveMatchedCustomerName(LedgerRecord record, List<String> normalizedNames) {
        if (record == null || normalizedNames == null) {
            return "";
        }
        String contractedCustomer = trim(record.getContractedCustomer());
        String endCustomer = trim(record.getEndCustomer());
        String salesforceName = trim(record.getSalfesforceName());
        for (String candidate : normalizedNames) {
            if (candidate.equals(contractedCustomer) || candidate.equals(endCustomer) || candidate.equals(salesforceName)) {
                return candidate;
            }
        }
        for (String candidate : normalizedNames) {
            if ((!contractedCustomer.isEmpty() && contractedCustomer.contains(candidate)) ||
                    (!endCustomer.isEmpty() && endCustomer.contains(candidate)) ||
                    (!salesforceName.isEmpty() && salesforceName.contains(candidate)) ||
                    candidate.contains(contractedCustomer) ||
                    candidate.contains(endCustomer) ||
                    (!salesforceName.isEmpty() && candidate.contains(salesforceName))) {
                return candidate;
            }
        }
        return normalizedNames.isEmpty() ? "" : normalizedNames.get(0);
    }

    private boolean isLedgerConsistentWithContext(LedgerRecord record, ChatFinanceContext context) {
        if (record == null || context == null) {
            return false;
        }
        String financeRegionCode = trim(context.financeRegionCode);
        if (!financeRegionCode.isEmpty() && !financeRegionCode.equalsIgnoreCase(trim(record.getRegionCode()))) {
            return false;
        }
        return matchesAnyCustomerName(record, normalizeCustomerNames(context.customerNames));
    }

    private boolean matchesAnyCustomerName(LedgerRecord record, List<String> normalizedNames) {
        if (record == null || normalizedNames == null || normalizedNames.isEmpty()) {
            return false;
        }
        String contractedCustomer = trim(record.getContractedCustomer());
        String endCustomer = trim(record.getEndCustomer());
        String salesforceName = trim(record.getSalfesforceName());
        for (String candidate : normalizedNames) {
            if (candidate.equals(contractedCustomer) || candidate.equals(endCustomer) || candidate.equals(salesforceName)) {
                return true;
            }
        }
        for (String candidate : normalizedNames) {
            if ((!contractedCustomer.isEmpty() && contractedCustomer.contains(candidate)) ||
                    (!endCustomer.isEmpty() && endCustomer.contains(candidate)) ||
                    (!salesforceName.isEmpty() && salesforceName.contains(candidate)) ||
                    candidate.contains(contractedCustomer) ||
                    candidate.contains(endCustomer) ||
                    (!salesforceName.isEmpty() && candidate.contains(salesforceName))) {
                return true;
            }
        }
        return false;
    }

    private LedgerRecord toLedgerRecord(Object[] row) {
        LedgerRecord record = new LedgerRecord();
        record.code = row[0] != null ? row[0].toString() : "";
        record.contractedCustomer = row[1] != null ? row[1].toString() : "";
        record.endCustomer = row[2] != null ? row[2].toString() : "";
        record.salfesforceName = row[3] != null ? row[3].toString() : "";
        record.sku = row[4] != null ? row[4].toString() : "";
        record.startDate = row[5];
        record.checkAccept = row[6] != null ? row[6].toString() : "";
        record.checkAcceptDate = row[7];
        record.checkAcceptReport = row[8] != null ? row[8].toString() : "";
        record.active = row[9] != null && Boolean.parseBoolean(String.valueOf(row[9]));
        record.contractName = row[10] != null ? row[10].toString() : "";
        record.region = row[11] != null ? row[11].toString() : "";
        record.regionName = row[12] != null ? row[12].toString() : "";
        record.regionCode = row[13] != null ? row[13].toString() : "";
        return record;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String inferProductAliasCode(String source) {
        String text = trim(source).toUpperCase();
        if (text.isEmpty()) {
            return "";
        }
        if (text.contains("JUMPSERVER") || text.contains("JS")) {
            return "JS";
        }
        if (text.contains("MAXKB") || text.contains("MK")) {
            return "MK";
        }
        if (text.contains("DATAEASE") || text.contains("DE")) {
            return "DE";
        }
        if (text.contains("SQLBOT")) {
            return "SQLBOT";
        }
        return "";
    }

    private String buildProductFilterSql(String productAliasCode) {
        return switch (trim(productAliasCode).toUpperCase()) {
            case "JS" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ecl.sku " +
                    "AND ed.active = 1 " +
                    "AND (UPPER(COALESCE(ed.name, '')) LIKE '%-JS-%' OR UPPER(COALESCE(ed.name, '')) LIKE 'JS-%' OR UPPER(COALESCE(ed.name, '')) LIKE '%JUMPSERVER%'))";
            case "MK" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ecl.sku " +
                    "AND ed.active = 1 " +
                    "AND (UPPER(COALESCE(ed.name, '')) LIKE '%-MK-%' OR UPPER(COALESCE(ed.name, '')) LIKE 'MK-%' OR UPPER(COALESCE(ed.name, '')) LIKE '%MAXKB%'))";
            case "DE" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ecl.sku " +
                    "AND ed.active = 1 " +
                    "AND (UPPER(COALESCE(ed.name, '')) LIKE '%-DE-%' OR UPPER(COALESCE(ed.name, '')) LIKE 'DE-%' OR UPPER(COALESCE(ed.name, '')) LIKE '%DATAEASE%'))";
            case "SQLBOT" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ecl.sku " +
                    "AND ed.active = 1 " +
                    "AND UPPER(COALESCE(ed.name, '')) LIKE '%SQLBOT%')";
            default -> "";
        };
    }

    private void logLookupInfo(String event, String extChatId, LookupMode lookupMode, long startNs, boolean found) {
        LOGGER.info("ledger_lookup event={} extChatId={}, mode={}, totalMs={}, found={}",
                event,
                extChatId,
                lookupMode.name().toLowerCase(),
                (System.nanoTime() - startNs) / 1_000_000,
                found);
    }

    private void logLookupWarn(String event, String extChatId, LookupMode lookupMode, long startNs, Throwable error) {
        LOGGER.warn("ledger_lookup event={} extChatId={}, mode={}, totalMs={}, err={}",
                event,
                extChatId,
                lookupMode.name().toLowerCase(),
                (System.nanoTime() - startNs) / 1_000_000,
                error != null ? error.getMessage() : "");
    }

    private String classifyLookupFailure(Throwable error, LookupMode lookupMode) {
        if (lookupMode == LookupMode.FAST && containsIgnoreCase(error, "timeout")) {
            return "fast_lookup_timeout";
        }
        return "ledger_query_failed";
    }

    private boolean containsIgnoreCase(Throwable error, String keyword) {
        Throwable current = error;
        String expected = trim(keyword).toLowerCase();
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @PreDestroy
    public void shutdownRefreshExecutor() {
        refreshExecutor.shutdown();
        try {
            if (!refreshExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                refreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            refreshExecutor.shutdownNow();
        }
    }

    private static class ChatFinanceContext {
        private String chatName;
        private Long clientId;
        private List<String> contractNumbers;
        private List<String> customerNames;
        private String productAliasCode;
        private String regionId;
        private String regionName;
        private String regionCode;
        private String financeRegionCode;
    }

    private static class RegionInfo {
        private final String name;
        private final String code;

        private RegionInfo(String name, String code) {
            this.name = name;
            this.code = code;
        }

        private static RegionInfo empty() {
            return new RegionInfo("", "");
        }
    }

    private static class LedgerMatch {
        private final LedgerRecord record;
        private final String matchedCustomerName;

        private LedgerMatch(LedgerRecord record, String matchedCustomerName) {
            this.record = record;
            this.matchedCustomerName = matchedCustomerName;
        }
    }

    private static class FreshCacheEntry {
        private final LedgerRecord record;
        private final long expiresAtMs;
        private final LookupMode coverageMode;

        private FreshCacheEntry(LedgerRecord record, long expiresAtMs, LookupMode coverageMode) {
            this.record = record;
            this.expiresAtMs = expiresAtMs;
            this.coverageMode = coverageMode;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }

        private boolean canServe(LookupMode lookupMode) {
            if (record != null) {
                return true;
            }
            return lookupMode == LookupMode.FAST;
        }
    }

    private static class LastKnownGoodCacheEntry {
        private final LedgerRecord record;
        private final long expiresAtMs;

        private LastKnownGoodCacheEntry(LedgerRecord record, long expiresAtMs) {
            this.record = record;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }

    private static class LookupExecutionResult {
        private final LedgerRecord record;

        private LookupExecutionResult(LedgerRecord record) {
            this.record = record;
        }
    }

    private enum LookupMode {
        FAST,
        FULL
    }

    public static class LedgerRecord {
        private String code;
        private String contractedCustomer;
        private String endCustomer;
        private String salfesforceName;
        private String sku;
        private Object startDate;
        private String checkAccept;
        private Object checkAcceptDate;
        private String checkAcceptReport;
        private boolean active;
        private String contractName;
        private String region;
        private String regionName;
        private String regionCode;

        public String getCode() {
            return code;
        }

        public String getContractedCustomer() {
            return contractedCustomer;
        }

        public String getEndCustomer() {
            return endCustomer;
        }

        public String getSku() {
            return sku;
        }

        public Object getStartDate() {
            return startDate;
        }

        public String getCheckAccept() {
            return checkAccept;
        }

        public Object getCheckAcceptDate() {
            return checkAcceptDate;
        }

        public String getCheckAcceptReport() {
            return checkAcceptReport;
        }

        public String getSalfesforceName() {
            return salfesforceName;
        }

        public boolean isActive() {
            return active;
        }

        public String getContractName() {
            return contractName;
        }

        public String getRegion() {
            return region;
        }

        public String getRegionName() {
            return regionName;
        }

        public String getRegionCode() {
            return regionCode;
        }
    }

    private static class RefreshThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "finance-ledger-refresh-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
