package vn.edu.topicregistration.repository;

import java.io.IOException;
import java.io.InputStream;

public interface ReportFileRepository {
    StoredReportFile store(InputStream source, long expectedSize) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey);

    record StoredReportFile(String storageKey, long sizeBytes) {
    }
}
