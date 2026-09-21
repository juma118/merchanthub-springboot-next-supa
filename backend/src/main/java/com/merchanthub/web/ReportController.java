package com.merchanthub.web;

import com.merchanthub.dto.ReportDtos.ExportResponse;
import com.merchanthub.service.ReportExportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportExportService exportService;

    public ReportController(ReportExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/orders/export")
    public ExportResponse exportOrders(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return exportService.exportOrdersCsv(WebParams.parseInstant(from), WebParams.parseInstant(to));
    }
}
