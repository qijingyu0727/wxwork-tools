package com.service;

import com.util.JdbcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class FinanceLedgerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinanceLedgerService.class);

    public LedgerRecord resolveLedgerRecordByExtChatId(String extChatId) {
        long startNs = System.nanoTime();
        try {
            ChatFinanceContext context = resolveChatFinanceContext(extChatId);
            LedgerRecord record = resolveLedgerRecord(context);
            LOGGER.info("resolveLedgerRecordByExtChatId timing extChatId={}, totalMs={}, found={}",
                    extChatId,
                    (System.nanoTime() - startNs) / 1_000_000,
                    record != null);
            return record;
        } catch (RuntimeException e) {
            LOGGER.warn("resolveLedgerRecordByExtChatId failed extChatId={}, totalMs={}, err={}",
                    extChatId,
                    (System.nanoTime() - startNs) / 1_000_000,
                    e.getMessage());
            throw e;
        }
    }

    private ChatFinanceContext resolveChatFinanceContext(String extChatId) {
        long startNs = System.nanoTime();
        JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT gc.name AS chat_name, " +
                    "       ss.contract_number, " +
                    "       ss.client_id, " +
                    "       sc.name AS client_name, " +
                    "       sc.abbreviated_name AS client_abbr, " +
                    "       sps.name AS product_service_name " +
                    "FROM group_chat gc " +
                    "LEFT JOIN support_subscription ss ON ss.group_chat_name = gc.name " +
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

            Set<String> contractSet = new LinkedHashSet<>();
            Set<String> nameSet = new LinkedHashSet<>();
            String productAliasCode = "";

            for (Object[] row : rows) {
                String contractNumber = trim(row[1] != null ? row[1].toString() : "");
                if (!contractNumber.isEmpty()) {
                    contractSet.add(contractNumber);
                }
                String clientName = trim(row[3] != null ? row[3].toString() : "");
                if (!clientName.isEmpty()) {
                    nameSet.add(clientName);
                }
                String clientAbbr = trim(row[4] != null ? row[4].toString() : "");
                if (!clientAbbr.isEmpty()) {
                    nameSet.add(clientAbbr);
                }
                if (productAliasCode.isEmpty()) {
                    productAliasCode = inferProductAliasCode(row[5] != null ? row[5].toString() : "");
                }
            }

            nameSet.addAll(deriveNamesFromChatName(context.chatName));
            context.contractNumbers.addAll(contractSet);
            context.customerNames.addAll(nameSet);
            context.productAliasCode = productAliasCode.isEmpty() ? inferProductAliasCode(context.chatName) : productAliasCode;
            LOGGER.info("resolveChatFinanceContext timing extChatId={}, rowCount={}, contractCount={}, customerNameCount={}, productAliasCode={}, totalMs={}",
                    extChatId,
                    rows.size(),
                    context.contractNumbers.size(),
                    context.customerNames.size(),
                    context.productAliasCode,
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

    private LedgerRecord resolveLedgerRecord(ChatFinanceContext context) {
        long startNs = System.nanoTime();
        JdbcUtils.setCordyscrmConfig();
        try {
            for (String contractNumber : context.contractNumbers) {
                LedgerRecord byContract = queryLedgerByContractNumber(contractNumber);
                if (byContract != null) {
                    LOGGER.info("resolveLedgerRecord stage=contractNumber hit contractNumber={}, contractCode={}, totalMs={}",
                            contractNumber, byContract.getCode(), (System.nanoTime() - startNs) / 1_000_000);
                    return byContract;
                }
            }

            if (!trim(context.productAliasCode).isEmpty()) {
                LedgerMatch exactWithProduct = queryLedgerByCustomerNames(context.customerNames, false, context.productAliasCode);
                if (exactWithProduct != null) {
                    LOGGER.info("resolveLedgerRecord stage=customerExactWithProduct hit customerName={}, productAliasCode={}, contractCode={}, totalMs={}",
                            exactWithProduct.matchedCustomerName,
                            context.productAliasCode,
                            exactWithProduct.record.getCode(),
                            (System.nanoTime() - startNs) / 1_000_000);
                    return exactWithProduct.record;
                }

                LedgerMatch fuzzyWithProduct = queryLedgerByCustomerNames(context.customerNames, true, context.productAliasCode);
                if (fuzzyWithProduct != null) {
                    LOGGER.info("resolveLedgerRecord stage=customerFuzzyWithProduct hit customerName={}, productAliasCode={}, contractCode={}, totalMs={}",
                            fuzzyWithProduct.matchedCustomerName,
                            context.productAliasCode,
                            fuzzyWithProduct.record.getCode(),
                            (System.nanoTime() - startNs) / 1_000_000);
                    return fuzzyWithProduct.record;
                }
            }

            LedgerMatch exactFallback = queryLedgerByCustomerNames(context.customerNames, false, "");
            if (exactFallback != null) {
                LOGGER.info("resolveLedgerRecord stage=customerExactFallback hit customerName={}, contractCode={}, totalMs={}",
                        exactFallback.matchedCustomerName,
                        exactFallback.record.getCode(),
                        (System.nanoTime() - startNs) / 1_000_000);
                return exactFallback.record;
            }

            LedgerMatch fuzzyFallback = queryLedgerByCustomerNames(context.customerNames, true, "");
            if (fuzzyFallback != null) {
                LOGGER.info("resolveLedgerRecord stage=customerFuzzyFallback hit customerName={}, contractCode={}, totalMs={}",
                        fuzzyFallback.matchedCustomerName,
                        fuzzyFallback.record.getCode(),
                        (System.nanoTime() - startNs) / 1_000_000);
                return fuzzyFallback.record;
            }
            LOGGER.warn("resolveLedgerRecord stage=miss chatName={}, contractCount={}, customerNameCount={}, productAliasCode={}, totalMs={}",
                    context.chatName, context.contractNumbers.size(), context.customerNames.size(), context.productAliasCode,
                    (System.nanoTime() - startNs) / 1_000_000);
            return null;
        } finally {
            JdbcUtils.clearConfig();
        }
    }

    private LedgerRecord queryLedgerByContractNumber(String contractNumber) {
        long startNs = System.nanoTime();
        String normalized = trim(contractNumber);
        if (normalized.isEmpty()) {
            return null;
        }
        String sql = "SELECT code, contracted_customer, end_customer, sku, start_date, check_accept_date, check_accept_report " +
                "FROM ekuaibao_contract_ledger " +
                "WHERE code = ? " +
                "ORDER BY id DESC LIMIT 1";
        List<Object[]> rows = JdbcUtils.query(sql, normalized);
        LOGGER.info("queryLedgerByContractNumber contractNumber={}, rowCount={}, costMs={}",
                normalized, rows.size(), (System.nanoTime() - startNs) / 1_000_000);
        if (rows.isEmpty()) {
            return null;
        }
        return toLedgerRecord(rows.get(0));
    }

    private LedgerMatch queryLedgerByCustomerNames(List<String> customerNames, boolean fuzzy, String productAliasCode) {
        long startNs = System.nanoTime();
        List<String> normalizedNames = normalizeCustomerNames(customerNames);
        if (normalizedNames.isEmpty()) {
            return null;
        }

        String productFilter = buildProductFilterSql(productAliasCode);
        String sql = "SELECT code, contracted_customer, end_customer, sku, start_date, check_accept_date, check_accept_report " +
                "FROM ekuaibao_contract_ledger WHERE ";
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

        sql += productFilter + "ORDER BY check_accept_date DESC, id DESC LIMIT 1";

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
        for (String candidate : normalizedNames) {
            if (candidate.equals(contractedCustomer) || candidate.equals(endCustomer)) {
                return candidate;
            }
        }
        for (String candidate : normalizedNames) {
            if ((!contractedCustomer.isEmpty() && contractedCustomer.contains(candidate)) ||
                    (!endCustomer.isEmpty() && endCustomer.contains(candidate)) ||
                    candidate.contains(contractedCustomer) ||
                    candidate.contains(endCustomer)) {
                return candidate;
            }
        }
        return normalizedNames.isEmpty() ? "" : normalizedNames.get(0);
    }

    private LedgerRecord toLedgerRecord(Object[] row) {
        LedgerRecord record = new LedgerRecord();
        record.code = row[0] != null ? row[0].toString() : "";
        record.contractedCustomer = row[1] != null ? row[1].toString() : "";
        record.endCustomer = row[2] != null ? row[2].toString() : "";
        record.sku = row[3] != null ? row[3].toString() : "";
        record.startDate = row[4];
        record.checkAcceptDate = row[5];
        record.checkAcceptReport = row[6] != null ? row[6].toString() : "";
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
            case "JS" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ekuaibao_contract_ledger.sku " +
                    "AND (UPPER(COALESCE(ed.name, '')) LIKE '%-JS-%' OR UPPER(COALESCE(ed.name, '')) LIKE 'JS-%' OR UPPER(COALESCE(ed.name, '')) LIKE '%JUMPSERVER%'))";
            case "MK" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ekuaibao_contract_ledger.sku " +
                    "AND (UPPER(COALESCE(ed.name, '')) LIKE '%-MK-%' OR UPPER(COALESCE(ed.name, '')) LIKE 'MK-%' OR UPPER(COALESCE(ed.name, '')) LIKE '%MAXKB%'))";
            case "DE" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ekuaibao_contract_ledger.sku " +
                    "AND (UPPER(COALESCE(ed.name, '')) LIKE '%-DE-%' OR UPPER(COALESCE(ed.name, '')) LIKE 'DE-%' OR UPPER(COALESCE(ed.name, '')) LIKE '%DATAEASE%'))";
            case "SQLBOT" -> " AND EXISTS (SELECT 1 FROM ekuaibao_dimensions ed WHERE ed.id = ekuaibao_contract_ledger.sku " +
                    "AND UPPER(COALESCE(ed.name, '')) LIKE '%SQLBOT%')";
            default -> "";
        };
    }

    private static class ChatFinanceContext {
        private String chatName;
        private List<String> contractNumbers;
        private List<String> customerNames;
        private String productAliasCode;
    }

    private static class LedgerMatch {
        private final LedgerRecord record;
        private final String matchedCustomerName;

        private LedgerMatch(LedgerRecord record, String matchedCustomerName) {
            this.record = record;
            this.matchedCustomerName = matchedCustomerName;
        }
    }

    public static class LedgerRecord {
        private String code;
        private String contractedCustomer;
        private String endCustomer;
        private String sku;
        private Object startDate;
        private Object checkAcceptDate;
        private String checkAcceptReport;

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

        public Object getCheckAcceptDate() {
            return checkAcceptDate;
        }

        public String getCheckAcceptReport() {
            return checkAcceptReport;
        }
    }
}
