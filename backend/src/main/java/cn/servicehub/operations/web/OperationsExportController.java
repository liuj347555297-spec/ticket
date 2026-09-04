package cn.servicehub.operations.web;

import cn.servicehub.operations.application.OperationsExportService;
import cn.servicehub.operations.domain.ReportExportTask;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/reports/operations/exports")
public class OperationsExportController {
 private final OperationsExportService exports;public OperationsExportController(OperationsExportService e){exports=e;}
 @PostMapping ExportTaskResponse request(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return response(exports.request(from,to));}
 @GetMapping("/{id}") ExportTaskResponse get(@PathVariable String id){return response(exports.get(id));}
 @GetMapping("/{id}/content") void content(@PathVariable String id,HttpServletResponse response)throws java.io.IOException{var c=exports.download(id);response.setContentType("text/csv;charset=UTF-8");response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(c.fileName(),java.nio.charset.StandardCharsets.UTF_8));response.setHeader("X-Content-SHA256",c.sha256());response.getOutputStream().write(c.bytes());}
 private static ExportTaskResponse response(ReportExportTask t){return new ExportTaskResponse(t.id(),t.reportType(),t.from(),t.to(),t.status(),t.fileName(),t.errorCode(),t.createdAt(),t.startedAt(),t.completedAt(),t.downloadCount());}
 record ExportTaskResponse(String id,String reportType,LocalDate from,LocalDate to,ReportExportTask.Status status,String fileName,String errorCode,java.time.Instant createdAt,java.time.Instant startedAt,java.time.Instant completedAt,int downloadCount){}
}
