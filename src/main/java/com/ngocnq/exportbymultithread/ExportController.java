package com.ngocnq.exportbymultithread;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Date;

@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/excel")
    public String homeExportExcel(Model model) {
        return "/templates/index.html";
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportToExcel() {
        System.out.println(new Date());

        StreamingResponseBody stream = outputStream -> {
            try {
                exportService.exportToExcel(outputStream);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Failed to export data to Excel file", e);
            }
        };

        System.out.println(new Date());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=data.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

}
