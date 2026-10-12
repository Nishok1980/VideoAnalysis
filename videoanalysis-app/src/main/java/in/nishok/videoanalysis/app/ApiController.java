package in.nishok.videoanalysis.app;

import static in.nishok.videoanalysis.domain.Model.*;
import in.nishok.videoanalysis.service.AnalysisService;
import in.nishok.videoanalysis.video.FfmpegVideo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController
{
    private final AnalysisService service;
    private final FfmpegVideo video;
    private final Path model;

    public ApiController(AnalysisService service, FfmpegVideo video,
        @Value("${analysis.model}") String model)
    {
        this.service = service;
        this.video = video;
        this.model = Path.of(model);
    }

    @GetMapping("/status")
    Map<String, Object> status()
    {
        var result = new LinkedHashMap<String, Object>();
        result.put("modelPresent", Files.isRegularFile(model));
        try
        {
            video.check();
            result.put("videoToolsReady", true);
        }
        catch (Exception exception)
        {
            result.put("videoToolsReady", false);
            result.put("message", "Install FFmpeg and FFprobe; see README.");
        }
        return result;
    }

    @GetMapping("/videos") List<VideoInfo> videos() { return service.videos(); }

    @PostMapping("/videos")
    VideoInfo upload(@RequestParam("file") MultipartFile file) throws Exception
    {
        try (var input = file.getInputStream())
        {
            return service.upload(input, file.getOriginalFilename());
        }
    }

    @DeleteMapping("/videos/{id}")
    void delete(@PathVariable UUID id) throws Exception { service.delete(id); }

    @GetMapping("/videos/{id}/{asset}")
    ResponseEntity<Resource> asset(@PathVariable UUID id,
        @PathVariable String asset)
    {
        var resource = new FileSystemResource(service.asset(id, asset));
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .header("Content-Type", asset.equals("preview.mp4")
                ? "video/mp4" : "image/jpeg")
            .body(resource);
    }

    public record StartRequest(UUID videoId, Rules rules) { }

    @PostMapping("/jobs")
    JobView start(@RequestBody StartRequest request)
    {
        if (request.videoId() == null || request.rules() == null)
        {
            throw new IllegalArgumentException("Video and rules required");
        }
        return service.start(request.videoId(), request.rules());
    }

    @GetMapping("/jobs") List<JobView> jobs() { return service.jobs(); }
    @GetMapping("/jobs/{id}")
    JobView job(@PathVariable UUID id) { return service.job(id); }
    @PostMapping("/jobs/{id}/cancel")
    void cancel(@PathVariable UUID id) { service.cancel(id); }

    public record ReviewRequest(Review review) { }

    @PutMapping("/jobs/{jobId}/events/{eventId}")
    void review(@PathVariable UUID jobId, @PathVariable UUID eventId,
        @RequestBody ReviewRequest request)
    {
        service.review(jobId, eventId, request.review());
    }

    @GetMapping("/jobs/{id}/export")
    ResponseEntity<Map<String, Object>> export(@PathVariable UUID id)
    {
        JobView job = service.job(id);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=analysis-"
                + id + ".json")
            .body(Map.of("schemaVersion", 1, "analysis", job,
                "sampleFramesPerSecond", FfmpegVideo.SAMPLE_FPS,
                "notice", "Observed events require human review. "
                + "Proximity does not establish feeding or intent."));
    }
}
