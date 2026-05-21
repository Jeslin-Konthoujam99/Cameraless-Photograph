package com.example.photoinfouploader;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Helper class to get location details (city, district, country) from coordinates
 * Uses Android's Geocoder for reverse geocoding
 */
public class GeocodingHelper {
    private static final String TAG = "GeocodingHelper";
    private Geocoder geocoder;

    public GeocodingHelper(Context context) {
        this.geocoder = new Geocoder(context, Locale.getDefault());
    }

    /**
     * Get detailed address information from latitude and longitude
     *
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return AddressInfo object with city, district, country, etc. or null if not found
     */
    public AddressInfo getAddressFromCoordinates(double latitude, double longitude) {
        try {
            Log.d(TAG, "Geocoding coordinates: " + latitude + ", " + longitude);

            // Get addresses from coordinates (1 result is enough)
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);

                // Create AddressInfo object
                AddressInfo info = new AddressInfo();

                // In Taiwan, getLocality() returns VILLAGE names (e.g., "Minhui Village")
                // adminArea returns the actual city (e.g., "Taipei City" / "台北市")
                // subAdminArea returns the district (e.g., "Da'an District" / "大安區")
                String adminArea = address.getAdminArea();
                String subAdminArea = address.getSubAdminArea();

                // City: ALWAYS use adminArea (cleaned), because getLocality() is too specific
                if (adminArea != null && !adminArea.isEmpty()) {
                    info.city = adminArea
                            .replace(" City", "")
                            .replace(" County", "")
                            .replace("市", "")
                            .replace("縣", "")
                            .trim();
                } else {
                    // Last resort fallback
                    info.city = address.getLocality();
                }

                // District: use subAdminArea
                info.district = subAdminArea;
                info.subDistrict = address.getThoroughfare();         // Street/Sub-area
                info.country = address.getCountryName();              // Country (e.g., "Taiwan")
                info.countryCode = address.getCountryCode();          // Country code (e.g., "TW")
                info.adminArea = address.getAdminArea();              // State/Province
                info.fullAddress = address.getAddressLine(0);         // Full address line

                // Log the results
                Log.d(TAG, "✅ Geocoding successful:");
                Log.d(TAG, "   City: " + info.city);
                Log.d(TAG, "   District: " + info.district);
                Log.d(TAG, "   SubDistrict: " + info.subDistrict);
                Log.d(TAG, "   Country: " + info.country);
                Log.d(TAG, "   AdminArea: " + info.adminArea);
                Log.d(TAG, "   CountryCode: " + info.countryCode);

                return info;
            } else {
                Log.w(TAG, "⚠️ No address found for coordinates");
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ Geocoding error: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get just the city name from coordinates
     */
    public String getCityName(double latitude, double longitude) {
        AddressInfo info = getAddressFromCoordinates(latitude, longitude);
        return (info != null && info.city != null) ? info.city : "Unknown";
    }

    /**
     * Get just the district name from coordinates
     */
    public String getDistrictName(double latitude, double longitude) {
        AddressInfo info = getAddressFromCoordinates(latitude, longitude);
        return (info != null && info.district != null) ? info.district : "Unknown";
    }

    /**
     * Get just the country name from coordinates
     */
    public String getCountryName(double latitude, double longitude) {
        AddressInfo info = getAddressFromCoordinates(latitude, longitude);
        return (info != null && info.country != null) ? info.country : "Unknown";
    }

    /**
     * Data class to hold address information
     *
     * Example for Taipei, Taiwan:
     * - city: "Taipei"
     * - district: "Taipei" (district level)
     * - subDistrict: "Dunnan Road" (street name)
     * - country: "Taiwan"
     * - countryCode: "TW"
     * - adminArea: "Taipei" (state/province)
     * - fullAddress: "123 Dunnan Road, Taipei 10001, Taiwan"
     */
    public static class AddressInfo {
        public String city;           // City name
        public String district;       // District (sub-region)
        public String subDistrict;    // Street name or sub-locality
        public String country;        // Country name
        public String countryCode;    // ISO country code
        public String adminArea;      // State/Province/Region
        public String fullAddress;    // Complete address line

        @Override
        public String toString() {
            return city + ", " + district + ", " + country;
        }

        /**
         * Get formatted location string
         * Example: "Taipei, Taipei, Taiwan"
         */
        public String getFormattedLocation() {
            StringBuilder sb = new StringBuilder();

            if (city != null && !city.isEmpty()) {
                sb.append(city);
            }

            if (district != null && !district.isEmpty() && !district.equals(city)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(district);
            }

            if (country != null && !country.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(country);
            }

            return sb.toString().isEmpty() ? "Unknown Location" : sb.toString();
        }

        /**
         * Get location with country code
         * Example: "Taipei, Taiwan (TW)"
         */
        public String getLocationWithCode() {
            if (country == null || countryCode == null) {
                return getFormattedLocation();
            }
            return getFormattedLocation() + " (" + countryCode + ")";
        }
    }
}