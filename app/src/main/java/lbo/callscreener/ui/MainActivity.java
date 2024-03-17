package lbo.callscreener.ui;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import lbo.callscreener.R;

public class MainActivity extends Activity {

    private static final int REQUEST_ID = 1;

    private static final int READ_CONTACT_PERM_REQUEST = 2;

    SharedPreferences.Editor editor = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set activity layout
        setContentView(R.layout.main_activity);

        // Request role as call screening service
        requestRole();

        // Request read user contact
        getPermissionReadContact();

        // Get configuration buttons
        SharedPreferences sharedPref = getSharedPreferences("CallScreenerPref",MODE_PRIVATE);
        editor = sharedPref.edit();

        ToggleButton blockCommercial = (ToggleButton) findViewById(R.id.blockCommercial);
        // Set saved state
        blockCommercial.setChecked(sharedPref.getBoolean("BLOCK_COMMERCIAL", false));
        // Set click listener
        blockCommercial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putBoolean("BLOCK_COMMERCIAL", blockCommercial.isChecked());
                editor.apply();
            }
        });

        ToggleButton blockMobile = (ToggleButton) findViewById(R.id.blockMobile);
        // Set saved state
        blockMobile.setChecked(sharedPref.getBoolean("BLOCK_MOBILE", false));
        // Set click listener
        blockMobile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putBoolean("BLOCK_MOBILE", blockMobile.isChecked());
                editor.apply();
            }
        });

        ToggleButton blockOutgoing = (ToggleButton) findViewById(R.id.blockOutCalls);
        // Set saved state
        blockOutgoing.setChecked(sharedPref.getBoolean("BLOCK_OUTGOING", false));
        // Set click listener
        blockOutgoing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putBoolean("BLOCK_OUTGOING", blockOutgoing.isChecked());
                editor.apply();
            }
        });

        ToggleButton completeBlock = (ToggleButton) findViewById(R.id.completeBlock);
        // Set saved state
        completeBlock.setChecked(sharedPref.getBoolean("BLOCK_COMPLETE", false));
        // Set click listener
        completeBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putBoolean("BLOCK_COMPLETE", completeBlock.isChecked());
                editor.apply();
            }
        });
    }

    /**
     * Ask user to be role screening service
     */
    public void requestRole() {
        RoleManager manager = (RoleManager) getSystemService(ROLE_SERVICE);
        Intent intent = manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
        startActivityForResult(intent, REQUEST_ID);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_ID == requestCode) {
            if (Activity.RESULT_OK == resultCode) {
                Log.i("CallScreener", "Acquired role of call screener");
            } else {
                Log.e("CallScreener", "Failed to get call screener role");
            }
        }
    }

    /**
     * Request READ_CONTACTS permissions
     */
    public void getPermissionReadContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                Toast.makeText(this, "Needed to filter mobiles", Toast.LENGTH_SHORT).show();
            }

            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},
                    READ_CONTACT_PERM_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (READ_CONTACT_PERM_REQUEST == requestCode) {
            if (1 == grantResults.length && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                // Permission has been granted
                editor.putBoolean("READ_CONTACTS_PERMISSION", true);
                editor.apply();
            } else {
                editor.putBoolean("READ_CONTACTS_PERMISSION", false);
                editor.apply();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
