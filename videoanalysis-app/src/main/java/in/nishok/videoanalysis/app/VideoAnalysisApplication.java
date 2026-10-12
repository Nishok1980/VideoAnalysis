package in.nishok.videoanalysis.app;

import in.nishok.videoanalysis.detection.YoloV10Detector;
import in.nishok.videoanalysis.service.AnalysisService;
import in.nishok.videoanalysis.video.FfmpegVideo;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VideoAnalysisApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(VideoAnalysisApplication.class, args);
    }

    @Bean
    FfmpegVideo video(@Value("${analysis.ffmpeg}") String ffmpeg,
        @Value("${analysis.ffprobe}") String ffprobe)
    {
        return new FfmpegVideo(ffmpeg, ffprobe);
    }

    @Bean(destroyMethod = "close")
    AnalysisService service(FfmpegVideo video,
        @Value("${analysis.model}") String model,
        @Value("${analysis.data}") String data) throws Exception
    {
        return new AnalysisService(video,
            () -> new YoloV10Detector(Path.of(model)), Path.of(data));
    }
}
