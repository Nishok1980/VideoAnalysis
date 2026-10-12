package in.nishok.videoanalysis.detection;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import in.nishok.videoanalysis.domain.Model.Detection;

/** FP32 YOLOv10 COCO end-to-end export: RGB NCHW -> [1,N,6]. */
public final class YoloV10Detector implements Detector
{
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final String inputName;
    private final String fingerprint;

    public YoloV10Detector(Path model) throws Exception
    {
        if (!Files.isRegularFile(model))
        {
            throw new IllegalArgumentException(
                "Model missing. Run scripts/setup-model.ps1, then try again.");
        }
        var digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(model))
        {
            byte[] chunk = new byte[65536];
            int count;
            while ((count = stream.read(chunk)) != -1)
            {
                digest.update(chunk, 0, count);
            }
        }
        fingerprint = HexFormat.of().formatHex(digest.digest());
        try (var options = new OrtSession.SessionOptions())
        {
            options.setIntraOpNumThreads(2);
            session = environment.createSession(model.toString(), options);
        }
        try
        {
            if (session.getInputInfo().size() != 1)
            {
                throw new IllegalArgumentException("Expected one model input");
            }
            inputName = session.getInputNames().iterator().next();
            var input = (TensorInfo) session.getInputInfo()
                .get(inputName).getInfo();
            if (input.type != OnnxJavaType.FLOAT
                || !Arrays.equals(input.getShape(), new long[]{1, 3, 640, 640}))
            {
                throw new IllegalArgumentException(
                    "Expected FP32 model input [1,3,640,640]");
            }
            var output = (TensorInfo) session.getOutputInfo().values()
                .iterator().next().getInfo();
            var shape = output.getShape();
            if (output.type != OnnxJavaType.FLOAT || shape.length != 3
                || shape[0] != 1 || shape[2] != 6)
            {
                throw new IllegalArgumentException(
                    "Expected YOLOv10 end-to-end output [1,N,6]");
            }
        }
        catch (Exception failure)
        {
            session.close();
            throw failure;
        }
    }

    @Override
    public List<Detection> detect(byte[] rgb, int width, int height,
        double confidence) throws Exception
    {
        int pixels = 640 * 640;
        if (rgb.length != pixels * 3)
        {
            throw new IllegalArgumentException("Expected 640x640 RGB frame");
        }
        float[] tensor = new float[pixels * 3];
        for (int i = 0; i < pixels; i++)
        {
            tensor[i] = (rgb[3 * i] & 255) / 255f;
            tensor[pixels + i] = (rgb[3 * i + 1] & 255) / 255f;
            tensor[2 * pixels + i] = (rgb[3 * i + 2] & 255) / 255f;
        }
        try (var input = OnnxTensor.createTensor(environment,
                 FloatBuffer.wrap(tensor), new long[]{1, 3, 640, 640});
             var result = session.run(Map.of(inputName, input)))
        {
            float[][][] output = (float[][][]) result.get(0).getValue();
            return YoloOutput.decode(output[0], width, height, confidence);
        }
    }

    @Override public String fingerprint() { return fingerprint; }
    @Override public void close() throws Exception { session.close(); }
}
