package eu.esa.sar.io.iceye;

import java.text.DateFormat;

import org.esa.snap.core.datamodel.ProductData.UTC;

public class IceyeStacConstants {

    static final String ACQUISITION_MODE = "ACQUISITION_MODE";
    static final String COMPLEX = "COMPLEX";
    static final String DETECTED = "DETECTED";
    static final String GDALMETADATA = "<GDALMetadata";
    static final String geo_ref_system_default = "WGS84";
    static final String ground = "ground";
    static final String ICEYE_FILE_PREFIX = "ICEYE";
    static final String left = "left";
    // static final String METADATA_JSON = "METADATA_JSON";
    static final String ICEYE_PROPERTIES = "ICEYE_PROPERTIES";
    static final String PRODUCT_NAME = "PRODUCT_NAME";
    static final String PRODUCT_TYPE = "PRODUCT_TYPE";
    static final String ProductMetadata = "productMetadata";
    static final String spot = "spot";
    static final String spotlight = "spotlight";
    static final String strip = "strip";
    static final String stripmap = "stripmap";
    static final String time = "time";
    static final String coeffs = "coeffs";
    static final String qlk_png = "qlk.png";
    static final String thm_png = "thm.png";
    static final String Quicklook = "Quicklook";
    static final String Thumbnail = "Thumbnail";

    static final DateFormat standardDateFormat = UTC.createDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    static final int ABS_CALIBRATION_FLAG_DEFAULT_VALUE = 0;
    static final int ANT_ELEV_CORR_FLAG_DEFAULT_VALUE = 1;
    static final int AZIMUTH_LOOKS_CPX_DEFAULT_VALUE = 1;
    static final int BISTATIC_CORRECTION_APPLIED_DEFAULT = 1;
    static final int COREGISTERED_STACK_DEFAULT_VALUE = 0;
    static final int INC_ANGLE_COMP_FLAG_DEFAULT_VALUE = 0;
    static final int RANGE_LOOKS_DEFAULT_VALUE = 1;
    static final int RANGE_SPREAD_COMP_FLAG_DEFAULT_VALUE = 1;
    static final int REPLICA_POWER_CORR_FLAG_DEFAULT_VALUE = 0;
    static final int SRGR_FLAG_AML_DEFAULT_VALUE = 1;
    static final int SRGR_FLAG_CPX_DEFAULT_VALUE = 0;

    static final int TIFFTagImageWidth = 256;
    static final int TIFFTagImageLength = 257;
    static final int TIFFTagModelTransformation = 34264;
    static final int TIFFTagGDAL_METADATA = 42112;

    static final int AMPLITUDE_BAND_INDEX = 0;
    static final int PHASE_BAND_INDEX = 1;
    static final int I_BAND_VIRTUAL_INDEX = 2;
    static final int Q_BAND_VIRTUAL_INDEX = 3;
    static final int QUICKLOOK_INDEX = 4;

    static final String amplitude_band_prefix = "Amplitude_";
    static final String phase_band_prefix = "Phase_";
    static final String i_band_prefix = "i_";
    static final String q_band_prefix = "q_";

    //
    // ICEYE STAC item tags
    //

    static final String iceye = "iceye:";
    static final String proj = "proj";
    static final String sar = "sar:";
    static final String sat = "sat:";
    static final String view = "view:";
    static final String SEP = ",";

    static final String acquisition_mode            = sar + "instrument_mode";
    static final String acquisition_start_utc       = "start_datetime";
    static final String acquisition_end_utc         = "end_datetime";
    static final String antenna_pointing            = sar + "observation_direction";
    static final String avg_scene_height            = iceye + "average_scene_height";
    static final String azimuth_bandwidth           = iceye + "processing_bandwidth_azimuth";
    static final String azimuth_looks               = sar + "looks_azimuth" ;
    static final String azimuth_spacing             = sar + "pixel_spacing_azimuth";
    static final String calibration_factor          = iceye + "calibration_factor";
    static final String centroid_estimates          = iceye + "doppler_centroid_coeffs";
    static final String data_take_id                = iceye + "image_id";
    static final String doppler_centroid_datetimes  = iceye + "doppler_centroid_datetimes";
    static final String doppler_rate_coffs          = iceye + "doppler_rate_coeffs";
    static final String first_line_time             = iceye + "zero_doppler_start_datetime";
    static final String grsr_coefficients           = iceye + "ground_to_slant_coeff";
    static final String grsr_zero_doppler_time      = "datetime";
    static final String incidence_far               = iceye + "incidence_angle_far";
    static final String incidence_near              = iceye + "incidence_angle_near";
    static final String inc_angle                   = view + "incidence_angle";
    static final String inc_angle_coeffs            = iceye + "incidence_angle_coeffs";
    static final String last_line_time              = iceye + "zero_doppler_end_datetime";
    static final String orbit_states                = iceye + "orbit_states";
    static final String PASS                        = sat + "orbit_state";
    static final String platform                    = "platform";
    static final String polarization                = sar + "polarizations";
    static final String ProcessingSystemIdentifier   = "processing:software" + SEP + "processor";
    static final String position                    = "position" ;
    static final String PROC_TIME                   = iceye + "processing_end_datetime";
    static final String product_name                = iceye + "filename";
    static final String product_type                = sar + "product_type";
    static final String pulse_repetition_frequency  = iceye + "acquisition_prf";
    static final String radar_frequency             = sar + "center_frequency";
    static final String range_bandwidth             = iceye + "pulse_bandwidth";
    static final String range_sampling_rate         = iceye + "acquisition_range_sampling_rate";
    static final String range_spacing               = sar + "pixel_spacing_range";
    static final String slant_range_to_first_pixel  = iceye + "range_near";
    static final String velocity                    = "velocity" ;
    static final String zero_doppler_time_utc       = iceye + "zero_doppler_start_datetime";
}
