package in.nishok.videoanalysis.app;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors
{
    @ExceptionHandler({IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception error)
    {
        return ResponseEntity.badRequest().body(Map.of("error",
            error instanceof IllegalArgumentException ? error.getMessage()
                : "Invalid request. Check the rule values and recording time."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(Exception error)
    {
        return ResponseEntity.status(404).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(Exception error)
    {
        return ResponseEntity.status(409).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge()
    {
        return ResponseEntity.status(413)
            .body(Map.of("error", "Maximum video size is 250 MB."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> failed(Exception error)
    {
        System.getLogger(ApiErrors.class.getName()).log(
            System.Logger.Level.WARNING, "Video request failed", error);
        return ResponseEntity.status(500).body(Map.of("error",
            "Video operation failed. Check FFmpeg, the video format, "
            + "available disk space and the application console."));
    }
}
