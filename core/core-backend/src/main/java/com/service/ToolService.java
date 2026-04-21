package com.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.model.CustomerData;
import com.model.ProductVersionSnapshot;
import com.model.request.AcceptanceReportRequest;
import com.model.request.MailDiagnoseRequest;
import com.model.request.SendToolMailRequest;
import com.util.HttpClientUtil;
import com.util.JdbcUtils;
import com.sun.mail.smtp.SMTPAddressFailedException;
import com.sun.mail.smtp.SMTPTransport;
import jakarta.annotation.Resource;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ToolService {
    private static final String MAIL_CLOSING_PRIMARY = "当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！";
    private static final String MAIL_CLOSING_SECONDARY = "感谢您信任飞致云的产品和服务，后续有问题可随时沟通！";


    private static final Logger LOGGER = LoggerFactory.getLogger(ToolService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern EMAIL_EXTRACT_PATTERN = Pattern.compile("(?i)([a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,})");
    private static final Pattern MAJOR_VERSION_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern DETAILED_VERSION_PATTERN = Pattern.compile("(?i)v?(\\d+(?:\\.\\d+)+)");
    private static final Pattern CONTRACT_DATE_PATTERN = Pattern.compile("20\\d{6}");
    private static final String EXTERNAL_CONFIG_PATH = "/opt/wxwork-tools/wxwork-tools.properties";
    private static final String ATTACH_RULE_PREFIX = "tool.mail.auto-attach.rule.";
    private static final String ATTACH_LINK_RULE_PREFIX = "tool.mail.attachment-link.rule.";
    private static final String NONE_ATTACHMENT = "NONE";
    private static final String ATTACHMENT_DELIVERY_MODE_ATTACH = "attach";
    private static final String ATTACHMENT_DELIVERY_MODE_LINK = "link";
    private static final String DEFAULT_TOOL_MAIL_CC = "ec_cssc@fit2cloud.com";
    private static final String ACCEPTANCE_DEFAULT_URL = "https://acid.fit2cloud.cn:67/chat/api/chat_message/019cbd20-4507-79d1-8920-d7ee49baf9f2";
    private static final String ACCEPTANCE_DEFAULT_DOCX_URL = "";
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private ChatGroupService chatGroupService;

    @Resource
    private FinanceLedgerService financeLedgerService;

    private final SmtpProfile smtpPrimaryProfile;
    private final SmtpProfile smtpSecondaryProfile;
    private final boolean smtpFallbackOnAuthFail;
    private final String smtpAuthMechanisms;
    private final String fromAddress;
    private final String authCode;
    private final String fromDisplayName;
    private final String attachmentDir;
    private final String attachmentDeliveryMode;
    private final boolean autoAttachEnabled;
    private final int retryTimes;
    private final int smtpConnectionTimeoutMs;
    private final int smtpTimeoutMs;
    private final int smtpWriteTimeoutMs;
    private final boolean smtpDebug;
    private final boolean mailLogVerbose;
    private final List<String> mailCcFixedDefault;
    private final Set<String> mailCcExcludeSalesUserIds;
    private final String fallbackVersionDataEase;
    private final String fallbackVersionJumpServer;
    private final String fallbackVersionMaxKB;
    private final String acceptanceDocxApiUrl;
    private final String acceptanceApiUrl;
    private final String acceptanceApiKey;
    private final boolean acceptanceLegacyFallbackEnabled;
    private final String activeConfigSource;
    private final boolean externalConfigExists;
    private final long externalConfigLastModified;
    private final List<AttachmentRule> attachmentRules;
    private final Map<String, String> attachmentLinksByFileName;

    public ToolService() {
        MailPropsContext mailCtx = loadMailProps();
        Properties mailProps = mailCtx.props;
        activeConfigSource = mailCtx.activeConfigSource;
        externalConfigExists = mailCtx.externalExists;
        externalConfigLastModified = mailCtx.externalLastModified;

        String legacyHost = getOrDefault(mailProps, "tool.mail.smtp-host", "smtp.exmail.qq.com");
        int legacyPort = parseIntOrDefault(getOrDefault(mailProps, "tool.mail.smtp-port", "465"), 465);
        boolean legacySsl = Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp-ssl-enable", "true"));
        boolean legacyStarttls = Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp-starttls-enable", "false"));

        smtpPrimaryProfile = new SmtpProfile(
                "primary",
                getOrDefault(mailProps, "tool.mail.smtp.primary.host", legacyHost),
                parseIntOrDefault(getOrDefault(mailProps, "tool.mail.smtp.primary.port", String.valueOf(legacyPort)), legacyPort),
                Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp.primary.ssl-enable", String.valueOf(legacySsl))),
                Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp.primary.starttls-enable", String.valueOf(legacyStarttls)))
        );
        smtpSecondaryProfile = new SmtpProfile(
                "secondary",
                getOrDefault(mailProps, "tool.mail.smtp.secondary.host", smtpPrimaryProfile.host),
                parseIntOrDefault(getOrDefault(mailProps, "tool.mail.smtp.secondary.port", "587"), 587),
                Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp.secondary.ssl-enable", "false")),
                Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp.secondary.starttls-enable", "true"))
        );
        smtpFallbackOnAuthFail = Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp-fallback-on-auth-fail", "true"));
        smtpAuthMechanisms = getOrDefault(mailProps, "tool.mail.smtp.auth-mechanisms", "LOGIN PLAIN");
        fromAddress = getOrDefault(mailProps, "tool.mail.from-address", "");
        authCode = getOrDefault(mailProps, "tool.mail.auth-code", "");
        fromDisplayName = getOrDefault(mailProps, "tool.mail.from-display-name", "Fit2Cloud Support");
        attachmentDir = getOrDefault(mailProps, "tool.mail.attachment-dir", "/opt/wxwork-tools/mail-attachments");
        attachmentDeliveryMode = normalizeAttachmentDeliveryMode(getOrDefault(mailProps, "tool.mail.attachment-delivery-mode", ATTACHMENT_DELIVERY_MODE_LINK));
        autoAttachEnabled = Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.auto-attach.enabled", "true"));
        retryTimes = Math.max(parseIntOrDefault(getOrDefault(mailProps, "tool.mail.retry-times", "1"), 1), 0);
        smtpConnectionTimeoutMs = Math.max(parseIntOrDefault(getOrDefault(mailProps, "tool.mail.smtp-connection-timeout-ms", "30000"), 30000), 1000);
        smtpTimeoutMs = Math.max(parseIntOrDefault(getOrDefault(mailProps, "tool.mail.smtp-timeout-ms", "180000"), 180000), 1000);
        smtpWriteTimeoutMs = Math.max(parseIntOrDefault(getOrDefault(mailProps, "tool.mail.smtp-write-timeout-ms", "180000"), 180000), 1000);
        smtpDebug = Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.smtp-debug", "false"));
        mailLogVerbose = Boolean.parseBoolean(getOrDefault(mailProps, "tool.mail.log-verbose", "false"));
        mailCcFixedDefault = parseConfiguredEmailList(getOrDefault(mailProps, "tool.mail.cc.fixed-default", DEFAULT_TOOL_MAIL_CC), "tool.mail.cc.fixed-default");
        mailCcExcludeSalesUserIds = parseConfiguredIdSet(getOrDefault(mailProps, "tool.mail.cc.exclude-sales-user-ids", ""));
        fallbackVersionDataEase = getOrDefault(mailProps, "tool.mail.version-fallback.dataease", "v2");
        fallbackVersionJumpServer = getOrDefault(mailProps, "tool.mail.version-fallback.jumpserver", "v4");
        fallbackVersionMaxKB = getOrDefault(mailProps, "tool.mail.version-fallback.maxkb", "v2");
        acceptanceDocxApiUrl = getOrDefault(mailProps, "tool.acceptance.docx-api.url", ACCEPTANCE_DEFAULT_DOCX_URL);
        acceptanceApiUrl = getOrDefault(mailProps, "tool.acceptance.api.url", ACCEPTANCE_DEFAULT_URL);
        acceptanceApiKey = getOrDefault(mailProps, "tool.acceptance.api.key", "");
        acceptanceLegacyFallbackEnabled = Boolean.parseBoolean(getOrDefault(mailProps, "tool.acceptance.legacy-fallback-enabled", "true"));
        attachmentRules = loadAttachRulesFromProperties(mailProps);
        attachmentLinksByFileName = loadAttachmentLinksFromProperties(mailProps);

        LOGGER.info("tool config loaded: activeConfigSource={}, externalConfigExists={}, externalConfigLastModified={}, debug={}, logVerbose={}, fallbackOnAuthFail={}, authMechanisms={}, fromAddress={}, authCodeLength={}, authCodeHashPrefix={}, retryTimes={}, ccFixedDefaultCount={}, ccExcludeSalesUserCount={}, profiles={}, attachmentDeliveryMode={}, attachmentLinkRuleCount={}, acceptanceDocxApiUrl={}, acceptanceApiUrl={}, acceptanceLegacyFallbackEnabled={}, acceptanceApiKeyLength={}",
                activeConfigSource,
                externalConfigExists,
                externalConfigLastModified,
                smtpDebug,
                mailLogVerbose,
                smtpFallbackOnAuthFail,
                smtpAuthMechanisms,
                maskEmail(fromAddress),
                trim(authCode).length(),
                authCodeHashPrefix(authCode),
                retryTimes,
                mailCcFixedDefault.size(),
                mailCcExcludeSalesUserIds.size(),
                buildProfileSummary(),
                attachmentDeliveryMode,
                attachmentLinksByFileName.size(),
                acceptanceDocxApiUrl,
                acceptanceApiUrl,
                acceptanceLegacyFallbackEnabled,
                trim(acceptanceApiKey).length());
    }

    public Map<String, Object> sendToolMail(SendToolMailRequest request, String loginUserId) throws Exception {
        String toEmail = request != null ? trim(request.getToEmail()) : "";
        if (toEmail.isEmpty()) {
            throw new IllegalArgumentException("目标邮箱不能为空");
        }
        List<String> toEmails = parseCcInputEmails(toEmail, "目标邮箱");
        if (toEmails.isEmpty()) {
            throw new IllegalArgumentException("目标邮箱不能为空");
        }
        String toEmailText = String.join(";", toEmails);

        String extChatId = request != null ? trim(request.getExtChatId()) : "";
        if (extChatId.isEmpty()) {
            throw new IllegalArgumentException("extChatId 不能为空");
        }
        String requestCcEmails = request != null ? request.getCcEmails() : null;
        CcResolution ccResolution = resolveEffectiveCcResolution(
                extChatId,
                requestCcEmails,
                request != null && request.getCcEmails() != null,
                loginUserId
        );
        List<String> ccEmails = ccResolution.finalCcEmails;
        String ccEmailText = String.join(";", ccEmails);

        String sender = trim(fromAddress);
        String senderAuthCode = trim(authCode);
        if (sender.isEmpty()) {
            throw new IllegalStateException("邮件配置缺失：tool.mail.from-address");
        }
        if (senderAuthCode.isEmpty()) {
            throw new IllegalStateException("邮件配置缺失：tool.mail.auth-code");
        }
        if (isPlaceholderSecret(senderAuthCode)) {
            throw new IllegalStateException("邮件配置缺失：tool.mail.auth-code（当前为占位值，请填写真实客户端专用密码）");
        }

        ProductVersionSnapshot snapshot = chatGroupService.getProductVersionSnapshot(extChatId);
        String resolvedProduct = trim(snapshot != null ? snapshot.getProductAlias() : "");
        String resolvedVersion = trim(snapshot != null ? snapshot.getVersion() : "");
        if (resolvedVersion.isEmpty()) {
            resolvedVersion = resolveFallbackVersion(resolvedProduct);
        }
        String resolvedMajorVersion = extractMajorVersion(resolvedVersion);

        AttachmentRule matchedRule = matchAttachmentRule(resolvedProduct, resolvedMajorVersion);
        String matchedRuleValue = matchedRule != null ? matchedRule.raw : "";

        List<File> filesToAttach = new ArrayList<>();
        List<String> attachedAttachments = new ArrayList<>();
        List<String> linkedAttachments = new ArrayList<>();
        List<String> attachmentLinks = new ArrayList<>();
        List<String> skippedAttachments = new ArrayList<>();
        String warningMessage = null;

        if (autoAttachEnabled) {
            if (matchedRule == null) {
                warningMessage = buildNoRuleWarning(resolvedProduct, resolvedMajorVersion);
            } else if (NONE_ATTACHMENT.equalsIgnoreCase(matchedRule.fileName)) {
                warningMessage = "当前产品无需附件";
            } else {
                File file = resolveAttachmentFile(matchedRule.fileName);
                String attachmentLink = resolveAttachmentLink(matchedRule.fileName);
                if (shouldDeliverAsLink(file, attachmentLink)) {
                    linkedAttachments.add(matchedRule.fileName);
                    attachmentLinks.add(attachmentLink);
                } else if (file.exists() && file.isFile()) {
                    filesToAttach.add(file);
                    attachedAttachments.add(file.getName());
                } else if (!attachmentLink.isEmpty()) {
                    linkedAttachments.add(matchedRule.fileName);
                    attachmentLinks.add(attachmentLink);
                } else {
                    skippedAttachments.add(matchedRule.fileName);
                    warningMessage = "匹配附件不存在，已跳过：" + matchedRule.fileName;
                }
            }
        }

        String customerName = resolveCustomerName(request, extChatId);
        String subject = (resolvedProduct.isEmpty() ? "产品" : resolvedProduct) + " 环境部署交付通知";
        MailContent content = buildMailContent(resolvedProduct, resolvedMajorVersion, customerName, resolvedVersion, linkedAttachments, attachmentLinks);

        List<SmtpProfile> profilesToTry = resolveProfilesToTry();
        LOGGER.info("send tool mail start: toCount={}, toEmail={}, ccCount={}, defaultCcApplied={}, clientId={}, salesUserCount={}, excludedSalesUserCount={}, fromEmail={}, attachments={}, linkedAttachments={}, product={}, resolvedVersion={}, majorVersion={}, activeConfigSource={}, authMechanisms={}, fallbackOnAuthFail={}, profiles={}",
                toEmails.size(),
                toEmailText,
                ccEmails.size(),
                ccResolution.defaultCcApplied,
                ccResolution.clientId,
                ccResolution.salesUserIds.size(),
                ccResolution.excludedSalesUserIds.size(),
                maskEmail(sender),
                filesToAttach.size(),
                linkedAttachments.size(),
                resolvedProduct,
                resolvedVersion,
                resolvedMajorVersion,
                activeConfigSource,
                smtpAuthMechanisms,
                smtpFallbackOnAuthFail,
                summarizeProfiles(profilesToTry));

        int retryCount = 0;
        SmtpProfile usedProfile = null;
        Exception lastError = null;
        String lastFailureStage = "";
        List<String> profileErrors = new ArrayList<>();
        boolean fallbackUsed = false;

        for (int profileIndex = 0; profileIndex < profilesToTry.size(); profileIndex++) {
            SmtpProfile profile = profilesToTry.get(profileIndex);
            JavaMailSenderImpl mailSender = createMailSender(profile, sender, senderAuthCode);
            int maxAttempts = retryTimes + 1;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    sendMailOnce(mailSender, profile, sender, toEmails, ccEmails, subject, content.content, content.html, filesToAttach);
                    retryCount += (attempt - 1);
                    usedProfile = profile;
                    lastError = null;
                    break;
                } catch (Exception e) {
                    lastError = e;
                    if (attempt >= maxAttempts) {
                        break;
                    }
                    LOGGER.warn("send tool mail failed, retrying. profile={}, attempt={}/{}, toEmail={}, ccText={}, invalidAddresses={}, errClass={}, errMsg={}, rootClass={}, rootMsg={}, hint={}",
                            profile.name, attempt, maxAttempts, toEmailText,
                            ccEmailText,
                            summarizeInvalidAddresses(e),
                            e.getClass().getName(), e.getMessage(),
                            rootCauseClassName(e), rootCauseMessage(e),
                            buildAuthHint(e));
                    Thread.sleep(400L);
                }
            }

            if (usedProfile != null) {
                break;
            }

            if (lastError != null) {
                lastFailureStage = detectFailureStage(lastError);
                String summary = profile.name + "(" + lastFailureStage + ":" + sanitizeMessage(rootCauseMessage(lastError)) + ")";
                profileErrors.add(summary);
            }
            boolean canFallback = smtpFallbackOnAuthFail
                    && profileIndex + 1 < profilesToTry.size()
                    && isAuthFailure(lastError);
            if (!canFallback) {
                break;
            }
            fallbackUsed = true;
            LOGGER.warn("smtp auth failed on profile={}, fallback to next profile", profile.name);
        }

        if (usedProfile == null) {
            LOGGER.error("send tool mail final failure: toEmail={}, ccText={}, invalidAddresses={}, failureStage={}, profiles={}",
                    toEmailText, ccEmailText, summarizeInvalidAddresses(lastError), lastFailureStage, profileErrors);
            throw new Exception(buildSendMailFailureMessage(lastError, profileErrors), lastError);
        }

        LOGGER.info("tool mail sent success, profile={}, fallbackUsed={}, toCount={}, toEmail={}, ccCount={}, subject={}, product={}, version={}, attachments={}, linkedAttachments={}",
                usedProfile.name, fallbackUsed, toEmails.size(), toEmailText, ccEmails.size(), subject, resolvedProduct, resolvedVersion, attachedAttachments, linkedAttachments);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toEmail", toEmailText);
        result.put("toEmails", toEmails);
        result.put("ccEmails", ccEmails);
        result.put("ccText", String.join(";", ccEmails));
        result.put("defaultCcApplied", ccResolution.defaultCcApplied);
        result.put("clientId", ccResolution.clientId);
        result.put("salesUserIds", ccResolution.salesUserIds);
        result.put("excludedSalesUserIds", ccResolution.excludedSalesUserIds);
        result.put("loginUserId", ccResolution.loginUserId);
        result.put("loginUserEmail", ccResolution.loginUserEmail);
        result.put("sameDeptEmails", ccResolution.sameDeptEmails);
        result.put("mandatoryCcList", ccResolution.mandatoryCcList);
        result.put("optionalDefaultCcList", ccResolution.optionalDefaultCcList);
        result.put("fromEmail", sender);
        result.put("subject", subject);
        result.put("resolvedProduct", resolvedProduct);
        result.put("resolvedVersion", resolvedVersion);
        result.put("resolvedMajorVersion", resolvedMajorVersion);
        result.put("matchedRule", matchedRuleValue);
        result.put("attachedAttachments", attachedAttachments);
        result.put("linkedAttachments", linkedAttachments);
        result.put("attachmentLinks", attachmentLinks);
        result.put("skippedAttachments", skippedAttachments);
        result.put("warningMessage", warningMessage);
        result.put("retryCount", retryCount);
        result.put("usedProfile", usedProfile.name);
        result.put("fallbackUsed", fallbackUsed);
        result.put("activeConfigSource", activeConfigSource);
        result.put("authMechanisms", smtpAuthMechanisms);
        result.put("failureStage", lastFailureStage);
        return result;
    }

    public Map<String, Object> getMailDefaultCc(String extChatId, String loginUserId) {
        String normalizedExtChatId = trim(extChatId);
        if (normalizedExtChatId.isEmpty()) {
            throw new IllegalArgumentException("extChatId 不能为空");
        }
        String normalizedLoginUserId = trim(loginUserId);
        return buildMailDefaultCcResult(normalizedExtChatId, normalizedLoginUserId);
    }

    private Map<String, Object> buildMailDefaultCcResult(String extChatId, String loginUserId) {
        CcResolution resolution = buildDefaultCcResolution(extChatId, loginUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultCcText", String.join(";", resolution.defaultCcList));
        result.put("defaultCcList", resolution.defaultCcList);
        result.put("clientId", resolution.clientId);
        result.put("salesUserIds", resolution.salesUserIds);
        result.put("salesEmails", resolution.salesEmails);
        result.put("excludedSalesUserIds", resolution.excludedSalesUserIds);
        result.put("loginUserId", resolution.loginUserId);
        result.put("loginUserEmail", resolution.loginUserEmail);
        result.put("sameDeptEmails", resolution.sameDeptEmails);
        result.put("mandatoryCcList", resolution.mandatoryCcList);
        result.put("optionalDefaultCcList", resolution.optionalDefaultCcList);
        return result;
    }

    public Map<String, Object> getAcceptanceReport(AcceptanceReportRequest request) throws Exception {
        String extChatId = request != null ? trim(request.getExtChatId()) : "";
        if (extChatId.isEmpty()) {
            throw new IllegalArgumentException("extChatId 不能为空");
        }
        if (trim(acceptanceApiKey).isEmpty()) {
            throw new IllegalStateException("配置缺失：tool.acceptance.api.key");
        }
        boolean hasDocxApi = !trim(acceptanceDocxApiUrl).isEmpty();
        boolean hasLegacyApi = !trim(acceptanceApiUrl).isEmpty();
        if (!hasDocxApi && !hasLegacyApi) {
            throw new IllegalStateException("配置缺失：tool.acceptance.docx-api.url 或 tool.acceptance.api.url 至少配置一个");
        }

        FinanceLedgerService.LedgerRecord ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
        if (ledgerRecord == null) {
            throw new IllegalArgumentException("未找到匹配合同台账，请确认群聊关联的合同编号或客户名称");
        }

        String contractNumber = trim(ledgerRecord.getCode());
        String partya = trim(ledgerRecord.getContractedCustomer());
        String finalCustomer = trim(ledgerRecord.getEndCustomer());
        String skuId = trim(ledgerRecord.getSku());
        String skuName = resolveDimensionNameBySkuId(skuId);
        String skuDisplay = trim(skuName).isEmpty() ? skuId : skuName;
        String skuLookupKey = trim(skuName).isEmpty() ? skuId : skuName;
        LOGGER.info("acceptance report dimension mapping, extChatId={}, skuId={}, dimensionName={}, skuDisplay={}, skuLookupKey={}",
                extChatId, skuId, skuName, skuDisplay, skuLookupKey);
        String contractSignMonth = formatDate(ledgerRecord.getStartDate(), YYYY_MM);
        String projectAcceptDate = formatDate(ledgerRecord.getCheckAcceptDate(), YYYY_MM_DD);

        SkuDescription skuDescription = querySkuDescription(skuLookupKey, skuId);
        String productName = trim(skuDescription != null ? skuDescription.productName : "");
        String productDesp = trim(skuDescription != null ? skuDescription.productDesp : "");
        LocalDateTime contractDateFromNumber = parseContractDateFromNumber(contractNumber);

        if (productName.isEmpty()) {
            productName = resolveProductNameFallback(extChatId, skuLookupKey);
            if (!productName.isEmpty()) {
                LOGGER.warn("acceptance report fallback productname by extChatId/skuLookupKey, extChatId={}, skuId={}, skuName={}, skuLookupKey={}, productname={}",
                        extChatId, skuId, skuName, skuLookupKey, productName);
            }
        }
        if (productDesp.isEmpty() && !productName.isEmpty()) {
            productDesp = productName + " 产品验收交付";
            LOGGER.warn("acceptance report fallback productdesp by productname, extChatId={}, productdesp={}",
                    extChatId, productDesp);
        }
        if (contractSignMonth.isEmpty() && contractDateFromNumber != null) {
            contractSignMonth = contractDateFromNumber.format(YYYY_MM);
            LOGGER.warn("acceptance report fallback contractsignmonth by contractnumber, extChatId={}, contractnumber={}, contractsignmonth={}",
                    extChatId, contractNumber, contractSignMonth);
        }
        if (projectAcceptDate.isEmpty() && contractDateFromNumber != null) {
            projectAcceptDate = contractDateFromNumber.format(YYYY_MM_DD);
            LOGGER.warn("acceptance report fallback projectacceptdate by contractnumber, extChatId={}, contractnumber={}, projectacceptdate={}",
                    extChatId, contractNumber, projectAcceptDate);
        }
        if (contractSignMonth.isEmpty()) {
            contractSignMonth = LocalDateTime.now(ZONE_SHANGHAI).format(YYYY_MM);
            LOGGER.warn("acceptance report fallback contractsignmonth by now, extChatId={}, contractsignmonth={}",
                    extChatId, contractSignMonth);
        }
        if (projectAcceptDate.isEmpty()) {
            projectAcceptDate = LocalDateTime.now(ZONE_SHANGHAI).format(YYYY_MM_DD);
            LOGGER.warn("acceptance report fallback projectacceptdate by now, extChatId={}, projectacceptdate={}",
                    extChatId, projectAcceptDate);
        }

        List<String> missingFields = new ArrayList<>();
        collectMissingField(missingFields, "contractnumber", contractNumber);
        collectMissingField(missingFields, "partya", partya);
        collectMissingField(missingFields, "finalcustomer", finalCustomer);
        collectMissingField(missingFields, "sku", skuDisplay);
        collectMissingField(missingFields, "productname", productName);
        collectMissingField(missingFields, "productdesp", productDesp);
        collectMissingField(missingFields, "contractsignmonth", contractSignMonth);
        collectMissingField(missingFields, "projectacceptdate", projectAcceptDate);
        if (!missingFields.isEmpty()) {
            JSONObject missingContext = new JSONObject(true);
            missingContext.put("contractnumber", contractNumber);
            missingContext.put("partya", partya);
            missingContext.put("finalcustomer", finalCustomer);
            missingContext.put("sku", skuDisplay);
            missingContext.put("skuId", skuId);
            missingContext.put("skuName", skuName);
            missingContext.put("skuDisplay", skuDisplay);
            missingContext.put("skuLookupKey", skuLookupKey);
            missingContext.put("productname", productName);
            missingContext.put("productdesp", productDesp);
            missingContext.put("contractsignmonth", contractSignMonth);
            missingContext.put("projectacceptdate", projectAcceptDate);
            LOGGER.warn("acceptance report precheck failed, extChatId={}, missingFields={}, fieldValues={}",
                    extChatId, missingFields, missingContext.toJSONString());
            throw new IllegalArgumentException("缺失字段: " + String.join(", ", missingFields));
        }

        JSONObject formData = new JSONObject(true);
        formData.put("contractnumber", contractNumber);
        formData.put("partya", partya);
        formData.put("finalcustomer", finalCustomer);
        formData.put("sku", skuDisplay);
        formData.put("productname", productName);
        formData.put("productdesp", productDesp);
        formData.put("contractsignmonth", contractSignMonth);
        formData.put("projectacceptdate", projectAcceptDate);

        AcceptanceUpstreamResult attempt;
        if (hasDocxApi) {
            attempt = requestAcceptanceReportNewApi(extChatId, formData);
            if (trim(attempt.reportBase64).isEmpty() && acceptanceLegacyFallbackEnabled && hasLegacyApi) {
                LOGGER.warn("acceptance report new-api empty, fallback to legacy extChatId={}, reason={}",
                        extChatId, trim(attempt.failureReason));
                AcceptanceUpstreamResult legacy = requestAcceptanceReportLegacy(extChatId, formData);
                legacy.fallbackUsed = true;
                legacy.fallbackReason = trim(attempt.failureReason);
                attempt = legacy;
            }
        } else {
            attempt = requestAcceptanceReportLegacy(extChatId, formData);
        }
        if (trim(attempt.reportBase64).isEmpty()) {
            String reason = trim(attempt.failureReason);
            if (reason.isEmpty()) {
                reason = "未返回可解析的文件流";
            }
            throw new IllegalStateException(reason);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestPayload", attempt.requestPayload);
        result.put("upstreamRaw", attempt.upstreamRaw);
        result.put("upstreamJson", attempt.upstreamJson);
        result.put("upstreamMode", attempt.mode);
        result.put("fallbackUsed", attempt.fallbackUsed);
        result.put("fallbackReason", attempt.fallbackReason);
        result.put("failureReason", attempt.failureReason);
        result.put("reportBase64", attempt.reportBase64);
        return result;
    }

    private AcceptanceUpstreamResult requestAcceptanceReportNewApi(String extChatId, JSONObject formData) {
        AcceptanceUpstreamResult result = new AcceptanceUpstreamResult();
        result.mode = "new-api";
        result.requestPayload = formData;
        if (trim(acceptanceDocxApiUrl).isEmpty()) {
            result.failureReason = "配置缺失：tool.acceptance.docx-api.url";
            return result;
        }
        String payloadJson = formData.toJSONString();
        LOGGER.info("acceptance report request start extChatId={}, upstreamMode={}, apiUrl={}, apiKeyPrefix={}",
                extChatId, result.mode, acceptanceDocxApiUrl, maskToken(acceptanceApiKey));
        LOGGER.info("acceptance report upstream post extChatId={}, upstreamMode={}, url={}, payload={}",
                extChatId, result.mode, acceptanceDocxApiUrl, payloadJson);
        try {
            result.upstreamRaw = HttpClientUtil.postJSONWithApiKey(acceptanceDocxApiUrl, payloadJson, acceptanceApiKey);
            LOGGER.info("acceptance report upstream response extChatId={}, upstreamMode={}, body={}",
                    extChatId, result.mode, shrinkForLog(result.upstreamRaw, 4000));
            result.upstreamJson = parseJsonSafely(result.upstreamRaw);

            String fromJson = extractBase64FromNode(result.upstreamJson, 0);
            String fromRaw = fromJson.isEmpty() ? extractBase64FromNode(parseJsonSafely(result.upstreamRaw), 0) : "";
            String candidate = fromJson.isEmpty() ? fromRaw : fromJson;
            result.reportBase64 = normalizeAndValidateReportBase64(extChatId, candidate, result.mode);
            if (trim(result.reportBase64).isEmpty()) {
                result.failureReason = resolveUpstreamError(result.upstreamJson);
                if (trim(result.failureReason).isEmpty()) {
                    result.failureReason = "new-api 未返回可解析的 docxBase64/reportBase64";
                }
                LOGGER.warn("acceptance report upstream parse failed extChatId={}, upstreamMode={}, reason={}",
                        extChatId, result.mode, result.failureReason);
            } else {
                LOGGER.info("acceptance report binary extracted extChatId={}, upstreamMode={}, base64Length={}",
                        extChatId, result.mode, result.reportBase64.length());
            }
        } catch (Exception e) {
            result.failureReason = "new-api 调用失败: " + trim(e.getMessage());
            LOGGER.error("acceptance report upstream call failed extChatId={}, upstreamMode={}, err={}",
                    extChatId, result.mode, e.getMessage(), e);
        }
        return result;
    }

    private AcceptanceUpstreamResult requestAcceptanceReportLegacy(String extChatId, JSONObject formData) {
        AcceptanceUpstreamResult result = new AcceptanceUpstreamResult();
        result.mode = "legacy-sse";
        if (trim(acceptanceApiUrl).isEmpty()) {
            result.failureReason = "配置缺失：tool.acceptance.api.url";
            result.requestPayload = buildLegacyAcceptancePayload(formData);
            return result;
        }
        JSONObject payload = buildLegacyAcceptancePayload(formData);
        result.requestPayload = payload;
        String payloadJson = payload.toJSONString();
        LOGGER.info("acceptance report request start extChatId={}, upstreamMode={}, apiUrl={}, apiKeyPrefix={}",
                extChatId, result.mode, acceptanceApiUrl, maskToken(acceptanceApiKey));
        LOGGER.info("acceptance report upstream post extChatId={}, upstreamMode={}, url={}, payload={}",
                extChatId, result.mode, acceptanceApiUrl, payloadJson);
        try {
            result.upstreamRaw = HttpClientUtil.postJSONWithApiKey(acceptanceApiUrl, payloadJson, acceptanceApiKey);
            LOGGER.info("acceptance report upstream response extChatId={}, upstreamMode={}, body={}",
                    extChatId, result.mode, shrinkForLog(result.upstreamRaw, 4000));
            result.upstreamJson = parseJsonSafely(result.upstreamRaw);
            if (result.upstreamJson instanceof JSONObject json && Boolean.TRUE.equals(json.getBoolean("isSse"))) {
                JSONArray events = json.getJSONArray("events");
                Object lastEvent = json.get("lastEvent");
                LOGGER.info("acceptance report upstream sse parsed extChatId={}, upstreamMode={}, eventCount={}, lastEvent={}",
                        extChatId,
                        result.mode,
                        events != null ? events.size() : 0,
                        shrinkForLog(lastEvent == null ? "" : String.valueOf(lastEvent), 2000));
            }
            result.reportBase64 = extractReportBase64(extChatId, result.upstreamJson, result.upstreamRaw);
            if (trim(result.reportBase64).isEmpty()) {
                result.failureReason = resolveUpstreamError(result.upstreamJson);
                if (trim(result.failureReason).isEmpty()) {
                    result.failureReason = "legacy-sse 未返回可解析的文件流";
                }
                LOGGER.warn("acceptance report binary extract failed extChatId={}, upstreamMode={}, reason={}",
                        extChatId, result.mode, result.failureReason);
            } else {
                LOGGER.info("acceptance report binary extracted extChatId={}, upstreamMode={}, base64Length={}",
                        extChatId, result.mode, result.reportBase64.length());
            }
        } catch (Exception e) {
            result.failureReason = "legacy-sse 调用失败: " + trim(e.getMessage());
            LOGGER.error("acceptance report upstream call failed extChatId={}, upstreamMode={}, err={}",
                    extChatId, result.mode, e.getMessage(), e);
        }
        return result;
    }

    private JSONObject buildLegacyAcceptancePayload(JSONObject formData) {
        JSONObject payload = new JSONObject(true);
        payload.put("message", "获取验收报告");
        payload.put("stream", true);
        payload.put("re_chat", false);
        payload.put("image_list", new JSONArray());
        payload.put("document_list", new JSONArray());
        payload.put("audio_list", new JSONArray());
        payload.put("video_list", new JSONArray());
        payload.put("other_list", new JSONArray());
        payload.put("form_data", formData);
        return payload;
    }

    private String resolveDimensionNameBySkuId(String ledgerSkuId) {
        String normalized = trim(ledgerSkuId);
        if (normalized.isEmpty()) {
            return "";
        }
        JdbcUtils.setCordyscrmConfig();
        try {
            String sql = "SELECT name FROM ekuaibao_dimensions WHERE id = ? LIMIT 1";
            List<Object[]> rows = JdbcUtils.query(sql, normalized);
            if (rows.isEmpty() || rows.get(0)[0] == null) {
                return "";
            }
            return trim(rows.get(0)[0].toString());
        } finally {
            JdbcUtils.clearConfig();
        }
    }

    private SkuDescription querySkuDescription(String primaryKey, String fallbackKey) {
        String primary = trim(primaryKey);
        String fallback = trim(fallbackKey);
        if (primary.isEmpty() && fallback.isEmpty()) {
            return null;
        }

        JdbcUtils.setLocalConfig();
        try {
            SkuDescription byPrimary = querySkuDescriptionByKey(primary);
            if (byPrimary != null) {
                return byPrimary;
            }
            if (!fallback.isEmpty() && !fallback.equalsIgnoreCase(primary)) {
                return querySkuDescriptionByKey(fallback);
            }
            return null;
        } finally {
            JdbcUtils.clearConfig();
        }
    }

    private SkuDescription querySkuDescriptionByKey(String key) {
        String normalized = trim(key);
        if (normalized.isEmpty()) {
            return null;
        }
        String sql = "SELECT productName, productDesp FROM sku_description " +
                "WHERE sku = ? OR UPPER(sku) = UPPER(?) " +
                "ORDER BY CASE WHEN sku = ? THEN 0 ELSE 1 END " +
                "LIMIT 1";
        List<Object[]> rows = JdbcUtils.query(sql, normalized, normalized, normalized);
        if (rows.isEmpty()) {
            return null;
        }
        SkuDescription description = new SkuDescription();
        description.productName = rows.get(0)[0] != null ? rows.get(0)[0].toString() : "";
        description.productDesp = rows.get(0)[1] != null ? rows.get(0)[1].toString() : "";
        return description;
    }

    private void collectMissingField(List<String> missingFields, String fieldName, String value) {
        if (trim(value).isEmpty()) {
            missingFields.add(fieldName);
        }
    }

    private String formatDate(Object value, DateTimeFormatter formatter) {
        LocalDateTime dateTime = toLocalDateTime(value);
        if (dateTime == null) {
            return "";
        }
        return formatter.format(dateTime);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return Instant.ofEpochMilli(date.getTime()).atZone(ZONE_SHANGHAI).toLocalDateTime();
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            long epochMilli = raw > 100000000000L ? raw : raw * 1000L;
            return Instant.ofEpochMilli(epochMilli).atZone(ZONE_SHANGHAI).toLocalDateTime();
        }

        String text = trim(String.valueOf(value));
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace("T", " "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            // ignore and fallback
        }
        try {
            return LocalDateTime.parse(text.replace("T", " "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"));
        } catch (Exception ignored) {
            // ignore and fallback
        }
        try {
            return LocalDateTime.parse(text + " 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object parseJsonSafely(String raw) {
        String text = trim(raw);
        if (text.isEmpty()) {
            return new JSONObject();
        }
        if (text.contains("data:")) {
            JSONArray events = new JSONArray();
            String[] lines = text.split("\\R");
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String payload = trim(trimmed.substring(5));
                if (payload.isEmpty() || "[DONE]".equalsIgnoreCase(payload)) {
                    continue;
                }
                try {
                    if (payload.startsWith("[")) {
                        events.add(JSONArray.parseArray(payload));
                    } else if (payload.startsWith("{")) {
                        events.add(JSONObject.parseObject(payload));
                    } else {
                        events.add(payload);
                    }
                } catch (Exception e) {
                    events.add(payload);
                }
            }
            if (!events.isEmpty()) {
                JSONObject sse = new JSONObject(true);
                sse.put("isSse", true);
                sse.put("events", events);
                sse.put("lastEvent", events.get(events.size() - 1));
                return sse;
            }
        }
        try {
            if (text.startsWith("[")) {
                return JSONArray.parseArray(text);
            }
            return JSONObject.parseObject(text);
        } catch (Exception e) {
            JSONObject fallback = new JSONObject(true);
            fallback.put("raw", text);
            return fallback;
        }
    }

    private String maskToken(String token) {
        String text = trim(token);
        if (text.length() <= 8) {
            return text;
        }
        return text.substring(0, 8) + "****";
    }

    private String shrinkForLog(String value, int max) {
        String text = trim(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...(truncated)";
    }

    private String extractBase64FromNode(Object node, int depth) {
        if (node == null || depth > 8) {
            return "";
        }
        if (node instanceof String text) {
            String normalized = normalizeBase64Text(text);
            return normalized.isEmpty() ? "" : normalized;
        }
        if (node instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                String found = extractBase64FromNode(array.get(i), depth + 1);
                if (!found.isEmpty()) {
                    return found;
                }
            }
            return "";
        }
        if (!(node instanceof JSONObject json)) {
            return "";
        }
        String[] priorityKeys = {"reportBase64", "docxBase64", "base64", "fileBase64", "docBase64", "wordBase64", "fileStream", "file_stream", "content", "data", "result"};
        for (String key : priorityKeys) {
            if (!json.containsKey(key)) {
                continue;
            }
            String found = extractBase64FromNode(json.get(key), depth + 1);
            if (!found.isEmpty()) {
                return found;
            }
        }
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String found = extractBase64FromNode(entry.getValue(), depth + 1);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return "";
    }

    private String normalizeBase64Text(String value) {
        String text = trim(value);
        if (text.isEmpty()) {
            return "";
        }
        if (text.startsWith("data:")) {
            int index = text.indexOf("base64,");
            if (index >= 0) {
                text = text.substring(index + 7);
            }
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length() - 1);
        }
        text = text.replaceAll("\\s+", "").replace('-', '+').replace('_', '/');
        if (text.length() < 16) {
            return "";
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean valid = (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '+' || ch == '/' || ch == '=';
            if (!valid) {
                return "";
            }
        }
        int mod = text.length() % 4;
        if (mod > 0) {
            text = text + "=".repeat(4 - mod);
        }
        return text;
    }

    private String normalizeAndValidateReportBase64(String extChatId, String base64, String stage) {
        String normalized = normalizeBase64Text(base64);
        if (normalized.isEmpty()) {
            return "";
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(normalized);
        } catch (Exception e) {
            LOGGER.warn("acceptance report invalid base64 extChatId={}, stage={}, length={}, err={}",
                    extChatId, stage, normalized.length(), e.getMessage());
            return "";
        }
        byte[] zipBytes = normalizeZipBytes(bytes);
        if (zipBytes.length == 0) {
            LOGGER.warn("acceptance report invalid zip header extChatId={}, stage={}, decodedLength={}",
                    extChatId, stage, bytes.length);
            return "";
        }
        return Base64.getEncoder().encodeToString(zipBytes);
    }

    private String resolveUpstreamError(Object upstreamJson) {
        if (!(upstreamJson instanceof JSONObject json)) {
            return "";
        }
        if (Boolean.FALSE.equals(json.getBoolean("success"))) {
            String err = trim(json.getString("error"));
            if (!err.isEmpty()) {
                return err;
            }
            err = trim(json.getString("errmsg"));
            if (!err.isEmpty()) {
                return err;
            }
            err = trim(json.getString("message"));
            if (!err.isEmpty()) {
                return err;
            }
        }
        Object data = json.get("data");
        if (data instanceof JSONObject dataJson) {
            String err = trim(dataJson.getString("error"));
            if (!err.isEmpty()) {
                return err;
            }
            err = trim(dataJson.getString("errmsg"));
            if (!err.isEmpty()) {
                return err;
            }
            err = trim(dataJson.getString("message"));
            if (!err.isEmpty()) {
                return err;
            }
        }
        String err = trim(json.getString("error"));
        if (!err.isEmpty()) {
            return err;
        }
        err = trim(json.getString("errmsg"));
        if (!err.isEmpty()) {
            return err;
        }
        return "";
    }

    private String extractReportBase64(String extChatId, Object upstreamJson, String upstreamRaw) {
        byte[] fromEvents = extractBytesFromSseEvents(extChatId, upstreamJson);
        byte[] normalized = normalizeZipBytes(fromEvents);
        LOGGER.info("acceptance report extract stage=sse-events extChatId={}, bytesLength={}, normalizedLength={}",
                extChatId, fromEvents.length, normalized.length);
        if (normalized.length > 0) {
            return Base64.getEncoder().encodeToString(normalized);
        }

        byte[] fromRaw = extractBytesFromText(upstreamRaw);
        normalized = normalizeZipBytes(fromRaw);
        LOGGER.info("acceptance report extract stage=upstream-raw extChatId={}, rawLength={}, bytesLength={}, normalizedLength={}",
                extChatId, trim(upstreamRaw).length(), fromRaw.length, normalized.length);
        if (normalized.length > 0) {
            return Base64.getEncoder().encodeToString(normalized);
        }

        String upstreamJsonText = toCompactJsonText(upstreamJson);
        byte[] fromJsonText = extractBytesFromText(upstreamJsonText);
        normalized = normalizeZipBytes(fromJsonText);
        LOGGER.info("acceptance report extract stage=upstream-json extChatId={}, jsonLength={}, bytesLength={}, normalizedLength={}",
                extChatId, upstreamJsonText.length(), fromJsonText.length, normalized.length);
        if (normalized.length > 0) {
            return Base64.getEncoder().encodeToString(normalized);
        }
        return "";
    }

    private byte[] extractBytesFromSseEvents(String extChatId, Object upstreamJson) {
        if (!(upstreamJson instanceof JSONObject json) || !Boolean.TRUE.equals(json.getBoolean("isSse"))) {
            return new byte[0];
        }
        Object eventsObj = json.get("events");
        if (!(eventsObj instanceof JSONArray events) || events.isEmpty()) {
            return new byte[0];
        }
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            Object event = events.get(i);
            List<String> candidates = collectSseEventCandidates(event);
            boolean extracted = false;
            for (int c = 0; c < candidates.size(); c++) {
                String candidate = trim(candidates.get(c));
                if (candidate.isEmpty()) {
                    continue;
                }
                byte[] bytes = extractBytesFromText(candidate);
                if (bytes.length > 0) {
                    chunks.add(bytes);
                    LOGGER.info("acceptance report sse event decoded extChatId={}, eventIndex={}, candidateIndex={}, candidateLength={}, bytesLength={}",
                            extChatId, i, c, candidate.length(), bytes.length);
                    extracted = true;
                    break;
                }
            }
            if (!extracted) {
                String eventType = event == null ? "null" : event.getClass().getSimpleName();
                LOGGER.info("acceptance report sse event no-binary extChatId={}, eventIndex={}, eventType={}, preview={}",
                        extChatId, i, eventType, shrinkForLog(String.valueOf(event), 500));
            }
        }
        return mergeByteChunks(chunks);
    }

    private List<String> collectSseEventCandidates(Object event) {
        List<String> candidates = new ArrayList<>();
        if (event == null) {
            return candidates;
        }
        if (event instanceof JSONObject eventJson) {
            String content = trim(eventJson.getString("content"));
            if (!content.isEmpty()) {
                candidates.add(content);
            }
            candidates.add(eventJson.toJSONString());
            return candidates;
        }
        if (event instanceof JSONArray eventArray) {
            candidates.add(eventArray.toJSONString());
            return candidates;
        }
        String text = trim(String.valueOf(event));
        if (text.isEmpty()) {
            return candidates;
        }
        candidates.add(text);
        if (text.startsWith("data:")) {
            candidates.add(trim(text.substring(5)));
        }
        Object parsed = parseFlatJson(text);
        if (parsed instanceof JSONObject parsedObj) {
            String content = trim(parsedObj.getString("content"));
            if (!content.isEmpty()) {
                candidates.add(content);
            }
            candidates.add(parsedObj.toJSONString());
        } else if (parsed instanceof JSONArray parsedArray) {
            candidates.add(parsedArray.toJSONString());
        }
        return candidates;
    }

    private Object parseFlatJson(String text) {
        String payload = trim(text);
        if (payload.startsWith("data:")) {
            payload = trim(payload.substring(5));
        }
        if (payload.isEmpty()) {
            return null;
        }
        try {
            if (payload.startsWith("[")) {
                return JSONArray.parseArray(payload);
            }
            if (payload.startsWith("{")) {
                return JSONObject.parseObject(payload);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String toCompactJsonText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JSONObject jsonObject) {
            return jsonObject.toJSONString();
        }
        if (value instanceof JSONArray jsonArray) {
            return jsonArray.toJSONString();
        }
        return trim(String.valueOf(value));
    }

    private byte[] extractBytesFromText(String text) {
        String normalized = trim(text);
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        byte[] base64Bytes = extractBase64BytesFromText(normalized);
        if (base64Bytes.length > 0) {
            return base64Bytes;
        }

        List<byte[]> chunks = extractPythonBytesLiterals(normalized);
        if (!chunks.isEmpty()) {
            byte[] merged = mergeByteChunks(chunks);
            if (findZipHeaderOffset(merged) >= 0) {
                return merged;
            }
        }

        String deescaped = deescapePythonBytesText(normalized);
        chunks = extractPythonBytesLiterals(deescaped);
        if (!chunks.isEmpty()) {
            byte[] merged = mergeByteChunks(chunks);
            if (findZipHeaderOffset(merged) >= 0) {
                return merged;
            }
        }

        byte[] escaped = decodeEscapedBytes(normalized, true);
        if (escaped.length > 0 && findZipHeaderOffset(escaped) >= 0) {
            return escaped;
        }
        byte[] escapedDeescaped = decodeEscapedBytes(deescaped, true);
        if (escapedDeescaped.length > 0 && findZipHeaderOffset(escapedDeescaped) >= 0) {
            return escapedDeescaped;
        }
        return new byte[0];
    }

    private byte[] extractBase64BytesFromText(String text) {
        String normalized = trim(text);
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        String deescaped = deescapePythonBytesText(normalized);
        if (!deescaped.equals(normalized)) {
            candidates.add(deescaped);
        }
        candidates.addAll(extractQuotedLiterals(normalized));
        if (!deescaped.equals(normalized)) {
            candidates.addAll(extractQuotedLiterals(deescaped));
        }
        for (String candidate : candidates) {
            String base64 = normalizeBase64Text(candidate);
            if (base64.isEmpty()) {
                continue;
            }
            try {
                byte[] decoded = Base64.getDecoder().decode(base64);
                byte[] zipBytes = normalizeZipBytes(decoded);
                if (zipBytes.length > 0) {
                    return zipBytes;
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return new byte[0];
    }

    private List<String> extractQuotedLiterals(String text) {
        List<String> values = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return values;
        }
        int len = text.length();
        int i = 0;
        while (i < len) {
            char quote = text.charAt(i);
            if (quote != '\'' && quote != '"') {
                i++;
                continue;
            }
            int j = i + 1;
            StringBuilder sb = new StringBuilder();
            while (j < len) {
                char ch = text.charAt(j);
                if (ch == '\\' && j + 1 < len) {
                    sb.append(text.charAt(j + 1));
                    j += 2;
                    continue;
                }
                if (ch == quote) {
                    break;
                }
                sb.append(ch);
                j++;
            }
            if (j < len && text.charAt(j) == quote) {
                String value = trim(sb.toString());
                if (!value.isEmpty()) {
                    values.add(value);
                }
                i = j + 1;
                continue;
            }
            i++;
        }
        return values;
    }

    private List<byte[]> extractPythonBytesLiterals(String text) {
        List<byte[]> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int length = text.length();
        int i = 0;
        while (i + 2 < length) {
            if (text.charAt(i) != 'b') {
                i++;
                continue;
            }
            char quote = text.charAt(i + 1);
            if (quote != '\'' && quote != '"') {
                i++;
                continue;
            }
            int start = i + 2;
            int end = start;
            while (end < length) {
                char ch = text.charAt(end);
                if (ch == '\\') {
                    end = Math.min(end + 2, length);
                    continue;
                }
                if (ch == quote) {
                    break;
                }
                end++;
            }
            if (end >= length || text.charAt(end) != quote) {
                i++;
                continue;
            }
            String literal = text.substring(start, end);
            byte[] decoded = decodeEscapedBytes(deescapePythonBytesText(literal), false);
            if (decoded.length > 0) {
                chunks.add(decoded);
            }
            i = end + 1;
        }
        return chunks;
    }

    private String deescapePythonBytesText(String value) {
        String text = value == null ? "" : value;
        text = text.replace("b\\'", "b'");
        text = text.replace("b\\\"", "b\"");
        text = text.replace("\\\\x", "\\x");
        text = text.replace("\\\\", "\\");
        text = text.replace("\\'", "'");
        text = text.replace("\\\"", "\"");
        return text;
    }

    private byte[] decodeEscapedBytes(String text, boolean requireEscape) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(text.length(), 32));
        boolean hasEscape = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\\') {
                out.write((byte) (ch & 0xFF));
                continue;
            }
            if (i + 1 >= text.length()) {
                out.write((byte) '\\');
                continue;
            }
            char next = text.charAt(++i);
            if (next == '\\' && i + 3 < text.length() && text.charAt(i + 1) == 'x') {
                if (isHexPair(text, i + 2)) {
                    out.write((byte) parseHexPair(text, i + 2));
                    i += 3;
                    hasEscape = true;
                    continue;
                }
            }
            if (next == 'x' && i + 2 < text.length()) {
                if (isHexPair(text, i + 1)) {
                    out.write((byte) parseHexPair(text, i + 1));
                    i += 2;
                    hasEscape = true;
                    continue;
                }
                out.write((byte) 'x');
                continue;
            }
            if (next >= '0' && next <= '7') {
                StringBuilder oct = new StringBuilder();
                oct.append(next);
                int count = 1;
                while (count < 3 && i + 1 < text.length()) {
                    char c = text.charAt(i + 1);
                    if (c < '0' || c > '7') {
                        break;
                    }
                    oct.append(c);
                    i++;
                    count++;
                }
                try {
                    out.write((byte) Integer.parseInt(oct.toString(), 8));
                    hasEscape = true;
                    continue;
                } catch (Exception ignored) {
                    // fall through
                }
            }
            switch (next) {
                case '\\' -> {
                    out.write((byte) '\\');
                    hasEscape = true;
                }
                case '\'' -> {
                    out.write((byte) '\'');
                    hasEscape = true;
                }
                case '"' -> {
                    out.write((byte) '"');
                    hasEscape = true;
                }
                case 'n' -> {
                    out.write((byte) '\n');
                    hasEscape = true;
                }
                case 'r' -> {
                    out.write((byte) '\r');
                    hasEscape = true;
                }
                case 't' -> {
                    out.write((byte) '\t');
                    hasEscape = true;
                }
                case 'a' -> {
                    out.write(0x07);
                    hasEscape = true;
                }
                case 'b' -> {
                    out.write(0x08);
                    hasEscape = true;
                }
                case 'f' -> {
                    out.write(0x0c);
                    hasEscape = true;
                }
                case 'v' -> {
                    out.write(0x0b);
                    hasEscape = true;
                }
                default -> out.write((byte) (next & 0xFF));
            }
        }
        if (requireEscape && !hasEscape) {
            return new byte[0];
        }
        return out.toByteArray();
    }

    private boolean isHexPair(String text, int index) {
        return index + 1 < text.length()
                && isHexChar(text.charAt(index))
                && isHexChar(text.charAt(index + 1));
    }

    private boolean isHexChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private int parseHexPair(String text, int index) {
        return Character.digit(text.charAt(index), 16) * 16
                + Character.digit(text.charAt(index + 1), 16);
    }

    private byte[] mergeByteChunks(List<byte[]> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new byte[0];
        }
        int total = 0;
        for (byte[] chunk : chunks) {
            if (chunk != null) {
                total += chunk.length;
            }
        }
        if (total == 0) {
            return new byte[0];
        }
        byte[] merged = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            if (chunk == null || chunk.length == 0) {
                continue;
            }
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }
        return merged;
    }

    private byte[] normalizeZipBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return new byte[0];
        }
        int offset = findZipHeaderOffset(bytes);
        if (offset < 0) {
            return new byte[0];
        }
        if (offset == 0) {
            return bytes;
        }
        return Arrays.copyOfRange(bytes, offset, bytes.length);
    }

    private int findZipHeaderOffset(byte[] bytes) {
        for (int i = 0; i <= bytes.length - 4; i++) {
            int b0 = bytes[i] & 0xFF;
            int b1 = bytes[i + 1] & 0xFF;
            int b2 = bytes[i + 2] & 0xFF;
            int b3 = bytes[i + 3] & 0xFF;
            if (b0 == 0x50 && b1 == 0x4B && (
                    (b2 == 0x03 && b3 == 0x04) ||
                    (b2 == 0x05 && b3 == 0x06) ||
                    (b2 == 0x07 && b3 == 0x08))) {
                return i;
            }
        }
        return -1;
    }

    private String resolveProductNameFallback(String extChatId, String sku) {
        try {
            ProductVersionSnapshot snapshot = chatGroupService.getProductVersionSnapshot(extChatId);
            String alias = trim(snapshot != null ? snapshot.getProductAlias() : "");
            if (!alias.isEmpty()) {
                return alias;
            }
        } catch (Exception ignored) {
            // ignore and fallback
        }
        String normalizedSku = trim(sku);
        if (!normalizedSku.isEmpty()) {
            return "SKU-" + normalizedSku;
        }
        return "未知产品";
    }

    private LocalDateTime parseContractDateFromNumber(String contractNumber) {
        String normalized = trim(contractNumber);
        if (normalized.isEmpty()) {
            return null;
        }
        Matcher matcher = CONTRACT_DATE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        String text = matcher.group();
        try {
            return LocalDateTime.parse(text + " 00:00:00", DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
        } catch (Exception ignored) {
            return null;
        }
    }

    public Map<String, Object> diagnoseMail(MailDiagnoseRequest request) {
        String sender = trim(fromAddress);
        String senderAuthCode = trim(authCode);
        if (sender.isEmpty()) {
            throw new IllegalStateException("邮件配置缺失：tool.mail.from-address");
        }
        if (senderAuthCode.isEmpty()) {
            throw new IllegalStateException("邮件配置缺失：tool.mail.auth-code");
        }
        if (isPlaceholderSecret(senderAuthCode)) {
            throw new IllegalStateException("邮件配置缺失：tool.mail.auth-code（当前为占位值，请填写真实客户端专用密码）");
        }

        boolean dryRun = request == null || request.getDryRun() == null || request.getDryRun();
        String toEmail = request != null ? trim(request.getToEmail()) : "";
        if (!dryRun && toEmail.isEmpty()) {
            throw new IllegalArgumentException("dryRun=false 时 toEmail 不能为空");
        }
        List<String> diagnoseToEmails = Collections.emptyList();
        if (!toEmail.isEmpty()) {
            diagnoseToEmails = parseCcInputEmails(toEmail, "toEmail");
        }

        List<SmtpProfile> profiles = resolveProfilesToTry();
        List<Map<String, Object>> profileResults = new ArrayList<>();
        boolean success = false;
        String failureStage = "";
        String rootMessage = "";

        for (int i = 0; i < profiles.size(); i++) {
            SmtpProfile profile = profiles.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", profile.name);
            item.put("host", profile.host);
            item.put("port", profile.port);
            item.put("ssl", profile.sslEnable);
            item.put("starttls", profile.starttlsEnable);
            item.put("authMechanisms", smtpAuthMechanisms);
            try {
                JavaMailSenderImpl mailSender = createMailSender(profile, sender, senderAuthCode);
                if (dryRun) {
                    mailSender.testConnection();
                } else {
                    sendMailOnce(mailSender, profile, sender, diagnoseToEmails, Collections.emptyList(), "[diagnose] SMTP connection test", "diagnose", false, new ArrayList<>());
                }
                item.put("authResult", "success");
                item.put("failureStage", "");
                item.put("rootMessage", "");
                profileResults.add(item);
                success = true;
                break;
            } catch (Exception e) {
                failureStage = detectFailureStage(e);
                rootMessage = sanitizeMessage(rootCauseMessage(e));
                item.put("authResult", "fail");
                item.put("failureStage", failureStage);
                item.put("rootMessage", rootMessage);
                profileResults.add(item);
                boolean canFallback = smtpFallbackOnAuthFail && i + 1 < profiles.size() && isAuthFailure(e);
                if (!canFallback) {
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeConfigSource", activeConfigSource);
        result.put("externalConfigPathExists", externalConfigExists);
        result.put("externalConfigLastModified", externalConfigLastModified);
        result.put("authMechanisms", smtpAuthMechanisms);
        result.put("fallbackOnAuthFail", smtpFallbackOnAuthFail);
        result.put("profilesTried", profileResults);
        result.put("authResult", success ? "success" : "fail");
        result.put("failureStage", success ? "" : failureStage);
        result.put("rootMessage", success ? "" : rootMessage);
        return result;
    }

    private void sendMailOnce(JavaMailSenderImpl mailSender,
                              SmtpProfile profile,
                              String sender,
                              List<String> toEmails,
                              List<String> ccEmails,
                              String subject,
                              String content,
                              boolean htmlContent,
                              List<File> filesToAttach) throws Exception {
        String toEmailText = toEmails == null ? "" : String.join(";", toEmails);
        if (mailLogVerbose) {
            LOGGER.info("sendMailOnce prepare: profile={}, host={}, port={}, ssl={}, starttls={}, from={}, to={}, ccCount={}, subject={}, attachmentCount={}",
                    profile.name,
                    profile.host,
                    profile.port,
                    profile.sslEnable,
                    profile.starttlsEnable,
                    maskEmail(sender),
                    toEmailText,
                    ccEmails == null ? 0 : ccEmails.size(),
                    subject,
                    filesToAttach.size());
        } else {
            LOGGER.debug("sendMailOnce prepare: profile={}, host={}, port={}, to={}, ccCount={}, attachmentCount={}",
                    profile.name, profile.host, profile.port, toEmailText, ccEmails == null ? 0 : ccEmails.size(), filesToAttach.size());
        }
        validateRecipientsBeforeSend(mailSender, sender, toEmails, ccEmails);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

        String displayName = trim(fromDisplayName);
        if (displayName.isEmpty()) {
            helper.setFrom(sender);
        } else {
            helper.setFrom(sender, displayName);
        }
        helper.setTo(toEmails.toArray(new String[0]));
        if (ccEmails != null && !ccEmails.isEmpty()) {
            helper.setCc(ccEmails.toArray(new String[0]));
        }
        helper.setSubject(subject);
        helper.setText(content, htmlContent);

        for (File file : filesToAttach) {
            helper.addAttachment(file.getName(), new FileSystemResource(file));
        }

        mailSender.send(mimeMessage);
        if (mailLogVerbose) {
            LOGGER.info("sendMailOnce success: profile={}, to={}, attachmentCount={}", profile.name, toEmailText, filesToAttach.size());
        } else {
            LOGGER.debug("sendMailOnce success: profile={}, to={}, attachmentCount={}", profile.name, toEmailText, filesToAttach.size());
        }
    }

    private void validateRecipientsBeforeSend(JavaMailSenderImpl mailSender,
                                              String sender,
                                              List<String> toEmails,
                                              List<String> ccEmails) throws MessagingException {
        List<String> finalToEmails = toEmails == null ? Collections.emptyList() : toEmails;
        List<String> finalCcEmails = ccEmails == null ? Collections.emptyList() : ccEmails;
        if (finalToEmails.isEmpty() && finalCcEmails.isEmpty()) {
            return;
        }

        LinkedHashMap<String, String> rejectedByAddress = new LinkedHashMap<>();
        MessagingException firstFailure = null;
        Transport rawTransport = null;
        try {
            rawTransport = mailSender.getSession().getTransport("smtp");
            if (!(rawTransport instanceof SMTPTransport transport)) {
                return;
            }
            transport.connect(mailSender.getHost(), mailSender.getPort(), mailSender.getUsername(), mailSender.getPassword());

            for (String email : finalToEmails) {
                try {
                    validateSingleRecipient(transport, sender, email);
                } catch (MessagingException e) {
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                    rejectedByAddress.put(email, resolveRecipientFailureReason(transport, e));
                }
            }

            for (String email : finalCcEmails) {
                try {
                    validateSingleRecipient(transport, sender, email);
                } catch (MessagingException e) {
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                    rejectedByAddress.put(email, resolveRecipientFailureReason(transport, e));
                }
            }
        } finally {
            if (rawTransport != null) {
                try {
                    rawTransport.close();
                } catch (MessagingException ignored) {
                    // ignore close failure
                }
            }
        }

        if (rejectedByAddress.isEmpty()) {
            return;
        }

        List<String> detailTexts = new ArrayList<>();
        Address[] invalidAddresses = new Address[rejectedByAddress.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : rejectedByAddress.entrySet()) {
            invalidAddresses[index++] = new InternetAddress(entry.getKey());
            String reason = trim(entry.getValue());
            if (reason.isEmpty()) {
                detailTexts.add(entry.getKey());
            } else {
                detailTexts.add(entry.getKey() + "(" + reason + ")");
            }
        }
        throw new SendFailedException("Invalid Addresses: " + String.join(", ", detailTexts), firstFailure, null, null, invalidAddresses);
    }

    private void validateSingleRecipient(SMTPTransport transport, String sender, String email) throws MessagingException {
        transport.issueCommand("RSET", 250);
        transport.issueCommand("MAIL FROM:<" + sender + ">", 250);
        int rcptCode = transport.simpleCommand("RCPT TO:<" + email + ">");
        if (rcptCode == 250 || rcptCode == 251) {
            return;
        }
        String response = sanitizeMessage(transport.getLastServerResponse());
        throw new SMTPAddressFailedException(new InternetAddress(email), "RCPT TO", rcptCode, response);
    }

    private String resolveRecipientFailureReason(SMTPTransport transport, MessagingException e) {
        String message = "";
        if (e instanceof SMTPAddressFailedException addressFailedException) {
            message = sanitizeMessage(trim(addressFailedException.getMessage()));
        }
        if (message.isEmpty()) {
            message = sanitizeMessage(trim(transport == null ? "" : transport.getLastServerResponse()));
        }
        if (message.isEmpty()) {
            message = sanitizeMessage(trim(e.getMessage()));
        }
        return message;
    }

    private JavaMailSenderImpl createMailSender(SmtpProfile profile, String sender, String senderAuthCode) {
        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(profile.host);
            mailSender.setPort(profile.port);
            mailSender.setUsername(sender);
            mailSender.setPassword(senderAuthCode);
            mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

            Properties properties = mailSender.getJavaMailProperties();
            properties.put("mail.transport.protocol", "smtp");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.ssl.enable", String.valueOf(profile.sslEnable));
            properties.put("mail.smtp.starttls.enable", String.valueOf(profile.starttlsEnable));
            properties.put("mail.smtp.connectiontimeout", String.valueOf(smtpConnectionTimeoutMs));
            properties.put("mail.smtp.timeout", String.valueOf(smtpTimeoutMs));
            properties.put("mail.smtp.writetimeout", String.valueOf(smtpWriteTimeoutMs));
            properties.put("mail.debug", String.valueOf(smtpDebug));
            properties.put("mail.smtp.auth.mechanisms", smtpAuthMechanisms);
            boolean hasLogin = containsAuthMechanism("LOGIN");
            boolean hasPlain = containsAuthMechanism("PLAIN");
            properties.put("mail.smtp.auth.login.disable", String.valueOf(!hasLogin));
            properties.put("mail.smtp.auth.plain.disable", String.valueOf(!hasPlain));
            return mailSender;
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("邮件依赖缺失（jakarta.mail），请刷新 Maven 依赖并重启服务", e);
        }
    }

    private String resolveCustomerName(SendToolMailRequest request, String extChatId) {
        String requestName = trim(request != null ? request.getCustomerName() : "");
        if (!requestName.isEmpty()) {
            return requestName;
        }
        try {
            CustomerData data = chatGroupService.getCustomerData(extChatId);
            String dbName = trim(data != null ? data.getName() : "");
            if (!dbName.isEmpty()) {
                return dbName;
            }
        } catch (Exception e) {
            LOGGER.warn("resolve customer name failed extChatId={}, err={}", extChatId, e.getMessage());
        }
        return "客户";
    }

    private CcResolution resolveEffectiveCcResolution(String extChatId,
                                                     String requestCcEmails,
                                                     boolean requestCcProvided,
                                                     String loginUserId) {
        CcResolution resolution = buildDefaultCcResolution(extChatId, loginUserId);
        if (requestCcProvided) {
            resolution.finalCcEmails = parseCcInputEmails(requestCcEmails, "抄送邮箱");
            resolution.defaultCcApplied = false;
        } else {
            resolution.finalCcEmails = new ArrayList<>(resolution.defaultCcList);
            resolution.defaultCcApplied = true;
        }
        if (!requestCcProvided && resolution.finalCcEmails.isEmpty()) {
            throw new IllegalStateException("抄送邮箱为空，请检查默认抄送配置");
        }
        return resolution;
    }

    private CcResolution buildDefaultCcResolution(String extChatId, String loginUserId) {
        CcResolution resolution = new CcResolution();
        LinkedHashSet<String> mandatoryCcSet = new LinkedHashSet<>();
        LinkedHashSet<String> optionalCcSet = new LinkedHashSet<>();
        if (!mailCcFixedDefault.isEmpty()) {
            optionalCcSet.addAll(mailCcFixedDefault);
        } else {
            optionalCcSet.add(DEFAULT_TOOL_MAIL_CC);
        }
        String normalizedExtChatId = trim(extChatId);
        if (normalizedExtChatId.isEmpty()) {
            resolution.optionalDefaultCcList = new ArrayList<>(optionalCcSet);
            resolution.defaultCcList = mergeCcLists(mandatoryCcSet, optionalCcSet);
            return resolution;
        }

        JdbcUtils.setCscrmConfig();
        try {
            enrichOperatorCc(resolution, mandatoryCcSet, loginUserId);
            resolution.mandatoryCcList = new ArrayList<>(mandatoryCcSet);
            resolution.clientId = queryLatestClientIdByExtChatId(normalizedExtChatId);
            if (resolution.clientId == null) {
                resolution.optionalDefaultCcList = new ArrayList<>(optionalCcSet);
                resolution.defaultCcList = mergeCcLists(mandatoryCcSet, optionalCcSet);
                LOGGER.warn("mail default cc: client not found by extChatId={}, fallback fixedCc only", normalizedExtChatId);
                return resolution;
            }

            List<String> salesUserIds = querySalesUserIdsByClientId(resolution.clientId);
            resolution.salesUserIds = new ArrayList<>(salesUserIds);
            if (salesUserIds.isEmpty()) {
                resolution.optionalDefaultCcList = new ArrayList<>(optionalCcSet);
                resolution.defaultCcList = mergeCcLists(mandatoryCcSet, optionalCcSet);
                return resolution;
            }

            List<String> filteredSalesUserIds = new ArrayList<>();
            List<String> excludedSalesUserIds = new ArrayList<>();
            for (String salesUserId : salesUserIds) {
                if (mailCcExcludeSalesUserIds.contains(salesUserId)) {
                    excludedSalesUserIds.add(salesUserId);
                } else {
                    filteredSalesUserIds.add(salesUserId);
                }
            }
            resolution.excludedSalesUserIds = excludedSalesUserIds;
            resolution.salesUserIds = filteredSalesUserIds;
            List<String> salesEmails = querySalesEmailsByUserIds(filteredSalesUserIds);
            resolution.salesEmails = salesEmails;
            optionalCcSet.addAll(salesEmails);
            resolution.optionalDefaultCcList = new ArrayList<>(optionalCcSet);
            resolution.defaultCcList = mergeCcLists(mandatoryCcSet, optionalCcSet);

            LOGGER.info("mail default cc resolved extChatId={}, clientId={}, salesUserCount={}, excludedSalesUserCount={}, operatorEmailPresent={}, sameDeptEmailCount={}, mandatoryCcCount={}, optionalCcCount={}, resolvedCcCount={}",
                    normalizedExtChatId,
                    resolution.clientId,
                    salesUserIds.size(),
                    excludedSalesUserIds.size(),
                    !trim(resolution.loginUserEmail).isEmpty(),
                    resolution.sameDeptEmails.size(),
                    resolution.mandatoryCcList.size(),
                    resolution.optionalDefaultCcList.size(),
                    resolution.defaultCcList.size());
            return resolution;
        } catch (Exception e) {
            resolution.optionalDefaultCcList = new ArrayList<>(optionalCcSet);
            resolution.defaultCcList = mergeCcLists(mandatoryCcSet, optionalCcSet);
            LOGGER.warn("mail default cc resolve failed extChatId={}, err={}, fallback fixedCc only",
                    normalizedExtChatId, e.getMessage());
            return resolution;
        } finally {
            JdbcUtils.clearConfig();
        }
    }

    private List<String> mergeCcLists(LinkedHashSet<String> mandatoryCcSet, LinkedHashSet<String> optionalCcSet) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (mandatoryCcSet != null) {
            merged.addAll(mandatoryCcSet);
        }
        if (optionalCcSet != null) {
            merged.addAll(optionalCcSet);
        }
        return new ArrayList<>(merged);
    }

    private void enrichOperatorCc(CcResolution resolution, LinkedHashSet<String> ccSet, String loginUserId) {
        String normalizedLoginUserId = trim(loginUserId);
        resolution.loginUserId = normalizedLoginUserId;
        if (normalizedLoginUserId.isEmpty()) {
            return;
        }
        StaffMailContext currentUser = queryStaffMailContextByExtId(normalizedLoginUserId);
        if (currentUser == null) {
            return;
        }
        resolution.loginUserEmail = currentUser.email;
        if (!trim(currentUser.email).isEmpty()) {
            ccSet.add(currentUser.email);
        }
        List<String> sameDeptEmails = queryStaffEmailsByDeptId(currentUser.deptId);
        resolution.sameDeptEmails = sameDeptEmails;
        ccSet.addAll(sameDeptEmails);
    }

    private Long queryLatestClientIdByExtChatId(String extChatId) {
        String sql = "SELECT ss.client_id " +
                "FROM group_chat gc " +
                "INNER JOIN support_subscription ss ON ss.group_chat_name = gc.name " +
                "WHERE gc.ext_chat_id = ? AND ss.client_id IS NOT NULL " +
                "ORDER BY COALESCE(ss.support_expired, 0) ASC, " +
                "         COALESCE(ss.expired, 0) ASC, " +
                "         COALESCE(ss.support_end_date, 0) DESC, " +
                "         COALESCE(ss.end_date, 0) DESC, " +
                "         ss.id DESC " +
                "LIMIT 1";
        List<Object[]> result = JdbcUtils.query(sql, extChatId);
        if (result.isEmpty() || result.get(0)[0] == null) {
            return null;
        }
        try {
            return Long.parseLong(result.get(0)[0].toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> querySalesUserIdsByClientId(Long clientId) {
        String sql = "SELECT DISTINCT csu.sales_user_user_id " +
                "FROM client_sales_users csu " +
                "WHERE csu.support_client_id = ? AND csu.sales_user_user_id IS NOT NULL AND csu.sales_user_user_id != '' " +
                "ORDER BY csu.sales_user_user_id";
        List<Object[]> result = JdbcUtils.query(sql, clientId);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Object[] row : result) {
            if (row == null || row.length == 0 || row[0] == null) {
                continue;
            }
            String id = trim(row[0].toString());
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    private List<String> querySalesEmailsByUserIds(List<String> salesUserIds) {
        if (salesUserIds == null || salesUserIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", Collections.nCopies(salesUserIds.size(), "?"));
        String sql = "SELECT su.user_id, su.email " +
                "FROM sales_user su " +
                "WHERE su.user_id IN (" + placeholders + ") " +
                "AND su.email IS NOT NULL AND su.email != '' " +
                "ORDER BY su.email";
        Object[] params = salesUserIds.toArray(new Object[0]);
        List<Object[]> result = JdbcUtils.query(sql, params);
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (Object[] row : result) {
            if (row == null || row.length < 2 || row[1] == null) {
                continue;
            }
            String email = trim(row[1].toString()).toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                continue;
            }
            if (EMAIL_PATTERN.matcher(email).matches()) {
                emails.add(email);
            }
        }
        return new ArrayList<>(emails);
    }

    private StaffMailContext queryStaffMailContextByExtId(String extId) {
        String normalizedExtId = trim(extId);
        if (normalizedExtId.isEmpty()) {
            return null;
        }
        String sql = "SELECT s.ext_id, s.email, s.dept_ids " +
                "FROM staff s " +
                "WHERE s.ext_id = ? " +
                "LIMIT 1";
        List<Object[]> result = JdbcUtils.query(sql, normalizedExtId);
        if (result.isEmpty()) {
            return null;
        }
        Object[] row = result.get(0);
        StaffMailContext context = new StaffMailContext();
        context.extId = normalizedExtId;
        context.email = normalizeEmail(row.length > 1 && row[1] != null ? row[1].toString() : "");
        context.deptId = parseDeptId(row.length > 2 && row[2] != null ? row[2].toString() : "");
        return context;
    }

    private List<String> queryStaffEmailsByDeptId(Long deptId) {
        if (deptId == null) {
            return new ArrayList<>();
        }
        String sql = "SELECT s.email, s.dept_ids " +
                "FROM staff s " +
                "WHERE s.email IS NOT NULL AND s.email != '' " +
                "AND s.dept_ids IS NOT NULL";
        List<Object[]> result = JdbcUtils.query(sql);
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (Object[] row : result) {
            if (row == null || row.length < 2) {
                continue;
            }
            String email = normalizeEmail(row[0] != null ? row[0].toString() : "");
            if (email.isEmpty()) {
                continue;
            }
            if (deptId.equals(parseDeptId(row[1] != null ? row[1].toString() : ""))) {
                emails.add(email);
            }
        }
        return new ArrayList<>(emails);
    }

    private Long parseDeptId(String raw) {
        String value = trim(raw);
        if (value.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = JSONArray.parseArray(value);
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item == null) {
                    continue;
                }
                String itemValue = trim(String.valueOf(item));
                if (itemValue.isEmpty()) {
                    continue;
                }
                try {
                    return Long.parseLong(itemValue);
                } catch (NumberFormatException ignored) {
                    // ignore invalid dept id
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.warn("parse dept_ids failed raw={}, err={}", value, e.getMessage());
            return null;
        }
    }

    private String normalizeEmail(String raw) {
        String email = trim(raw).toLowerCase(Locale.ROOT);
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            return "";
        }
        return email;
    }

    private List<String> parseCcInputEmails(String raw, String fieldName) {
        List<String> parts = splitMultiValue(raw);
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String part : parts) {
            String email = trim(part).toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                continue;
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException(fieldName + "格式不正确: " + part);
            }
            emails.add(email);
        }
        return new ArrayList<>(emails);
    }

    private List<String> parseConfiguredEmailList(String raw, String configKey) {
        List<String> parts = splitMultiValue(raw);
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String part : parts) {
            String email = trim(part).toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                continue;
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                LOGGER.warn("ignore invalid configured email, key={}, value={}", configKey, part);
                continue;
            }
            emails.add(email);
        }
        if (emails.isEmpty() && "tool.mail.cc.fixed-default".equals(configKey)) {
            emails.add(DEFAULT_TOOL_MAIL_CC);
        }
        return new ArrayList<>(emails);
    }

    private Set<String> parseConfiguredIdSet(String raw) {
        List<String> parts = splitMultiValue(raw);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : parts) {
            String value = trim(part);
            if (!value.isEmpty()) {
                set.add(value);
            }
        }
        return set;
    }

    private List<String> splitMultiValue(String raw) {
        String normalized = trim(raw)
                .replace('；', ';')
                .replace('，', ';')
                .replace(',', ';');
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = normalized.split(";");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String value = trim(part);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private MailContent buildMailContent(String productAlias,
                                         String majorVersion,
                                         String customerName,
                                         String resolvedVersion,
                                         List<String> linkedAttachments,
                                         List<String> attachmentLinks) {
        String plainText = buildMailPlainText(productAlias, majorVersion, customerName, resolvedVersion);
        return new MailContent(buildHtmlMailContent(plainText, linkedAttachments, attachmentLinks), true);
    }

    private String buildMailPlainText(String productAlias,
                                      String majorVersion,
                                      String customerName,
                                      String resolvedVersion) {
        String version = normalizeVersionDisplay(resolvedVersion, majorVersion);
        String name = trim(customerName).isEmpty() ? "客户" : trim(customerName);

        if ("DataEase".equalsIgnoreCase(productAlias)) {
            return """
                    Dear all：

                    %s DataEase 专业版环境部署交付已经完成。目前交付版本：%s

                    以下为 DataEase 的一些相关链接：

                    DataEase 官方文档： https://dataease.io/docs/v2/
                    DataEase 官方知识库：https://kb.fit2cloud.com/categories/dataease
                    DataEase 嵌入式手册： https://rtykuh4z44.feishu.cn/docx/QN1TdHfxjofZXXxZ0toc9oIpnWd?from=from_copylink
                    DataEase API 文档：https://rtykuh4z44.feishu.cn/docx/JKlCdC7twoI53IxpUdRc25U9nrh
                    DataEase 论坛： https://bbs.fit2cloud.com/c/de/6

                    当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！

                    感谢您信任飞致云的产品和服务，后续有问题可随时沟通！
                    """.formatted(name, version);
        }

        if ("JumpServer".equalsIgnoreCase(productAlias) && "3".equals(majorVersion)) {
            return """
                    Dear all：

                    %s JumpServer 环境部署交付已经完成。目前交付版本：%s

                    以下为堡垒机的一些相关链接：

                    JumpServer 官方知识库：https://kb.fit2cloud.com/
                    JumpServer 更新日志请关注飞致云官网信息：https://docs.jumpserver.org/zh/v3/change_log/
                    JumpServer 系统参数说明：https://docs.jumpserver.org/zh/v3/guide/env/
                    JumpServer 官方suport门户工单地址：https://support.fit2cloud.com。

                    点击下方链接，即可下载交付资料，内容包含：

                    JumpServer 使用手册 （管理员手册、审计员手册、普通用户）
                    JumpServer运维手册

                    当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！

                    感谢您信任飞致云的产品和服务，后续有问题可随时沟通！
                    """.formatted(name, version);
        }

        if ("JumpServer".equalsIgnoreCase(productAlias) && "4".equals(majorVersion)) {
            return """
                    Dear all：

                    %s JumpServer 环境部署交付已经完成。目前交付版本：%s

                    以下为堡垒机的一些相关链接：

                    JumpServer 官方知识库：https://kb.fit2cloud.com/
                    JumpServer 更新日志请关注飞致云官网信息：https://docs.jumpserver.org/zh/v4/change_log/
                    JumpServer 系统参数说明：https://docs.jumpserver.org/zh/v3/guide/env/
                    JumpServer 官方suport门户工单地址：https://support.fit2cloud.com。

                    点击下方链接，即可下载交付资料，内容包含：

                    JumpServer 使用手册 （管理员手册、审计员手册、普通用户）
                    JumpServer 运维手册

                    当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！

                    感谢您信任飞致云的产品和服务，后续有问题可随时沟通！
                    """.formatted(name, version);
        }

        if ("MaxKB".equalsIgnoreCase(productAlias) && "1".equals(majorVersion)) {
            return """
                    Dear all：

                    %s MaxKB 环境部署交付已经完成。目前交付版本：%s
                    
                    以下为 MaxKB 的一些相关链接：

                    官方文档: https://maxkb.cn/docs
                    官方论坛：https://bbs.fit2cloud.com/c/mk/11
                    认证培训中心：https://edu.fit2cloud.com/
                    MaxKB 学习视频：https://space.bilibili.com/510493147/lists/3590204?type=season
                    专业版升级流程：https://bbp5ress1o.feishu.cn/wiki/JGqxwv9FciGInLkp0bWcq5f2nRd

                    点击下方链接，即可下载交付资料，内容包含：

                    MaxKB 部署运维手册、测试文档、常见问题文档
                    MaxKB 问答调优手册、高级编排案例、本地部署向量模型操作手册

                    当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！

                    感谢您信任飞致云的产品和服务，后续有问题可随时沟通！
                    """.formatted(name, version);
        }

        if ("MaxKB".equalsIgnoreCase(productAlias) && "2".equals(majorVersion)) {
            return """
                    Dear all：

                    %s MaxKB 环境部署交付已经完成。目前交付版本：%s

                    以下为 MaxKB 的一些相关链接：

                    官方文档: https://maxkb.cn/docs/v2/
                    官方论坛：https://bbs.fit2cloud.com/c/mk/11
                    认证培训中心：https://edu.fit2cloud.com/
                    MaxKB 学习视频：https://space.bilibili.com/510493147/lists/3590204?type=season
                    专业版升级流程：https://bbp5ress1o.feishu.cn/wiki/JGqxwv9FciGInLkp0bWcq5f2nRd

                    点击下方链接，即可下载交付资料，内容包含：

                    MaxKB 部署运维手册、测试文档、常见问题文档
                    MaxKB 问答调优手册、高级编排案例、本地部署向量模型操作手册

                    当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！

                    感谢您信任飞致云的产品和服务，后续有问题可随时沟通！
                    """.formatted(name, version);
        }

        return """
                Dear all：

                %s %s 环境部署交付已经完成。目前交付版本：%s

                当前交付实施已经完成，后续的问题可以在群里沟通，我们的一线技术支持人员将会及时响应您的问题！

                感谢您信任飞致云的产品和服务，后续有问题可随时沟通！
                """.formatted(name, trim(productAlias).isEmpty() ? "产品" : productAlias, version);
    }

    private String buildHtmlMailContent(String plainText, List<String> linkedAttachments, List<String> attachmentLinks) {
        String normalizedPlainText = plainText == null ? "" : plainText.replace("\r\n", "\n");
        String closingBlock = MAIL_CLOSING_PRIMARY + "\n\n" + MAIL_CLOSING_SECONDARY;
        String bodyText = normalizedPlainText;
        boolean hasClosingBlock = false;
        int closingIndex = normalizedPlainText.indexOf(closingBlock);
        if (closingIndex >= 0) {
            bodyText = normalizedPlainText.substring(0, closingIndex).trim();
            hasClosingBlock = true;
        }

        List<String> paragraphs = splitMailParagraphs(bodyText);
        String greeting = paragraphs.isEmpty() ? "Dear all：" : paragraphs.get(0);
        String summary = paragraphs.size() > 1 ? paragraphs.get(1) : "";
        String resourceHeader = "";
        String resourceLines = "";
        String downloadHeader = "";
        String downloadLines = "";

        for (int i = 2; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);
            if (paragraph.startsWith("以下为")) {
                resourceHeader = paragraph;
                if (i + 1 < paragraphs.size()) {
                    resourceLines = paragraphs.get(i + 1);
                    i++;
                }
                continue;
            }
            if (paragraph.startsWith("点击下方链接")) {
                downloadHeader = paragraph;
                if (i + 1 < paragraphs.size()) {
                    downloadLines = paragraphs.get(i + 1);
                    i++;
                }
            }
        }

        int downloadSize = Math.min(linkedAttachments == null ? 0 : linkedAttachments.size(),
                attachmentLinks == null ? 0 : attachmentLinks.size());

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',PingFang SC,'Microsoft YaHei',sans-serif;line-height:1.8;color:#1f2937;font-size:14px;max-width:720px;\">");
        html.append("<div style=\"padding:20px 22px;border:1px solid #d9f2e6;background:linear-gradient(180deg,#f7fffb 0%,#ffffff 100%);border-radius:16px;box-shadow:0 8px 24px rgba(15,23,42,0.06);\">");
        html.append("<div style=\"font-size:18px;font-weight:700;color:#166534;margin-bottom:8px;\">").append(escapeHtml(greeting)).append("</div>");
        if (!summary.isEmpty()) {
            html.append("<div style=\"color:#374151;white-space:pre-wrap;\">").append(escapeHtml(summary)).append("</div>");
        }
        html.append("</div>");
        if (!resourceHeader.isEmpty() || !resourceLines.isEmpty()) {
            html.append("<div style=\"margin-top:18px;padding:16px 18px;border:1px solid #dbeafe;background:#f8fbff;border-radius:14px;\">");
            html.append("<div style=\"font-size:15px;font-weight:600;color:#1d4ed8;margin-bottom:10px;\">相关资料</div>");
            if (!resourceHeader.isEmpty()) {
                html.append("<div style=\"color:#374151;margin-bottom:")
                        .append(resourceLines.isEmpty() ? "0" : "10")
                        .append("px;\">")
                        .append(escapeHtml(resourceHeader))
                        .append("</div>");
            }
            if (!resourceLines.isEmpty()) {
                html.append("<div style=\"white-space:pre-wrap;color:#1f2937;\">").append(escapeHtml(resourceLines)).append("</div>");
            }
            html.append("</div>");
        }
        if (!downloadHeader.isEmpty() || !downloadLines.isEmpty() || downloadSize > 0) {
            html.append("<div style=\"margin-top:18px;padding:16px 18px;border:1px solid #dbe4ff;background:#f6f8ff;border-radius:14px;\">");
            html.append("<div style=\"font-size:15px;font-weight:600;color:#1d4ed8;margin-bottom:8px;\">交付资料</div>");
            if (!downloadHeader.isEmpty()) {
                html.append("<div style=\"color:#374151;margin-bottom:")
                        .append((!downloadLines.isEmpty() || downloadSize > 0) ? "10" : "0")
                        .append("px;\">")
                        .append(escapeHtml(downloadHeader))
                        .append("</div>");
            }
            if (!downloadLines.isEmpty()) {
                html.append("<div style=\"white-space:pre-wrap;color:#1f2937;margin-bottom:")
                        .append(downloadSize > 0 ? "14" : "0")
                        .append("px;\">")
                        .append(escapeHtml(downloadLines))
                        .append("</div>");
            }
            for (int i = 0; i < downloadSize; i++) {
                html.append("<div style=\"margin:0 0 14px 0;\">");
                html.append("<div style=\"font-size:13px;color:#111827;margin-bottom:6px;\">")
                        .append(escapeHtml(linkedAttachments.get(i)))
                        .append("</div>");
                html.append("<a href=\"")
                        .append(escapeHtmlAttribute(attachmentLinks.get(i)))
                        .append("\" style=\"display:inline-block;padding:10px 16px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:10px;font-size:13px;font-weight:600;\">下载附件</a>");
                html.append("</div>");
            }
            html.append("</div>");
        }
        if (hasClosingBlock) {
            html.append("<div style=\"margin-top:18px;padding:16px 18px;border:1px solid #fde68a;background:#fffaf0;border-radius:14px;\">");
            html.append("<div style=\"font-size:15px;font-weight:600;color:#b45309;margin-bottom:10px;\">后续支持</div>");
            html.append("<div style=\"color:#374151;\">");
            html.append("<div style=\"margin-bottom:8px;\">").append(escapeHtml(MAIL_CLOSING_PRIMARY)).append("</div>");
            html.append("<div style=\"font-weight:600;color:#92400e;\">").append(escapeHtml(MAIL_CLOSING_SECONDARY)).append("</div>");
            html.append("</div></div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private List<String> splitMailParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String part : text.split("\\n\\s*\\n")) {
            String normalized = trim(part);
            if (!normalized.isEmpty()) {
                paragraphs.add(normalized);
            }
        }
        return paragraphs;
    }

    private String resolveFallbackVersion(String productAlias) {
        if ("DataEase".equalsIgnoreCase(productAlias)) {
            return trim(fallbackVersionDataEase);
        }
        if ("JumpServer".equalsIgnoreCase(productAlias)) {
            return trim(fallbackVersionJumpServer);
        }
        if ("MaxKB".equalsIgnoreCase(productAlias)) {
            return trim(fallbackVersionMaxKB);
        }
        return "";
    }

    private String normalizeVersionDisplay(String resolvedVersion, String majorVersion) {
        String version = trim(resolvedVersion);
        if (!version.isEmpty()) {
            Matcher matcher = DETAILED_VERSION_PATTERN.matcher(version);
            if (matcher.find()) {
                return "v" + matcher.group(1);
            }
            if (version.toLowerCase().startsWith("v")) {
                return version;
            }
            return "v" + version;
        }

        String major = trim(majorVersion);
        if (!major.isEmpty()) {
            return "v" + major;
        }
        return "待补充";
    }

    private AttachmentRule matchAttachmentRule(String productAlias, String majorVersion) {
        if (!autoAttachEnabled || productAlias == null || productAlias.isEmpty()) {
            return null;
        }
        for (AttachmentRule rule : attachmentRules) {
            if (!rule.product.equalsIgnoreCase(productAlias)) {
                continue;
            }
            if ("*".equals(rule.majorVersion) || rule.majorVersion.equalsIgnoreCase(majorVersion)) {
                return rule;
            }
        }
        return null;
    }

    private File resolveAttachmentFile(String fileName) {
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("附件文件名非法: " + fileName);
        }
        return Path.of(attachmentDir, fileName).toFile();
    }

    private String resolveAttachmentLink(String fileName) {
        return trim(attachmentLinksByFileName.get(fileName));
    }

    private boolean shouldDeliverAsLink(File file, String attachmentLink) {
        if (attachmentLink.isEmpty()) {
            return false;
        }
        return ATTACHMENT_DELIVERY_MODE_LINK.equalsIgnoreCase(attachmentDeliveryMode);
    }

    private String extractMajorVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "";
        }
        Matcher matcher = MAJOR_VERSION_PATTERN.matcher(version);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String buildNoRuleWarning(String productAlias, String majorVersion) {
        if (productAlias == null || productAlias.isEmpty()) {
            return "未识别产品，已按纯正文发送";
        }
        if (majorVersion == null || majorVersion.isEmpty()) {
            return "未识别版本主号，已按纯正文发送";
        }
        return "未匹配到附件规则，已按纯正文发送";
    }

    private List<SmtpProfile> resolveProfilesToTry() {
        List<SmtpProfile> profiles = new ArrayList<>();
        if (smtpPrimaryProfile != null && !trim(smtpPrimaryProfile.host).isEmpty()) {
            profiles.add(smtpPrimaryProfile);
        }
        if (smtpFallbackOnAuthFail
                && smtpSecondaryProfile != null
                && !trim(smtpSecondaryProfile.host).isEmpty()
                && !smtpSecondaryProfile.sameEndpoint(smtpPrimaryProfile)) {
            profiles.add(smtpSecondaryProfile);
        }
        if (profiles.isEmpty()) {
            throw new IllegalStateException("SMTP profile 为空，请检查邮件配置");
        }
        return profiles;
    }

    private String buildProfileSummary() {
        List<SmtpProfile> profiles = resolveProfilesToTry();
        return summarizeProfiles(profiles);
    }

    private String summarizeProfiles(List<SmtpProfile> profiles) {
        List<String> texts = new ArrayList<>();
        for (SmtpProfile profile : profiles) {
            texts.add(profile.name + "(" + profile.host + ":" + profile.port + ",ssl=" + profile.sslEnable + ",starttls=" + profile.starttlsEnable + ")");
        }
        return String.join(", ", texts);
    }

    private boolean containsAuthMechanism(String mechanism) {
        String configured = trim(smtpAuthMechanisms).toUpperCase();
        return configured.contains(mechanism);
    }

    private String rootCauseClassName(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur == null ? "" : cur.getClass().getName();
    }

    private String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur == null ? "" : trim(cur.getMessage());
    }

    private boolean isAuthFailure(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof AuthenticationFailedException) {
                return true;
            }
            cur = cur.getCause();
        }
        String text = (trim(t == null ? "" : t.getMessage()) + " " + rootCauseMessage(t)).toLowerCase();
        return text.contains("authentication failed") || text.contains("535");
    }

    private String detectFailureStage(Throwable t) {
        if (isAuthFailure(t)) {
            return "auth";
        }
        String text = (trim(t == null ? "" : t.getMessage()) + " " + rootCauseMessage(t)).toLowerCase();
        if (text.contains("ehlo")) {
            return "ehlo";
        }
        if (text.contains("connect") || text.contains("timeout") || text.contains("unknownhost")) {
            return "connect";
        }
        return "send";
    }

    private String sanitizeMessage(String text) {
        return trim(text)
                .replaceAll("(?i)password\\s*=\\s*[^\\s,;]+", "password=<masked>")
                .replaceAll("(?i)auth[-_ ]?code\\s*=\\s*[^\\s,;]+", "authCode=<masked>");
    }

    private String authCodeHashPrefix(String code) {
        String value = trim(code);
        if (value.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4 && i < bytes.length; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }

    private String buildAuthHint(Throwable t) {
        String text = (trim(t == null ? "" : t.getMessage()) + " " + rootCauseClassName(t) + " " + rootCauseMessage(t)).toLowerCase();
        if (text.contains("authentication failed") || text.contains("535")) {
            return "；请确认使用企业邮箱客户端专用密码（非网页登录密码），并检查 /opt/wxwork-tools/wxwork-tools.properties 是否覆盖了当前配置";
        }
        if (text.contains("unknownhost") || text.contains("nodename nor servname provided")) {
            return "；请检查 SMTP 主机地址是否正确且 DNS 可解析";
        }
        if (text.contains("connect timed out") || text.contains("connection timed out")) {
            return "；请检查 SMTP 主机/端口是否可达（建议在 465/SSL 与 587/STARTTLS 两组配置间切换验证）";
        }
        if (text.contains("connection refused")) {
            return "；SMTP 连接被拒绝，请确认主机端口与防火墙策略";
        }
        if (text.contains("sslhandshakeexception") || text.contains("handshake_failure")) {
            return "；SSL/TLS 握手失败，请核对端口与 SSL/STARTTLS 配置组合";
        }
        return "";
    }

    private String buildSendMailFailureMessage(Throwable lastError, List<String> profileErrors) {
        String stage = detectFailureStage(lastError);
        String root = sanitizeMessage(rootCauseMessage(lastError));
        if (root.isEmpty()) {
            root = sanitizeMessage(trim(lastError == null ? "" : lastError.getMessage()));
        }
        if (root.isEmpty()) {
            root = "unknown";
        }
        String invalidAddressText = buildInvalidAddressText(lastError);
        String profileText = profileErrors == null || profileErrors.isEmpty()
                ? ""
                : "；profiles=" + String.join("; ", profileErrors);
        return "重试后仍发送失败(stage=" + stage + "): " + root + invalidAddressText + buildAuthHint(lastError) + profileText;
    }

    private String buildInvalidAddressText(Throwable t) {
        List<String> invalidAddresses = extractInvalidAddresses(t);
        if (invalidAddresses.isEmpty()) {
            return "";
        }
        return "；invalidAddresses=" + String.join(",", invalidAddresses);
    }

    private String summarizeInvalidAddresses(Throwable t) {
        List<String> invalidAddresses = extractInvalidAddresses(t);
        if (invalidAddresses.isEmpty()) {
            return "-";
        }
        return String.join(",", invalidAddresses);
    }

    private List<String> extractInvalidAddresses(Throwable t) {
        LinkedHashSet<String> invalidAddresses = new LinkedHashSet<>();
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SMTPAddressFailedException addressFailedException) {
                InternetAddress address = addressFailedException.getAddress();
                if (address != null) {
                    String value = trim(address.toString()).toLowerCase(Locale.ROOT);
                    if (!value.isEmpty()) {
                        invalidAddresses.add(value);
                    }
                }
            }
            if (cur instanceof SendFailedException sendFailedException) {
                Address[] addresses = sendFailedException.getInvalidAddresses();
                if (addresses != null) {
                    for (Address address : addresses) {
                        if (address == null) {
                            continue;
                        }
                        String value = trim(address.toString()).toLowerCase(Locale.ROOT);
                        if (!value.isEmpty()) {
                            invalidAddresses.add(value);
                        }
                    }
                }
            }
            collectEmailsFromText(invalidAddresses, cur.getMessage());
            cur = cur.getCause();
        }
        return new ArrayList<>(invalidAddresses);
    }

    private void collectEmailsFromText(Set<String> target, String text) {
        String value = trim(text).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return;
        }
        Matcher matcher = EMAIL_EXTRACT_PATTERN.matcher(value);
        while (matcher.find()) {
            String email = trim(matcher.group(1));
            if (!email.isEmpty()) {
                target.add(email);
            }
        }
    }

    private boolean isPlaceholderSecret(String secret) {
        String value = trim(secret).toLowerCase();
        if (value.isEmpty()) {
            return true;
        }
        return "待填".equals(value)
                || "todo".equals(value)
                || "changeme".equals(value)
                || "your_password".equals(value)
                || "******".equals(value)
                || "***".equals(value);
    }

    private String maskEmail(String email) {
        String value = trim(email);
        int index = value.indexOf('@');
        if (index <= 1) {
            return value;
        }
        return value.charAt(0) + "***" + value.substring(index);
    }

    private List<AttachmentRule> loadAttachRulesFromProperties(Properties props) {
        List<AttachmentRule> rules = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(ATTACH_RULE_PREFIX)) {
                continue;
            }
            String indexText = key.substring(ATTACH_RULE_PREFIX.length());
            int index = parseIntOrDefault(indexText, Integer.MAX_VALUE);
            String raw = trim(props.getProperty(key));
            if (raw.isEmpty()) {
                continue;
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3) {
                LOGGER.warn("invalid auto attach rule: key={}, value={}", key, raw);
                continue;
            }
            String product = trim(parts[0]);
            String majorVersion = trim(parts[1]);
            String fileName = trim(parts[2]);
            if (product.isEmpty() || majorVersion.isEmpty() || fileName.isEmpty()) {
                LOGGER.warn("invalid auto attach rule(empty part): key={}, value={}", key, raw);
                continue;
            }
            AttachmentRule rule = new AttachmentRule();
            rule.index = index;
            rule.product = product;
            rule.majorVersion = majorVersion;
            rule.fileName = fileName;
            rule.raw = raw;
            rules.add(rule);
        }
        rules.sort(Comparator.comparingInt(r -> r.index));
        return rules;
    }

    private Map<String, String> loadAttachmentLinksFromProperties(Properties props) {
        Map<String, IndexedAttachmentLink> indexed = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(ATTACH_LINK_RULE_PREFIX)) {
                continue;
            }
            String indexText = key.substring(ATTACH_LINK_RULE_PREFIX.length());
            int index = parseIntOrDefault(indexText, Integer.MAX_VALUE);
            String raw = trim(props.getProperty(key));
            if (raw.isEmpty()) {
                continue;
            }
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                LOGGER.warn("invalid attachment link rule: key={}, value={}", key, raw);
                continue;
            }
            String fileName = trim(parts[0]);
            String url = trim(parts[1]);
            if (fileName.isEmpty() || url.isEmpty()) {
                LOGGER.warn("invalid attachment link rule(empty part): key={}, value={}", key, raw);
                continue;
            }
            indexed.put(fileName, new IndexedAttachmentLink(index, url));
        }
        List<Map.Entry<String, IndexedAttachmentLink>> entries = new ArrayList<>(indexed.entrySet());
        entries.sort(Comparator.comparingInt(entry -> entry.getValue().index));
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, IndexedAttachmentLink> entry : entries) {
            result.put(entry.getKey(), entry.getValue().url);
        }
        return result;
    }

    private String normalizeAttachmentDeliveryMode(String mode) {
        String normalized = trim(mode).toLowerCase(Locale.ROOT);
        if (ATTACHMENT_DELIVERY_MODE_LINK.equals(normalized)) {
            return ATTACHMENT_DELIVERY_MODE_LINK;
        }
        return ATTACHMENT_DELIVERY_MODE_ATTACH;
    }

    private String escapeHtml(String text) {
        return trim(text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeHtmlAttribute(String text) {
        return trim(text)
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private String getOrDefault(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private MailPropsContext loadMailProps() {
        Properties props = new Properties();
        boolean classpathLoaded = false;
        try (InputStream classpathInput = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("wxwork-tools.properties")) {
            if (classpathInput != null) {
                props.load(new InputStreamReader(classpathInput, StandardCharsets.UTF_8));
                classpathLoaded = true;
            }
        } catch (IOException e) {
            LOGGER.warn("load classpath wxwork-tools.properties failed: {}", e.getMessage());
        }

        Path externalPath = Path.of(EXTERNAL_CONFIG_PATH);
        boolean externalExists = Files.exists(externalPath);
        boolean externalLoaded = false;
        long externalLastModified = 0L;
        if (externalExists) {
            try (InputStream externalInput = Files.newInputStream(externalPath)) {
                props.load(new InputStreamReader(externalInput, StandardCharsets.UTF_8));
                externalLoaded = true;
                FileTime lastModifiedTime = Files.getLastModifiedTime(externalPath);
                externalLastModified = lastModifiedTime != null ? lastModifiedTime.toMillis() : 0L;
            } catch (IOException e) {
                LOGGER.warn("load {} failed: {}", EXTERNAL_CONFIG_PATH, e.getMessage());
            }
        }
        String source = externalLoaded ? "external" : (classpathLoaded ? "classpath" : "default");
        return new MailPropsContext(props, source, externalExists, externalLastModified);
    }

    private static class SkuDescription {
        private String productName;
        private String productDesp;
    }

    private static class AcceptanceUpstreamResult {
        private String mode;
        private Object requestPayload;
        private String upstreamRaw = "";
        private Object upstreamJson = new JSONObject(true);
        private String reportBase64 = "";
        private String failureReason = "";
        private boolean fallbackUsed = false;
        private String fallbackReason = "";
    }

    private static class CcResolution {
        private List<String> defaultCcList = new ArrayList<>();
        private List<String> mandatoryCcList = new ArrayList<>();
        private List<String> optionalDefaultCcList = new ArrayList<>();
        private List<String> finalCcEmails = new ArrayList<>();
        private Long clientId;
        private List<String> salesUserIds = new ArrayList<>();
        private List<String> excludedSalesUserIds = new ArrayList<>();
        private List<String> salesEmails = new ArrayList<>();
        private boolean defaultCcApplied = true;
        private String loginUserId = "";
        private String loginUserEmail = "";
        private List<String> sameDeptEmails = new ArrayList<>();
    }

    private static class StaffMailContext {
        private String extId = "";
        private String email = "";
        private Long deptId;
    }

    private static class AttachmentRule {
        private int index;
        private String product;
        private String majorVersion;
        private String fileName;
        private String raw;
    }

    private static class IndexedAttachmentLink {
        private final int index;
        private final String url;

        private IndexedAttachmentLink(int index, String url) {
            this.index = index;
            this.url = url;
        }
    }

    private static class MailContent {
        private final String content;
        private final boolean html;

        private MailContent(String content, boolean html) {
            this.content = content;
            this.html = html;
        }
    }

    private static class SmtpProfile {
        private final String name;
        private final String host;
        private final int port;
        private final boolean sslEnable;
        private final boolean starttlsEnable;

        private SmtpProfile(String name, String host, int port, boolean sslEnable, boolean starttlsEnable) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.sslEnable = sslEnable;
            this.starttlsEnable = starttlsEnable;
        }

        private boolean sameEndpoint(SmtpProfile other) {
            if (other == null) {
                return false;
            }
            return this.port == other.port
                    && this.sslEnable == other.sslEnable
                    && this.starttlsEnable == other.starttlsEnable
                    && String.valueOf(this.host).equalsIgnoreCase(String.valueOf(other.host));
        }

        @Override
        public String toString() {
            return name + "(" + host + ":" + port + ",ssl=" + sslEnable + ",starttls=" + starttlsEnable + ")";
        }
    }

    private static class MailPropsContext {
        private final Properties props;
        private final String activeConfigSource;
        private final boolean externalExists;
        private final long externalLastModified;

        private MailPropsContext(Properties props, String activeConfigSource, boolean externalExists, long externalLastModified) {
            this.props = props;
            this.activeConfigSource = activeConfigSource;
            this.externalExists = externalExists;
            this.externalLastModified = externalLastModified;
        }
    }
}
