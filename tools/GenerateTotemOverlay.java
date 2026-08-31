import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Pure-JDK replacement for tools/generate_totem_overlay.py.
 * Generates the deterministic eight-frame flame overlay and its .png.mcmeta.
 *
 * <p>Usage: java tools/GenerateTotemOverlay.java &lt;output.png&gt;</p>
 */
public final class GenerateTotemOverlay {
    private static final int FRAME_SIZE = 32;
    private static final int FRAME_COUNT = 8;

    // The four original-texture right-eye pixels, stored as x/y pairs.
    private static final int[] RIGHT_EYE_PIXELS = {
            9, 5, 10, 5, 9, 6, 10, 6
    };

    // Flame roots are intentionally not used by the drawing code; they document
    // where each strand starts. Keep them beside the keyframes for parity with
    // the Python generator.
    private static final int[] FLAME_ROOTS = {
            19, 11, 21, 11, 19, 13, 21, 13
    };

    private static final int COLOR_DEEP_BLUE = packageRgb(8, 17, 126);
    private static final int COLOR_ELECTRIC_BLUE = packageRgb(16, 54, 255);
    private static final int COLOR_CYAN = packageRgb(0, 226, 255);
    private static final int COLOR_PALE_CYAN = packageRgb(205, 255, 250);
    private static final int COLOR_VIOLET = packageRgb(112, 20, 255);
    private static final int COLOR_MAGENTA = packageRgb(247, 0, 232);

    private static final int[] PULSE_COLORS = {
            COLOR_PALE_CYAN, COLOR_CYAN, COLOR_PALE_CYAN, COLOR_CYAN,
            COLOR_PALE_CYAN, COLOR_CYAN, COLOR_PALE_CYAN, COLOR_CYAN
    };

    /*
     * Four flame strands per keyframe. Coordinates are in the higher-resolution
     * 32x32 frame space, matching the Python generator exactly.
     */
    private static final int[][][][] FLAME_KEYFRAMES = {
            {
                    {{19, 11}, {20, 9}, {22, 8}, {23, 6}},
                    {{21, 11}, {22, 9}, {24, 8}, {25, 6}},
                    {{19, 13}, {20, 11}, {22, 10}, {24, 9}},
                    {{21, 13}, {22, 11}, {24, 10}, {26, 8}}
            },
            {
                    {{19, 11}, {21, 9}, {22, 7}, {25, 6}, {26, 3}},
                    {{21, 11}, {23, 9}, {25, 8}, {27, 6}, {29, 5}},
                    {{19, 13}, {21, 11}, {23, 10}, {25, 8}},
                    {{21, 13}, {23, 11}, {26, 10}, {28, 8}, {31, 7}}
            },
            {
                    {{19, 11}, {21, 9}, {23, 8}, {24, 5}, {27, 3}, {28, 0}},
                    {{21, 11}, {23, 10}, {26, 8}, {28, 6}, {31, 5}},
                    {{19, 13}, {21, 11}, {24, 10}, {26, 7}, {29, 6}},
                    {{21, 13}, {23, 11}, {25, 9}, {27, 5}, {30, 3}, {31, 1}}
            },
            {
                    {{19, 11}, {20, 9}, {23, 7}, {23, 4}, {26, 2}, {25, 0}},
                    {{21, 11}, {22, 9}, {25, 8}, {27, 5}, {30, 4}},
                    {{19, 13}, {21, 11}, {22, 8}, {25, 6}, {27, 3}},
                    {{21, 13}, {23, 11}, {26, 9}, {28, 6}, {31, 3}}
            },
            {
                    {{19, 11}, {22, 10}, {24, 8}, {27, 7}, {29, 4}, {31, 3}},
                    {{21, 11}, {23, 9}, {26, 7}, {28, 4}, {30, 1}},
                    {{19, 13}, {21, 11}, {24, 9}, {25, 6}},
                    {{21, 13}, {23, 11}, {26, 10}, {29, 8}, {31, 6}}
            },
            {
                    {{19, 11}, {20, 9}, {23, 8}, {25, 6}},
                    {{21, 11}, {23, 10}, {25, 8}, {26, 5}, {28, 3}},
                    {{19, 13}, {21, 11}, {23, 10}},
                    {{21, 13}, {23, 11}, {25, 10}, {27, 8}}
            },
            {
                    {{19, 11}, {21, 9}, {24, 8}, {25, 5}, {28, 3}, {30, 0}},
                    {{21, 11}, {23, 9}, {24, 6}, {27, 4}, {29, 2}},
                    {{19, 13}, {21, 11}, {24, 10}, {27, 8}, {30, 7}},
                    {{21, 13}, {24, 11}, {26, 9}, {29, 6}, {31, 4}}
            },
            {
                    {{19, 11}, {21, 9}, {23, 8}},
                    {{21, 11}, {23, 10}, {25, 7}},
                    {{19, 13}, {21, 11}, {23, 10}},
                    {{21, 13}, {23, 11}, {26, 9}}
            }
    };

    /*
     * Spark positions are deliberately discontinuous in every frame. Each
     * entry is x, y, packed rgb.
     */
    private static final int[][][] SPARK_KEYFRAMES = {
            {{26, 5, COLOR_VIOLET}, {28, 4, COLOR_ELECTRIC_BLUE}},
            {{28, 2, COLOR_VIOLET}, {31, 4, COLOR_MAGENTA}, {26, 1, COLOR_ELECTRIC_BLUE}},
            {{30, 0, COLOR_MAGENTA}, {31, 7, COLOR_VIOLET}, {27, 1, COLOR_ELECTRIC_BLUE}},
            {{28, 0, COLOR_VIOLET}, {31, 1, COLOR_MAGENTA}, {29, 7, COLOR_ELECTRIC_BLUE}},
            {{31, 0, COLOR_MAGENTA}, {30, 5, COLOR_VIOLET}, {27, 2, COLOR_ELECTRIC_BLUE}},
            {{30, 1, COLOR_VIOLET}, {31, 4, COLOR_MAGENTA}, {28, 6, COLOR_ELECTRIC_BLUE}},
            {{31, 2, COLOR_MAGENTA}, {29, 0, COLOR_VIOLET}, {31, 7, COLOR_ELECTRIC_BLUE}},
            {{27, 5, COLOR_ELECTRIC_BLUE}, {30, 2, COLOR_VIOLET}, {31, 0, COLOR_MAGENTA}}
    };

    private static final String MC_META = "{\n"
            + "  \"animation\": {\n"
            + "    \"frametime\": 2,\n"
            + "    \"interpolate\": false\n"
            + "  }\n"
            + "}\n";

    private GenerateTotemOverlay() {
    }

    public static void main(String[] args) throws Exception {
        // Avoid any accidental display/headless initialization in CI or servers.
        System.setProperty("java.awt.headless", "true");
        if (args.length != 1) {
            System.err.println("Usage: java tools/GenerateTotemOverlay.java <output.png>");
            System.exit(2);
        }
        generate(Path.of(args[0]));
    }

    public static void generate(Path output) throws IOException {
        BufferedImage overlay = new BufferedImage(
                FRAME_SIZE,
                FRAME_SIZE * FRAME_COUNT,
                BufferedImage.TYPE_INT_ARGB
        );

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            BufferedImage rendered = renderFrame(frame);
            int yOffset = frame * FRAME_SIZE;
            for (int y = 0; y < FRAME_SIZE; y++) {
                for (int x = 0; x < FRAME_SIZE; x++) {
                    overlay.setRGB(x, y + yOffset, rendered.getRGB(x, y));
                }
            }
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!ImageIO.write(overlay, "png", output.toFile())) {
            throw new IOException("No PNG writer is available for: " + output);
        }

        Files.writeString(
                Path.of(output.toString() + ".mcmeta"),
                MC_META,
                StandardCharsets.UTF_8
        );
    }

    private static BufferedImage renderFrame(int frame) {
        BufferedImage image = new BufferedImage(
                FRAME_SIZE,
                FRAME_SIZE,
                BufferedImage.TYPE_INT_ARGB
        );
        drawEyeRoots(image, frame);

        int[][][] strands = FLAME_KEYFRAMES[frame];
        for (int strandIndex = 0; strandIndex < strands.length; strandIndex++) {
            drawFlameStrand(image, strands[strandIndex], frame, strandIndex);
        }

        int[][] sparks = SPARK_KEYFRAMES[frame];
        for (int sparkIndex = 0; sparkIndex < sparks.length; sparkIndex++) {
            int x = sparks[sparkIndex][0];
            int y = sparks[sparkIndex][1];
            int color = sparks[sparkIndex][2];
            compositePixel(image, x, y, color, sparkIndex == 0 ? 230 : 178);
            if ((frame + sparkIndex) % 3 == 0) {
                compositePixel(image, x - 1, y + 1, COLOR_DEEP_BLUE, 92);
            }
        }
        return image;
    }

    private static void drawEyeRoots(BufferedImage image, int frame) {
        int pulse = PULSE_COLORS[frame];
        for (int cellIndex = 0; cellIndex < RIGHT_EYE_PIXELS.length / 2; cellIndex++) {
            int color = cellIndex == frame % (RIGHT_EYE_PIXELS.length / 2)
                    ? COLOR_PALE_CYAN
                    : pulse;
            int sourceX = RIGHT_EYE_PIXELS[cellIndex * 2];
            int sourceY = RIGHT_EYE_PIXELS[cellIndex * 2 + 1];
            for (int y = sourceY * 2; y < sourceY * 2 + 2; y++) {
                for (int x = sourceX * 2; x < sourceX * 2 + 2; x++) {
                    compositePixel(image, x, y, color, 238);
                }
            }
        }
    }

    private static void drawFlameStrand(
            BufferedImage image,
            int[][] vertices,
            int frame,
            int strandIndex
    ) {
        List<int[]> points = rasterizePolyline(vertices);
        int lastIndex = Math.max(1, points.size() - 1);

        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            double progress = (double) pointIndex / lastIndex;
            int outer = outerFlameColor(progress);
            int x = points.get(pointIndex)[0];
            int y = points.get(pointIndex)[1];

            for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                if ((pointIndex * 3 + directionIndex + frame + strandIndex) % 5 == 0) {
                    continue;
                }
                int offsetX = directionIndex == 0 ? 1
                        : directionIndex == 1 ? -1 : 0;
                int offsetY = directionIndex == 2 ? 1
                        : directionIndex == 3 ? -1 : 0;
                compositePixel(
                        image,
                        x + offsetX,
                        y + offsetY,
                        outer,
                        progress < 0.75 ? 112 : 156
                );
            }
        }

        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            double progress = (double) pointIndex / lastIndex;
            int core = coreFlameColor(progress);
            if (progress > 0.28
                    && progress < 0.82
                    && (pointIndex + frame * 2 + strandIndex) % 7 == 0) {
                continue;
            }
            int x = points.get(pointIndex)[0];
            int y = points.get(pointIndex)[1];
            compositePixel(
                    image,
                    x,
                    y,
                    core,
                    progress < 0.58 ? 250 : 224
            );
        }
    }

    private static List<int[]> rasterizePolyline(int[][] vertices) {
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i + 1 < vertices.length; i++) {
            List<int[]> segment = rasterizeLine(vertices[i], vertices[i + 1]);
            if (!points.isEmpty() && Arrays.equals(segment.get(0), points.get(points.size() - 1))) {
                segment.remove(0);
            }
            points.addAll(segment);
        }
        return points;
    }

    private static List<int[]> rasterizeLine(int[] start, int[] end) {
        int x = start[0];
        int y = start[1];
        int endX = end[0];
        int endY = end[1];
        int deltaX = Math.abs(endX - x);
        int deltaY = -Math.abs(endY - y);
        int stepX = x < endX ? 1 : -1;
        int stepY = y < endY ? 1 : -1;
        int error = deltaX + deltaY;
        List<int[]> points = new ArrayList<>();

        while (true) {
            points.add(new int[]{x, y});
            if (x == endX && y == endY) {
                return points;
            }
            int doubledError = 2 * error;
            if (doubledError >= deltaY) {
                error += deltaY;
                x += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    private static int outerFlameColor(double progress) {
        if (progress < 0.24) {
            return COLOR_ELECTRIC_BLUE;
        }
        if (progress < 0.55) {
            return COLOR_DEEP_BLUE;
        }
        if (progress < 0.80) {
            return COLOR_VIOLET;
        }
        return COLOR_MAGENTA;
    }

    private static int coreFlameColor(double progress) {
        if (progress < 0.24) {
            return COLOR_PALE_CYAN;
        }
        if (progress < 0.55) {
            return COLOR_CYAN;
        }
        if (progress < 0.80) {
            return COLOR_ELECTRIC_BLUE;
        }
        return COLOR_VIOLET;
    }

    private static void compositePixel(
            BufferedImage image,
            int x,
            int y,
            int packedRgb,
            int alpha
    ) {
        if (x < 0 || x >= FRAME_SIZE || y < 0 || y >= FRAME_SIZE || alpha <= 0) {
            return;
        }

        int old = image.getRGB(x, y);
        int oldRed = red(old);
        int oldGreen = green(old);
        int oldBlue = blue(old);
        int oldAlpha = alpha(old);

        double sourceAlpha = alpha / 255.0;
        double destinationAlpha = oldAlpha / 255.0;
        double resultAlpha = sourceAlpha + destinationAlpha * (1.0 - sourceAlpha);

        int sourceRed = red(packedRgb);
        int sourceGreen = green(packedRgb);
        int sourceBlue = blue(packedRgb);

        int newRed = roundHalfEven(
                (sourceRed * sourceAlpha
                        + oldRed * destinationAlpha * (1.0 - sourceAlpha))
                        / resultAlpha
        );
        int newGreen = roundHalfEven(
                (sourceGreen * sourceAlpha
                        + oldGreen * destinationAlpha * (1.0 - sourceAlpha))
                        / resultAlpha
        );
        int newBlue = roundHalfEven(
                (sourceBlue * sourceAlpha
                        + oldBlue * destinationAlpha * (1.0 - sourceAlpha))
                        / resultAlpha
        );
        int newAlpha = roundHalfEven(resultAlpha * 255.0);

        image.setRGB(
                x,
                y,
                (newAlpha << 24)
                        | (newRed << 16)
                        | (newGreen << 8)
                        | newBlue
        );
    }

    /*
     * Python's round() uses banker's rounding. Match that behavior so the Java
     * output is pixel-identical to the legacy Pillow output even at .5 ties.
     */
    private static int roundHalfEven(double value) {
        double floor = Math.floor(value);
        double fraction = value - floor;
        long floorLong = (long) floor;
        if (fraction > 0.5) {
            return (int) floorLong + 1;
        }
        if (fraction < 0.5) {
            return (int) floorLong;
        }
        return (floorLong & 1L) == 0L
                ? (int) floorLong
                : (int) floorLong + 1;
    }

    private static int packageRgb(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xff;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xff;
    }

    private static int blue(int argb) {
        return argb & 0xff;
    }
}
