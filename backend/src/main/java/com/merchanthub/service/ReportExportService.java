package com.merchanthub.service;

import com.merchanthub.config.AppProperties;
import com.merchanthub.dto.OrderDtos.OrderSummary;
import com.merchanthub.dto.ReportDtos.ExportResponse;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Exports the merchant's orders as CSV to S3 and hands back a short-lived
 * presigned download link. Disabled (returns a clear error, not a stack
 * trace) unless {@code merchanthub.reports-s3-bucket} is set — see
 * {@link com.merchanthub.config.S3Config}.
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    private static final Duration DOWNLOAD_LINK_TTL = Duration.ofMinutes(15);

    private final OrderService orders;
    private final AppProperties props;
    private final ObjectProvider<S3Client> s3Client;
    private final ObjectProvider<S3Presigner> s3Presigner;

    public ReportExportService(OrderService orders, AppProperties props,
                                ObjectProvider<S3Client> s3Client, ObjectProvider<S3Presigner> s3Presigner) {
        this.orders = orders;
        this.props = props;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public ExportResponse exportOrdersCsv(Instant from, Instant to) {
        S3Client client = s3Client.getIfAvailable();
        S3Presigner presigner = s3Presigner.getIfAvailable();
        if (client == null || presigner == null) {
            throw new ApiExceptions.BadRequest(
                    "Report export is disabled — set REPORTS_S3_BUCKET to enable it.");
        }

        var merchantId = TenantContext.requireMerchantId();
        String csv = toCsv(orders.list(null, from, to, 0, 5000).content());
        String key = "reports/%s/orders-%s.csv".formatted(merchantId, Instant.now().toString().replace(":", "-"));

        client.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getReportsS3Bucket())
                        .key(key)
                        .contentType("text/csv")
                        .build(),
                RequestBody.fromString(csv, StandardCharsets.UTF_8));

        String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(DOWNLOAD_LINK_TTL)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(props.getReportsS3Bucket())
                                .key(key)
                                .build())
                        .build())
                .url()
                .toString();

        log.info("Exported order report for merchant {} to s3://{}/{}", merchantId, props.getReportsS3Bucket(), key);
        return new ExportResponse(key, url, Instant.now().plus(DOWNLOAD_LINK_TTL));
    }

    private static String toCsv(java.util.List<OrderSummary> rows) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        StringBuilder sb = new StringBuilder("external_id,total,currency,status,customer_email,created_at,item_count\n");
        for (OrderSummary o : rows) {
            sb.append(csvField(o.externalId())).append(',')
                    .append(o.total()).append(',')
                    .append(csvField(o.currency())).append(',')
                    .append(csvField(o.status())).append(',')
                    .append(csvField(o.customerEmail())).append(',')
                    .append(o.createdAt() == null ? "" : fmt.format(o.createdAt())).append(',')
                    .append(o.itemCount()).append('\n');
        }
        return sb.toString();
    }

    private static String csvField(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")
                ? "\"" + escaped + "\""
                : escaped;
    }
}
