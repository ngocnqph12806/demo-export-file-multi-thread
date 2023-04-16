package com.ngocnq.exportbymultithread;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private final DataDtoRepository dataDtoRepository;

    private static final int BATCH_SIZE = 10000;
    private static final int THREAD_POOL_SIZE = 5;

//    @PostConstruct
//    public void init() {
//        Thread thread = new Thread(() -> {
//            ExecutorService executorService = Executors.newFixedThreadPool(10);
//            for (int j = 0; j < 500; j++) {
//                Runnable task = () -> {
//                    List<DataDto> dtos = new ArrayList<>();
//                    for (int i = 0; i < 10000; i++) {
//                        dtos.add(DataDto.builder()
//                                .noNumber(generateRandomString(10))
//                                .name(generateRandomString(50))
//                                .email(generateRandomString(100))
//                                .phoneNumber(generateRandomString(20))
//                                .userName(generateRandomString(100))
//                                .password(generateRandomString(100))
//                                .address(generateRandomString(200))
//                                .city(generateRandomString(50))
//                                .country(generateRandomString(50))
//                                .countryCode(generateRandomString(10))
//                                .description(generateRandomString(255))
//                                .build());
//                    }
//                    dataDtoRepository.saveAll(dtos);
//                    System.out.println("save all");
//                };
//                executorService.submit(task);
//            }
//            executorService.shutdown();
//            try {
//                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        thread.start();
//    }

    public void exportToExcel(OutputStream outputStream) throws IOException, InterruptedException {
        Workbook workbook = new SXSSFWorkbook();

        // Lấy tổng số lượng bản ghi
        int total = (int) dataDtoRepository.count();
//        int total = 1005000;
        int numberSheet = total / 1000000;
        numberSheet = total % 1000000 > 0 ? numberSheet + 1 : numberSheet;
        CountDownLatch latch = new CountDownLatch((int) Math.ceil(total / BATCH_SIZE));
        // Tạo Semaphore để đồng bộ hóa truy cập vào file Excel
        Semaphore semaphore = new Semaphore(1);
        // Tạo ExecutorService để quản lý các luồng
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger finalI1 = new AtomicInteger(-1);
        int maxRecords = 1000000;
        int totalRecords;
        for (int k = 0; k < numberSheet; k++) {
            if (total > maxRecords) {
                totalRecords = maxRecords;
                total = total - totalRecords;
                maxRecords += totalRecords;
            } else {
                totalRecords = total;
                total = total - totalRecords;
                maxRecords += totalRecords;
            }
            writeToSheet(workbook, latch, semaphore, executorService, k, finalI1, totalRecords);
        }

        try {
            boolean allCompleted = latch.await(30, TimeUnit.SECONDS);
            if (allCompleted) {
                // Tất cả các tiến trình đã hoàn thành trong 5 giây
                log.info("đã xong");
                // Đợi cho tất cả các luồng hoàn thành

            } else {
                // Không phải tất cả các tiến trình đã hoàn thành trong 5 giây
                log.info("chưa xong");
            }
        } catch (InterruptedException e) {
            // Xử lý lỗi gián đoạn ở đây, ví dụ in ra một thông báo lỗi
            System.err.println("Thread đã bị gián đoạn: " + e.getMessage());
        } finally {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
            // Ghi workbook ra OutputStream
            workbook.write(outputStream);
            workbook.close();
        }
    }

    private void writeToSheet(Workbook workbook, CountDownLatch latch, Semaphore semaphore, ExecutorService executorService, int k, AtomicInteger finalI1, int totalRecords) {
        Sheet sheet = workbook.createSheet("Data" + k);
        AtomicInteger currentRow = new AtomicInteger(0);
        int totalBatches = (int) Math.ceil(totalRecords / BATCH_SIZE);
        for (int i = 0; i < totalBatches; i++) {
            // Thực hiện xử lý trên từng batch bản ghi
            AtomicInteger finalCurrentRow = currentRow;
            Runnable task = () -> {
                try {
                    finalI1.getAndIncrement();
                    log.info("{}", finalI1);
                    Pageable sortedByName =
                            PageRequest.of(finalI1.get(), BATCH_SIZE);
                    List<DataDto> dataList = dataDtoRepository.findAll(sortedByName).getContent();
                    int numberColumn;
                    for (DataDto data : dataList) {
                        numberColumn = 0;
                        // Đợi cho Semaphore được giải phóng
                        semaphore.acquire();

                        // Ghi dữ liệu vào file Excel
                        Row row = sheet.createRow(finalCurrentRow.getAndIncrement());
                        row.createCell(numberColumn++).setCellValue(data.getId());
                        row.createCell(numberColumn++).setCellValue(data.getNoNumber());
                        row.createCell(numberColumn++).setCellValue(data.getName());
                        row.createCell(numberColumn++).setCellValue(data.getEmail());
                        row.createCell(numberColumn++).setCellValue(data.getPhoneNumber());
                        row.createCell(numberColumn++).setCellValue(data.getUserName());
                        row.createCell(numberColumn++).setCellValue(data.getPassword());
                        row.createCell(numberColumn++).setCellValue(data.getAddress());
                        row.createCell(numberColumn++).setCellValue(data.getCity());
                        row.createCell(numberColumn++).setCellValue(data.getCountry());
                        row.createCell(numberColumn++).setCellValue(data.getCountryCode());
                        row.createCell(numberColumn).setCellValue(data.getDescription());

                        // Giải phóng Semaphore
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    log.info("Finished");
                    latch.countDown();
                }
            };
            // Thực hiện task trên 1 luồng của ExecutorService
            executorService.submit(task);
        }
    }

    public static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            // Generate a random integer between 0 and 25 inclusive
            int randomInt = random.nextInt(26);
            // Add 97 to the random integer to get a lowercase ASCII character
            char randomChar = (char) (randomInt + 97);
            sb.append(randomChar);
        }

        return sb.toString();
    }

}
