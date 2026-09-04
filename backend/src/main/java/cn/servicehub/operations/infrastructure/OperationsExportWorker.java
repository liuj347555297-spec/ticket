package cn.servicehub.operations.infrastructure;

import cn.servicehub.operations.application.OperationsExportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component public class OperationsExportWorker { private final OperationsExportService exports; public OperationsExportWorker(OperationsExportService exports){this.exports=exports;} @Scheduled(fixedDelayString="${servicehub.operations.export-delay-ms:5000}") public void process(){exports.processPending();} }
