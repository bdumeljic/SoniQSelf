package com.bdumeljic.soniqself;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class PermissionsActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 0;

    private LinearLayout mAskPermission;
    private Button mAskPermissionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startPlayAct();
        } else {
            setContentView(R.layout.activity_permissions);
            checkPermissions();
            mAskPermission = (LinearLayout) findViewById(R.id.need_permission);
            mAskPermissionButton = (Button) findViewById(R.id.need_permission_button);
            mAskPermissionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkPermissions();
                }
            });
        }

    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_FINE_LOCATION);
        } else {
            startPlayAct();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPlayAct();
            } else {
                mAskPermission.setVisibility(View.VISIBLE);
            }
        }
    }

    private void startPlayAct() {
        startActivity(new Intent(this, PlayDayActivity.class));
        finish();
    }
}

