package eu.esa.sar.io.iceye;

import java.awt.image.DataBuffer;

import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.datamodel.Product;
import eu.esa.sar.commons.io.SARReader;

public class IceyeAMLProductReader extends IceyeAMLCPXProductReader {

    public IceyeAMLProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    void addProductSpecificMetadata(MetadataElement absRoot) {
        // addMetaLong(absRoot, AbstractMetadata.azimuth_looks, IceyeStacConstants.azimuth_looks);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks, 4);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks, IceyeStacConstants.RANGE_LOOKS_DEFAULT_VALUE);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, IceyeStacConstants.DETECTED);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.multilook_flag, 1);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, IceyeStacConstants.SRGR_FLAG_AML_DEFAULT_VALUE);
        addSRGRCoefficients(absRoot);
    }

    private void addSRGRCoefficients(MetadataElement absRoot) {
        try {
            MetadataElement srgr = absRoot.getElement(AbstractMetadata.srgr_coefficients);
            MetadataElement list = new MetadataElement(AbstractMetadata.srgr_coef_list + ".1");
            srgr.addElement(list);

            AbstractMetadata.addAbstractedAttribute(list, AbstractMetadata.ground_range_origin,
                    ProductData.TYPE_FLOAT64, "m", "Ground Range Origin");
            AbstractMetadata.setAttribute(list, AbstractMetadata.ground_range_origin, 0.0);

            double[] coeffs = getDoublesFromJSON(IceyeStacConstants.grsr_coefficients);
            for (int i = 0; i < coeffs.length; i++) {
                MetadataElement coefElem = new MetadataElement(AbstractMetadata.coefficient + '.' + i);
                list.addElement(coefElem);
                AbstractMetadata.addAbstractedAttribute(coefElem, AbstractMetadata.srgr_coef,
                        ProductData.TYPE_FLOAT64, "", "SRGR Coefficient");
                AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, (double) coeffs[i]);
            }
            ProductData.UTC zero_doppler_time = parseUTC((String) getFromJSON(IceyeStacConstants.zero_doppler_time_utc));
            // ProductData.UTC zero_doppler_time = parseUTC((String) getFromJSON(IceyeStacConstants.grsr_zero_doppler_time));
            list.setAttributeUTC(AbstractMetadata.srgr_coef_time, zero_doppler_time);
            // list.setAttributeUTC(AbstractMetadata.srgr_coef_time, parseUTC("2023-10-12T06:59:54.124993Z"));
        } catch (Exception e) {
            SystemUtils.LOG.severe("Unable to parse SRGR coefficients: " + e.getMessage());
        }
    }

    void addProductSpecificBands(Product product, String polarization) {
        final Band ampBand = product.getBand(IceyeStacConstants.amplitude_band_prefix + polarization);
        SARReader.createVirtualIntensityBand(product, ampBand, '_' + polarization);
    }

    float[] getSlantRangeTimeList(int gridWidth, int gridHeight, int subSamplingX) {
        float[] slantRangeTimeList = new float[gridWidth * gridHeight];
        double[] coeffs = getDoublesFromJSON(IceyeStacConstants.grsr_coefficients);
        Double rangeSpacing;
        Object value = getFromJSON(IceyeStacConstants.range_spacing);
            if (value instanceof Long) {
                rangeSpacing = ((Long) value).doubleValue();
            } else {
                rangeSpacing = (Double) value;
            }
        // double rangeSpacing = (double) getFromJSON(IceyeStacConstants.range_spacing);
        for (int i = 0; i < gridHeight; i++) {
            for (int j = 0; j < gridWidth; j++) {
                double groundRangeDist = rangeSpacing * subSamplingX * j;
                double slantRangeDist = applyPolynomial(coeffs, groundRangeDist);
                slantRangeTimeList[i * gridWidth
                        + j] = (float) (slantRangeDist / Constants.halfLightSpeed * Constants.sTOns);
            }
        }
        return slantRangeTimeList;
    }

    float getRasterValue(int srcIndex, int bandIndex, DataBuffer dataBuffer) {
        return dataBuffer.getElemFloat(srcIndex);
    }
}
