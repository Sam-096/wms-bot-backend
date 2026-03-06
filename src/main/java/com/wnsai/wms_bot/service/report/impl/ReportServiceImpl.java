package com.wnsai.wms_bot.service.report.impl;

import com.opencsv.CSVWriter;
import com.wnsai.wms_bot.dto.report.ReportRequest;
import com.wnsai.wms_bot.dto.report.ReportResponse;
import com.wnsai.wms_bot.entity.Bond;
import com.wnsai.wms_bot.entity.GatePass;
import com.wnsai.wms_bot.entity.GeneratedReport;
import com.wnsai.wms_bot.entity.InwardTransaction;
import com.wnsai.wms_bot.entity.OutwardTransaction;
import com.wnsai.wms_bot.entity.StockInventory;
import com.wnsai.wms_bot.exception.EntityNotFoundException;
import com.wnsai.wms_bot.repository.*;
import com.wnsai.wms_bot.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final GeneratedReportRepository     reportRepo;
    private final InwardTransactionRepository   inwardRepo;
    private final OutwardTransactionRepository  outwardRepo;
    private final StockInventoryRepository      stockRepo;
    private final GatePassRepository            gatePassRepo;
    private final BondRepository                bondRepo;

    @Override
    public Mono<ReportResponse> generate(ReportRequest request, String userId) {
        return Mono.fromCallable(() -> {
            UUID userUuid = userId != null ? UUID.fromString(userId) : null;
            GeneratedReport report = GeneratedReport.builder()
                    .warehouseId(request.warehouseId())
                    .userId(userUuid)
                    .reportType(request.reportType())
                    .format(request.format())
                    .status("GENERATING")
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .build();
            report = reportRepo.save(report);
            log.info("Report job created id={} type={}", report.getId(), request.reportType());
            return report;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(report -> {
            // Kick off async generation — fire-and-forget
            final UUID reportId = report.getId();
            Mono.fromCallable(() -> generateContent(request, reportId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            path -> reportRepo.markReady(reportId, "READY", path),
                            err  -> {
                                log.error("Report generation failed id={}: {}", reportId, err.getMessage());
                                reportRepo.markFailed(reportId, err.getMessage());
                            }
                    );
            return Mono.just(toResponse(report));
        });
    }

    @Override
    public Mono<ReportResponse> getStatus(UUID reportId) {
        return Mono.fromCallable(() -> {
            GeneratedReport report = reportRepo.findById(reportId)
                    .orElseThrow(() -> new EntityNotFoundException("GeneratedReport", reportId));
            return toResponse(report);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<byte[]> download(UUID reportId) {
        return Mono.fromCallable(() -> {
            GeneratedReport report = reportRepo.findById(reportId)
                    .orElseThrow(() -> new EntityNotFoundException("GeneratedReport", reportId));
            if (!"READY".equals(report.getStatus())) {
                throw new IllegalStateException("Report is not ready yet. Status: " + report.getStatus());
            }
            return Files.readAllBytes(Path.of(report.getFilePath()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<ReportResponse>> getHistory(String warehouseId) {
        return Mono.fromCallable(() ->
            reportRepo.findTop20ByWarehouseIdOrderByGeneratedAtDesc(warehouseId)
                    .stream().map(this::toResponse).collect(Collectors.toList())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    // ─── Generation logic ────────────────────────────────────────────────────

    private String generateContent(ReportRequest request, UUID reportId) throws Exception {
        byte[] content = "CSV".equalsIgnoreCase(request.format())
                ? generateCsv(request)
                : generatePdf(request);

        String ext  = "CSV".equalsIgnoreCase(request.format()) ? ".csv" : ".pdf";
        Path   temp = Files.createTempFile("report_" + reportId, ext);
        Files.write(temp, content);
        log.info("Report written to {}", temp);
        return temp.toString();
    }

    private byte[] generateCsv(ReportRequest req) throws Exception {
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            switch (req.reportType()) {
                case "STOCK_SUMMARY" -> {
                    writer.writeNext(new String[]{"Item Name", "Item Code", "Current Stock", "Min Threshold", "Unit", "Low Stock"});
                    for (StockInventory s : stockRepo.findByWarehouseId(req.warehouseId())) {
                        boolean low = s.getCurrentStock() != null && s.getMinThreshold() != null
                                && s.getCurrentStock().compareTo(s.getMinThreshold()) <= 0;
                        writer.writeNext(new String[]{
                                s.getItemName(), s.getItemCode(),
                                String.valueOf(s.getCurrentStock()),
                                String.valueOf(s.getMinThreshold()),
                                s.getUnit(), String.valueOf(low)
                        });
                    }
                }
                case "INWARD_SUMMARY" -> {
                    writer.writeNext(new String[]{"GRN Number", "Supplier", "Item", "Qty Bags", "Status", "Date"});
                    for (InwardTransaction t : inwardRepo.findByWarehouseId(req.warehouseId())) {
                        writer.writeNext(new String[]{
                                t.getGrnNumber(), t.getSupplierName(), t.getItemName(),
                                String.valueOf(t.getQuantityBags()), t.getStatus(),
                                String.valueOf(t.getInwardDate())
                        });
                    }
                }
                case "OUTWARD_SUMMARY" -> {
                    writer.writeNext(new String[]{"Dispatch Number", "Customer", "Item", "Qty Bags", "Status", "Date"});
                    for (OutwardTransaction t : outwardRepo.findByWarehouseId(req.warehouseId())) {
                        writer.writeNext(new String[]{
                                t.getDispatchNumber(), t.getCustomerName(), t.getItemName(),
                                String.valueOf(t.getQuantityBags()), t.getStatus(),
                                String.valueOf(t.getOutwardDate())
                        });
                    }
                }
                case "GATE_PASS_LOG" -> {
                    writer.writeNext(new String[]{"Pass Number", "Vehicle", "Driver", "Purpose", "Status", "Entry Time", "Exit Time"});
                    for (GatePass g : gatePassRepo.findByWarehouseId(req.warehouseId())) {
                        writer.writeNext(new String[]{
                                g.getPassNumber(), g.getVehicleNumber(), g.getDriverName(),
                                g.getPurpose(), g.getStatus(),
                                String.valueOf(g.getEntryTime()), String.valueOf(g.getExitTime())
                        });
                    }
                }
                case "BOND_STATUS" -> {
                    writer.writeNext(new String[]{"Bond Number", "Item", "Quantity", "Bond Date", "Expiry Date", "Status"});
                    for (Bond b : bondRepo.findByWarehouseId(req.warehouseId())) {
                        writer.writeNext(new String[]{
                                b.getBondNumber(), b.getItemName(),
                                String.valueOf(b.getQuantity()),
                                String.valueOf(b.getBondDate()),
                                String.valueOf(b.getExpiryDate()), b.getStatus()
                        });
                    }
                }
                default -> {
                    writer.writeNext(new String[]{"Report Type", "Generated At"});
                    writer.writeNext(new String[]{req.reportType(), OffsetDateTime.now().toString()});
                }
            }
        }
        return sw.toString().getBytes();
    }

    private byte[] generatePdf(ReportRequest req) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(50, 780);
                cs.showText("Godown AI - " + req.reportType().replace("_", " "));
                cs.newLineAtOffset(0, -20);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Warehouse: " + req.warehouseId() + "  |  Generated: " + LocalDate.now());
                cs.newLineAtOffset(0, -30);

                List<String[]> rows = collectRows(req);
                for (String[] row : rows) {
                    cs.showText(String.join("  |  ", row));
                    cs.newLineAtOffset(0, -15);
                }
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private List<String[]> collectRows(ReportRequest req) {
        return switch (req.reportType()) {
            case "STOCK_SUMMARY" -> stockRepo.findByWarehouseId(req.warehouseId())
                    .stream()
                    .map(s -> new String[]{s.getItemName(),
                            String.valueOf(s.getCurrentStock()), s.getUnit()})
                    .collect(Collectors.toList());
            case "INWARD_SUMMARY" -> inwardRepo.findByWarehouseId(req.warehouseId())
                    .stream()
                    .map(t -> new String[]{t.getGrnNumber(), t.getItemName(), t.getStatus()})
                    .collect(Collectors.toList());
            case "BOND_STATUS" -> bondRepo.findByWarehouseId(req.warehouseId())
                    .stream()
                    .map(b -> new String[]{b.getBondNumber(), b.getItemName(), b.getStatus()})
                    .collect(Collectors.toList());
            default -> Collections.singletonList(new String[]{"No data for " + req.reportType()});
        };
    }

    private ReportResponse toResponse(GeneratedReport r) {
        return new ReportResponse(
                r.getId(), r.getStatus(), r.getReportType(), r.getFormat(),
                r.getWarehouseId(), r.getGeneratedAt(), r.getExpiresAt(), r.getErrorMessage());
    }
}
