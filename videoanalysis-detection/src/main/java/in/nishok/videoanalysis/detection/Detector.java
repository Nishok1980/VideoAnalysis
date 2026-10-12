package in.nishok.videoanalysis.detection;

import java.util.List;
import in.nishok.videoanalysis.domain.Model.Detection;

/** Model boundary; a test double is injected only by tests. */
public interface Detector extends AutoCloseable
{
    List<Detection> detect(byte[] rgb, int width, int height,
        double confidence) throws Exception;
    String fingerprint();
    @Override void close() throws Exception;
}
