/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.s1tbx.sar.gpf.geometric;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.util.FastMath;
import org.esa.s1tbx.insar.gpf.geometric.SARGeocoding;
import org.esa.s1tbx.insar.gpf.geometric.SARUtils;
import org.esa.snap.datamodel.AbstractMetadata;
import org.esa.snap.datamodel.OrbitStateVector;
import org.esa.snap.datamodel.PosVector;
import org.esa.snap.datamodel.Unit;
import org.esa.snap.dem.dataio.DEMFactory;
import org.esa.snap.dem.dataio.FileElevationModel;
import org.esa.snap.eo.Constants;
import org.esa.snap.eo.GeoUtils;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.GeoCoding;
import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.MetadataElement;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.datamodel.TiePointGrid;
import org.esa.snap.framework.dataop.dem.ElevationModel;
import org.esa.snap.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.snap.framework.dataop.dem.ElevationModelRegistry;
import org.esa.snap.framework.dataop.resamp.ResamplingFactory;
import org.esa.snap.framework.gpf.Operator;
import org.esa.snap.framework.gpf.OperatorException;
import org.esa.snap.framework.gpf.OperatorSpi;
import org.esa.snap.framework.gpf.Tile;
import org.esa.snap.framework.gpf.annotations.OperatorMetadata;
import org.esa.snap.framework.gpf.annotations.Parameter;
import org.esa.snap.framework.gpf.annotations.SourceProduct;
import org.esa.snap.framework.gpf.annotations.TargetProduct;
import org.esa.snap.gpf.InputProductValidator;
import org.esa.snap.gpf.OperatorUtils;
import org.esa.snap.gpf.ReaderUtils;
import org.esa.snap.gpf.TileIndex;
import org.esa.snap.util.Maths;
import org.esa.snap.util.ProductUtils;

import java.awt.Rectangle;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * This operator implements the terrain flattening algorithm proposed by
 * David Small. For details, see the paper below and the references therein.
 * David Small, "Flattening Gamma: Radiometric Terrain Correction for SAR imagery",
 * IEEE Transaction on Geoscience and Remote Sensing, Vol. 48, No. 8, August 2011.
 */

@OperatorMetadata(alias = "Terrain-Flattening",
        category = "Radar/Radiometric",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Terrain Flattening")
public final class TerrainFlatteningOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands",
            rasterDataNodeType = Band.class, label = "Source Bands")
    private String[] sourceBandNames;

    @Parameter(description = "The digital elevation model.",
            defaultValue = "SRTM 3Sec",
            label = "Digital Elevation Model")
    private String demName = "SRTM 3Sec";

    @Parameter(defaultValue = ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
            label = "DEM Resampling Method")
    private String demResamplingMethod = ResamplingFactory.BILINEAR_INTERPOLATION_NAME;

    @Parameter(label = "External DEM")
    private File externalDEMFile = null;

    @Parameter(label = "DEM No Data Value", defaultValue = "0")
    private double externalDEMNoDataValue = 0;

    @Parameter(defaultValue = "false", label = "Output Simulated Image")
    private boolean outputSimulatedImage = false;

    private ElevationModel dem = null;
    private FileElevationModel fileElevationModel = null;
    private TiePointGrid latitudeTPG = null;
    private TiePointGrid longitudeTPG = null;
    private GeoCoding targetGeoCoding = null;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private boolean srgrFlag = false;
    private boolean isElevationModelAvailable = false;

    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private double wavelength = 0.0; // in m
    private double demNoDataValue = 0; // no data value for DEM
    private SARGeocoding.Orbit orbit = null;

    private double noDataValue = 0;
    private double beta0 = 0;

    private int tileSize = 400;

    private OrbitStateVector[] orbitStateVectors = null;
    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private Band[] targetBands = null;
    protected final HashMap<Band, Band> targetBandToSourceBandMap = new HashMap<>(2);
    private boolean nearRangeOnLeft = true;
    private boolean skipBistaticCorrection = false;

    // set this flag to true to output terrain flattened sigma0
    private boolean outputSigma0 = false;

    enum UnitType {AMPLITUDE,INTENSITY,COMPLEX,RATIO}

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.snap.framework.datamodel.Product}
     * annotated with the {@link org.esa.snap.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.snap.framework.gpf.OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            final InputProductValidator validator = new InputProductValidator(sourceProduct);
            validator.checkIfMapProjected(false);

            if (!validator.isCalibrated(sourceProduct)) {
                throw new OperatorException("Source product should be calibrated to beta0");
            }

            getMetadata();

            getTiePointGrid();

            getSourceImageDimension();

            computeSensorPositionsAndVelocities();

            createTargetProduct();

            if (externalDEMFile == null) {
                DEMFactory.checkIfDEMInstalled(demName);
            }

            DEMFactory.validateDEM(demName, sourceProduct);

            noDataValue = sourceProduct.getBands()[0].getNoDataValue();

            beta0 = azimuthSpacing * rangeSpacing;

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    @Override
    public synchronized void dispose() {
        if (dem != null) {
            dem.dispose();
            dem = null;
        }
        if (fileElevationModel != null) {
            fileElevationModel.dispose();
        }
    }

    /**
     * Retrieve required data from Abstracted Metadata
     *
     * @throws Exception if metadata not found
     */
    private void getMetadata() throws Exception {

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
        wavelength = SARUtils.getRadarFrequency(absRoot);
        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
        firstLineUTC = AbstractMetadata.parseUTC(absRoot.getAttributeString(AbstractMetadata.first_line_time)).getMJD(); // in days
        lastLineUTC = AbstractMetadata.parseUTC(absRoot.getAttributeString(AbstractMetadata.last_line_time)).getMJD(); // in days
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / Constants.secondsInDay; // s to day
        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);

        if (srgrFlag) {
            srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
        } else {
            nearEdgeSlantRange = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.slant_range_to_first_pixel);
        }

        final String mission = RangeDopplerGeocodingOp.getMissionType(absRoot);
        final String pass = absRoot.getAttributeString("PASS");
        if (mission.equals("RS2") && pass.contains("DESCENDING")) {
            nearRangeOnLeft = false;
        }

        if (mission.contains("CSKS") || mission.contains("TSX") || mission.equals("RS2") || mission.contains("SENTINEL")) {
            skipBistaticCorrection = true;
        }
    }

    /**
     * Get source image width and height.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Compute sensor position and velocity for each range line.
     */
    private void computeSensorPositionsAndVelocities() {

        orbit = new SARGeocoding.Orbit(orbitStateVectors, firstLineUTC, lineTimeInterval, sourceImageHeight);
    }

    /**
     * Get incidence angle and slant range time tie point grids.
     */
    private void getTiePointGrid() {
        latitudeTPG = OperatorUtils.getLatitude(sourceProduct);
        if (latitudeTPG == null) {
            throw new OperatorException("Product without latitude tie point grid");
        }

        longitudeTPG = OperatorUtils.getLongitude(sourceProduct);
        if (longitudeTPG == null) {
            throw new OperatorException("Product without longitude tie point grid");
        }

    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceImageWidth,
                sourceImageHeight);

        addSelectedBands();

        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);

        if (externalDEMFile != null && fileElevationModel == null) { // if external DEM file is specified by user
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, externalDEMFile.getPath());
        } else {
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        }

        absTgt.setAttributeString("DEM resampling method", demResamplingMethod);
        absTgt.setAttributeInt(AbstractMetadata.abs_calibration_flag, 1);

        if (externalDEMFile != null) {
            absTgt.setAttributeDouble("external DEM no data value", externalDEMNoDataValue);
        }

        targetGeoCoding = targetProduct.getGeoCoding();

        // set the tile width to the image width to reduce tiling effect
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), tileSize);
    }

    /**
     * Add user selected bands to target product.
     */
    private void addSelectedBands() {

        final Band[] sourceBands = OperatorUtils.getSourceBands(sourceProduct, sourceBandNames, true);
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

        String gamma0BandName, sigma0BandName = null;
        String tgtUnit;
        for (final Band srcBand : sourceBands) {
            final String srcBandName = srcBand.getName();

            if(!srcBandName.startsWith("Beta0")) {      //beta0 or polsar product
                continue;
            }

            final String unit = srcBand.getUnit();
            if (unit == null) {
                throw new OperatorException("band " + srcBandName + " requires a unit");
            }

            if (unit.contains(Unit.DB)) {
                throw new OperatorException("Terrain flattening of bands in dB is not supported");
            } else if (unit.contains(Unit.PHASE)) {
                continue;
            } else if (unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY)) {
                gamma0BandName = "Gamma0_" + srcBandName;
                tgtUnit = unit;
                if (outputSigma0) {
                    sigma0BandName = "Sigma0_" + srcBandName;
                }
            } else { // amplitude or intensity
                final String pol = OperatorUtils.getBandPolarization(srcBandName, absRoot);
                gamma0BandName = "Gamma0";
                sigma0BandName = "Sigma0";
                if (pol != null && !pol.isEmpty()) {
                    gamma0BandName = "Gamma0_" + pol.toUpperCase();
                    sigma0BandName = "Sigma0_" + pol.toUpperCase();
                }
                tgtUnit = Unit.INTENSITY;
            }

            if (targetProduct.getBand(gamma0BandName) == null) {
                Band tgtBand = targetProduct.addBand(gamma0BandName, ProductData.TYPE_FLOAT32);
                tgtBand.setUnit(tgtUnit);
                targetBandToSourceBandMap.put(tgtBand, srcBand);
            }

            if (outputSigma0 && targetProduct.getBand(sigma0BandName) == null) {
                Band tgtBand = targetProduct.addBand(sigma0BandName, ProductData.TYPE_FLOAT32);
                tgtBand.setUnit(tgtUnit);
                targetBandToSourceBandMap.put(tgtBand, srcBand);
            }
        }

        if(targetProduct.getNumBands() == 0) {
            throw new OperatorException("TerrainFlattening requires beta0 or T3 as input");
        }

        if (outputSimulatedImage) {
            Band tgtBand = targetProduct.addBand("simulatedImage", ProductData.TYPE_FLOAT32);
            tgtBand.setUnit("Ratio");
        }

        targetBands = targetProduct.getBands();
        for(int i=0; i < targetBands.length; ++i) {
            if(targetBands[i].getUnit().equals(Unit.REAL)) {
                final String trgBandName = targetBands[i].getName();
                final int idx = trgBandName.indexOf("_");
                String suffix = "";
                if (idx != -1) {
                    suffix = trgBandName.substring(trgBandName.indexOf("_"));
                }
                ReaderUtils.createVirtualIntensityBand(
                        targetProduct, targetBands[i], targetBands[i + 1], "Gamma0", suffix);
            }
        }
    }


    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {

        try {
            if (!isElevationModelAvailable) {
                getElevationModel();
            }

            final int x0 = targetRectangle.x;
            final int y0 = targetRectangle.y;
            final int w = targetRectangle.width;
            final int h = targetRectangle.height;
            // System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            double[] tileOverlapPercentage = {0.0, 0.0};
            computeTileOverlapPercentage(x0, y0, w, h, tileOverlapPercentage);

            final double[][] gamma0ReferenceArea = new double[h][w];
            double[][] sigma0ReferenceArea = null;
            if (outputSigma0) {
                sigma0ReferenceArea = new double[h][w];
            }

            final boolean validSimulation = generateSimulatedImage(
                    x0, y0, w, h, tileOverlapPercentage, gamma0ReferenceArea, sigma0ReferenceArea);
            if (!validSimulation) {
                return;
            }

            outputNormalizedImage(x0, y0, w, h, gamma0ReferenceArea, sigma0ReferenceArea, targetTiles, targetRectangle);

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Generate simulated image for normalization.
     *
     * @param x0             X coordinate of the upper left corner pixel of given tile.
     * @param y0             Y coordinate of the upper left corner pixel of given tile.
     * @param w              Width of given tile.
     * @param h              Height of given tile.
     * @param gamma0ReferenceArea The simulated image for flattened gamma0 generation.
     * @param sigma0ReferenceArea The simulated image for flattened sigma0 generation.
     * @return Boolean flag indicating if the simulation is successful.
     */
    private boolean generateSimulatedImage(final int x0, final int y0, final int w, final int h,
                                           final double[] tileOverlapPercentage,
                                           final double[][] gamma0ReferenceArea,
                                           final double[][] sigma0ReferenceArea) {

        try {
            final int ymin = Math.max(y0 - (int) (tileSize * tileOverlapPercentage[1]), 0);
            final int ymax = y0 + h + (int) (tileSize * Math.abs(tileOverlapPercentage[0]));
            final int xmax = x0 + w;

            final TerrainData terrainData = new TerrainData(w, ymax - ymin);
            final boolean valid = getLocalDEM(x0, ymin, w, ymax - ymin, terrainData);
            if (!valid) {
                return false;
            }

            final PosVector earthPoint = new PosVector();
            final PosVector sensorPos = new PosVector();
            for (int y = ymin; y < ymax; y++) {

                final double[] azimuthIndex = new double[w];
                final double[] rangeIndex = new double[w];
                final double[] gamma0Area = new double[w];
                final double[] elevationAngle = new double[w];
                final boolean[] savePixel = new boolean[w];
                double[] sigma0Area = null;
                if (outputSigma0) {
                    sigma0Area = new double[w];
                }

                for (int x = x0; x < xmax; x++) {
                    final int i = x - x0;
                    final int xx = x - x0 + 1;
                    final int yy = y - ymin + 1;

                    final double alt = terrainData.localDEM[yy][xx];
                    if (alt == demNoDataValue) {
                        savePixel[i] = false;
                        continue;
                    }

                    GeoUtils.geo2xyzWGS84(terrainData.latPixels[yy][xx], terrainData.lonPixels[yy][xx], alt, earthPoint);

                    double zeroDopplerTime = SARGeocoding.getEarthPointZeroDopplerTime(
                            firstLineUTC, lineTimeInterval, wavelength, earthPoint,
                            orbit.sensorPosition, orbit.sensorVelocity);

                    double slantRange = SARGeocoding.computeSlantRange(zeroDopplerTime, orbit, earthPoint, sensorPos);

                    if(!skipBistaticCorrection) {
                        // skip bistatic correction for COSMO, TerraSAR-X and RadarSAT-2 and S-1
                        zeroDopplerTime += slantRange / Constants.lightSpeedInMetersPerDay;
                        slantRange = SARGeocoding.computeSlantRange(
                                zeroDopplerTime, orbit, earthPoint, sensorPos);
                    }

                    azimuthIndex[i] = (zeroDopplerTime - firstLineUTC) / lineTimeInterval;

                    rangeIndex[i] = SARGeocoding.computeRangeIndex(
                            srgrFlag, sourceImageWidth, firstLineUTC, lastLineUTC, rangeSpacing,
                            zeroDopplerTime, slantRange, nearEdgeSlantRange, srgrConvParams);

                    if (rangeIndex[i] <= 0.0) {
                        continue;
                    }

                    if (!nearRangeOnLeft) {
                        rangeIndex[i] = sourceImageWidth - 1 - rangeIndex[i];
                    }

                    final LocalGeometry localGeometry = new LocalGeometry(earthPoint, sensorPos, terrainData, xx, yy);

                    gamma0Area[i] = computeGamma0Area(localGeometry, demNoDataValue);
                    if (gamma0Area[i] == noDataValue) {
                        savePixel[i] = false;
                        continue;
                    }

                    if (outputSigma0) {
                        sigma0Area[i] = computeSigma0Area(localGeometry, demNoDataValue);
                    }

                    elevationAngle[i] = computeElevationAngle(slantRange, earthPoint, sensorPos);

                    savePixel[i] = rangeIndex[i] >= x0 && rangeIndex[i] < x0 + w &&
                            azimuthIndex[i] > y0 - 1 && azimuthIndex[i] < y0 + h;
                }

                if (nearRangeOnLeft) {
                    // traverse from near range to far range to detect shadowing area
                    double maxElevAngle = 0.0;
                    for (int x = x0; x < x0 + w; x++) {
                        int i = x - x0;
                        if (savePixel[i] && elevationAngle[i] > maxElevAngle) {
                            maxElevAngle = elevationAngle[i];
                            saveGamma0Area(x0, y0, w, h, gamma0Area[i], azimuthIndex[i], rangeIndex[i],
                                    gamma0ReferenceArea);

                            if (outputSigma0) {
                                saveSigma0Area(x0, y0, w, h, sigma0Area[i], azimuthIndex[i], rangeIndex[i],
                                        sigma0ReferenceArea);
                            }
                        }
                    }

                } else {
                    // traverse from near range to far range to detect shadowing area
                    double maxElevAngle = 0.0;
                    for (int x = x0 + w - 1; x >= x0; x--) {
                        int i = x - x0;
                        if (savePixel[i] && elevationAngle[i] > maxElevAngle) {
                            maxElevAngle = elevationAngle[i];
                            saveGamma0Area(x0, y0, w, h, gamma0Area[i], azimuthIndex[i], rangeIndex[i],
                                    gamma0ReferenceArea);

                            if (outputSigma0) {
                                saveSigma0Area(x0, y0, w, h, sigma0Area[i], azimuthIndex[i], rangeIndex[i],
                                        sigma0ReferenceArea);
                            }
                        }
                    }
                }
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
        return true;
    }

    /**
     * Output normalized image.
     *
     * @param x0              X coordinate of the upper left corner pixel of given tile.
     * @param y0              Y coordinate of the upper left corner pixel of given tile.
     * @param w               Width of given tile.
     * @param h               Height of given tile.
     * @param gamma0ReferenceArea The simulated image for flattened gamma0 generation.
     * @param sigma0ReferenceArea The simulated image for flattened sigma0 generation.
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed.
     */
    private void outputNormalizedImage(final int x0, final int y0, final int w, final int h,
                                       final double[][] gamma0ReferenceArea, final double[][] sigma0ReferenceArea,
                                       final Map<Band, Tile> targetTiles, final Rectangle targetRectangle) {

        for (Band tgtBand:targetBands) {
            final Tile targetTile = targetTiles.get(tgtBand);
            final ProductData targetData = targetTile.getDataBuffer();
            final TileIndex trgIndex = new TileIndex(targetTile);
            final String unit = tgtBand.getUnit();
            final String bandName = tgtBand.getName();

            Band srcBand = null;
            Tile sourceTile = null;
            ProductData sourceData = null;
            if (bandName.contains("Gamma0") || bandName.contains("Sigma0")) {
                srcBand = targetBandToSourceBandMap.get(tgtBand);
                sourceTile = getSourceTile(srcBand, targetRectangle);
                sourceData = sourceTile.getDataBuffer();
            }

            double[][] simulatedImage = gamma0ReferenceArea;
            if (bandName.contains("Sigma0")) {
                simulatedImage = sigma0ReferenceArea;
            }

            UnitType unitType = UnitType.AMPLITUDE;
            if (unit.contains(Unit.AMPLITUDE)) {
                unitType = UnitType.AMPLITUDE;
            } else if (unit.contains(Unit.INTENSITY)) {
                unitType = UnitType.INTENSITY;
            } else if (unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY)) {
                unitType = UnitType.COMPLEX;
            } else if (unit.contains("Ratio")) {
                unitType = UnitType.RATIO;
            }

            double v, simVal;
            for (int y = y0; y < y0 + h; y++) {
                final int yy = y - y0;
                trgIndex.calculateStride(y);
                for (int x = x0; x < x0 + w; x++) {
                    final int xx = x - x0;
                    final int idx = trgIndex.getIndex(x);
                    simVal = simulatedImage[yy][xx];

                    if (simVal != noDataValue && simVal != 0.0) {

                        switch (unitType) {
                            case AMPLITUDE :
                                v = sourceData.getElemDoubleAt(idx);
                                targetData.setElemDoubleAt(idx, v*v / simVal);
                                break;
                            case INTENSITY:
                                v = sourceData.getElemDoubleAt(idx);
                                targetData.setElemDoubleAt(idx, v / simVal);
                                break;
                            case COMPLEX:
                                v = sourceData.getElemDoubleAt(idx);
                                targetData.setElemDoubleAt(idx, v / Math.sqrt(simVal));
                                break;
                            case RATIO:
                                targetData.setElemDoubleAt(idx, simVal);
                                break;
                        }

                    } else {
                        targetData.setElemDoubleAt(idx, noDataValue);
                    }

                }
            }
        }
    }

    /**
     * Get elevation model.
     *
     * @throws Exception The exceptions.
     */
    private synchronized void getElevationModel() throws Exception {

        if (isElevationModelAvailable) {
            return;
        }

        if (externalDEMFile != null && fileElevationModel == null) { // if external DEM file is specified by user

            fileElevationModel = new FileElevationModel(externalDEMFile,
                    ResamplingFactory.createResampling(demResamplingMethod), externalDEMNoDataValue);

            demNoDataValue = externalDEMNoDataValue;
            demName = externalDEMFile.getPath();

        } else {

            final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
            final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
            if (demDescriptor == null) {
                throw new OperatorException("The DEM '" + demName + "' is not supported.");
            }

            dem = demDescriptor.createDem(ResamplingFactory.createResampling(demResamplingMethod));
            if (dem == null) {
                throw new OperatorException("The DEM '" + demName + "' has not been installed.");
            }

            demNoDataValue = dem.getDescriptor().getNoDataValue();
        }
        isElevationModelAvailable = true;
    }

    private void computeTileOverlapPercentage(final int x0, final int y0, final int w, final int h,
                                              double[] overlapPercentages)
            throws Exception {

        final PixelPos pixPos = new PixelPos();
        final GeoPos geoPos = new GeoPos();
        final PosVector earthPoint = new PosVector();
        final PosVector sensorPos = new PosVector();
        double tileOverlapPercentageMax = -Double.MAX_VALUE;
        double tileOverlapPercentageMin = Double.MAX_VALUE;
        for (int y = y0; y < y0 + h; y += 20) {
            for (int x = x0; x < x0 + w; x += 20) {
                pixPos.setLocation(x, y);
                targetGeoCoding.getGeoPos(pixPos, geoPos);
                final double alt = dem.getElevation(geoPos);
                GeoUtils.geo2xyzWGS84(geoPos.getLat(), geoPos.getLon(), alt, earthPoint);

                final double zeroDopplerTime = SARGeocoding.getEarthPointZeroDopplerTime(
                        firstLineUTC, lineTimeInterval, wavelength, earthPoint, orbit.sensorPosition, orbit.sensorVelocity);

                if (zeroDopplerTime == SARGeocoding.NonValidZeroDopplerTime) {
                    continue;
                }

                final double slantRange = SARGeocoding.computeSlantRange(zeroDopplerTime, orbit, earthPoint, sensorPos);

                final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / Constants.lightSpeedInMetersPerDay;

                final int azimuthIndex = (int) ((zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval + 0.5);

                double tileOverlapPercentage = (azimuthIndex - y) / (double) tileSize;

                if (tileOverlapPercentage > tileOverlapPercentageMax) {
                    tileOverlapPercentageMax = tileOverlapPercentage;
                }
                if (tileOverlapPercentage < tileOverlapPercentageMin) {
                    tileOverlapPercentageMin = tileOverlapPercentage;
                }
            }
        }

        if (tileOverlapPercentageMin != Double.MAX_VALUE && tileOverlapPercentageMin < 0.0) {
            overlapPercentages[0] = tileOverlapPercentageMin - 1.0;
        } else {
            overlapPercentages[0] = 0.0;
        }

        if (tileOverlapPercentageMax != -Double.MAX_VALUE && tileOverlapPercentageMax > 0.0) {
            overlapPercentages[1] = tileOverlapPercentageMax + 1.0;
        } else {
            overlapPercentages[1] = 0.0;
        }
    }

    /**
     * Read DEM for current tile.
     *
     * @param x0          The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0          The y coordinate of the pixel at the upper left corner of current tile.
     * @param tileHeight  The tile height.
     * @param tileWidth   The tile width.
     * @param terrainData The DEM for the tile.
     * @return false if all values are no data
     * @throws Exception from dem
     */
    private boolean getLocalDEM(final int x0, final int y0, final int tileWidth, final int tileHeight,
                                final TerrainData terrainData) throws Exception {

        // Note: the localDEM covers current tile with 1 extra row above, 1 extra row below, 1 extra column to
        //       the left and 1 extra column to the right of the tile.
        final GeoPos geoPos = new GeoPos();
        final int maxY = y0 + tileHeight + 1;
        final int maxX = x0 + tileWidth + 1;
        double alt;
        boolean valid = false;
        for (int y = y0 - 1; y < maxY; y++) {
            final int yy = y - y0 + 1;
            for (int x = x0 - 1; x < maxX; x++) {
                final int xx = x - x0 + 1;

                final double lat = latitudeTPG.getPixelDouble(x + 0.5f, y + 0.5f);
                final double lon = longitudeTPG.getPixelDouble(x + 0.5f, y + 0.5f);
                geoPos.setLocation(lat, lon);

                if (externalDEMFile == null) {
                    alt = dem.getElevation(geoPos);
                } else {
                    alt = fileElevationModel.getElevation(geoPos);
                }

                terrainData.localDEM[yy][xx] = alt;
                terrainData.latPixels[yy][xx] = lat;
                terrainData.lonPixels[yy][xx] = lon;

                if (alt != demNoDataValue)
                    valid = true;
            }
        }
        if (fileElevationModel != null) {
            //fileElevationModel.clearCache();
        }

        return valid;
    }

    /**
     * Distribute the local illumination area to the 4 adjacent pixels using bi-linear distribution.
     *
     * @param x0              The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0              The y coordinate of the pixel at the upper left corner of current tile.
     * @param w               The tile width.
     * @param h               The tile height.
     * @param gamma0Area      The illuminated area.
     * @param azimuthIndex    Azimuth pixel index for the illuminated area.
     * @param rangeIndex      Range pixel index for the illuminated area.
     * @param gamma0ReferenceArea  Buffer for the simulated image.
     */
    private void saveGamma0Area(final int x0, final int y0, final int w, final int h, final double gamma0Area,
                                final double azimuthIndex, final double rangeIndex,
                                final double[][] gamma0ReferenceArea) {

        final int ia0 = (int) azimuthIndex;
        final int ia1 = ia0 + 1;
        final int ir0 = (int) rangeIndex;
        final int ir1 = ir0 + 1;

        final double wr = rangeIndex - ir0;
        final double wa = azimuthIndex - ia0;
        final double wac = 1 - wa;

        if (ir0 >= x0) {
            final double wrc = 1 - wr;
            if (ia0 >= y0)
                gamma0ReferenceArea[ia0 - y0][ir0 - x0] += wrc * wac * gamma0Area / beta0;

            if (ia1 < y0 + h)
                gamma0ReferenceArea[ia1 - y0][ir0 - x0] += wrc * wa * gamma0Area / beta0;
        }
        if (ir1 < x0 + w) {
            if (ia0 >= y0)
                gamma0ReferenceArea[ia0 - y0][ir1 - x0] += wr * wac * gamma0Area / beta0;

            if (ia1 < y0 + h)
                gamma0ReferenceArea[ia1 - y0][ir1 - x0] += wr * wa * gamma0Area / beta0;
        }
    }

    private void saveSigma0Area(final int x0, final int y0, final int w, final int h, final double sigma0Area,
                                final double azimuthIndex, final double rangeIndex,
                                final double[][] sigma0ReferenceArea) {

        final int ia0 = (int) azimuthIndex;
        final int ia1 = ia0 + 1;
        final int ir0 = (int) rangeIndex;
        final int ir1 = ir0 + 1;

        final double wr = rangeIndex - ir0;
        final double wa = azimuthIndex - ia0;
        final double wac = 1 - wa;

        if (ir0 >= x0) {
            final double wrc = 1 - wr;
            if (ia0 >= y0)
                sigma0ReferenceArea[ia0 - y0][ir0 - x0] += wrc * wac * sigma0Area / beta0;

            if (ia1 < y0 + h)
                sigma0ReferenceArea[ia1 - y0][ir0 - x0] += wrc * wa * sigma0Area / beta0;
        }
        if (ir1 < x0 + w) {
            if (ia0 >= y0)
                sigma0ReferenceArea[ia0 - y0][ir1 - x0] += wr * wac * sigma0Area / beta0;

            if (ia1 < y0 + h)
                sigma0ReferenceArea[ia1 - y0][ir1 - x0] += wr * wa * sigma0Area / beta0;
        }
    }

    /**
     * Compute elevation angle (in degree).
     *
     * @param slantRange The slant range.
     * @param earthPoint The coordinate for target on earth surface.
     * @param sensorPos  The coordinate for satellite position.
     * @return The elevation angle in degree.
     */
    private static double computeElevationAngle(
            final double slantRange, final PosVector earthPoint, final PosVector sensorPos) {

        final double H2 = sensorPos.x * sensorPos.x + sensorPos.y * sensorPos.y + sensorPos.z * sensorPos.z;
        final double R2 = earthPoint.x * earthPoint.x + earthPoint.y * earthPoint.y + earthPoint.z * earthPoint.z;

        return FastMath.acos((slantRange * slantRange + H2 - R2) / (2 * slantRange * Math.sqrt(H2))) * Constants.RTOD;
    }

    /**
     * Compute local illuminated area for given point.
     *
     * @param lg             Local geometry information.
     * @param demNoDataValue Invalid DEM value.
     * @return The computed local illuminated area.
     */
    private double computeGamma0Area(final LocalGeometry lg, final double demNoDataValue) {

        if (lg.t00Height == demNoDataValue || lg.t01Height == demNoDataValue ||
                lg.t10Height == demNoDataValue || lg.t11Height == demNoDataValue) {
            return noDataValue;
        }

        final PosVector t00 = new PosVector();
        final PosVector t01 = new PosVector();
        final PosVector t10 = new PosVector();
        final PosVector t11 = new PosVector();

        GeoUtils.geo2xyzWGS84(lg.t00Lat, lg.t00Lon, lg.t00Height, t00);
        GeoUtils.geo2xyzWGS84(lg.t01Lat, lg.t01Lon, lg.t01Height, t01);
        GeoUtils.geo2xyzWGS84(lg.t10Lat, lg.t10Lon, lg.t10Height, t10);
        GeoUtils.geo2xyzWGS84(lg.t11Lat, lg.t11Lon, lg.t11Height, t11);

        // compute slant range direction
        final PosVector s = new PosVector(
                lg.sensorPos.x - lg.centerPoint.x,
                lg.sensorPos.y - lg.centerPoint.y,
                lg.sensorPos.z - lg.centerPoint.z);

        Maths.normalizeVector(s);

        // project points t00, t01, t10 and t11 to the plane that perpendicular to slant range
        final double t00s = Maths.innerProduct(t00, s);
        final double t01s = Maths.innerProduct(t01, s);
        final double t10s = Maths.innerProduct(t10, s);
        final double t11s = Maths.innerProduct(t11, s);

        final double[] p00 = {t00.x - t00s * s.x, t00.y - t00s * s.y, t00.z - t00s * s.z};
        final double[] p01 = {t01.x - t01s * s.x, t01.y - t01s * s.y, t01.z - t01s * s.z};
        final double[] p10 = {t10.x - t10s * s.x, t10.y - t10s * s.y, t10.z - t10s * s.z};
        final double[] p11 = {t11.x - t11s * s.x, t11.y - t11s * s.y, t11.z - t11s * s.z};

        // compute distances between projected points
        final double p00p01 = distance(p00, p01);
        final double p00p10 = distance(p00, p10);
        final double p11p01 = distance(p11, p01);
        final double p11p10 = distance(p11, p10);
        final double p10p01 = distance(p10, p01);

        // compute semi-perimeters of two triangles: p00-p01-p10 and p11-p01-p10
        final double h1 = 0.5 * (p00p01 + p00p10 + p10p01);
        final double h2 = 0.5 * (p11p01 + p11p10 + p10p01);

        // compute the illuminated area
        return Math.sqrt(h1 * (h1 - p00p01) * (h1 - p00p10) * (h1 - p10p01)) +
                Math.sqrt(h2 * (h2 - p11p01) * (h2 - p11p10) * (h2 - p10p01));
    }

    private double computeSigma0Area(final LocalGeometry lg, final double demNoDataValue) {

        if (lg.t00Height == demNoDataValue || lg.t01Height == demNoDataValue ||
                lg.t10Height == demNoDataValue || lg.t11Height == demNoDataValue) {
            return noDataValue;
        }

        final PosVector t00 = new PosVector();
        final PosVector t01 = new PosVector();
        final PosVector t10 = new PosVector();
        final PosVector t11 = new PosVector();

        GeoUtils.geo2xyzWGS84(lg.t00Lat, lg.t00Lon, lg.t00Height, t00);
        GeoUtils.geo2xyzWGS84(lg.t01Lat, lg.t01Lon, lg.t01Height, t01);
        GeoUtils.geo2xyzWGS84(lg.t10Lat, lg.t10Lon, lg.t10Height, t10);
        GeoUtils.geo2xyzWGS84(lg.t11Lat, lg.t11Lon, lg.t11Height, t11);

        final double[] T00 = {t00.x, t00.y, t00.z};
        final double[] T01 = {t01.x, t01.y, t01.z};
        final double[] T10 = {t10.x, t10.y, t10.z};
        final double[] T11 = {t11.x, t11.y, t11.z};

        // compute distances between projected points
        final double T00T01 = distance(T00, T01);
        final double T00T10 = distance(T00, T10);
        final double T11T01 = distance(T11, T01);
        final double T11T10 = distance(T11, T10);
        final double T10T01 = distance(T10, T01);

        // compute semi-perimeters of two triangles: T00-T01-T10 and T11-T01-T10
        final double h1 = 0.5 * (T00T01 + T00T10 + T10T01);
        final double h2 = 0.5 * (T11T01 + T11T10 + T10T01);

        // compute the illuminated area
        return Math.sqrt(h1 * (h1 - T00T01) * (h1 - T00T10) * (h1 - T10T01)) +
                Math.sqrt(h2 * (h2 - T11T01) * (h2 - T11T10) * (h2 - T10T01));
    }


    private static double distance(final double[] p1, final double[] p2) {
        return Math.sqrt((p1[0] - p2[0]) * (p1[0] - p2[0]) +
                (p1[1] - p2[1]) * (p1[1] - p2[1]) +
                (p1[2] - p2[2]) * (p1[2] - p2[2]));
    }


    public static class LocalGeometry {
        public final double t00Lat;
        public final double t00Lon;
        public final double t00Height;
        public final double t01Lat;
        public final double t01Lon;
        public final double t01Height;
        public final double t10Lat;
        public final double t10Lon;
        public final double t10Height;
        public final double t11Lat;
        public final double t11Lon;
        public final double t11Height;
        public final PosVector sensorPos;
        public final PosVector centerPoint;

        public LocalGeometry(final PosVector earthPoint, final PosVector sensPos,
                             final TerrainData terrainData, final int xx, final int yy) {

            t00Lat = terrainData.latPixels[yy][xx];
            t00Lon = terrainData.lonPixels[yy][xx];
            t00Height = terrainData.localDEM[yy][xx];
            t01Lat = terrainData.latPixels[yy - 1][xx];
            t01Lon = terrainData.lonPixels[yy - 1][xx];
            t01Height = terrainData.localDEM[yy - 1][xx];
            t10Lat = terrainData.latPixels[yy][xx + 1];
            t10Lon = terrainData.lonPixels[yy][xx + 1];
            t10Height = terrainData.localDEM[yy][xx + 1];
            t11Lat = terrainData.latPixels[yy - 1][xx + 1];
            t11Lon = terrainData.lonPixels[yy - 1][xx + 1];
            t11Height = terrainData.localDEM[yy - 1][xx + 1];
            centerPoint = earthPoint;
            sensorPos = sensPos;
        }
    }

    private static class TerrainData {
        final double[][] localDEM;
        final double[][] latPixels;
        final double[][] lonPixels;

        public TerrainData(int w, int h) {
            localDEM = new double[h + 2][w + 2];
            latPixels = new double[h + 2][w + 2];
            lonPixels = new double[h + 2][w + 2];
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see org.esa.snap.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.snap.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TerrainFlatteningOp.class);
        }
    }
}
