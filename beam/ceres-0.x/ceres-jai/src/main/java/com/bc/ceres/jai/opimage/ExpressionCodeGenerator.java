/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package com.bc.ceres.jai.opimage;

import com.bc.ceres.compiler.CodeMapper;

import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class ExpressionCodeGenerator {

    private static final String HEAD_COMMENT = "" +
            "/*\n" +
            " * This is machine-genereated code, DO NOT EDIT!\n" +
            " * Code generated by {0}.\n" +
            " */\n";

    private static final String HEAD_PART = "" +
            "package {0};\n" +
            "\n" +
            "import javax.media.jai.ImageLayout;\n" +
            "import javax.media.jai.PointOpImage;\n" +
            "import javax.media.jai.PixelAccessor;\n" +
            "import javax.media.jai.UnpackedImageData;\n" +
            "import java.awt.Rectangle;\n" +
            "import java.awt.image.Raster;\n" +
            "import java.awt.image.WritableRaster;\n" +
            "import java.util.Vector;\n" +
            "import java.util.Map;\n" +
            "import static java.lang.Math.*;\n" +
            "\n" +
            "public final class {1} extends PointOpImage '{'\n" +
            "\n" +
            "    public {1}(Vector sources, Map config, ImageLayout layout) '{'\n" +
            "        super(sources, layout, config, true);\n" +
            "        // Set flag to permit in-place operation.\n" +
            "        permitInPlaceOperation();\n" +
            "    '}'\n" +
            "\n" +
            "    protected void computeRect(Raster[] srcRasters,\n" +
            "                               WritableRaster destRaster,\n" +
            "                               Rectangle destRectangle) '{'\n" +
            "\n";
    private static final String SRC_DEF_PART = "" +
            "        final PixelAccessor src{0}Acc = new PixelAccessor(getSourceImage({0}));\n" +
            "        final UnpackedImageData src{0}Pixels = src{0}Acc.getPixels(srcRasters[{0}], destRectangle, srcRasters[{0}].getSampleModel().getDataType(), false);\n" +
            "        final int src{0}LineStride = src{0}Pixels.lineStride;\n" +
            "        final int src{0}PixelStride = src{0}Pixels.pixelStride;\n" +
            "        int src{0}LineOffset = src{0}Pixels.bandOffsets[0];\n" +
            "        final {1}[] src{0}Data = src{0}Pixels.get{2}Data(0);\n" +
            "\n";
    private static final String DST_DEF_PART = "" +
            "        final PixelAccessor destAcc = new PixelAccessor(this);\n" +
            "        final UnpackedImageData destPixels = destAcc.getPixels(destRaster, destRectangle, getSampleModel().getDataType(), true);\n" +
            "        final int destLineStride = destPixels.lineStride;\n" +
            "        final int destPixelStride = destPixels.pixelStride;\n" +
            "        int destLineOffset = destPixels.bandOffsets[0];\n" +
            "        final {1}[] destData = destPixels.get{2}Data(0);\n" +
            "\n";
    private static final String Y_LOOP_PART = "" +
            "        final int width = destRectangle.width;\n" +
            "        final int height = destRectangle.height;\n" +
            "\n" +
            "        for (int y = 0; y < height; y++) {\n" +
            "";
    private static final String SRC_OFFS_PART = "" +
            "            int src{0}PixelOffset = src{0}LineOffset;\n" +
            "            src{0}LineOffset += src{0}LineStride;\n" +
            "\n";
    private static final String X_LOOP_PART = "" +
            "            int destPixelOffset = destLineOffset;\n" +
            "            destLineOffset += destLineStride;\n" +
            "\n" +
            "            for (int x = 0; x < width; x++) {\n";
    private static final String EXPR_VAR_PART = "" +
            "                final {1} _{0} = src{0}Data[src{0}PixelOffset];\n";
    private static final String EXPR_PART = "" +
            "                destData[destPixelOffset] = ({0})({1});\n" +
            "\n";
    private static final String SRC_PIXEL_INC_PART = "" +
            "                src{0}PixelOffset += src{0}PixelStride;\n";
    private static final String FINAL_PART = "" +
            "                destPixelOffset += destPixelStride;\n" +
            "            } // next x\n" +
            "        } // next y\n" +
            "\n" +
            "        destAcc.setPixels(destPixels);\n" +
            "    }\n" +
            "}\n";


    public static ExpressionCode generate(String packageName,
                                          String className,
                                          Map<String, RenderedImage> sourceMap,
                                          int dataType,
                                          String expression) {
        MyNameMapper mapper = new MyNameMapper(sourceMap);
        CodeMapper.CodeMapping codeMapping = CodeMapper.mapCode(expression, mapper);

        StringBuilder codeBuilder = new StringBuilder();

        codeBuilder.append(MessageFormat.format(HEAD_COMMENT, ExpressionCodeGenerator.class.getName()));
        codeBuilder.append(MessageFormat.format(HEAD_PART, packageName, className));
        Vector<RenderedImage> sources = mapper.sources;
        for (int i = 0; i < sources.size(); i++) {
            String typeName = getTypeName(sources, i);
            codeBuilder.append(MessageFormat.format(SRC_DEF_PART,
                                                    String.valueOf(i),
                                                    typeName,
                                                    getCamelCase(typeName)));
        }
        String dstTypeName = getTypeName(dataType);
        codeBuilder.append(MessageFormat.format(DST_DEF_PART,
                                                String.valueOf(sources.size()),
                                                dstTypeName,
                                                getCamelCase(dstTypeName)));
        codeBuilder.append(Y_LOOP_PART);
        for (int i = 0; i < sources.size(); i++) {
            codeBuilder.append(MessageFormat.format(SRC_OFFS_PART, String.valueOf(i)));
        }
        codeBuilder.append(X_LOOP_PART);
        for (int i = 0; i < sources.size(); i++) {
            String typeName = getTypeName(sources, i);
            codeBuilder.append(MessageFormat.format(EXPR_VAR_PART, String.valueOf(i), typeName));
        }
        codeBuilder.append(MessageFormat.format(EXPR_PART, dstTypeName, codeMapping.getMappedCode()));
        for (int i = 0; i < sources.size(); i++) {
            codeBuilder.append(MessageFormat.format(SRC_PIXEL_INC_PART, String.valueOf(i)));
        }
        codeBuilder.append(FINAL_PART);

        return new ExpressionCode(packageName + "." + className, codeBuilder.toString(), sources);
    }

    private static String getTypeName(Vector<RenderedImage> sources, int i) {
        return getTypeName(sources.get(i).getSampleModel().getDataType());
    }

    static String getCamelCase(String typeName) {
        return Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
    }

    static String getTypeName(int type) {
        String typeName;
        if (type == DataBuffer.TYPE_BYTE) {
            typeName = "byte";
        } else if (type == DataBuffer.TYPE_SHORT) {
            typeName = "short";
        } else if (type == DataBuffer.TYPE_USHORT) {
            typeName = "short";
        } else if (type == DataBuffer.TYPE_INT) {
            typeName = "int";
        } else if (type == DataBuffer.TYPE_FLOAT) {
            typeName = "float";
        } else if (type == DataBuffer.TYPE_DOUBLE) {
            typeName = "double";
        } else {
            typeName = "double";
        }
        return typeName;
    }

    private static class MyNameMapper implements CodeMapper.NameMapper {
        private final Map<String, RenderedImage> imageMap;
        private final Vector<RenderedImage> sources = new Vector<RenderedImage>(16);
        private final Map<String, Integer> indexMap = new HashMap<String, Integer>(16);
        private int counter;

        public MyNameMapper(Map<String, RenderedImage> imageMap) {
            this.imageMap = imageMap;
        }

        public String mapName(String name) {
            boolean b = imageMap.containsKey(name);
            if (!b) {
                return null;
            }
            if (!indexMap.containsKey(name)) {
                indexMap.put(name, counter++);
                sources.add(imageMap.get(name));
            }
            RenderedImage image = imageMap.get(name);
            if (image.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT) {
                return MessageFormat.format("(_{0} & 0xffff)", indexMap.get(name));
            }
            return MessageFormat.format("_{0}", indexMap.get(name));
        }
    }
}
