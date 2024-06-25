package eu.esa.sar.io.iceye;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

import eu.esa.sar.commons.io.SARFileFilter;
import eu.esa.sar.commons.io.SARProductReaderPlugIn;
import org.esa.snap.core.dataio.DecodeQualification;
import org.esa.snap.core.dataio.ProductReader;
import org.esa.snap.core.util.io.SnapFileFilter;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;

public class IceyeProductReaderPlugIn implements SARProductReaderPlugIn {

    private final String PLUGIN_DESCRIPTION = "UNDER CONSTRUCTION -- Iceye Products";
    private final String[] FORMAT_NAMES = { "IceyeProduct" };
    private final String[] FILE_PREFIXES = { "ICEYE" };
    private final String[] FILE_EXTS = { ".tif", ".h5", ".xml", ".json" };
    private final String[] METADATA_EXTS = { ".xml", ".json" };
    private final Class<?>[] VALID_INPUT_TYPES = new Class[] { Path.class, File.class, String.class };

    @Override
    public Class<?>[] getInputTypes() {
        return VALID_INPUT_TYPES;
    }

    public DecodeQualification getDecodeQualification(Object input) {
        final Path path = ReaderUtils.getPathFromInput(input);

        if (path == null || path.getFileName() == null) {
            return DecodeQualification.UNABLE;
        }

        final String filename = path.getFileName().toString().toUpperCase();

        if (filename.startsWith(IceyeStacConstants.ICEYE_FILE_PREFIX)) {
            for (String ext : FILE_EXTS) {
                if (filename.endsWith(ext.toUpperCase())) {
                    return DecodeQualification.INTENDED;
                }
            }
        }

        return DecodeQualification.UNABLE;
    }

    public ProductReader createReaderInstance() {
        return new IceyeProductReader(this);
    }

    public String[] getProductMetadataFilePrefixes() {
        return FILE_PREFIXES;
    }

    public String[] getDefaultFileExtensions() {
        return FILE_EXTS;
    }

    public String[] getProductMetadataFileExtensions() {
        return METADATA_EXTS;
    }

    public String[] getFormatNames() {
        return FORMAT_NAMES;
    }

    public String getDescription(Locale locale) {
        return PLUGIN_DESCRIPTION;
    }

    @Override
    public SnapFileFilter getProductFileFilter() {
        return new SARFileFilter(this);
    }
}
