package lbo.callscreener.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lbo.callscreener.MainApplication;
import lbo.callscreener.ui.MainActivity;

/**
 * https://issuetracker.google.com/issues/141363242
 * https://issuetracker.google.com/issues/130081372
 *
 * https://www.fcc.gov/call-authentication
 */

public class CallScreenerService extends CallScreeningService {
    private SharedPreferences sharedPref = null;

    // Regular expression to match mobile numbers
    private Pattern mobilePattern = null;

    // Regular expression to match commercial numbers
    private Pattern commercialPattern = null;

    // Regular expression to match payed "services à valeur ajoutée" (SVA) numbers
    private Pattern svaPattern = null;

    private void initRegex() {
        mobilePattern = null;
        commercialPattern = null;
        svaPattern = null;

        // Number ranges are based on Arcep : https://www.arcep.fr/la-regulation/grands-dossiers-thematiques-transverses/la-numerotation.html
        mobilePattern = Pattern.compile("^(0|\\+33|\\+590|\\+594||\\+596|\\+262)(6|7[3-9])");
        commercialPattern = Pattern.compile("^(0|\\+33|\\+590|\\+594||\\+596|\\+262)(162|163|270|271|377|378|424|425|568|569|948|949|9475|9476|9477|9478|9479)");

        // Match charged numbers referenced by Arcep here: https://www.arcep.fr/demarches-et-services/utilisateurs/les-numeros-08-et-les-numeros-courts.html
        svaPattern = Pattern.compile("(^(0|\\+33|\\+590|\\+594||\\+596|\\+262)(81|82|89|836|860|868))|(^(1|3))");
    }

    /**
     * Initialize handle to Shared preferences if it is null, and return the preference value
     * @param pref Name of the preference
     * @return Value of the preference or false if it does not exists
     */
    private boolean initAndGetPref(@NonNull String pref) {
        sharedPref = null;

        sharedPref = getSharedPreferences("CallScreenerPref",MODE_PRIVATE);

        return sharedPref.getBoolean(pref, false);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        initRegex();
    }

    /**
     * Called for all incoming and outgoing calls which do not involve contacts in the user contact list
     * @param callDetails Information about a new call, see {@link Call.Details}.
     */
    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        switch (callDetails.getCallDirection()) {
            case Call.Details.DIRECTION_INCOMING:
                handleIncomingCall(callDetails);
                break;
            case Call.Details.DIRECTION_OUTGOING:
                handleOutgoingCall(callDetails);
                break;
            default:
                // Handle unknown direction, should not happen
                break;
        }
    }

    /**
     * Handle incoming calls
     * @param callDetails
     */
    private void handleIncomingCall(@NonNull Call.Details callDetails) {
        // Phone number is found in the call handle (Uri of the form tel:....)

        // Screen the incoming call based on the caller phone number
        boolean accepted = screenIncomingCall(callDetails.getHandle().getSchemeSpecificPart());

        // Try to get verification status
        int verificationStatus = callDetails.getCallerNumberVerificationStatus();

        // Build screening response
        CallResponse.Builder responseBuilder = new CallResponse.Builder();

        if (!accepted) {
            // Get blocking preference
            boolean blockComplete = sharedPref != null ?
                    sharedPref.getBoolean("BLOCK_COMPLETE", false) :
                    initAndGetPref("BLOCK_COMPLETE");

            if (blockComplete) {
                responseBuilder.setDisallowCall(true)     // Block call
                        .setRejectCall(true);              // Set call as disconnected as if the user had manually rejected it
            } else {
                responseBuilder.setSilenceCall(true);     // Keep trace of the call in the logs
            }
        }

        CallResponse response = responseBuilder.build();

        respondToCall(callDetails, response);
    }

    /**
     * Handle outgoing calls
     * @param callDetails
     */
    private void handleOutgoingCall(@NonNull Call.Details callDetails) {
        // Get blocking preferences
        boolean blockOutgoing = sharedPref != null ?
                sharedPref.getBoolean("BLOCK_OUTGOING", true) :
                initAndGetPref("BLOCK_OUTGOING");

        boolean accepted = true;

        if (blockOutgoing) {
            String phoneNumber = callDetails.getHandle().getSchemeSpecificPart();

            if (svaPattern == null) {
                initRegex();
            }

            if (blockOutgoing && svaPattern.matcher(phoneNumber).find()) {
                accepted = false;
            }
        }

        // Build screening response
        CallResponse.Builder responseBuilder = new CallResponse.Builder();

        if (!accepted) {
            responseBuilder.setDisallowCall(true);
        }

        CallResponse response = responseBuilder.build();

        respondToCall(callDetails, response);
    }

    /**
     * Check if the phone number is allowed or not
     * @param phoneNumber Caller phone number
     * @return true if phone number is accepted, false otherwise
     */
    private boolean screenIncomingCall(@NonNull String phoneNumber) {
        // Get blocking preferences
        boolean blockCommercial = sharedPref != null ?
                sharedPref.getBoolean("BLOCK_COMMERCIAL", true) :
                initAndGetPref("BLOCK_COMMERCIAL");
        boolean blockMobile = sharedPref != null ?
                sharedPref.getBoolean("BLOCK_MOBILE", true) :
                initAndGetPref("BLOCK_MOBILE");
        boolean hasPermission = sharedPref != null ?
                sharedPref.getBoolean("READ_CONTACTS_PERMISSION", false) :
                initAndGetPref("READ_CONTACTS_PERMISSION");

        // Test for mobile phone number
        // Range covers 06 and 073 - 079
        // Need permission to read contacts to do it
        if (mobilePattern == null || commercialPattern == null) {
            initRegex();
        }

        Matcher mobileMatcher = mobilePattern.matcher(phoneNumber);
        if (
                blockMobile && hasPermission && mobileMatcher.find()
        ) {
            if (isCallerKnown(phoneNumber)) {
                return true;
            } else {
                return false;
            }
        }

        // Test for commercial numbers
        Matcher commercialMatcher = commercialPattern.matcher(phoneNumber);
        if (
                blockCommercial && commercialMatcher.find()
        ) {
            return false;
        }

        return true;
    }

    /**
     * Check if the caller is in the contact list
     * @param phoneNumber number of the caller
     * @return true if the phone number is in the contact list
     */
    private boolean isCallerKnown(@NonNull String phoneNumber) {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
        );

        ContentResolver resolver = getContentResolver();

        String[] contactProjection = {
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER,
                ContactsContract.PhoneLookup._ID
        };

        Cursor cursor = resolver.query(uri, contactProjection, null, null, null);

        if (null != cursor && cursor.moveToFirst()) {
            cursor.close();
            return true;
        }

        if (null != cursor) {
            cursor.close();
        }

        return false;
    }
}
